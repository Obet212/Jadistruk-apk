package com.example.data.ocr

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
  val tanggal: ExtractedCandidate<String>? = null,
  val waktu: ExtractedCandidate<String>? = null,
  val referensi: ExtractedCandidate<String>? = null,
  val bank: ExtractedCandidate<String>? = null,
  val rekening: ExtractedCandidate<String>? = null,
  val penerima: ExtractedCandidate<String>? = null,
  val nominal: ExtractedCandidate<Long>? = null,
  val admin: ExtractedCandidate<Long>? = null,
  val conflicts: Map<TargetRecipientField, List<String>> = emptyMap(),
  val reviewRequired: List<String> = emptyList(),
)

object MultiCategoryExtractor {

  // Regex Label Identifiers
  private val BANK_LABEL = Regex("(?i)^(?:bank\\s*tujuan|nama\\s*bank|ke\\s*bank|bank\\s*penerima|tujuan\\s*transfer|bank)[:\\-\\s]*$")
  private val RECIPIENT_LABEL = Regex("(?i)^(?:nama\\s*penerima|nama\\s*tujuan|nama\\s*lengkap|penerima\\s*transfer|penerima|beneficiary|recipient|tujuan|ke\\s*nama|kirim\\s*ke|kepada|ke)[:\\-\\s]*$")
  private val SENDER_LABEL = Regex("(?i)(?:pengirim|nama\\s*pengirim|dari\\s*rekening|dari\\s*bank|dari|sumber\\s*dana|rekening\\s*asal|rek\\s*asal)")
  private val ACCOUNT_LABEL = Regex("(?i)^(?:nomor\\s*rekening|no\\.?\\s*rekening|rekening\\s*tujuan|rekening\\s*penerima|no\\.?\\s*rek|account\\s*number|account|akun\\s*tujuan|ke\\s*rekening)[:\\-\\s]*$")
  private val DATE_LABEL = Regex("(?i)^(?:tanggal\\s*transaksi|transaction\\s*date|tgl\\s*transaksi|tanggal|tgl|date)[:\\-\\s]*$")
  private val TIME_LABEL = Regex("(?i)^(?:waktu\\s*transaksi|jam\\s*transaksi|transaction\\s*time|waktu|jam|time|pukul)[:\\-\\s]*$")
  private val REF_LABEL = Regex("(?i)^(?:nomor\\s*referensi|no\\.?\\s*referensi|kode\\s*transaksi|transaction\\s*id|id\\s*transaksi|serial\\s*number|reference|no\\.?\\s*transaksi|no\\.?\\s*ref|reff?|sn)[:\\-\\s]*$")
  private val AMOUNT_LABEL = Regex("(?i)^(?:nominal\\s*transfer|jumlah\\s*transfer|total\\s*transfer|nominal|jumlah|amount|total\\s*diterima)[:\\-\\s]*$")
  private val ADMIN_LABEL = Regex("(?i)^(?:biaya\\s*admin|biaya\\s*transfer|admin\\s*fee|admin|fee)[:\\-\\s]*$")
  private val STATUS_OR_NOISE = Regex("(?i)^(?:transaksi\\s*berhasil|transfer\\s*berhasil|bukti\\s*transfer|status|berhasil|sukses|selesai|detail\\s*transaksi|kembali|bagikan|unduh|qris|bi-fast|real\\s*time\\s*online|llg|rtgs)$")

  fun extractFromLines(
    lines: List<OcrTextLine>,
    designatedField: TargetRecipientField? = null,
    sourceAreaId: String? = null
  ): MultiCategoryExtraction {
    val cleanLineTexts = lines.map { it.text.trim() }.filter { it.isNotBlank() }
    return extractFromTextLines(cleanLineTexts, designatedField, sourceAreaId)
  }

  fun extractFromTextLines(
    rawLines: List<String>,
    designatedField: TargetRecipientField? = null,
    sourceAreaId: String? = null
  ): MultiCategoryExtraction {
    var tanggalCandidate: ExtractedCandidate<String>? = null
    var waktuCandidate: ExtractedCandidate<String>? = null
    var referensiCandidate: ExtractedCandidate<String>? = null
    var bankCandidate: ExtractedCandidate<String>? = null
    var rekeningCandidate: ExtractedCandidate<String>? = null
    var penerimaCandidate: ExtractedCandidate<String>? = null
    var nominalCandidate: ExtractedCandidate<Long>? = null
    var adminCandidate: ExtractedCandidate<Long>? = null

    val conflicts = mutableMapOf<TargetRecipientField, MutableList<String>>()
    val reviewRequired = mutableListOf<String>()

    val n = rawLines.size
    val consumedIndices = mutableSetOf<Int>()

    // PASS 1: Pencarian Berdasarkan Label (Inline "LABEL: VALUE" atau Baris Berikutnya)
    var i = 0
    while (i < n) {
      val line = rawLines[i]
      val nextLine = if (i + 1 < n) rawLines[i + 1] else null

      // Deteksi & lewati informasi pengirim secara ketat
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

      // 1. TANGGAL
      if (isDateLabel(line) || hasDatePrefix(line)) {
        val inlineVal = extractInlineValue(line)
        val candidateText = if (inlineVal.isNotBlank()) inlineVal else nextLine ?: ""
        val (sanitized, issue) = FieldValidator.sanitizeDate(candidateText)
        if (sanitized.isNotBlank() && issue == null) {
          val conf = if (designatedField == TargetRecipientField.TANGGAL) 98 else 95
          tanggalCandidate = ExtractedCandidate(
            field = TargetRecipientField.TANGGAL,
            value = sanitized,
            rawText = candidateText,
            confidence = conf,
            sourceAreaId = sourceAreaId,
            reason = "Tanggal transaksi valid: $sanitized"
          )
          consumedIndices.add(i)
          if (inlineVal.isBlank() && nextLine != null) {
            consumedIndices.add(i + 1)
            i++
          }
        }
      }

      // 2. WAKTU
      else if (isTimeLabel(line) || hasTimePrefix(line)) {
        val inlineVal = extractInlineValue(line)
        val candidateText = if (inlineVal.isNotBlank()) inlineVal else nextLine ?: ""
        val (sanitized, issue) = FieldValidator.sanitizeTime(candidateText)
        if (sanitized.isNotBlank() && issue == null) {
          val conf = if (designatedField == TargetRecipientField.WAKTU) 98 else 95
          waktuCandidate = ExtractedCandidate(
            field = TargetRecipientField.WAKTU,
            value = sanitized,
            rawText = candidateText,
            confidence = conf,
            sourceAreaId = sourceAreaId,
            reason = "Waktu transaksi 24 jam valid: $sanitized"
          )
          consumedIndices.add(i)
          if (inlineVal.isBlank() && nextLine != null) {
            consumedIndices.add(i + 1)
            i++
          }
        }
      }

      // 3. REFERENSI / KODE TRANSAKSI
      else if (isRefLabel(line) || hasRefPrefix(line)) {
        val inlineVal = extractInlineValue(line)
        val candidateText = if (inlineVal.isNotBlank()) inlineVal else nextLine ?: ""
        val (sanitized, issue) = FieldValidator.sanitizeRefNumber(candidateText)
        if (sanitized.isNotBlank() && issue == null) {
          val conf = if (designatedField == TargetRecipientField.REFERENSI) 98 else 95
          referensiCandidate = ExtractedCandidate(
            field = TargetRecipientField.REFERENSI,
            value = sanitized,
            rawText = candidateText,
            confidence = conf,
            sourceAreaId = sourceAreaId,
            reason = "Nomor referensi / SN transaksi: $sanitized"
          )
          consumedIndices.add(i)
          if (inlineVal.isBlank() && nextLine != null) {
            consumedIndices.add(i + 1)
            i++
          }
        }
      }

      // 4. BANK TUJUAN
      else if (isBankLabel(line) || hasBankPrefix(line)) {
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
            reason = "Bank tujuan terdaftar: ${match.officialName}"
          )
          consumedIndices.add(i)
          if (inlineVal.isBlank() && nextLine != null) {
            consumedIndices.add(i + 1)
            i++
          }
        }
      }

      // 5. NOMOR REKENING (Maksimal 15 digit)
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
          reviewRequired.add("Nomor rekening setelah label '$line': ${issue ?: "perlu pemeriksaan manual"}")
        }
      }

      // 6. NAMA PENERIMA
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
            reason = "Nama penerima terverifikasi: $sanitized"
          )
          consumedIndices.add(i)
          if (inlineVal.isBlank() && nextLine != null) {
            consumedIndices.add(i + 1)
            i++
          }
        }
      }

      // 7. NOMINAL TRANSFER
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

      // 8. BIAYA ADMIN
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

    // PASS 2: Pattern, Regex, dan Pencocokan Konteks Baris Bebas yang Belum Terpakai
    for (idx in 0 until n) {
      if (idx in consumedIndices) continue
      val line = rawLines[idx]
      if (STATUS_OR_NOISE.matches(line)) continue
      if (SENDER_LABEL.containsMatchIn(line)) continue

      // A. Pola Tanggal & Jam Gabungan dalam 1 baris
      if (tanggalCandidate == null || waktuCandidate == null) {
        val (dtVal, _) = FieldValidator.sanitizeDateTime(line)
        if (dtVal.isNotBlank()) {
          val (dVal, dErr) = FieldValidator.sanitizeDate(line)
          val (tVal, tErr) = FieldValidator.sanitizeTime(line)
          if (tanggalCandidate == null && dVal.isNotBlank() && dErr == null) {
            tanggalCandidate = ExtractedCandidate(
              field = TargetRecipientField.TANGGAL,
              value = dVal,
              rawText = line,
              confidence = 90,
              sourceAreaId = sourceAreaId,
              reason = "Pola tanggal kalender terdeteksi: $dVal"
            )
          }
          if (waktuCandidate == null && tVal.isNotBlank() && tErr == null) {
            waktuCandidate = ExtractedCandidate(
              field = TargetRecipientField.WAKTU,
              value = tVal,
              rawText = line,
              confidence = 90,
              sourceAreaId = sourceAreaId,
              reason = "Pola waktu 24 jam terdeteksi: $tVal"
            )
          }
          consumedIndices.add(idx)
          continue
        }
      }

      // B. Pola Referensi (> 15 digit alfanumerik)
      if (referensiCandidate == null) {
        val digitsCount = line.count { it.isDigit() }
        val isAlphanumericCode = line.length in 16..40 && digitsCount >= 10 && !line.contains("Rp", ignoreCase = true)
        if (isAlphanumericCode) {
          val (refCode, err) = FieldValidator.sanitizeRefNumber(line)
          if (refCode.isNotBlank() && err == null) {
            referensiCandidate = ExtractedCandidate(
              field = TargetRecipientField.REFERENSI,
              value = refCode,
              rawText = line,
              confidence = 92,
              sourceAreaId = sourceAreaId,
              reason = "Kode referensi panjang (>15 karakter): $refCode"
            )
            consumedIndices.add(idx)
            continue
          }
        }
      }

      // C. Pola Nominal Mata Uang (Rp / IDR)
      if (nominalCandidate == null) {
        if (line.contains("Rp", ignoreCase = true) || line.contains("IDR", ignoreCase = true)) {
          val (amt, err) = FieldValidator.parseNominal(line)
          if (amt > 0L && err == null) {
            nominalCandidate = ExtractedCandidate(
              field = TargetRecipientField.NOMINAL,
              value = amt,
              rawText = line,
              confidence = 90,
              sourceAreaId = sourceAreaId,
              reason = "Pola mata uang nominal: $amt"
            )
            consumedIndices.add(idx)
            continue
          }
        }
      }

      // D. Kamus Bank
      if (bankCandidate == null) {
        val match = BankRegistry.matchBank(line)
        if (match.isConfident) {
          bankCandidate = ExtractedCandidate(
            field = TargetRecipientField.BANK,
            value = match.officialName,
            rawText = line,
            confidence = 88,
            sourceAreaId = sourceAreaId,
            reason = "Kamus bank: ${match.officialName}"
          )
          consumedIndices.add(idx)
          continue
        }
      }

      // E. Pola Rekening (10–15 digit)
      if (rekeningCandidate == null) {
        val (accStr, err) = FieldValidator.sanitizeAccount(line, referensiCandidate?.value ?: "")
        if (accStr.isNotBlank() && err == null) {
          rekeningCandidate = ExtractedCandidate(
            field = TargetRecipientField.REKENING,
            value = accStr,
            rawText = line,
            confidence = 85,
            sourceAreaId = sourceAreaId,
            reason = "Digit rekening 10–15 angka: $accStr"
          )
          consumedIndices.add(idx)
          continue
        }
      }

      // F. Pola Nama Penerima
      if (penerimaCandidate == null) {
        val (nameStr, err) = FieldValidator.sanitizeRecipientName(line)
        if (nameStr.isNotBlank() && err == null && !line.any { it.isDigit() }) {
          penerimaCandidate = ExtractedCandidate(
            field = TargetRecipientField.PENERIMA,
            value = nameStr,
            rawText = line,
            confidence = 75,
            sourceAreaId = sourceAreaId,
            reason = "Pola nama penerima: $nameStr"
          )
          consumedIndices.add(idx)
          continue
        }
      }
    }

    // PASS 3: Validasi Silang & Pencegahan Tertukar (Cross-Validation)
    // 1. Pastikan rekening tidak tertukar dengan referensi
    if (rekeningCandidate != null && referensiCandidate != null) {
      if (rekeningCandidate.value == referensiCandidate.value) {
        if (rekeningCandidate.value.length > 15) {
          // Nilai lebih tepat menjadi referensi
          rekeningCandidate = null
          reviewRequired.add("Nomor rekening diidentifikasi sebagai kode referensi (>15 digit). Silakan cek manual.")
        } else {
          referensiCandidate = null
        }
      }
    }

    // 2. Pastikan nama penerima tidak sama dengan nama bank
    if (penerimaCandidate != null && bankCandidate != null) {
      if (penerimaCandidate.value.equals(bankCandidate.value, ignoreCase = true)) {
        penerimaCandidate = null
        reviewRequired.add("Nama penerima sama dengan nama bank tujuan. Silakan isi nama penerima manual.")
      }
    }

    return MultiCategoryExtraction(
      tanggal = tanggalCandidate,
      waktu = waktuCandidate,
      referensi = referensiCandidate,
      bank = bankCandidate,
      rekening = rekeningCandidate,
      penerima = penerimaCandidate,
      nominal = nominalCandidate,
      admin = adminCandidate,
      conflicts = conflicts,
      reviewRequired = reviewRequired.distinct()
    )
  }

  fun mergeExtractions(
    extractions: List<MultiCategoryExtraction>
  ): MultiCategoryExtraction {
    var tanggal: ExtractedCandidate<String>? = null
    var waktu: ExtractedCandidate<String>? = null
    var referensi: ExtractedCandidate<String>? = null
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

      tanggal = mergeField(TargetRecipientField.TANGGAL, tanggal, ext.tanggal, allConflicts, allReviews)
      waktu = mergeField(TargetRecipientField.WAKTU, waktu, ext.waktu, allConflicts, allReviews)
      referensi = mergeField(TargetRecipientField.REFERENSI, referensi, ext.referensi, allConflicts, allReviews)
      bank = mergeField(TargetRecipientField.BANK, bank, ext.bank, allConflicts, allReviews)
      rekening = mergeField(TargetRecipientField.REKENING, rekening, ext.rekening, allConflicts, allReviews)
      penerima = mergeField(TargetRecipientField.PENERIMA, penerima, ext.penerima, allConflicts, allReviews)
      nominal = mergeField(TargetRecipientField.NOMINAL, nominal, ext.nominal, allConflicts, allReviews)
      admin = mergeField(TargetRecipientField.ADMIN, admin, ext.admin, allConflicts, allReviews)
    }

    return MultiCategoryExtraction(
      tanggal = tanggal,
      waktu = waktu,
      referensi = referensi,
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

    return if (incoming.confidence > current.confidence + 10) {
      incoming
    } else if (current.confidence > incoming.confidence + 10) {
      current
    } else {
      conflicts.getOrPut(field) { mutableListOf() }.apply {
        if (!contains(current.value.toString())) add(current.value.toString())
        if (!contains(incoming.value.toString())) add(incoming.value.toString())
      }
      reviews.add("Terdapat beberapa kandidat untuk ${field.fieldName}: '${current.value}' vs '${incoming.value}'")
      current
    }
  }

  // --- Helper methods for label identification ---
  private fun isDateLabel(str: String): Boolean = DATE_LABEL.matches(str.trim())
  private fun hasDatePrefix(str: String): Boolean =
    str.contains(":") && Regex("(?i)^(?:tanggal|tgl|date)").containsMatchIn(str)

  private fun isTimeLabel(str: String): Boolean = TIME_LABEL.matches(str.trim())
  private fun hasTimePrefix(str: String): Boolean =
    str.contains(":") && Regex("(?i)^(?:waktu|jam|time|pukul)").containsMatchIn(str)

  private fun isRefLabel(str: String): Boolean = REF_LABEL.matches(str.trim())
  private fun hasRefPrefix(str: String): Boolean =
    str.contains(":") && Regex("(?i)^(?:nomor\\s*referensi|no\\.?\\s*referensi|reference|ref|kode\\s*transaksi|sn)").containsMatchIn(str)

  private fun isBankLabel(str: String): Boolean = BANK_LABEL.matches(str.trim())
  private fun hasBankPrefix(str: String): Boolean =
    str.contains(":") && Regex("(?i)^(?:bank\\s*tujuan|nama\\s*bank|ke\\s*bank|bank\\s*penerima|bank)").containsMatchIn(str)

  private fun isRecipientLabel(str: String): Boolean = RECIPIENT_LABEL.matches(str.trim())
  private fun hasRecipientPrefix(str: String): Boolean =
    str.contains(":") && Regex("(?i)^(?:nama\\s*penerima|nama\\s*tujuan|nama\\s*lengkap|penerima|tujuan\\s*transfer|ke\\s*nama|kirim\\s*ke|kepada|ke)").containsMatchIn(str)

  private fun isAccountLabel(str: String): Boolean = ACCOUNT_LABEL.matches(str.trim())
  private fun hasAccountPrefix(str: String): Boolean =
    str.contains(":") && Regex("(?i)^(?:nomor\\s*rekening|no\\.?\\s*rekening|rekening\\s*tujuan|rekening\\s*penerima|no\\.?\\s*rek|account\\s*number|account)").containsMatchIn(str)

  private fun isAmountLabel(str: String): Boolean = AMOUNT_LABEL.matches(str.trim())
  private fun hasAmountPrefix(str: String): Boolean =
    str.contains(":") && Regex("(?i)^(?:nominal\\s*transfer|jumlah\\s*transfer|total\\s*transfer|nominal|jumlah|amount)").containsMatchIn(str)

  private fun isAdminLabel(str: String): Boolean = ADMIN_LABEL.matches(str.trim())
  private fun hasAdminPrefix(str: String): Boolean =
    str.contains(":") && Regex("(?i)^(?:biaya\\s*admin|biaya\\s*transfer|admin\\s*fee|admin)").containsMatchIn(str)

  private fun hasKnownFieldLabel(str: String): Boolean {
    return isDateLabel(str) || isTimeLabel(str) || isRefLabel(str) || isBankLabel(str) ||
      isRecipientLabel(str) || isAccountLabel(str) || isAmountLabel(str) || isAdminLabel(str)
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
