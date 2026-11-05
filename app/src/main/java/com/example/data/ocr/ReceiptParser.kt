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
   * extracting all 7 categories from each area,
   * merging multiple areas without loss,
   * and falling back to global full-text extraction for any missing categories.
   */
  fun parseOcrResult(ocrResult: OcrResult, activeTemplates: List<OcrTemplate> = emptyList()): Receipt {
    if (ocrResult.isEmpty) {
      return Receipt(rawOcrText = "")
    }

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

        val mergedAreas = MultiCategoryExtractor.mergeExtractions(areaExtractions)
        val fullTextExtraction = MultiCategoryExtractor.extractFromTextLines(ocrResult.fullText.lines())

        val finalTanggal = mergedAreas.tanggal?.value ?: fullTextExtraction.tanggal?.value ?: baseFullTextReceipt.transactionDate
        val finalWaktu = mergedAreas.waktu?.value ?: fullTextExtraction.waktu?.value ?: baseFullTextReceipt.transactionTime
        val finalRef = mergedAreas.referensi?.value ?: fullTextExtraction.referensi?.value ?: baseFullTextReceipt.refNumber
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

        val allReviews = (mergedAreas.reviewRequired + fullTextExtraction.reviewRequired + baseFullTextReceipt.reviewFlags).distinct()

        return baseFullTextReceipt.copy(
          transactionDate = finalTanggal,
          transactionTime = finalWaktu,
          refNumber = finalRef,
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

    val tanggal = extraction.tanggal?.value ?: ""
    val waktu = extraction.waktu?.value ?: ""
    val refNumber = extraction.referensi?.value ?: extractRefNumber(lines)
    val recipientName = extraction.penerima?.value ?: ""
    val recipientAccount = extraction.rekening?.value ?: ""
    val amount = extraction.nominal?.value ?: 0L
    val adminFee = extraction.admin?.value ?: 0L
    val notes = extractNotes(lines)

    return Receipt(
      recipientName = recipientName,
      recipientBank = detectedBank,
      recipientAccount = recipientAccount,
      amount = amount,
      adminFee = adminFee,
      transactionDate = tanggal,
      transactionTime = waktu,
      refNumber = refNumber,
      notes = notes,
      candidateConflicts = extraction.conflicts,
      reviewFlags = extraction.reviewRequired,
      rawOcrText = rawText
    )
  }

  fun extractRefNumber(lines: List<String>): String {
    val refKeywords = Regex(
      "(?:no(?:mor)?\\s*referensi|no\\.?\\s*ref|id\\s*transaksi|no\\.?\\s*transaksi|reff?|reference|serial\\s*number|sn|kode\\s*transaksi)",
      RegexOption.IGNORE_CASE
    )

    for (i in lines.indices) {
      val line = lines[i]
      if (refKeywords.containsMatchIn(line)) {
        val split = line.split(Regex("[:=-]"))
        if (split.size > 1) {
          val ref = split.drop(1).joinToString(":").trim()
          val (cleaned, issue) = FieldValidator.sanitizeRefNumber(ref)
          if (cleaned.isNotBlank() && issue == null) return cleaned
        }
        if (i + 1 < lines.size) {
          val next = lines[i + 1].trim()
          val (cleaned, issue) = FieldValidator.sanitizeRefNumber(next)
          if (cleaned.isNotBlank() && issue == null) return cleaned
        }
      }
    }

    return ""
  }

  fun extractNotes(lines: List<String>): String {
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
