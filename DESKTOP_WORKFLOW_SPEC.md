# DESKTOP_WORKFLOW_SPEC.md

**Project:** Android Cargo Manifest App — Desktop/Windows  
**Document Type:** Workflow Specification  
**Status:** Planning / Workflow Definition  
**Depends on:** `DESKTOP_DEVELOPMENT_PLAN.md`, `DEVELOPMENT_RULES.md`, `DESKTOP_TECHNICAL_SPEC.md`

---

# 1. Tujuan

Dokumen ini mengunci **alur kerja pengguna** untuk aplikasi Desktop.

Fokus Desktop:

1. membantu persiapan Manifest harian;
2. menjaga aturan nama file;
3. menjaga struktur arsip;
4. mencegah duplikasi;
5. membuka Template/Manifest Excel dengan cepat;
6. mencari Manifest lama dari arsip.

Desktop **bukan pengganti Excel** dan bukan sistem untuk mengatur seluruh workflow Cargo.

---

# 2. Prinsip Workflow

Aplikasi harus mengikuti prinsip:

> **Desktop membantu pekerjaan sebelum dan di sekitar Excel, bukan memaksa seluruh pekerjaan pindah ke Desktop.**

Alur utama:

```text
Desktop
   ↓
Persiapan Manifest
   ↓
Excel
   ↓
Pekerjaan Manifest
   ↓
Arsip
   ↓
Desktop Search
```

---

# 3. Scope MVP

MVP memiliki empat area utama:

```text
┌─────────────────────────────┐
│          Dashboard          │
├─────────────────────────────┤
│ 1. Buat Manifest            │
│ 2. Cari Manifest             │
│ 3. Arsip                     │
│ 4. Pengaturan                │
└─────────────────────────────┘
```

Fokus implementasi pertama:

```text
Buat Manifest
↓
Buka Excel
```

Kemudian:

```text
Cari Manifest
↓
Buka Excel
```

---

# 4. Workflow A — Membuka Aplikasi

Saat aplikasi dijalankan:

```text
Start Desktop
      ↓
Load Configuration
      ↓
Check Template
      ↓
Check Manifest Root Folder
      ↓
Dashboard
```

Jika konfigurasi belum tersedia, aplikasi meminta pengguna menentukan lokasi yang diperlukan.

---

# 5. Dashboard

Dashboard harus sederhana.

Informasi utama:

```text
Tanggal hari ini
Lokasi arsip
Status Template
```

Aksi utama:

```text
[ BUAT MANIFEST ]

[ CARI MANIFEST ]

[ ARSIP ]

[ PENGATURAN ]
```

Jangan memenuhi Dashboard dengan fitur yang tidak digunakan setiap hari.

---

# 6. Workflow B — Buat Manifest Harian

## 6.1 Tujuan

Membuat Manifest baru berdasarkan Master Template Excel.

---

## 6.2 Flow

```text
Klik "Buat Manifest"
        ↓
Ambil tanggal Windows
        ↓
Tampilkan tanggal hari ini
        ↓
Pilih / masukkan Flight
        ↓
Buat nama file
        ↓
Tentukan folder tahun/bulan
        ↓
Periksa duplikasi
        ↓
Validasi Template
        ↓
Copy Template
        ↓
Buka Manifest
```

---

# 7. Aturan Tanggal

Tanggal default berasal dari tanggal sistem Windows.

Contoh:

```text
21 Agustus 2026
```

Nama Manifest:

```text
MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
```

Pengguna tidak perlu mengetik tanggal untuk workflow normal.

---

# 8. Perubahan Tanggal

Jika diperlukan koreksi tanggal:

```text
Buat Manifest
      ↓
Tanggal default = hari ini
      ↓
[ Ubah tanggal ]
      ↓
Konfirmasi
      ↓
Buat Manifest
```

Perubahan tanggal harus disengaja.

Aplikasi tidak boleh mengubah tanggal Manifest lama secara otomatis.

---

# 9. Aturan Flight

Flight adalah parameter Manifest.

Contoh:

```text
Flight 1
Flight 2
Flight 3
```

Format final mengikuti aturan yang sudah ditetapkan:

```text
MANIFES {TANGGAL} {BULAN} {TAHUN} FLIGHT {N}.xlsx
```

Nilai Flight wajib valid sebelum file dibuat.

---

# 10. Workflow C — Penentuan Folder

Tanggal menentukan struktur arsip:

```text
MANIFEST/
└── 2026/
    └── AGUSTUS/
```

Kemudian file:

```text
MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
```

Aplikasi membuat folder jika belum tersedia.

---

# 11. Workflow D — Duplicate Protection

Sebelum membuat file:

```text
Target file exists?
```

### Jika tidak ada:

```text
Copy Template
↓
Manifest berhasil dibuat
```

### Jika sudah ada:

```text
Manifest sudah ada.

[ Buka Manifest ]
[ Batalkan ]
```

Tidak boleh:

- overwrite otomatis;
- menghapus file lama;
- mengganti nama secara acak;
- membuat duplikat tersembunyi.

---

# 12. Workflow E — Template Excel

Master Template:

```text
TEMPLATE/
└── template_manifest.xlsx
```

Workflow:

```text
Master Template
      ↓
Copy
      ↓
Manifest Baru
```

Master Template tidak boleh digunakan sebagai file kerja langsung.

---

# 13. Workflow F — Membuka Excel

Setelah Manifest berhasil dibuat:

```text
Manifest created
      ↓
Open file
      ↓
Windows default spreadsheet application
```

Jika Excel tersedia, Manifest dapat terbuka di Excel.

Jika Excel tidak tersedia tetapi terdapat aplikasi `.xlsx` lain:

```text
Gunakan aplikasi default Windows
```

Jika tidak ada aplikasi pembuka:

```text
Manifest berhasil dibuat.

Lokasi:
...
```

File tetap aman.

---

# 14. Workflow G — Selesai Membuat Manifest

Setelah Excel dibuka:

```text
Desktop
   ↓
Tidak mengontrol pekerjaan di Excel
```

Pengguna bebas bekerja di Excel.

Desktop tidak perlu meminta pengguna menginput ulang seluruh data Manifest.

---

# 15. Workflow H — Pencarian Manifest

Tujuan:

> Menemukan Manifest lama berdasarkan informasi yang diingat pengguna.

Contoh:

```text
Tripleks Tony
```

atau:

```text
Tony
```

atau:

```text
Tripleks
```

atau:

```text
KAL001
```

---

# 16. Search Flow

```text
Klik "Cari Manifest"
        ↓
Masukkan keyword
        ↓
Optional:
Tahun
Bulan
Tanggal
Flight
        ↓
Scan folder arsip
        ↓
Cari file Excel
        ↓
Baca isi Excel
        ↓
Cari keyword
        ↓
Tampilkan hasil
```

---

# 17. Contoh Pencarian "Tripleks Tony"

Arsip:

```text
MANIFEST/
└── 2026/
    └── AGUSTUS/
        ├── MANIFES 20 AGUSTUS 2026 FLIGHT 1.xlsx
        ├── MANIFES 21 AGUSTUS 2026 FLIGHT 1.xlsx
        └── MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
```

Isi salah satu file:

```text
TRIPLEKS | TONY | 5 KOLI | 50 KG
```

Query:

```text
Tripleks Tony
```

Hasil:

```text
MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
```

Pengguna dapat:

```text
[ Buka ]
```

---

# 18. Search Berdasarkan Nama File

Jika pengguna mencari:

```text
Flight 2
```

aplikasi dapat terlebih dahulu mencari berdasarkan nama file.

Tidak perlu membaca isi Excel jika informasi sudah tersedia dari nama file.

---

# 19. Search Berdasarkan Isi Excel

Jika pengguna mencari:

```text
Tripleks Tony
```

Desktop membaca workbook secara read-only.

```text
Excel
 ↓
Read
 ↓
Search
 ↓
Result
```

Tidak ada perubahan terhadap Excel.

---

# 20. Filter Search

Filter opsional:

```text
Tahun: 2026
Bulan: Agustus
Tanggal: 21
Flight: 2
```

Filter digunakan untuk mengurangi jumlah file yang perlu diperiksa.

---

# 21. Hasil Pencarian

Setiap hasil minimal menampilkan:

```text
Nama Manifest
Tanggal
Flight
Lokasi file
Status
```

Contoh:

```text
MANIFES 21 AGUSTUS 2026 FLIGHT 2

Tanggal : 21 Agustus 2026
Flight  : 2
Status  : Ditemukan

[ Buka Excel ]
```

---

# 22. Jika Banyak Hasil

Contoh:

```text
Hasil ditemukan: 12

1. MANIFES 21 AGUSTUS 2026 FLIGHT 2
2. MANIFES 20 AGUSTUS 2026 FLIGHT 1
3. MANIFES 18 AGUSTUS 2026 FLIGHT 2
...
```

Urutan default:

```text
Tanggal terbaru
↓
Flight
↓
Relevansi keyword
```

Algoritma ranking dapat disederhanakan pada MVP.

---

# 23. Jika Tidak Ada Hasil

Tampilkan:

```text
Manifest tidak ditemukan.

Coba:
- kata kunci lain;
- tahun lain;
- bulan lain;
- hapus filter.
```

Jangan membuat hasil palsu.

---

# 24. Jika File Rusak

Jika file ditemukan tetapi tidak dapat dibaca:

```text
File ditemukan tetapi tidak dapat dibaca.

Nama:
...

Lokasi:
...
```

Pencarian harus tetap melanjutkan file lain.

---

# 25. Jika File Hilang

Jika metadata/path menunjuk ke file yang sudah tidak ada:

```text
File tidak ditemukan di lokasi arsip.
```

Tidak membuat file pengganti.

---

# 26. Workflow I — Arsip

Menu Arsip memungkinkan pengguna menjelajah:

```text
ARSIP
├── 2021
├── 2022
├── 2023
├── 2024
├── 2025
└── 2026
```

Kemudian:

```text
2026
└── AGUSTUS
```

Kemudian daftar Manifest.

---

# 27. Membuka Arsip

Saat pengguna memilih Manifest:

```text
Pilih file
   ↓
[ Buka Excel ]
```

Desktop membuka file asli.

Tidak membuat copy hanya untuk membuka.

---

# 28. Workflow J — Pengaturan

Pengaturan MVP minimal:

```text
Manifest Root Folder
Template Excel
```

Contoh:

```text
Manifest Root:
D:\CARGO\MANIFEST

Template:
D:\CARGO\TEMPLATE	emplate_manifest.xlsx
```

Perubahan lokasi harus divalidasi.

---

# 29. Workflow K — Folder Tidak Ditemukan

Jika root folder hilang:

```text
Manifest folder tidak ditemukan.

[ Pilih Folder ]
[ Pengaturan ]
```

Jangan otomatis membuat folder di lokasi yang tidak diketahui pengguna.

Untuk lokasi default yang sudah disepakati, aplikasi dapat membuat folder yang memang menjadi tanggung jawab aplikasi.

---

# 30. Workflow L — Template Tidak Ditemukan

Jika Template hilang:

```text
Template Excel tidak ditemukan.

[ Pilih Template ]
[ Pengaturan ]
```

Jangan membuat Manifest kosong sebagai pengganti Template.

---

# 31. Workflow M — Search Tanpa Database

MVP:

```text
Folder
 ↓
File .xlsx
 ↓
Read Excel
 ↓
Search
```

SQLite/FTS5 tidak diperlukan agar search dapat bekerja.

Jika pencarian nanti terbukti lambat, optimasi dilakukan setelah benchmark.

---

# 32. Workflow N — Background Search

Pencarian banyak file dilakukan di background.

UI:

```text
Mencari...

125 / 1.250 file

[ Batalkan ]
```

UI tetap responsif.

---

# 33. Workflow O — Cancel Search

Jika pengguna menekan:

```text
[ Batalkan ]
```

maka:

```text
Stop search
↓
Return to Search Screen
```

Tidak ada perubahan pada file.

---

# 34. Workflow P — Daily Usage

Workflow penggunaan harian yang diharapkan:

```text
Datang / mulai pekerjaan
        ↓
Buka Desktop
        ↓
Buat Manifest
        ↓
Pilih Flight
        ↓
Manifest dibuat
        ↓
Excel terbuka
        ↓
Input / pekerjaan dilakukan di Excel
        ↓
Simpan Excel
        ↓
Selesai
```

Desktop tidak perlu terus terbuka selama pengguna bekerja di Excel.

---

# 35. Workflow Q — Historical Usage

Jika membutuhkan data lama:

```text
Buka Desktop
      ↓
Cari Manifest
      ↓
"Tripleks Tony"
      ↓
Hasil
      ↓
Buka Excel
      ↓
Lihat / gunakan data
```

Tujuan utama Desktop di bagian ini adalah **mengurangi waktu mencari arsip**, bukan menggantikan isi Excel.

---

# 36. Workflow R — Tidak Ada Internet

Fungsi inti tetap berjalan tanpa internet:

```text
Buat Manifest
Search
Archive
Open Excel
```

Tidak membutuhkan server online untuk MVP.

---

# 37. Workflow S — Data Integrity

Semua operasi harus menjaga:

```text
Master Template
       ↓
Tidak berubah

Manifest lama
       ↓
Tidak berubah

Manifest baru
       ↓
File baru

Search
       ↓
Read-only
```

---

# 38. Workflow T — Recovery

Jika aplikasi berhenti saat membuat Manifest:

```text
Restart
↓
Check target file
↓
Jika file valid → gunakan file
Jika file tidak ada → buat ulang
```

Aplikasi tidak boleh membuat duplikat secara otomatis.

---

# 39. Workflow U — Backup

Backup tidak menjadi fungsi wajib MVP.

Namun Desktop tidak boleh menghapus arsip lama.

Backup dapat ditambahkan sebagai fitur terpisah setelah kebutuhan ditentukan.

---

# 40. Workflow V — Update Application

Update aplikasi tidak boleh menghapus:

```text
MANIFEST/
TEMPLATE/
DATA penting pengguna
```

Lokasi data pengguna harus dipisahkan dari file aplikasi.

---

# 41. Workflow W — Error Principle

Setiap error harus menjawab:

```text
Apa yang terjadi?
Apa dampaknya?
Apa yang bisa dilakukan pengguna?
```

Contoh:

```text
Manifest tidak dapat dibuat.

Template Excel tidak ditemukan.

[ Pilih Template ]
```

---

# 42. Workflow X — Tidak Ada Input Ulang yang Tidak Perlu

Desktop tidak boleh meminta pengguna mengisi kembali data yang memang akan dikerjakan di Excel.

Contoh yang tidak menjadi tujuan MVP:

```text
Desktop:
Customer
PTI
BTB
Koli
KG
Trademark
Invoice
...
```

Kemudian:

```text
Excel:
Input lagi
```

Ini merupakan duplikasi pekerjaan dan harus dihindari.

---

# 43. Workflow Y — Batas Desktop

Desktop tidak mengatur:

```text
Timbangan
↓
BTB
↓
Admin
↓
PTI
↓
Manifest
↓
Stowing
```

Workflow tersebut tetap mengikuti proses kerja lapangan.

Desktop hanya membantu:

```text
Manifest Preparation
+
Manifest Archive
+
Manifest Search
```

---

# 44. Workflow Z — Future Features

Fitur masa depan tidak boleh masuk MVP tanpa review:

- PTI management;
- BTB management;
- customer database;
- automatic cargo reconciliation;
- invoice management;
- stowing management;
- cloud synchronization;
- multi-user server;
- online backend.

---

# 45. End-to-End MVP Workflow

## Workflow Harian

```text
START
  ↓
Open Desktop
  ↓
Dashboard
  ↓
Buat Manifest
  ↓
Tanggal otomatis
  ↓
Flight
  ↓
Duplicate Check
  ↓
Copy Template
  ↓
Save:
MANIFEST/YYYY/BULAN/
  ↓
Open Excel
  ↓
User bekerja di Excel
  ↓
Simpan
  ↓
END
```

## Workflow Historical Search

```text
START
  ↓
Open Desktop
  ↓
Cari Manifest
  ↓
Keyword
  ↓
Optional Filters
  ↓
Folder Search
  ↓
Excel Content Search
  ↓
Results
  ↓
Open Original Excel
  ↓
END
```

---

# 46. MVP Acceptance Criteria

MVP dianggap memenuhi workflow jika:

```text
[ ] Dapat membuat Manifest menggunakan Template
[ ] Tanggal default otomatis benar
[ ] Flight dapat ditentukan
[ ] Nama file sesuai format
[ ] Folder tahun/bulan benar
[ ] Folder otomatis dibuat jika diperlukan
[ ] Duplikasi tidak ditimpa
[ ] Manifest dapat dibuka di Excel
[ ] Master Template tidak berubah
[ ] Manifest lama tidak berubah
[ ] Dapat menjelajah arsip
[ ] Dapat mencari berdasarkan nama file
[ ] Dapat mencari isi Excel
[ ] Contoh "Tripleks Tony" dapat ditemukan
[ ] Search tidak mengubah Excel
[ ] Search tetap responsif
[ ] Search dapat dibatalkan
[ ] File rusak tidak menghentikan seluruh search
[ ] Aplikasi dapat berjalan tanpa internet
```

---

# 47. Final Workflow Principle

> **Desktop bukan tempat semua pekerjaan Cargo dipindahkan.**

> **Desktop adalah alat bantu untuk membuat, mengatur, membuka, dan mencari Manifest.**

> **Excel tetap menjadi tempat kerja Manifest.**

> **Arsip tetap menjadi sumber data historis.**

> **Search harus dimulai dari filesystem dan Excel, bukan database.**

> **Kompleksitas hanya ditambahkan jika pekerjaan nyata membutuhkannya.**

---

# 48. Status

| Workflow | Status |
|---|---|
| Application startup | Defined |
| Dashboard | Defined |
| Daily Manifest | Defined |
| Date handling | Defined |
| Flight handling | Defined |
| Duplicate protection | Defined |
| Template handling | Defined |
| Excel opening | Defined |
| Archive browsing | Defined |
| File-name search | Defined |
| Excel content search | Defined |
| "Tripleks Tony" search | Defined |
| Search filters | Defined |
| Background search | Defined |
| Search cancellation | Defined |
| Error handling | Defined |
| Offline operation | Defined |
| PTI/BTB management | Out of MVP scope |
| Cloud/backend | Out of MVP scope |
| Implementation | Not started |

**Status:** Workflow locked for review. UI design and implementation should begin only after this workflow is approved.
