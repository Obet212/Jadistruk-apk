# CETAKIN - Aplikasi Android Pengolah & Pencetak Struk Otomatis

Aplikasi Android Native modern untuk menerima hasil **Share** gambar struk / bukti transaksi dari berbagai aplikasi (BCA Mobile, BRImo, Livin' Mandiri, DANA, GoPay, ShopeePay, OVO, dll.), mengolahnya menggunakan **Google ML Kit Text Recognition (OCR)** berbasis koordinat toleransi dan fungsi kolom, serta mencetaknya ke printer thermal **ESC/POS (Bluetooth / Print System / PDF)** atau membagikannya kembali sebagai gambar struk.

---

## 📥 Unduh Aplikasi (Download APK)

Anda dapat langsung mengunduh file APK versi terbaru pada tautan berikut:
- **Tautan Langsung APK di Repositori:** [Unduh CETAKIN-v1.0.apk](./release/CETAKIN-v1.0.apk)
- **Tautan Raw GitHub:** [https://raw.githubusercontent.com/Obet212/Jadistruk-apk/main/release/CETAKIN-v1.0.apk](https://raw.githubusercontent.com/Obet212/Jadistruk-apk/main/release/CETAKIN-v1.0.apk)

---

## 🚀 Fitur Utama

### 1. Penerimaan Share Android Resmi
- Terintegrasi dengan Android Share Intent (`ACTION_SEND` dan `ACTION_SEND_MULTIPLE`).
- Mendeteksi package name aplikasi sumber secara otomatis (misal `com.bca`, `id.co.bri.brimo`, `id.dana`).
- Mendukung pemilihan langsung dari Galeri (Android PhotoPicker) untuk pengujian mandiri.
- **100% Aman & Sesuai Kebijakan Play Store:** Tanpa Accessibility Service, tanpa background screenshot, tanpa membaca data privat aplikasi lain.

### 2. Sistem Template & Pengaturan Kolom
- **1 Aplikasi Sumber = 1 Template Khusus:** Template tidak akan pernah tertukar antar aplikasi sumber.
- **Kolom Manual:** Diisi teks langsung oleh pengguna, perataan **rata kiri** (misal Biaya Admin, Status SUKSES/BERHASIL).
- **Kolom Otomatis (OCR):** Dibaca otomatis dari gambar menggunakan koordinat X, Y, lebar, dan tinggi toleransi; perataan **rata kanan**.
- Pengaturan urutan kolom (naik/turun), aktif/nonaktif, hapus, dan tambah kolom tanpa batas.

### 3. OCR Berbasis Fungsi Kolom (Google ML Kit)
- **Tanggal:** Menjaga format tanggal valid (`DD/MM/YYYY`, dll.).
- **Waktu:** Menjaga format jam (`HH:MM`).
- **Nama:** Mempertahankan huruf dan spasi, membersihkan teks header.
- **Rekening:** Hanya angka, **wajib mempertahankan angka 0 di depan**.
- **Nomor HP:** Hanya angka, **wajib mempertahankan angka 0 di depan** (mendukung konversi otomatis `+62` ke `0`).
- **Nominal:** Mempertahankan angka dan tanda titik/koma/Rp tanpa kesalahan perhitungan.
- **Referensi:** Kombinasi huruf dan angka.
- **Keterangan & Teks Bebas:** Karakter yang didukung.
- Aturan karakter manual: Hanya Huruf, Hanya Angka, Huruf & Angka, Karakter Bebas.

### 4. Sistem Normalisasi Karakter ESC/POS Printer
- **Konversi Bullet Sensor:** Simbol sensor bulat (`•`, `●`, `·`) **otomatis diubah menjadi bintang (`*`)** dengan **jumlah karakter yang sama persis** (contoh: `••••••` menjadi `******`).
- Mencegah teks berubah menjadi tanda tanya (`?`) pada printer thermal ESC/POS.
- Pembersihan karakter Unicode khusus ke standar ASCII yang didukung thermal printer.

### 5. Preview Struk Interaktif & Edit Langsung (Tap-to-Edit)
- Tampilan realistis kertas struk kasir/PPOB/Bank.
- **Ketuk baris manapun pada preview** untuk memunculkan popup pengeditan langsung.
- Pengguna dapat mengoreksi data OCR atau mengubah teks tanpa berpindah halaman.
- Perubahan langsung memperbarui preview, hasil cetak, dan gambar yang dibagikan.

### 6. Pencetakan & Pembagian
- **Bluetooth ESC/POS Thermal Printer:** Mendukung printer kasir 58mm & 80mm via koneksi Bluetooth SPP.
- **Android System Print / PDF:** Dapat dicetak ke printer Wi-Fi, kabel, atau disimpan sebagai PDF di perangkat manapun.
- **Bagikan Gambar Struk:** Render struk resolusi tinggi ke format gambar PNG untuk dibagikan ke WhatsApp, Telegram, dll.

### 7. Penyimpanan Lokal Room SQLite
- Seluruh aplikasi sumber, template, konfigurasi kolom, koordinat OCR, dan riwayat struk tersimpan permanen di basis data lokal Room SQLite perangkat. Data aman dan tidak hilang saat aplikasi ditutup atau perangkat direstart.

---

## 🛠️ Teknologi & Arsitektur
- **Bahasa:** Kotlin 100%
- **UI Framework:** Jetpack Compose (Material Design 3)
- **OCR Engine:** Google ML Kit Text Recognition On-Device
- **Arsitektur:** MVVM (Model-View-ViewModel) + Repository Pattern + Clean Architecture
- **Penyimpanan Lokal:** Android Jetpack Room (SQLite) dengan Kotlin Coroutines & Flow
- **Printing:** Raw ESC/POS Byte Generator & Android PrintManager
