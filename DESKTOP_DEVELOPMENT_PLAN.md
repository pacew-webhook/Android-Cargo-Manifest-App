# Desktop Development Plan — Cargo Manifest

> Dokumen ini adalah panduan resmi pengembangan versi Desktop/Windows dari **Android Cargo Manifest App**.
>
> **Tujuan utama:** membuat aplikasi Cargo Manifest untuk laptop/Windows tanpa merusak aplikasi Android yang sudah berjalan.

---

## 1. Tujuan Project

Project ini akan dikembangkan menjadi aplikasi multi-platform:

```text
                 CARGO MANIFEST
                       |
          +------------+------------+
          |                         |
       Android                   Desktop
          |                         |
      Jetpack Compose          Compose Desktop
          |                         |
          +------------+------------+
                       |
                 Shared Logic
                       |
              Database + Business
                   Logic
```

Target akhir:

- Android APK tetap berjalan.
- Aplikasi Desktop Windows tersedia.
- Logic bisnis dapat digunakan bersama.
- Data Cargo/Manifest konsisten.
- Excel Manifest dapat dibuat langsung dari Desktop.
- Project dapat dikembangkan tanpa membuat ulang seluruh aplikasi dari nol.

---

# 2. Prinsip Pengembangan

### Prinsip utama

> **Jangan menyimpan data di tempat yang tidak bisa di-query.**  
> **Jangan biarkan state penting hilang.**  
> **Jangan menjalankan task berat di UI Thread.**

Tambahan untuk multiplatform:

> **Jangan membawa Android API ke shared code.**

Contoh API yang tidak boleh masuk ke `commonMain`:

- `android.content.Context`
- `android.net.Uri`
- `Activity`
- CameraX
- Android-specific ML Kit
- Android Toast
- Android file picker
- Android lifecycle API

Kode seperti itu harus tetap berada di layer platform-specific.

---

# 3. Struktur Project Target

Struktur yang diinginkan:

```text
Android-Cargo-Manifest-App/
│
├── app/                         # Android application
│
├── shared/                      # Shared business/domain layer
│   └── src/
│       ├── commonMain/
│       │   └── kotlin/
│       │       └── com/example/cargomanifest/shared/
│       │           ├── model/
│       │           ├── repository/
│       │           └── usecase/
│       │
│       ├── androidMain/
│       └── desktopMain/
│
├── desktopApp/                  # Windows/Desktop application
│   └── src/
│       └── main/
│           ├── kotlin/
│           └── resources/
│
└── README.md
```

---

# 4. Pembagian Tanggung Jawab

## `app/`

Khusus Android.

Berisi:

- Android Activity
- Android lifecycle
- CameraX
- Android OCR
- Android file picker
- Android permissions
- Android-specific UI
- Android-specific integrations

Kode Android lama **jangan dihapus hanya karena Desktop sedang dibuat**.

---

## `shared/`

Tempat logic yang dapat digunakan Android dan Desktop.

Prioritas:

### Model

Contoh:

```text
Cargo
Manifest
Flight
BtbRecord
StowingItem
```

### Business Logic

Contoh:

```text
hitung total KG
hitung total koli
validasi PTI
validasi manifest
pengelompokan cargo
perhitungan stowing
```

### Repository Interface

Contoh:

```text
CargoRepository
ManifestRepository
BtbRepository
```

Implementasi database dapat berbeda berdasarkan platform.

---

## `desktopApp/`

Khusus aplikasi Windows/Desktop.

Target awal:

```text
Dashboard
Cargo
Manifest
BTB
Stowing
Excel
Settings
```

---

# 5. Prioritas Pengembangan Desktop

Jangan langsung memindahkan seluruh fitur Android.

Gunakan tahapan berikut.

## Phase 1 — Desktop MVP

Buat terlebih dahulu:

- Dashboard
- Input Cargo
- Edit Cargo
- Hapus Cargo
- Daftar Cargo
- Search Cargo
- Total Koli
- Total KG
- Flight information

Target:

```text
Tambah Cargo
     ↓
Tersimpan
     ↓
Muncul di tabel
     ↓
Bisa dicari
     ↓
Bisa diedit
```

---

# 6. Phase 2 — Database

Room menjadi sumber kebenaran utama untuk data aplikasi.

Jangan menggunakan SharedPreferences sebagai database Cargo.

Target arsitektur:

```text
Desktop UI
    ↓
ViewModel
    ↓
Repository
    ↓
Database
```

Data persistent harus dapat di-query.

SharedPreferences hanya boleh digunakan untuk preference sederhana jika memang diperlukan, bukan untuk menyimpan daftar Cargo/Manifest.

---

# 7. Phase 3 — Excel

Excel merupakan fitur penting Desktop.

Target:

```text
Cargo Data
    ↓
Manifest Generator
    ↓
Template Excel
    ↓
Manifest.xlsx
```

Gunakan template Excel yang sudah digunakan project.

Jangan membuat format Manifest baru jika template existing masih dapat digunakan.

### Persyaratan

Generator harus mempertahankan:

- format sheet
- header
- merged cells
- formula
- layout
- kolom cargo
- PTI
- koli
- KG
- flight
- aircraft
- FROM
- TO
- tanggal

---

# 8. Phase 4 — Manifest Search

Buat pencarian cepat:

```text
PTI
Pengirim
Penerima
Flight
Tanggal
Jenis Cargo
```

Jika dataset sudah besar, gunakan Full-Text Search.

Jangan langsung menggunakan:

```sql
LIKE '%keyword%'
```

untuk seluruh tabel jika FTS lebih sesuai.

---

# 9. Phase 5 — BTB

Setelah Cargo dan Manifest stabil, port fitur:

- BTB
- bukti timbang
- referensi BTB
- status BTB
- pencarian BTB

Pastikan data BTB tidak bergantung pada SharedPreferences.

---

# 10. Phase 6 — Stowing

Kemudian port:

- Stowing
- PAG
- checklist
- perhitungan
- export
- status stowing

Semua data penting harus masuk database/repository.

---

# 11. Phase 7 — OCR

OCR tidak menjadi prioritas awal Desktop.

OCR Android yang menggunakan:

- CameraX
- Android ML Kit
- Android permissions

harus tetap berada pada Android layer.

Untuk Desktop, jika OCR diperlukan, buat adapter/implementasi terpisah.

Shared layer hanya menerima hasil OCR, misalnya:

```text
OCR Engine
    ↓
RecognizedText
    ↓
Shared Business Logic
```

---

# 12. Phase 8 — Windows Packaging

Setelah Desktop MVP stabil:

```text
desktopApp
    ↓
Gradle
    ↓
Windows package
    ↓
CargoManifest.exe
```

Target akhir:

```text
CargoManifest.exe
```

Jika diperlukan, buat installer:

```text
CargoManifest-Setup.exe
```

Aplikasi harus dapat berjalan di laptop Windows tanpa Android Studio.

---

# 13. Arsitektur Data

Target:

```text
                UI
                 |
        +--------+--------+
        |                 |
     Android           Desktop
        |                 |
        +--------+--------+
                 |
              ViewModel
                 |
             Repository
                 |
          Business Logic
                 |
              Database
```

UI tidak boleh mengakses database secara langsung.

---

# 14. Aturan Room

Room digunakan sebagai **Single Source of Truth** untuk data utama.

Data seperti berikut harus dapat di-query:

- Cargo
- Manifest
- BTB
- Stowing
- Flight
- history

Hindari menyimpan daftar object JSON besar di SharedPreferences.

---

# 15. State Management

Bedakan:

### Persistent Data

Disimpan di database:

```text
Cargo
Manifest
BTB
Stowing
Flight
```

### UI State

Disimpan melalui state/ViewModel/SavedStateHandle sesuai kebutuhan:

```text
selected page
editing item
dialog visibility
filter
temporary selection
```

Jangan menyimpan UI state sementara ke database tanpa alasan.

---

# 16. Background Task

Task berat tidak boleh berjalan di UI Thread.

Contoh:

- import Excel
- export Excel
- scanning folder
- PDF generation
- database migration
- cleanup cache

Android:

```text
WorkManager
```

Desktop:

gunakan coroutine/background dispatcher atau mekanisme desktop yang sesuai.

---

# 17. Cache Cleanup

File temporary seperti:

```text
Excel
PDF
OCR temporary files
```

harus dibersihkan secara otomatis.

Target Android:

```text
cache file > 7 hari
        ↓
hapus
```

Desktop dapat menggunakan cleanup mechanism yang sesuai Windows.

---

# 18. Excel Architecture

Jangan membuat UI mengetahui detail Apache POI.

Gunakan:

```text
UI
 ↓
ManifestExportUseCase
 ↓
ExcelExporter
 ↓
Apache POI
 ↓
Manifest.xlsx
```

Dengan demikian jika library Excel berubah, UI tidak perlu diubah.

---

# 19. File Picker

Jangan membawa Android `Uri` ke `commonMain`.

Gunakan abstraction:

```text
FileReference
```

Kemudian:

```text
Android → Android file picker
Desktop → Windows file chooser
```

---

# 20. Dependency Rules

### `commonMain`

Boleh:

- Kotlin standard library
- multiplatform-compatible libraries
- domain logic
- repository interfaces

Tidak boleh:

- Android Context
- Activity
- Android Uri
- CameraX
- Android ML Kit

### `androidMain`

Boleh menggunakan Android API.

### `desktopMain`

Boleh menggunakan:

- JVM
- desktop file system
- Windows/Desktop APIs
- desktop-specific libraries

---

# 21. Git Branch Strategy

Disarankan:

```text
main
│
├── android
│
└── desktop-development
```

Atau:

```text
main
  |
  +-- feature/desktop-foundation
  +-- feature/desktop-cargo
  +-- feature/desktop-excel
  +-- feature/desktop-manifest
  +-- feature/desktop-btb
  +-- feature/desktop-stowing
  +-- feature/windows-build
```

Jangan mengembangkan Desktop langsung di `main` sampai MVP stabil.

---

# 22. Definition of Done — Desktop MVP

Desktop MVP dianggap selesai jika:

- [ ] Aplikasi dapat dibuka di Windows
- [ ] Cargo dapat ditambahkan
- [ ] Cargo dapat diedit
- [ ] Cargo dapat dihapus
- [ ] Cargo dapat dicari
- [ ] Total koli dihitung
- [ ] Total KG dihitung
- [ ] Data tersimpan setelah aplikasi ditutup
- [ ] Manifest dapat dibuat
- [ ] Excel dapat diexport
- [ ] Template Excel tetap sesuai format
- [ ] Tidak ada database operation di UI Thread
- [ ] Android project tetap dapat di-build

---

# 23. Definition of Done — Windows Release

- [ ] Desktop application stabil
- [ ] Windows build berhasil
- [ ] `.exe`/installer berhasil dibuat
- [ ] Database persistent
- [ ] Excel export stabil
- [ ] Error handling tersedia
- [ ] Backup database tersedia
- [ ] Logging tersedia
- [ ] Android APK tetap berjalan
- [ ] Dokumentasi instalasi tersedia

---

# 24. Urutan Implementasi yang Wajib

Jangan mengerjakan secara acak.

Gunakan urutan:

```text
1. Audit Android project
        ↓
2. Shared model
        ↓
3. Repository interface
        ↓
4. Desktop database
        ↓
5. Desktop Cargo UI
        ↓
6. Manifest
        ↓
7. Excel Export
        ↓
8. Search / FTS
        ↓
9. BTB
        ↓
10. Stowing
        ↓
11. OCR
        ↓
12. Windows packaging
```

---

# 25. Aturan Penting untuk AI/Developer

Jika project ini dikembangkan menggunakan AI atau developer lain:

1. Jangan menghapus fitur Android tanpa alasan.
2. Jangan mengubah database schema tanpa migration plan.
3. Jangan memasukkan Android API ke `commonMain`.
4. Jangan membuat duplicate business logic Android/Desktop jika dapat dibuat shared.
5. Jangan menyimpan data utama Cargo di SharedPreferences.
6. Jangan menjalankan proses Excel berat di UI Thread.
7. Jangan mengganti template Excel tanpa persetujuan.
8. Setiap perubahan besar harus dapat di-build.
9. Android harus tetap dipertahankan selama Desktop dikembangkan.
10. Prioritaskan stabilitas data daripada penambahan UI.

---

# 26. Target Akhir

Project ini bukan dua aplikasi yang sepenuhnya berbeda.

Targetnya:

```text
                 Cargo Manifest
                       |
          +------------+------------+
          |                         |
       Android                    Windows
          |                         |
       APK                       EXE
          |                         |
          +------------+------------+
                       |
                 Shared Logic
                       |
              Cargo / Manifest
              BTB / Stowing
                 Database
                       |
                  Excel/PDF
```

**Tujuan akhirnya adalah satu sistem Cargo Manifest dengan dua client: Android dan Windows Desktop.**

---

## Current Status

Saat dokumen ini dibuat:

- Android project: **existing**
- Desktop structure: **scaffold**
- Shared module: **scaffold**
- Desktop UI: **belum diimplementasikan**
- Windows `.exe`: **belum dibuat**
- Room migration: **belum selesai**
- Excel Desktop exporter: **belum dipindahkan**
- BTB Desktop: **belum**
- Stowing Desktop: **belum**

### Next Task

> **Implement Desktop Foundation + Cargo MVP.**

Jangan langsung mengerjakan OCR, BTB, atau Stowing sebelum Cargo + Database + Excel foundation stabil.

# 27. Aturan Pembuatan dan Arsip File Manifest Harian

Setiap Manifest yang dibuat Desktop **wajib mengikuti tanggal sistem saat Manifest dibuat**.

Tanggal tidak boleh diketik manual untuk menentukan tanggal file Manifest.

## Aturan tanggal

Aplikasi harus mengambil tanggal dari tanggal sistem Windows.

Contoh:

```text
Tanggal Windows: 21 Agustus 2026
Flight: 2
```

Maka nama file wajib menjadi:

```text
MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
```

## Aturan template

File template Excel asli **tidak boleh diubah atau ditimpa**.

Alurnya:

```text
template_manifest.xlsx
        ↓
COPY
        ↓
MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
        ↓
Isi data Manifest
        ↓
Simpan
```

Template asli harus tetap tersedia untuk pembuatan Manifest berikutnya.

## Aturan folder berdasarkan bulan

File Manifest harus otomatis dipindahkan/disimpan ke folder berdasarkan tahun dan bulan.

Struktur:

```text
MANIFEST/
└── 2026/
    └── AGUSTUS/
        ├── MANIFES 01 AGUSTUS 2026 FLIGHT 1.xlsx
        ├── MANIFES 01 AGUSTUS 2026 FLIGHT 2.xlsx
        ├── MANIFES 21 AGUSTUS 2026 FLIGHT 1.xlsx
        └── MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
```

Ketika bulan berganti:

```text
MANIFEST/
├── 2026/
│   ├── AGUSTUS/
│   └── SEPTEMBER/
```

Aplikasi harus membuat folder tahun/bulan secara otomatis jika belum tersedia.

## Format nama file

Format resmi:

```text
MANIFES {DD} {BULAN} {YYYY} FLIGHT {N}.xlsx
```

Contoh:

```text
MANIFES 21 AGUSTUS 2026 FLIGHT 1.xlsx
MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
MANIFES 22 AGUSTUS 2026 FLIGHT 1.xlsx
```

Gunakan nama bulan Bahasa Indonesia:

```text
JANUARI
FEBRUARI
MARET
APRIL
MEI
JUNI
JULI
AGUSTUS
SEPTEMBER
OKTOBER
NOVEMBER
DESEMBER
```

## Aturan duplikasi

Aplikasi **tidak boleh menimpa Manifest yang sudah ada**.

Jika file berikut sudah ada:

```text
MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
```

maka aplikasi harus memberikan peringatan:

> Manifest 21 Agustus 2026 Flight 2 sudah ada.

Pilihan yang tersedia:

```text
[ Buka Manifest yang Ada ]
[ Batalkan ]
```

Jangan membuat file dengan nama berbeda secara otomatis untuk menyembunyikan duplikasi, karena setiap tanggal + flight harus mempunyai identitas Manifest yang jelas.

## Aturan pembukaan Excel

Setelah Manifest baru berhasil dibuat dari template, Desktop dapat membuka file tersebut menggunakan aplikasi spreadsheet default Windows.

Alur:

```text
CargoManifest.exe
       ↓
Pilih/Buat Manifest
       ↓
Ambil tanggal Windows
       ↓
Pilih Flight
       ↓
Copy template
       ↓
Buat nama file
       ↓
Buat folder tahun/bulan
       ↓
Simpan Manifest
       ↓
Buka file dengan Excel/default spreadsheet
```

## Aturan perubahan tanggal

Tanggal Manifest ditentukan ketika Manifest dibuat.

Jika tanggal Windows berubah dari:

```text
21 AGUSTUS 2026
```

menjadi:

```text
22 AGUSTUS 2026
```

Manifest baru harus menggunakan:

```text
MANIFES 22 AGUSTUS 2026 FLIGHT N.xlsx
```

Manifest tanggal sebelumnya tidak boleh diubah otomatis.

## Aturan penting

1. Template asli tidak boleh ditimpa.
2. Tanggal Manifest harus berasal dari tanggal sistem ketika Manifest dibuat.
3. Nama file harus mengikuti format resmi.
4. Folder tahun dan bulan dibuat otomatis.
5. Bulan menggunakan Bahasa Indonesia.
6. File Manifest yang sudah ada tidak boleh ditimpa.
7. Manifest tanggal sebelumnya tidak boleh berubah hanya karena tanggal sistem berubah.
8. File baru harus dibuat dari template.
9. Setelah file dibuat, Desktop dapat membuka file tersebut di Excel/default spreadsheet.
10. Logika penamaan dan pengarsipan harus berada di service/use case, bukan di UI.

## Target implementasi

Gunakan abstraction seperti:

```text
ManifestFileService
```

dengan tanggung jawab:

```text
getCurrentManifestDate()
getMonthFolder()
buildManifestFileName()
createManifestFromTemplate()
checkExistingManifest()
openManifest()
```

Dengan demikian UI Desktop cukup memanggil service tersebut dan tidak menangani detail filesystem secara langsung.
