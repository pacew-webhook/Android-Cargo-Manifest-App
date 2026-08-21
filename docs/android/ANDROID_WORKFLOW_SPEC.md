<!-- Dokumentasi ini menggunakan Bahasa Indonesia. -->

# ANDROID_WORKFLOW_SPEC.md

**Proyek:** Android Cargo Manifest App  
**Tujuan:** Mendokumentasikan alur kerja operasional yang direpresentasikan oleh aplikasi saat ini

## 1. Navigasi Utama

```text
Buka Aplikasi
  ↓
Menu Utama
  ├── Data Manifest Cargo
  ├── Data Stowingan Palet
  ├── Pencarian Basis Data Manifest
  ├── Bukti Timbang Barang
  └── Flight Tracking (opsional/terpisah)
```

## 2. Alur Kerja Manifest

Alur kerja konseptual saat ini:

```text
Data Stowing / Cargo yang diimpor
        ↓
CargoViewModel
        ↓
Pengelompokan / pengeditan Manifest
        ↓
Tampilan Manifest
        ↓
Ekspor / pemrosesan Excel
```

Pengelompokan Manifest saat ini menggunakan beberapa field termasuk PTI, Pelanggan, dan Deskripsi.

## 3. Alur Kerja BTB

Aplikasi memiliki modul BTB khusus:

```text
Bukti Timbang Barang
        ↓
Data BTB / foto / OCR
        ↓
Validasi / pengelolaan label
        ↓
Excel atau pemrosesan lanjutan
```

Aturan operasional yang sebenarnya harus mengikuti proses Cargo pengguna di lapangan, bukan menganggap PTI dan BTB selalu dapat dipertukarkan.

## 4. Alur Kerja Stowing

Proyek memiliki Activity dan ViewModel Stowing yang terpisah.

Implementasi saat ini menggunakan data Stowing yang disimpan serta pemrosesan yang berkaitan dengan Excel.

## 5. Pencarian Historis Manifest

Proyek Android saat ini sudah memiliki fitur Pencarian Manifest.

Alur konseptual:

```text
Layar Pencarian
    ↓
ViewModel Pencarian
    ↓
Data Pencarian Manifest
    ↓
Hasil
    ↓
Buka / periksa Manifest yang relevan
```

Arsitektur pencarian harus diverifikasi terhadap implementasi aktual sebelum diganti dengan desain yang berbeda.

## 6. Flight Tracking

Flight Tracking bersifat opsional dan dipisahkan dari alur kerja Cargo:

```text
Menu Utama
   ↓
Flight Tracking Activity
```

Fitur ini tidak boleh menjadi langkah wajib untuk membuat atau memproses Manifest.

## 7. Prinsip Excel

Excel tetap menjadi dokumen kerja operasional yang penting.

Aplikasi dapat:

- mengimpor Excel;
- membuat/mengekspor Excel;
- membuka/membagikan file Excel;
- menggunakan template yang disertakan dalam aplikasi.

Aplikasi tidak boleh mengubah file Manifest milik pengguna secara diam-diam saat melakukan operasi baca/pencarian.

## 8. Perubahan Alur Kerja di Masa Depan

Setiap alur kerja baru terkait rekonsiliasi PTI/BTB/Pelanggan harus terlebih dahulu didokumentasikan di sini sebelum implementasi berskala besar.

Hindari menambahkan otomatisasi yang menganggap semua orang mengikuti proses manual yang sama apabila praktik di lapangan diketahui berbeda.
