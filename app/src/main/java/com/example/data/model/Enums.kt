package com.example.data.model

enum class ColumnType(val displayName: String) {
    MANUAL("Kolom Manual (Teks Tetap)"),
    AUTO("Kolom Otomatis (Dari OCR)")
}

enum class ColumnFunction(val displayName: String, val description: String) {
    DATE("Tanggal", "Angka & pemisah tanggal (misal DD/MM/YYYY)"),
    TIME("Waktu", "Angka & tanda titik dua/titik (misal 14:30)"),
    NAME("Nama", "Hanya huruf dan spasi"),
    ACCOUNT_NUMBER("Rekening", "Hanya angka, wajib pertahankan angka 0 di depan"),
    PHONE_NUMBER("Nomor HP", "Hanya angka, wajib pertahankan angka 0 di depan"),
    NOMINAL("Nominal", "Angka & pemisah nominal (Rp, titik, koma)"),
    REFERENCE("Referensi", "Kombinasi huruf dan angka (No. Ref/Trx)"),
    NOTES("Keterangan", "Huruf, angka, dan spasi"),
    FREE_TEXT("Teks Bebas", "Seluruh karakter yang didukung")
}

enum class CharRule(val displayName: String) {
    LETTERS_ONLY("Hanya Huruf"),
    NUMBERS_ONLY("Hanya Angka"),
    LETTERS_AND_NUMBERS("Huruf dan Angka"),
    FREE_CHARS("Karakter Bebas")
}

enum class TextAlignment {
    LEFT,
    RIGHT
}
