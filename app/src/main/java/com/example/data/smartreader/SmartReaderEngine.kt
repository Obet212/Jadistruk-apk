package com.example.data.smartreader

import com.example.data.model.OcrResult

/**
 * Mesin utama OBETSTRUK SMART READER.
 * Mengoordinasikan deteksi otomatis sumber transaksi, pemilihan parser khusus per aplikasi,
 * eksekusi ekstraksi modular offline, dan validasi silang data.
 */
object SmartReaderEngine {

  private val parsers: Map<TransactionSource, SmartReceiptParser> = mapOf(
    TransactionSource.DANA to DanaReceiptParser(),
    TransactionSource.GOPAY to GopayReceiptParser(),
    TransactionSource.OVO to OvoReceiptParser(),
    TransactionSource.SHOPEEPAY to ShopeePayReceiptParser(),
    TransactionSource.BRIMO to BrimoReceiptParser(),
    TransactionSource.BRI_QITA to BriQitaReceiptParser(),
    TransactionSource.BCA to BcaReceiptParser(),
    TransactionSource.LIVIN to LivinReceiptParser(),
    TransactionSource.BNI to BniReceiptParser(),
    TransactionSource.BSI to BsiReceiptParser(),
    TransactionSource.GENERIC to GenericSmartReceiptParser()
  )

  /**
   * Memproses hasil OCR on-device secara otomatis.
   * @param ocrResult Hasil pembacaan teks ML Kit lokal
   * @param overrideSource Pilihan template sumber manual (jika pengguna ingin mengganti parser)
   */
  fun process(
    ocrResult: OcrResult,
    overrideSource: TransactionSource? = null
  ): SmartParsedReceipt {
    if (ocrResult.isEmpty) {
      return SmartParsedReceipt(
        source = TransactionSource.GENERIC,
        missingFields = listOf("Tidak ada teks yang terbaca pada gambar bukti transfer."),
        rawOcrText = ""
      )
    }

    val lines = ocrResult.lines.map { it.text.trim() }.filter { it.isNotBlank() }
    val fullText = ocrResult.fullText

    // 1. Deteksi Sumber Transaksi (atau gunakan override jika dipilih pengguna)
    val detection = SourceDetector.detectSource(fullText, lines)
    val targetSource = overrideSource ?: detection.source

    // 2. Pilih parser spesifik
    val parser = parsers[targetSource] ?: parsers.getValue(TransactionSource.GENERIC)
    val parsedResult = parser.parse(lines, fullText, ocrResult)

    // 3. Fallback cerdas: Jika parser spesifik belum menemukan beberapa field kunci,
    // cross-check dengan generic parser agar tidak ada informasi yang hilang
    val genericFallback = parsers.getValue(TransactionSource.GENERIC).parse(lines, fullText, ocrResult)

    val finalDate = parsedResult.transactionDate.ifBlank { genericFallback.transactionDate }
    val finalTime = parsedResult.transactionTime.ifBlank { genericFallback.transactionTime }
    val finalRef = parsedResult.refNumber.ifBlank { genericFallback.refNumber }
    val finalAmount = if (parsedResult.amount > 0L) parsedResult.amount else genericFallback.amount
    val finalAdmin = if (parsedResult.adminFee >= 0L && parsedResult.detectedFields.contains(com.example.data.model.TargetRecipientField.ADMIN)) {
      parsedResult.adminFee
    } else {
      genericFallback.adminFee
    }
    val finalBank = parsedResult.recipientBank.ifBlank { genericFallback.recipientBank }
    val finalAccount = parsedResult.recipientAccount.ifBlank { genericFallback.recipientAccount }
    val finalRecipient = parsedResult.recipientName.ifBlank { genericFallback.recipientName }
    val finalSender = parsedResult.senderName.ifBlank { genericFallback.senderName }
    val finalSenderBank = parsedResult.senderBank
    val finalSenderAccount = parsedResult.senderAccount
    val finalType = parsedResult.transactionType.ifBlank { genericFallback.transactionType }
    val finalStatus = if (targetSource != TransactionSource.GENERIC && parsedResult.transactionStatus.isNotBlank()) {
      parsedResult.transactionStatus
    } else {
      parsedResult.transactionStatus.ifBlank { genericFallback.transactionStatus }
    }
    val finalNotes = if (targetSource != TransactionSource.GENERIC) {
      parsedResult.notes
    } else {
      val gen = genericFallback.notes.trim()
      if (gen == "-") "" else gen
    }

    // Evaluasi field yang belum lengkap
    val missingList = mutableListOf<String>()
    if (finalRecipient.isBlank()) missingList.add("Nama penerima")
    if (finalBank.isBlank()) missingList.add("Bank / E-Wallet tujuan")
    if (finalAccount.isBlank()) missingList.add("Nomor rekening / No. HP")
    if (finalAmount == 0L) missingList.add("Nominal transaksi")
    if (finalDate.isBlank()) missingList.add("Tanggal transaksi")
    if (finalRef.isBlank()) {
      if (targetSource == TransactionSource.BRI_QITA) {
        missingList.add("Nomor referensi Qita")
      } else {
        missingList.add("Nomor referensi (min 16 digit)")
      }
    }

    val warnings = (parsedResult.reviewWarnings + genericFallback.reviewWarnings).distinct()

    return parsedResult.copy(
      source = targetSource,
      senderName = finalSender,
      senderBank = finalSenderBank,
      senderAccount = finalSenderAccount,
      transactionDate = finalDate,
      transactionTime = finalTime,
      refNumber = finalRef,
      transactionType = finalType,
      transactionStatus = finalStatus,
      notes = finalNotes,
      amount = finalAmount,
      adminFee = finalAdmin,
      recipientBank = finalBank,
      recipientAccount = finalAccount,
      recipientName = finalRecipient,
      missingFields = missingList,
      reviewWarnings = warnings,
      rawOcrText = fullText,
      ocrResult = ocrResult
    )
  }

  fun parseWithSource(ocrResult: OcrResult, source: TransactionSource): SmartParsedReceipt {
    return process(ocrResult, overrideSource = source)
  }

  fun getSupportedSources(): List<TransactionSource> {
    return TransactionSource.entries
  }
}
