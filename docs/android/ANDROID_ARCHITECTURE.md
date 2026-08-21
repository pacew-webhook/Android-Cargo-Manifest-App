<!-- Dokumentasi ini menggunakan Bahasa Indonesia. -->

# ANDROID_ARCHITECTURE.md

**Proyek:** Android Cargo Manifest App  
**Tujuan:** Menjelaskan baseline arsitektur aktual dan batas tanggung jawab setiap bagian

## 1. Struktur Tingkat Tinggi

```text
MainActivity
    ↓
MainMenuScreen
    ├── CargoAppScreen
    │     ↓
    │   CargoViewModel
    │     ↓
    │   CargoDatabase / CargoDao
    │
    ├── ManifestSearchScreen
    │     ↓
    │   ManifestSearchViewModel
    │     ↓
    │   ManifestDatabase / ManifestDao
    │
    ├── StowingActivity
    │     ↓
    │   StowingViewModel
    │
    ├── BuktiTimbangActivity
    │     ↓
    │   Komponen BTB / repository
    │
    └── FlightTrackingActivity
```

## 2. Lapisan UI

File UI utama saat ini antara lain:

- `MainMenuScreen.kt`
- `CargoAppScreen.kt`
- `ManifestSearchScreen.kt`
- `BtbCheckDialog.kt`
- screen berbasis Activity untuk Stowing, BTB, OCR, label BTB, dan Flight Tracking.

Compose digunakan untuk menu utama, UI Manifest, dan Pencarian Manifest.

## 3. Lapisan State / Presentasi

ViewModel yang ada saat ini meliputi:

- `CargoViewModel`
- `ManifestSearchViewModel`
- `BtbViewModel`
- `StowingViewModel`

ViewModel saat ini menangani state presentasi sekaligus sebagian orkestrasi penyimpanan/file. Pada pengembangan mendatang, operasi data/file yang dapat digunakan kembali sebaiknya dipindahkan ke batas Repository/service jika memang diperlukan.

## 4. Lapisan Penyimpanan Data

### Cargo

```text
CargoViewModel
    ↓
CargoDao
    ↓
CargoDatabase
```

### Pencarian Manifest

```text
ManifestSearchViewModel
    ↓
ManifestDao
    ↓
ManifestDatabase
```

### BTB

Proyek memiliki:

- `BtbDao`
- `BtbEntity`
- `BtbRepository`
- `BtbPhotoEntity`

### Kondisi Penting Saat Ini

Proyek **belum menggunakan arsitektur dengan satu sumber kebenaran**.

SharedPreferences masih digunakan untuk beberapa area, termasuk:

- `stowing_prefs`
- `btb_reference`
- `btb_reference_status`
- `cargo_photos`
- `stowing_draft`
- `cargo_archive`
- `manifest_settings`

Hal ini harus diperlakukan sebagai utang arsitektur yang sudah diketahui dan tidak boleh dihapus diam-diam saat mengerjakan fitur yang tidak berkaitan.

## 5. Lapisan Excel

`ExcelUtils.kt` dan `ManifestExcelImporter.kt` menangani pemrosesan yang berkaitan dengan Excel.

Asset yang saat ini tersedia antara lain:

- `template_manifest.xlsx`
- `Bukti_Timbang_Barang_BTB.xlsx`
- `STOWINGAN_PAG_TEMPLATE.xlsx`

Apache POI digunakan untuk memproses workbook.

## 6. Lapisan OCR

Komponen OCR saat ini:

```text
CameraX
   ↓
ScaleOcrActivity / BtbOcrScanner
   ↓
ML Kit Text Recognition
   ↓
Pemrosesan berat / BTB
```

## 7. Integrasi Eksternal

`N8nClient.kt` menyediakan integrasi n8n dari sisi Android.

Alur kerja dan dokumentasi n8n tetap dipisahkan dari arsitektur UI inti.

## 8. Batas Navigasi

Flight Tracking sengaja dibuat sebagai Activity terpisah dan bukan bagian dari alur form Cargo Manifest.

Pemisahan ini harus dipertahankan kecuali kebutuhan masa depan secara jelas mengubahnya.

## 9. Aturan Arsitektur

- UI tidak boleh memiliki pekerjaan File/Basis Data yang berjalan lama secara langsung.
- ViewModel bertugas menyediakan state untuk UI dan mengoordinasikan operasi.
- Akses basis data harus dilakukan di luar main thread.
- Pemrosesan Excel harus dilakukan di luar main thread.
- Jangan memperkenalkan mekanisme penyimpanan kedua tanpa mendokumentasikan alasannya.
