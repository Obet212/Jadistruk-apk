package com.example.data.smartreader

/**
 * Daftar sumber aplikasi pembayaran dan mobile banking yang didukung
 * oleh OBETSTRUK SMART READER.
 */
enum class TransactionSource(
  val id: String,
  val displayName: String,
  val category: String, // "E-Wallet", "Bank", atau "Umum"
  val primaryColorHex: Long,
  val defaultTransactionType: String
) {
  DANA(
    id = "dana",
    displayName = "DANA",
    category = "E-Wallet",
    primaryColorHex = 0xFF118EEA,
    defaultTransactionType = "Kirim Uang"
  ),
  GOPAY(
    id = "gopay",
    displayName = "GoPay",
    category = "E-Wallet",
    primaryColorHex = 0xFF00AED6,
    defaultTransactionType = "Transfer Saldo"
  ),
  OVO(
    id = "ovo",
    displayName = "OVO",
    category = "E-Wallet",
    primaryColorHex = 0xFF4C3494,
    defaultTransactionType = "Transfer OVO"
  ),
  SHOPEEPAY(
    id = "shopeepay",
    displayName = "ShopeePay",
    category = "E-Wallet",
    primaryColorHex = 0xFFEE4D2D,
    defaultTransactionType = "Transfer Dana"
  ),
  BRIMO(
    id = "brimo",
    displayName = "BRImo (Bank BRI)",
    category = "Bank",
    primaryColorHex = 0xFF00529C,
    defaultTransactionType = "Transfer Antar Bank"
  ),
  BRI_QITA(
    id = "bri_qita",
    displayName = "BRI Qita (Bank BRI)",
    category = "Bank",
    primaryColorHex = 0xFF00529C,
    defaultTransactionType = "Transfer Bank BRI"
  ),
  BCA(
    id = "bca",
    displayName = "BCA Mobile / myBCA",
    category = "Bank",
    primaryColorHex = 0xFF003882,
    defaultTransactionType = "m-Transfer"
  ),
  LIVIN(
    id = "livin",
    displayName = "Livin' by Mandiri",
    category = "Bank",
    primaryColorHex = 0xFF003066,
    defaultTransactionType = "Resi Transfer"
  ),
  BNI(
    id = "bni",
    displayName = "BNI Mobile Banking",
    category = "Bank",
    primaryColorHex = 0xFFF15A24,
    defaultTransactionType = "Transfer Antar Rekening"
  ),
  BSI(
    id = "bsi",
    displayName = "BSI Mobile",
    category = "Bank",
    primaryColorHex = 0xFF00A29A,
    defaultTransactionType = "Transfer Syariah"
  ),
  GENERIC(
    id = "generic",
    displayName = "Bukti Transfer Standar",
    category = "Umum",
    primaryColorHex = 0xFF2A6049,
    defaultTransactionType = "Transfer Bank"
  );

  companion object {
    fun fromId(id: String?): TransactionSource {
      if (id == null) return GENERIC
      return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GENERIC
    }
  }
}
