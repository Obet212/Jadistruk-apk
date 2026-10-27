package com.example.data.ocr

import com.example.data.model.FieldBoundingArea
import com.example.data.model.OcrResult
import com.example.data.model.OcrTextLine
import com.example.data.model.TargetRecipientField
import java.util.Locale

data class ExtractedCandidate<T>(
  val field: TargetRecipientField,
  val value: T,
  val rawText: String,
  val confidence: Int, // 0..100
  val sourceAreaId: String? = null,
  val reason: String = "",
)

data class MultiCategoryExtraction(
  val waktu: ExtractedCandidate<String>? = null,
  val bank: ExtractedCandidate<String>? = null,
  val rekening: ExtractedCandidate<String>? = null,
  val penerima: ExtractedCandidate<String>? = null,
  val nominal: ExtractedCandidate<Long>? = null,
  val admin: ExtractedCandidate<Long>? = null,
  val conflicts: Map<TargetRecipientField, List<String>> = emptyMap(),
  val reviewRequired: List<String> = emptyList(),
)

object MultiCategoryExtractor {

  // Label patterns
  private val BANK_LABEL = Regex("(?i)^(?:bank\\s*tujuan|nama\\s*bank|ke\\s*bank|bank\\s*penerima|tujuan\\s*transfer|bank)[:\\-\\s]*$")
  private val RECIPIENT_LABEL = Regex("(?i)^(?:nama\\s*penerima|nama\\s*tujuan|nama\\s*lengkap|penerima|tujuan\\s*transfer|penerima\\s*transfer|ke\\s*nama|kirim\\s*ke|tujuan|ke)[:\\-\\s]*$")
  private val SENDER_LABEL = Regex("(?i)(?:pengirim|nama\\s*pengirim|dari|sumber\\s*dana|rekening\\s*asal|rek\\s*asal)")
  private val ACCOUNT_LABEL = Regex("(?i)^(?:nomor\\s*rekening|no\\.?\\s*rekening|rekening\\s*tujuan|no\\.?\\s*rek|nomor\\s*hp|no\\.?\\s*hp|nomor\\s*ponsel|dana\\s*id|akun\\s*tujuan|ke\\s*rekening)[:\\-\\s]*$")
  private val DATE_LABEL = Regex("(?i)^(?:tanggal|tgl|waktu|date|time|tgl\\s*transaksi|waktu\\s*transaksi)[:\\-\\s]*$")
  private val AMOUNT_LABEL = Regex("(?i)^(?:nominal\\s*transfer|jumlah\\s*transfer|nominal|jumlah|amount|total\\s*diterima|total\\s*transfer|total\\s*bayar|total)[:\\-\\s]*$")
  private val ADMIN_LABEL = Regex("(?i)^(?:biaya\\s*admin|biaya\\s*transfer|admin\\s*fee|admin|fee)[:\\-\\s]*$")
  private val STATUS_OR_NOISE = Regex("(?i)^(?:transaksi\\s*berhasil|transfer\\s*berhasil|bukti\\s*transfer|status|berhasil|sukses|selesai|detail\\s*transaksi|kembali|bagikan|unduh|qris|bi-fast|real\\s*time\\s*online|llg|rtgs)$")

  /**
   * Extracts all 5 categories from structured OCR lines within an area.
   * If designatedField is provided, candidates matching that field receive a priority boost.
   */
  fun extractFromLines(
    lines: List<OcrTextLine>,
    designatedField: TargetRecipientField? = null,
    sourceAreaId: String? = null
  ): MultiCategoryExtraction {
    val cleanLineTexts = lines.map { it.text.trim() }.filter { it.isNotBlank() }
    return extractFromTextLines(cleanLineTexts, designatedField, sourceAreaId)
  }

  /**
   * Core multi-category engine operating on sequential lines in reading order.
   * One area containing multiple fields will have each field recognized into its own category.
   */
  fun extractFromTextLines(
    rawLines: List<String>,
    designatedField: TargetRecipientField? = null,
    sourceAreaId: String? = null
  ): MultiCategoryExtraction {
    var waktuCandidate: ExtractedCandidate<String>? = null
    var bankCandidate: ExtractedCandidate<String>? = null
    var rekeningCandidate: ExtractedCandidate<String>? = null
    var penerimaCandidate: ExtractedCandidate<String>? = null
    var nominalCandidate: ExtractedCandidate<Long>? = null
    var adminCandidate: ExtractedCandidate<Long>? = null

    val conflicts = mutableMapOf<TargetRecipientField, MutableList<String>>()
    val reviewRequired = mutableListOf<String>()

    val n = rawLines.size
    val consumedIndices = mutableSetOf<Int>()

    // PASS 1: Explicit Label-Value Pairs (both inline "LABEL: VALUE" and multi-line "LABEL \n VALUE")
    var i = 0
    while (i < n) {
      val line = rawLines[i]
      val nextLine = if (i + 1 < n) rawLines[i + 1] else null

      // Check SENDER label to strictly skip sender information
      if (SENDER_LABEL.containsMatchIn(line)) {
        consumedIndices.add(i)
        if (nextLine != null && !hasKnownFieldLabel(nextLine)) {
          consumedIndices.add(i + 1)
          i += 2
          continue
        }
        i++
        continue
      }

      // 1. BANK detection from label
      if (isBankLabel(line) || hasBankPrefix(line)) {
        val inlineVal = extractInlineValue(line)
        val candidateText = if (inlineVal.isNotBlank()) inlineVal else nextLine ?: ""
        val match = BankRegistry.matchBank(candidateText)
        if (match.isConfident) {
          val conf = if (designatedField == TargetRecipientField.BANK) 98 else 95
          bankCandidate = ExtractedCandidate(
            field = TargetRecipientField.BANK,
            value = match.officialName,
            rawText = candidateText,
            confidence = conf,
            sourceAreaId = sourceAreaId,
            reason = "Cocok dengan label Bank dan kamus resmi: ${match.officialName}"
          )
          consumedIndices.add(i)
          if (inlineVal.isBlank() && nextLine != null) {
            consumedIndices.add(i + 1)
            i++
          }
        }
      }

      // 2. PENERIMA detection from label
      else if (isRecipientLabel(line) || hasRecipientPrefix(line)) {
        val inlineVal = extractInlineValue(line)
        val candidateText = if (inlineVal.isNotBlank()) inlineVal else nextLine ?: ""
        val (sanitized, issue) = FieldValidator.sanitizeRecipientName(candidateText)
        if (sanitized.isNotBlank() && issue == null) {
          val conf = if (designatedField == TargetRecipientField.PENERIMA) 98 else 95
          penerimaCandidate = ExtractedCandidate(
            field = TargetRecipientField.PENERIMA,
            value = sanitized,
            rawText = candidateText,
            confidence = conf,
            sourceAreaId = sourceAreaId,
            reason = "Cocok dengan label Penerima: $sanitized"
          )
          consumedIndices.add(i)
          if (inlineVal.isBlank() && nextLine != null) {
            consumedIndices.add(i + 1)
            i++
          }
        }
      }

      // 3. REKENING detection from label
      else if (isAccountLabel(line) || hasAccountPrefix(line)) {
        val inlineVal = extractInlineValue(line)
        val candidateText = if (inlineVal.isNotBlank()) inlineVal else nextLine ?: ""
        val (sanitized, issue) = FieldValidator.sanitizeAccount(candidateText)
        if (sanitized.isNotBlank() && issue == null) {
          val conf = if (designatedField == TargetRecipientField.REKENING) 98 else 95
          rekeningCandidate = ExtractedCandidate(
            field = TargetRecipientField.REKENING,
            value = sanitized,
            rawText = candidateText,
            confidence = conf,
            sourceAreaId = sourceAreaId,
            reason = "Nomor rekening valid 10–15 digit: $sanitized"
          )
          consumedIndices.add(i)
          if (inlineVal.isBlank() && nextLine != null) {
            consumedIndices.add(i + 1)
            i++
          }
        } else if (candidateText.isNotBlank()) {
          reviewRequired.add("Nomor rekening setelah label '$line' tidak memenuhi syarat 10–15 digit.")
        }
      }

      // 4. WAKTU detection from label
      else if (isDateLabel(line) || hasDatePrefix(line)) {
        val inlineVal = extractInlineValue(line)
        val candidateText = if (inlineVal.isNotBlank()) inlineVal else nextLine ?: ""
        val (sanitized, issue) = FieldValidator.sanitizeDateTime(candidateText)
        if (sanitized.isNotBlank() && issue == null) {
          val conf = if (designatedField == TargetRecipientField.WAKTU) 98 else 95
          waktuCandidate = ExtractedCandidate(
            field = TargetRecipientField.WAKTU,
            value = sanitized,
            rawText = candidateText,
            confidence = conf,
            sourceAreaId = sourceAreaId,
            reason = "Waktu transaksi tervalidasi: $sanitized"
          )
          consumedIndices.add(i)
          if (inlineVal.isBlank() && nextLine != null) {
            consumedIndices.add(i + 1)
            i++
          }
        }
      }

      // 5. NOMINAL detection from label
      else if (isAmountLabel(line) || hasAmountPrefix(line)) {
        val inlineVal = extractInlineValue(line)
        val candidateText = if (inlineVal.isNotBlank()) inlineVal else nextLine ?: ""
        val (nominalVal, issue) = FieldValidator.parseNominal(candidateText)
        if (nominalVal > 0L && issue == null) {
          val conf = if (designatedField == TargetRecipientField.NOMINAL) 98 else 95
          nominalCandidate = ExtractedCandidate(
            field = TargetRecipientField.NOMINAL,
            value = nominalVal,
            rawText = candidateText,
            confidence = conf,
            sourceAreaId = sourceAreaId,
            reason = "Nominal transfer Rupiah: $nominalVal"
          )
          consumedIndices.add(i)
          if (inlineVal.isBlank() && nextLine != null) {
            consumedIndices.add(i + 1)
            i++
          }
        }
      }

      // 6. ADMIN detection from label
      else if (isAdminLabel(line) || hasAdminPrefix(line)) {
        val inlineVal = extractInlineValue(line)
        val candidateText = if (inlineVal.isNotBlank()) inlineVal else nextLine ?: ""
        val fee = FieldValidator.parseAdmin(candidateText)
        adminCandidate = ExtractedCandidate(
          field = TargetRecipientField.ADMIN,
          value = fee,
          rawText = candidateText,
          confidence = 90,
          sourceAreaId = sourceAreaId,
          reason = "Biaya admin: $fee"
        )
        consumedIndices.add(i)
        if (inlineVal.isBlank() && nextLine != null) {
          consumedIndices.add(i + 1)
          i++
        }
      }

      i++
    }

    // PASS 2: Pattern, Dictionary, and Format Matching for Remaining Unconsumed Lines
    for (idx in 0 until n) {
      if (idx in consumedIndices) continue
      val line = rawLines[idx]
      if (STATUS_OR_NOISE.matches(line)) continue
      if (SENDER_LABEL.containsMatchIn(line)) continue

      // A. WAKTU pattern matching
      if (waktuCandidate == null) {
        val (dateStr, issue) = FieldValidator.sanitizeDateTime(line)
        if (dateStr.isNotBlank() && issue == null) {
          val conf = if (designatedField == TargetRecipientField.WAKTU) 92 else 85
          waktuCandidate = ExtractedCandidate(
            field = TargetRecipientField.WAKTU,
            value = dateStr,
            rawText = line,
            confidence = conf,
            sourceAreaId = sourceAreaId,
            reason = "Pola format tanggal/jam terverifikasi: $dateStr"
          )
          consumedIndices.add(idx)
          continue
        }
      }

      // B. BANK dictionary matching
      if (bankCandidate == null) {
        val match = BankRegistry.matchBank(line)
        if (match.isConfident) {
          val conf = if (designatedField == TargetRecipientField.BANK) 92 else 85
          bankCandidate = ExtractedCandidate(
            field = TargetRecipientField.BANK,
            value = match.officialName,
            rawText = line,
            confidence = conf,
            sourceAreaId = sourceAreaId,
            reason = "Nama cocok dengan kamus bank: ${match.officialName}"
          )
          consumedIndices.add(idx)
          continue
        }
      }

      // C. NOMINAL currency pattern matching (Rp / IDR)
      if (nominalCandidate == null) {
        if (line.contains("Rp", ignoreCase = true) || line.contains("IDR", ignoreCase = true)) {
          val (amt, issue) = FieldValidator.parseNominal(line)
          if (amt > 0L && issue == null) {
            val conf = if (designatedField == TargetRecipientField.NOMINAL) 92 else 85
            nominalCandidate = ExtractedCandidate(
              field = TargetRecipientField.NOMINAL,
              value = amt,
              rawText = line,
              confidence = conf,
              sourceAreaId = sourceAreaId,
              reason = "Pola mata uang Rupiah terdeteksi: $amt"
            )
            consumedIndices.add(idx)
            continue
          }
        }
      }

      // D. REKENING digit sequence matching (strictly 10–15 digits)
      if (rekeningCandidate == null) {
        val (accountStr, issue) = FieldValidator.sanitizeAccount(line)
        if (accountStr.isNotBlank() && issue == null) {
          val conf = if (designatedField == TargetRecipientField.REKENING) 92 else 80
          rekeningCandidate = ExtractedCandidate(
            field = TargetRecipientField.REKENING,
            value = accountStr,
            rawText = line,
            confidence = conf,
            sourceAreaId = sourceAreaId,
            reason = "Digit rekening 10–15 angka: $accountStr"
          )
          consumedIndices.add(idx)
          continue
        }
      }

      // E. PENERIMA name matching
      if (penerimaCandidate == null) {
        val (nameStr, issue) = FieldValidator.sanitizeRecipientName(line)
        if (nameStr.isNotBlank() && issue == null) {
          // Verify it's not numbers, not noise, and has letters
          if (!line.any { it.isDigit() } && nameStr.length in 3..50) {
            val conf = if (designatedField == TargetRecipientField.PENERIMA) 90 else 70
            penerimaCandidate = ExtractedCandidate(
              field = TargetRecipientField.PENERIMA,
              value = nameStr,
              rawText = line,
              confidence = conf,
              sourceAreaId = sourceAreaId,
              reason = "Nama penerima terdeteksi: $nameStr"
            )
            consumedIndices.add(idx)
            continue
          }
        }
      }
    }

    return MultiCategoryExtraction(
      waktu = waktuCandidate,
      bank = bankCandidate,
      rekening = rekeningCandidate,
      penerima = penerimaCandidate,
      nominal = nominalCandidate,
      admin = adminCandidate,
      conflicts = conflicts,
      reviewRequired = reviewRequired
    )
  }

  /**
   * Merges multi-category extractions from multiple distinct areas or fallback full text.
   * Highest confidence candidate wins; conflicts between equally strong candidates are logged for review.
   */
  fun mergeExtractions(
    extractions: List<MultiCategoryExtraction>
  ): MultiCategoryExtraction {
    var waktu: ExtractedCandidate<String>? = null
    var bank: ExtractedCandidate<String>? = null
    var rekening: ExtractedCandidate<String>? = null
    var penerima: ExtractedCandidate<String>? = null
    var nominal: ExtractedCandidate<Long>? = null
    var admin: ExtractedCandidate<Long>? = null

    val allConflicts = mutableMapOf<TargetRecipientField, MutableList<String>>()
    val allReviews = mutableListOf<String>()

    for (ext in extractions) {
      allReviews.addAll(ext.reviewRequired)
      ext.conflicts.forEach { (field, list) ->
        allConflicts.getOrPut(field) { mutableListOf() }.addAll(list)
      }

      // Merge WAKTU
      waktu = mergeField(TargetRecipientField.WAKTU, waktu, ext.waktu, allConflicts, allReviews)
      // Merge BANK
      bank = mergeField(TargetRecipientField.BANK, bank, ext.bank, allConflicts, allReviews)
      // Merge REKENING
      rekening = mergeField(TargetRecipientField.REKENING, rekening, ext.rekening, allConflicts, allReviews)
      // Merge PENERIMA
      penerima = mergeField(TargetRecipientField.PENERIMA, penerima, ext.penerima, allConflicts, allReviews)
      // Merge NOMINAL
      nominal = mergeField(TargetRecipientField.NOMINAL, nominal, ext.nominal, allConflicts, allReviews)
      // Merge ADMIN
      admin = mergeField(TargetRecipientField.ADMIN, admin, ext.admin, allConflicts, allReviews)
    }

    return MultiCategoryExtraction(
      waktu = waktu,
      bank = bank,
      rekening = rekening,
      penerima = penerima,
      nominal = nominal,
      admin = admin,
      conflicts = allConflicts,
      reviewRequired = allReviews.distinct()
    )
  }

  private fun <T> mergeField(
    field: TargetRecipientField,
    current: ExtractedCandidate<T>?,
    incoming: ExtractedCandidate<T>?,
    conflicts: MutableMap<TargetRecipientField, MutableList<String>>,
    reviews: MutableList<String>
  ): ExtractedCandidate<T>? {
    if (incoming == null) return current
    if (current == null) return incoming

    if (incoming.value == current.value) {
      return if (incoming.confidence > current.confidence) incoming else current
    }

    // Different values
    return if (incoming.confidence > current.confidence + 10) {
      incoming
    } else if (current.confidence > incoming.confidence + 10) {
      current
    } else {
      // Conflict: equally plausible candidates!
      conflicts.getOrPut(field) { mutableListOf() }.apply {
        if (!contains(current.value.toString())) add(current.value.toString())
        if (!contains(incoming.value.toString())) add(incoming.value.toString())
      }
      reviews.add("Konflik nilai pada kolom ${field.fieldName}: '${current.value}' vs '${incoming.value}'")
      // Retain current but flag for user review
      current
    }
  }

  // --- Helper methods for label identification ---
  private fun isBankLabel(str: String): Boolean = BANK_LABEL.matches(str.trim())
  private fun hasBankPrefix(str: String): Boolean =
    str.contains(":") && Regex("(?i)^(?:bank\\s*tujuan|nama\\s*bank|ke\\s*bank|bank\\s*penerima|bank)").containsMatchIn(str)

  private fun isRecipientLabel(str: String): Boolean = RECIPIENT_LABEL.matches(str.trim())
  private fun hasRecipientPrefix(str: String): Boolean =
    str.contains(":") && Regex("(?i)^(?:nama\\s*penerima|nama\\s*tujuan|nama\\s*lengkap|penerima|tujuan\\s*transfer|ke\\s*nama|kirim\\s*ke)").containsMatchIn(str)

  private fun isAccountLabel(str: String): Boolean = ACCOUNT_LABEL.matches(str.trim())
  private fun hasAccountPrefix(str: String): Boolean =
    str.contains(":") && Regex("(?i)^(?:nomor\\s*rekening|no\\.?\\s*rekening|rekening\\s*tujuan|no\\.?\\s*rek|nomor\\s*hp|no\\.?\\s*hp|dana\\s*id)").containsMatchIn(str)

  private fun isDateLabel(str: String): Boolean = DATE_LABEL.matches(str.trim())
  private fun hasDatePrefix(str: String): Boolean =
    str.contains(":") && Regex("(?i)^(?:tanggal|tgl|waktu|date|time)").containsMatchIn(str)

  private fun isAmountLabel(str: String): Boolean = AMOUNT_LABEL.matches(str.trim())
  private fun hasAmountPrefix(str: String): Boolean =
    str.contains(":") && Regex("(?i)^(?:nominal\\s*transfer|jumlah\\s*transfer|nominal|jumlah|amount|total\\s*transfer|total\\s*bayar|total)").containsMatchIn(str)

  private fun isAdminLabel(str: String): Boolean = ADMIN_LABEL.matches(str.trim())
  private fun hasAdminPrefix(str: String): Boolean =
    str.contains(":") && Regex("(?i)^(?:biaya\\s*admin|biaya\\s*transfer|admin\\s*fee|admin)").containsMatchIn(str)

  private fun hasKnownFieldLabel(str: String): Boolean {
    return isBankLabel(str) || isRecipientLabel(str) || isAccountLabel(str) ||
      isDateLabel(str) || isAmountLabel(str) || isAdminLabel(str)
  }

  private fun extractInlineValue(line: String): String {
    return if (line.contains(":")) {
      line.substringAfter(":").trim()
    } else if (line.contains("=")) {
      line.substringAfter("=").trim()
    } else {
      ""
    }
  }
}
