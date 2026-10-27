package com.example.data.ocr

import com.example.data.model.AreaConfigHelper
import com.example.data.model.OcrResult
import com.example.data.model.OcrTemplate
import com.example.data.model.Receipt
import com.example.data.model.TargetRecipientField
import java.util.Locale

object ReceiptParser {

  /**
   * Main entry point for OCR parsing.
   * Leverages user-defined areas if an active template with areas is matched,
   * extracting all 5 categories from each area (a single area can produce multiple categories),
   * merging multiple areas without loss or silent overwriting,
   * and falling back to global full-text extraction for any missing categories.
   */
  fun parseOcrResult(ocrResult: OcrResult, activeTemplates: List<OcrTemplate> = emptyList()): Receipt {
    if (ocrResult.isEmpty) {
      return Receipt(rawOcrText = "")
    }

    // Check if an active template has area configs and matches detection keywords
    val matchedTemplateWithAreas = activeTemplates.firstOrNull { tpl ->
      if (tpl.areaConfigJson.isBlank()) return@firstOrNull false
      val keywords = tpl.detectionKeywords.split(",").map { it.trim().lowercase(Locale.ROOT) }
      keywords.any { kw -> kw.isNotEmpty() && ocrResult.fullText.lowercase(Locale.ROOT).contains(kw) }
    }

    val baseFullTextReceipt = parse(ocrResult.fullText, activeTemplates)

    if (matchedTemplateWithAreas != null) {
      val areas = AreaConfigHelper.fromJson(matchedTemplateWithAreas.areaConfigJson)
      if (areas.isNotEmpty()) {
        val areaExtractions = mutableListOf<MultiCategoryExtraction>()

        // 1. Extract all categories from each bounding area (one area can produce multiple categories)
        for (area in areas) {
          val structuralLines = AreaConfigHelper.extractLinesForArea(area, ocrResult)
          val extraction = if (structuralLines.isNotEmpty()) {
            MultiCategoryExtractor.extractFromLines(
              lines = structuralLines,
              designatedField = area.field,
              sourceAreaId = area.id
            )
          } else {
            val textLines = AreaConfigHelper.extractTextForArea(area, ocrResult).lines()
            MultiCategoryExtractor.extractFromTextLines(
              rawLines = textLines,
              designatedField = area.field,
              sourceAreaId = area.id
            )
          }
          areaExtractions.add(extraction)
        }

        // 2. Merge extractions from all areas
        val mergedAreas = MultiCategoryExtractor.mergeExtractions(areaExtractions)

        // 3. Fallback extraction from full text for any missing categories
        val fullTextExtraction = MultiCategoryExtractor.extractFromTextLines(ocrResult.fullText.lines())

        // Merge: Area extractions take precedence, full text fills missing gaps
        val finalWaktu = mergedAreas.waktu?.value ?: fullTextExtraction.waktu?.value ?: baseFullTextReceipt.transactionDate
        val finalBank = mergedAreas.bank?.value ?: fullTextExtraction.bank?.value ?: baseFullTextReceipt.recipientBank
        val finalRekening = mergedAreas.rekening?.value ?: fullTextExtraction.rekening?.value ?: baseFullTextReceipt.recipientAccount
        val finalPenerima = mergedAreas.penerima?.value ?: fullTextExtraction.penerima?.value ?: baseFullTextReceipt.recipientName
        val finalNominal = mergedAreas.nominal?.value ?: fullTextExtraction.nominal?.value ?: baseFullTextReceipt.amount
        val finalAdmin = mergedAreas.admin?.value ?: fullTextExtraction.admin?.value ?: baseFullTextReceipt.adminFee

        val allConflicts = mutableMapOf<TargetRecipientField, List<String>>()
        mergedAreas.conflicts.forEach { (k, v) -> allConflicts[k] = v }
        fullTextExtraction.conflicts.forEach { (k, v) ->
          if (!allConflicts.containsKey(k)) allConflicts[k] = v
        }

        val allReviews = (mergedAreas.reviewRequired + fullTextExtraction.reviewRequired).distinct()

        return baseFullTextReceipt.copy(
          transactionDate = finalWaktu,
          recipientBank = finalBank,
          recipientAccount = finalRekening,
          recipientName = finalPenerima,
          amount = finalNominal,
          adminFee = finalAdmin,
          candidateConflicts = allConflicts,
          reviewFlags = allReviews,
          ocrResult = ocrResult
        )
      }
    }

    return baseFullTextReceipt.copy(ocrResult = ocrResult)
  }

  /**
   * Parses raw full text using MultiCategoryExtractor and supporting extraction logic.
   */
  fun parse(rawText: String, activeTemplates: List<OcrTemplate> = emptyList()): Receipt {
    if (rawText.isBlank()) {
      return Receipt(transactionDate = "", rawOcrText = rawText)
    }

    val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }

    // Run multi-category extraction on text lines
    val extraction = MultiCategoryExtractor.extractFromTextLines(lines)

    // Template bank override check
    var detectedBank = extraction.bank?.value ?: ""
    if (detectedBank.isBlank()) {
      for (tpl in activeTemplates) {
        val keywords = tpl.detectionKeywords.split(",").map { it.trim().lowercase(Locale.ROOT) }
        val matches = keywords.any { kw -> kw.isNotEmpty() && rawText.lowercase(Locale.ROOT).contains(kw) }
        if (matches) {
          detectedBank = tpl.bankName
          break
        }
      }
    }

    val recipientName = extraction.penerima?.value ?: ""
    val recipientAccount = extraction.rekening?.value ?: ""
    val amount = extraction.nominal?.value ?: 0L
    val transactionDate = extraction.waktu?.value ?: ""
    val adminFee = extraction.admin?.value ?: 0L

    return Receipt(
      recipientName = recipientName,
      recipientBank = detectedBank,
      recipientAccount = recipientAccount,
      amount = amount,
      adminFee = adminFee,
      transactionDate = transactionDate,
      refNumber = "",
      notes = "",
      candidateConflicts = extraction.conflicts,
      reviewFlags = extraction.reviewRequired,
      rawOcrText = rawText
    )
  }

  private fun extractRefNumber(lines: List<String>): String {
    val refKeywords = Regex(
      "(?:no(?:mor)?\\s*referensi|no\\.?\\s*ref|id\\s*transaksi|no\\.?\\s*transaksi|reff?|reference)",
      RegexOption.IGNORE_CASE
    )

    for (i in lines.indices) {
      val line = lines[i]
      if (refKeywords.containsMatchIn(line)) {
        val split = line.split(Regex("[:=-]"))
        if (split.size > 1) {
          val ref = split.drop(1).joinToString(":").trim()
          if (ref.length >= 4) return ref
        }
        if (i + 1 < lines.size) {
          val next = lines[i + 1].trim()
          if (next.length in 6..30) return next
        }
      }
    }

    return ""
  }

  private fun extractNotes(lines: List<String>): String {
    val notesKeywords = Regex("(?:berita|catatan|keterangan|pesan|note|remark)", RegexOption.IGNORE_CASE)

    for (i in lines.indices) {
      val line = lines[i]
      if (notesKeywords.containsMatchIn(line)) {
        val split = line.split(Regex("[:=-]"))
        if (split.size > 1) {
          val note = split.drop(1).joinToString(":").trim()
          if (note.isNotEmpty() && !note.equals("-", ignoreCase = true)) return note
        }
        if (i + 1 < lines.size) {
          val next = lines[i + 1].trim()
          if (next.isNotEmpty() && next.length < 50) return next
        }
      }
    }

    return ""
  }
}
