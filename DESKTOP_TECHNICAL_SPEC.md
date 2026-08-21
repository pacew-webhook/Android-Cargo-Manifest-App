# DESKTOP_TECHNICAL_SPEC.md

**Project:** Android Cargo Manifest App — Desktop/Windows  
**Document Type:** Technical Specification  
**Status:** Planning / Architecture Definition  
**Implementation:** Not started  
**Depends on:** `DESKTOP_DEVELOPMENT_PLAN.md` and `DEVELOPMENT_RULES.md`

---

# 1. Purpose

Dokumen ini menerjemahkan konsep produk Desktop menjadi rancangan teknis.

Desktop memiliki dua fungsi utama:

1. **Manifest Daily Manager**
2. **Historical Manifest Search**

Desktop tidak menggantikan Excel sebagai tempat input/edit Manifest.

---

# 2. Technical Goals

Sistem harus:

- berjalan secara lokal di Windows;
- dapat membuat Manifest dari template Excel;
- membuat struktur folder otomatis;
- membuat nama file otomatis;
- mencegah overwrite;
- membuka Manifest dengan aplikasi spreadsheet default;
- membaca arsip Excel lama;
- menyediakan pencarian historis;
- tetap responsif saat membaca banyak file;
- menjaga file asli tidak berubah;
- dapat dibuild menjadi `.exe`;
- tidak bergantung pada internet untuk fungsi inti.

---

# 3. Recommended Technology

## 3.1 Programming Language

**Kotlin** direkomendasikan agar pengembangan Desktop tetap dekat dengan project Android yang sudah menggunakan Kotlin.

Namun kode Android tidak boleh dipindahkan secara paksa.

---

## 3.2 Desktop Framework

**Compose Multiplatform for Desktop** direkomendasikan untuk UI Windows.

Alasan:

- menggunakan Kotlin;
- konsep UI dekat dengan Jetpack Compose;
- dapat membuat aplikasi desktop native-style;
- memungkinkan reuse konsep/model tertentu tanpa memaksa shared code;
- cocok untuk project yang sudah menggunakan Compose di Android.

---

## 3.3 Build System

Gunakan:

```text
Gradle
Kotlin DSL
```

Struktur harus memungkinkan build khusus Desktop tanpa mengganggu Android.

---

## 3.4 Windows Packaging

Target awal:

```text
Windows 10/11
```

Distribusi dapat menggunakan:

```text
.exe
```

atau installer Windows yang sesuai.

Pilihan packaging final ditentukan setelah prototype berhasil.

---

# 4. Proposed Project Structure

Struktur konseptual:

```text
Desktop/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
├── src/
│   └── main/
│       ├── kotlin/
│       │   └── ...
│       └── resources/
│           ├── templates/
│           └── ...
├── docs/
└── README.md
```

Jika Desktop ditempatkan dalam repository Android yang sama, gunakan struktur terpisah:

```text
project/
├── app/                  # Android
├── desktop/              # Windows Desktop
├── docs/
└── ...
```

**Struktur final harus diputuskan sebelum coding.**

---

# 5. Architecture

Gunakan pemisahan tanggung jawab:

```text
┌───────────────────────────────┐
│             UI                │
│          Compose Desktop      │
└──────────────┬────────────────┘
               ↓
┌───────────────────────────────┐
│       Application Layer       │
│   Manifest / Archive UseCase  │
└──────────────┬────────────────┘
               ↓
┌──────────────┴────────────────┐
│        Domain / Models        │
└──────────────┬────────────────┘
               ↓
┌──────────────┴────────────────┐
│ Infrastructure / Services     │
│ Excel | Files | Search | Log  │
└───────────────────────────────┘
```

UI tidak boleh membaca atau menulis filesystem secara langsung.

---

# 6. Core Modules

Modul konseptual:

```text
ui
application
domain
data
infrastructure
```

Contoh:

```text
ui/
application/
domain/
data/
infrastructure/
    filesystem/
    excel/
    search/
    logging/
```

---

# 7. Core Services

## 7.1 ManifestFileService

Tanggung jawab:

```text
getCurrentDate()
buildManifestFileName()
getManifestFolder()
findExistingManifest()
createManifest()
openManifest()
listManifestFiles()
```

Service ini bertanggung jawab atas aturan file Manifest.

---

## 7.2 ExcelTemplateService

Tanggung jawab:

```text
findTemplate()
validateTemplate()
copyTemplate()
```

Tidak boleh mengubah Master Template.

---

## 7.3 ArchiveService

Tanggung jawab:

```text
listYears()
listMonths()
listManifests()
findManifest()
```

---

## 7.4 ArchiveSearchService

Tanggung jawab:

```text
scanArchive()
indexFile()
search()
rebuildIndex()
removeMissingFiles()
```

---

## 7.5 FileSystemService

Tanggung jawab:

- membuat directory;
- memeriksa file;
- membaca metadata;
- membuka file;
- memastikan path valid.

---

## 7.6 LoggingService

Tanggung jawab:

- application log;
- indexing log;
- error log.

---

# 8. Domain Models

Model awal sebaiknya sederhana.

## ManifestReference

```text
ManifestReference
├── date
├── flight
├── fileName
├── filePath
└── status
```

## SearchResult

```text
SearchResult
├── fileName
├── filePath
├── date
├── flight
├── matchedText
└── source
```

Jangan membuat model Cargo/PTI/BTB kompleks pada MVP Desktop jika tidak dibutuhkan untuk pencarian.

---

# 9. Manifest Creation Flow

```text
User
 ↓
Klik "Buat Manifest"
 ↓
Ambil tanggal Windows
 ↓
Pilih Flight
 ↓
Build file name
 ↓
Build year/month folder
 ↓
Check duplicate
 ↓
Validate template
 ↓
Copy template
 ↓
Open created file
```

---

# 10. File Naming Algorithm

Input:

```text
date = 2026-08-21
flight = 2
```

Output:

```text
MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
```

Tidak boleh menggunakan locale Windows secara langsung jika hasilnya dapat berubah menjadi bahasa Inggris.

Gunakan mapping bulan yang dikontrol aplikasi.

---

# 11. Archive Path Algorithm

Input:

```text
date = 2026-08-21
```

Output:

```text
MANIFEST/
2026/
AGUSTUS/
```

Final:

```text
MANIFEST/2026/AGUSTUS/MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
```

---

# 12. Duplicate Protection

Sebelum copy template:

```text
exists(targetFile)?
```

Jika:

```text
true
```

jangan overwrite.

Return:

```text
ManifestAlreadyExists
```

UI kemudian menampilkan:

```text
Manifest sudah ada.

[ Buka Manifest ]
[ Batalkan ]
```

---

# 13. Template Handling

Master template harus memiliki lokasi yang dapat dikonfigurasi.

Prioritas desain:

1. lokasi default;
2. lokasi custom jika diperlukan;
3. validasi file;
4. copy ke destination.

Jangan menyimpan satu-satunya template hanya di lokasi sementara.

---

# 14. Opening Excel

Setelah file berhasil dibuat:

```text
Desktop App
     ↓
Created Manifest Path
     ↓
Windows Default Application
     ↓
Excel / Compatible Spreadsheet
```

Gunakan mekanisme OS untuk membuka file dengan aplikasi default.

Jangan mengasumsikan Excel selalu terinstall.

Jika tidak ada aplikasi yang dapat membuka file:

```text
Manifest berhasil dibuat,
tetapi tidak dapat dibuka otomatis.
Lokasi file: ...
```

File tetap tersimpan.

---

# 15. Historical Search Architecture

Search harus dipisahkan menjadi dua tahap:

```text
INDEXING
    ↓
SEARCH
```

Bukan membaca seluruh Excel setiap kali pengguna mengetik karakter.

---

# 16. Indexing Pipeline

```text
Archive Folder
      ↓
File Discovery
      ↓
Excel Reader
      ↓
Text Extraction
      ↓
Normalization
      ↓
Search Index
```

Indexing harus berjalan di background.

---

# 17. Excel Text Extraction

Tahap awal harus mengambil teks yang memang tersedia di workbook.

Kemungkinan sumber:

- worksheet cells;
- header;
- rows;
- formulas jika diperlukan;
- metadata yang relevan.

Jangan mengubah workbook saat membaca.

---

# 18. Search Normalization

Untuk meningkatkan pencarian:

```text
"ULIN"
"ulin"
" Ulin "
```

dapat dinormalisasi menjadi bentuk yang konsisten.

Namun nilai asli file tidak boleh diubah.

Normalization hanya berlaku untuk index/search.

---

# 19. Search Index Technology

Untuk MVP, gunakan **SQLite/FTS5** hanya jika kebutuhan volume arsip membenarkannya.

Alternatif awal yang lebih sederhana:

```text
SQLite
+
FTS5
```

Keuntungan:

- pencarian cepat;
- database lokal;
- tidak memerlukan server;
- dapat dibuat ulang dari Excel;
- cocok untuk arsip besar.

Tetapi:

> **FTS5 bukan kewajiban pada prototype pertama.**

Prototype boleh dimulai dengan index sederhana untuk mengukur kebutuhan sebenarnya.

---

# 20. Index Schema — Konsep Awal

Contoh konseptual:

```text
manifest_files
├── id
├── file_path
├── file_name
├── manifest_date
├── flight
├── file_modified_time
└── indexed_at
```

FTS:

```text
manifest_search
├── file_id
├── content
└── ...
```

Schema final harus mengikuti hasil pengujian arsip nyata.

---

# 21. Search Flow

```text
User enters keyword
        ↓
Normalize query
        ↓
Search Index
        ↓
Results
        ↓
Sort by relevance/date
        ↓
User selects result
        ↓
Open original Excel
```

---

# 22. Search Filters

MVP dapat menyediakan:

- keyword;
- tahun;
- tanggal;
- flight.

Filter tambahan hanya ditambahkan jika data historis benar-benar mendukungnya.

---

# 23. Index Rebuild

Fungsi:

```text
Rebuild Search Index
```

harus tersedia.

Flow:

```text
Clear Index
    ↓
Scan Archive
    ↓
Read Excel
    ↓
Index
    ↓
Complete
```

Jika satu file gagal dibaca, lanjutkan file berikutnya dan simpan error.

---

# 24. Detecting Changed Files

Index dapat menyimpan:

```text
file_path
file_size
modified_time
```

Jika berubah:

```text
Re-index file
```

Jika tidak berubah:

```text
Skip
```

Ini menghindari pembacaan ulang seluruh arsip setiap kali aplikasi dibuka.

---

# 25. Detecting Missing Files

Jika index menunjuk ke file yang tidak ada:

```text
Missing
```

Jangan membuat file pengganti.

Tampilkan status:

```text
File tidak ditemukan
```

Index dapat dibersihkan melalui rebuild/sync.

---

# 26. Background Processing

Operasi berikut tidak boleh dijalankan di UI thread:

- scan archive;
- membaca banyak Excel;
- indexing;
- rebuild index;
- operasi filesystem besar.

Gunakan background coroutine/dispatcher yang sesuai.

UI harus menerima:

```text
Idle
Scanning
Indexing
Completed
Error
Cancelled
```

---

# 27. Cancellation

Indexing panjang sebaiknya dapat dibatalkan.

Contoh:

```text
Indexing...
245 / 2,430 files

[ Batalkan ]
```

Pembatalan tidak boleh merusak file Excel.

---

# 28. Error Handling

Gunakan error domain yang jelas.

Contoh:

```text
TemplateNotFound
TemplateInvalid
ManifestAlreadyExists
DirectoryCreationFailed
ManifestCreationFailed
ExcelOpenFailed
ArchiveReadFailed
IndexingFailed
```

UI menerjemahkan error teknis menjadi pesan pengguna.

---

# 29. Logging

Minimal:

```text
timestamp
level
operation
file
message
exception
```

Log tidak boleh menyimpan data yang tidak diperlukan.

---

# 30. Configuration

Konfigurasi yang mungkin diperlukan:

```text
Manifest Root Folder
Template File
Archive Root
Search Index Location
```

Default harus tersedia.

Pengaturan dapat disimpan lokal.

---

# 31. Recommended Directory Layout

Contoh:

```text
CargoManifest/
├── MANIFEST/
│   ├── 2021/
│   ├── 2022/
│   ├── 2023/
│   ├── 2024/
│   ├── 2025/
│   └── 2026/
│
├── TEMPLATE/
│   └── template_manifest.xlsx
│
├── DATA/
│   └── search-index.db
│
└── LOGS/
    └── application.log
```

Lokasi root harus dapat dikonfigurasi.

---

# 32. Do Not Mix Data

Jangan menyimpan:

```text
search-index.db
```

di dalam folder:

```text
MANIFEST/
```

Jangan menyimpan log atau temporary file sebagai bagian dari arsip Manifest.

---

# 33. Security

Desktop tidak membutuhkan backend online untuk MVP.

Tidak boleh:

- hard-code password;
- hard-code API key;
- mengupload Manifest ke internet;
- mengirim arsip ke server tanpa kebutuhan;
- menambahkan telemetry yang tidak diperlukan.

---

# 34. Performance Targets

Target awal:

- UI tetap responsif saat indexing;
- pembuatan Manifest terasa instan setelah template tersedia;
- pencarian index lokal terasa instan untuk penggunaan normal;
- tidak membaca seluruh arsip setiap kali keyword berubah.

Angka benchmark final ditentukan setelah menggunakan arsip nyata.

---

# 35. Compatibility

Target:

```text
Windows 10
Windows 11
```

Spreadsheet:

- Microsoft Excel jika tersedia;
- aplikasi spreadsheet default Windows yang dapat membuka `.xlsx`.

---

# 36. Testing Architecture

Testing minimal:

```text
Unit Tests
├── filename
├── month mapping
├── folder path
├── duplicate detection
└── search normalization

Integration Tests
├── template copy
├── archive scanning
├── Excel extraction
└── index rebuild

Manual Tests
├── Excel opening
├── Windows packaging
└── real archive search
```

---

# 37. Prototype Strategy

Jangan langsung membuat seluruh sistem.

Urutan prototype:

```text
Prototype 1
↓
Buat Manifest dari template

Prototype 2
↓
Folder + filename + duplicate protection

Prototype 3
↓
Buka Excel

Prototype 4
↓
Scan archive

Prototype 5
↓
Search sederhana

Prototype 6
↓
SQLite/FTS5 jika diperlukan

Prototype 7
↓
Windows packaging
```

---

# 38. Technical Decision Gates

Sebelum memilih teknologi final untuk bagian yang belum pasti:

### Gate 1 — Excel

Pastikan library yang dipilih dapat membaca dan/atau menyalin template tanpa merusak struktur.

### Gate 2 — Search

Ukur jumlah dan ukuran arsip nyata sebelum memutuskan apakah FTS5 wajib.

### Gate 3 — Packaging

Uji `.exe` pada Windows bersih/semi-bersih sebelum release.

---

# 39. Data Integrity Rules

Technical implementation wajib menjaga:

```text
Template asli
    ≠
Manifest hasil copy
```

dan:

```text
Excel asli
    ≠
Search Index
```

Index tidak boleh menjadi satu-satunya tempat penyimpanan data.

---

# 40. Android Isolation

Jika Desktop berada dalam repository yang sama:

```text
Android module
    │
    └── tetap dapat build

Desktop module
    │
    └── build terpisah
```

Perubahan Desktop harus diuji tanpa mengandalkan build Desktop untuk membuktikan Android aman.

Minimal setelah perubahan lintas-module:

```text
Android build
+
Desktop build
```

---

# 41. Release Architecture

Target akhir:

```text
Source Code
    ↓
Gradle Build
    ↓
Desktop Application
    ↓
Windows Packaging
    ↓
Installer / EXE
```

Jenis packaging final dipilih setelah prototype.

---

# 42. Future Extensions

Fitur berikut hanya boleh dipertimbangkan setelah MVP stabil:

- backup otomatis;
- drag & drop archive;
- advanced search;
- preview metadata;
- duplicate file detection;
- archive health checker;
- export search result;
- optional shared code;
- optional sync.

Tidak ada fitur future yang otomatis menjadi bagian MVP.

---

# 43. Technical Non-Goals

MVP tidak bertujuan untuk:

- menggantikan seluruh workflow Cargo;
- membuat ERP;
- membuat server backend;
- membuat aplikasi web;
- melakukan cloud sync;
- menjadi sistem PTI/BTB;
- menggantikan proses administrasi;
- menggantikan Excel sebagai dokumen kerja.

---

# 44. Implementation Readiness Checklist

Sebelum coding:

```text
[ ] DESKTOP_DEVELOPMENT_PLAN.md disetujui
[ ] DEVELOPMENT_RULES.md disetujui
[ ] Template Excel asli tersedia
[ ] Contoh Manifest nyata tersedia
[ ] Struktur folder arsip dikonfirmasi
[ ] Format nama file dikonfirmasi
[ ] Target Windows dikonfirmasi
[ ] Teknologi prototype disetujui
[ ] Strategi search disetujui
[ ] Backup strategy diketahui
```

---

# 45. Final Technical Principle

Arsitektur Desktop harus mengikuti prinsip:

> **File Manifest adalah data nyata.**

> **Excel adalah dokumen utama.**

> **Desktop mengelola proses di sekitar dokumen tersebut.**

> **Search index hanya mempercepat pencarian.**

> **Teknologi dipilih berdasarkan kebutuhan nyata, bukan karena terlihat canggih.**

> **Jangan membangun kompleksitas sebelum masalahnya benar-benar ada.**

---

# 46. Current Status

| Area | Status |
|---|---|
| Product concept | Defined |
| Development rules | Defined |
| Technical architecture | Defined |
| Desktop framework | Recommended: Compose Multiplatform |
| Language | Recommended: Kotlin |
| Build | Gradle Kotlin DSL |
| Excel workflow | Defined |
| Archive structure | Defined |
| Search architecture | Defined |
| SQLite/FTS5 | Conditional / Prototype first |
| Windows target | Windows 10/11 |
| `.exe` packaging | Planned |
| Implementation | **Not started** |

**Status:** Technical planning — do not begin full implementation until the readiness checklist is satisfied.
