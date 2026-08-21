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

Untuk MVP, **tidak menggunakan SQLite + FTS5 sebagai mekanisme utama**.

Pencarian dilakukan langsung terhadap:

1. struktur folder arsip;
2. nama file Manifest;
3. isi workbook Excel.

Konsep:

```text
User Search
     ↓
Folder Archive
     ↓
Find .xlsx files
     ↓
Read Excel
     ↓
Extract text
     ↓
Search keyword
     ↓
Display Manifest
     ↓
Open original Excel
```

Tujuan utama adalah menjaga sistem sederhana dan tidak membuat database pencarian sebelum benar-benar diperlukan.

---

# 16. Search Modes

## 16.1 File/Folder Search

Digunakan untuk pencarian berdasarkan:

- tahun;
- bulan;
- tanggal;
- flight;
- nama file.

Contoh:

```text
FLIGHT 2
21 AGUSTUS 2026
2026
```

Tidak perlu membaca isi Excel jika informasi tersebut sudah tersedia dari nama file atau struktur folder.

---

## 16.2 Excel Content Search

Digunakan jika pengguna mencari data yang berada di dalam Manifest.

Contoh:

```text
Tripleks Tony
```

atau:

```text
Tripleks
```

atau:

```text
Tony
```

atau:

```text
KAL001
```

Flow:

```text
Search: "Tripleks Tony"
        ↓
Scan Manifest folder
        ↓
Find .xlsx
        ↓
Read workbook
        ↓
Search cells/text
        ↓
Return matching files
```

Desktop tidak mengubah workbook selama proses pencarian.

---

# 17. Excel Text Extraction

Tahap awal mengambil teks yang tersedia di workbook.

Sumber yang dapat dibaca:

- worksheet cells;
- header;
- rows;
- nilai teks;
- nilai numerik jika diperlukan;
- formula result jika library mendukung dan memang diperlukan.

Contoh:

```text
Excel:

TRIPLEKS | TONY | 5 KOLI | 50 KG
```

Query:

```text
Tripleks Tony
```

harus dapat menemukan workbook tersebut.

---

# 18. Search Normalization

Pencarian dapat dinormalisasi:

```text
"ULIN"
"ulin"
" Ulin "
```

menjadi bentuk pencarian yang konsisten.

Normalization hanya digunakan untuk pencarian.

Nilai asli Excel tidak boleh diubah.

---

# 19. Search Query

MVP harus mendukung minimal:

```text
Single keyword:
Tripleks

Multiple keywords:
Tripleks Tony

Identifier:
KAL001

Customer:
Tony

Cargo:
Pinang
```

Untuk multiple keywords, hasil yang mengandung semua kata pencarian diprioritaskan.

---

# 20. Search Flow

```text
User enters keyword
        ↓
Normalize query
        ↓
Search file/folder metadata
        ↓
Search Excel content
        ↓
Collect matching files
        ↓
Sort results
        ↓
Display results
        ↓
Open original Excel
```

Hasil harus menunjuk langsung ke file Excel asli.

---

# 21. Search Filters

MVP dapat menyediakan:

- keyword;
- tahun;
- bulan;
- tanggal;
- flight.

Filter digunakan untuk mempersempit jumlah Excel yang harus dibaca.

Contoh:

```text
Keyword : Tripleks Tony
Tahun   : 2026
Bulan   : Agustus
```

Dengan demikian Desktop tidak perlu membaca seluruh arsip jika pengguna sudah memberikan filter.

---

# 22. Search Performance Strategy

Prioritas optimasi:

```text
1. Batasi folder berdasarkan filter
2. Filter berdasarkan nama file
3. Hanya baca .xlsx
4. Baca workbook di background
5. Hentikan pembacaan file jika tidak relevan
6. Tampilkan hasil secara bertahap jika diperlukan
```

Jangan langsung membuat database hanya untuk mengatasi masalah performa yang belum terbukti.

---

# 23. Optional Search Cache

Jika pencarian langsung mulai terasa lambat, tahap berikutnya dapat menggunakan cache sederhana.

Contoh:

```text
Excel File
   ↓
Extracted Text Cache
   ↓
Search
```

Cache tetap bukan sumber data utama.

File Excel tetap menjadi sumber asli.

---

# 24. SQLite / FTS5 — Future Optimization Only

SQLite + FTS5 **bukan requirement MVP**.

Teknologi tersebut hanya dipertimbangkan jika pengujian arsip nyata menunjukkan:

- jumlah file sangat besar;
- pencarian terlalu lambat;
- pencarian berulang membaca file yang sama terlalu sering;
- cache sederhana tidak cukup.

Urutan keputusan:

```text
Folder/File Search
       ↓
Excel Content Search
       ↓
Measure Performance
       ↓
Cukup?
 ┌─────┴─────┐
 YA          TIDAK
 ↓             ↓
Selesai    Cache
               ↓
           Cukup?
          ┌────┴────┐
         YA         TIDAK
          ↓           ↓
       Selesai    SQLite/FTS5
```

---

# 25. No Mandatory Search Database

Aplikasi tidak boleh memiliki persyaratan:

```text
Database harus ada
```

agar pencarian dapat bekerja.

Jika database/cache hilang:

```text
Folder Excel
    ↓
Search
```

tetap harus dapat dilakukan.

---

# 26. Background Processing

Operasi berikut tidak boleh dijalankan di UI thread:

- scan banyak folder;
- membaca banyak Excel;
- content search;
- membuat cache;
- rebuild cache.

Gunakan background coroutine/dispatcher yang sesuai.

UI harus menerima status:

```text
Idle
Scanning
Reading Excel
Searching
Completed
Error
Cancelled
```

---

# 27. Progressive Search

Jika arsip besar, hasil dapat ditampilkan bertahap:

```text
Searching...

Found:
1. MANIFES 21 AGUSTUS 2026 FLIGHT 2
2. MANIFES 03 SEPTEMBER 2025 FLIGHT 1
3. ...
```

Pengguna tidak harus menunggu seluruh arsip selesai jika hasil relevan sudah ditemukan.

---

# 28. Cancellation

Pencarian panjang harus dapat dibatalkan.

Contoh:

```text
Searching 850 / 2,400 files

[ Batalkan ]
```

Pembatalan tidak boleh mengubah file Excel.

---

# 29. Missing Files

Jika file yang ditemukan berdasarkan metadata sudah tidak ada:

```text
File tidak ditemukan
```

Jangan membuat file pengganti.

Hasil tersebut dapat ditandai sebagai:

```text
Missing
```

---

# 30. Corrupted Excel

Jika satu workbook rusak atau gagal dibaca:

```text
File ditemukan
      ↓
Gagal membaca
      ↓
Catat error
      ↓
Lanjutkan file berikutnya
```

Satu file rusak tidak boleh menghentikan seluruh pencarian.

---

# 31. Search Result Model

Contoh:

```text
SearchResult
├── fileName
├── filePath
├── date
├── flight
├── matchedText
└── status
```

`matchedText` digunakan untuk membantu pengguna memahami mengapa file tersebut muncul.

---

# 32. Result Ranking

Untuk multiple keyword:

```text
Tripleks Tony
```

prioritaskan:

1. file yang mengandung `Tripleks` dan `Tony`;
2. file yang memiliki kedua kata dalam worksheet yang sama;
3. file yang hanya mengandung salah satu kata.

Algoritma ranking dapat disederhanakan pada MVP dan ditingkatkan jika diperlukan.

---

# 33. Archive Search Safety

Search harus bersifat read-only.

Selama pencarian:

```text
Excel
 ↓
READ
```

bukan:

```text
Excel
 ↓
READ
 ↓
WRITE
```

Tidak boleh ada perubahan format, formula, isi, tanggal, atau nama file akibat pencarian.

---

# 34. Search Technical Decision

Keputusan teknis saat ini:

| Fitur | MVP |
|---|---|
| Folder search | **WAJIB** |
| File name search | **WAJIB** |
| Excel content search | **WAJIB** |
| Background search | **WAJIB** |
| SQLite | Tidak wajib |
| FTS5 | Tidak wajib |
| Search cache | Opsional |
| Search database | Future optimization |

---

# 35. Performance Benchmark

Sebelum menggunakan SQLite/FTS5, ukur:

```text
Jumlah file
Total ukuran arsip
Waktu pencarian
Memory usage
CPU usage
```

Contoh pengujian:

```text
100 Excel
500 Excel
1.000 Excel
2.000 Excel
5.000 Excel
```

Keputusan optimasi harus berdasarkan hasil nyata.

---

# 36. Final Search Principle

> **Mulai dari folder.**

> **Baca Excel hanya ketika diperlukan.**

> **Gunakan filter untuk mempersempit pencarian.**

> **Jangan membuat database sebelum ada masalah performa.**

> **Jika suatu hari folder + Excel search sudah terlalu lambat, barulah pertimbangkan cache atau SQLite/FTS5.**

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
| Search architecture | Folder + Excel Content Search |
| SQLite/FTS5 | Future optimization only |
| Windows target | Windows 10/11 |
| `.exe` packaging | Planned |
| Implementation | **Not started** |

**Status:** Technical planning — do not begin full implementation until the readiness checklist is satisfied.
