# Desktop Cargo Manifest — Product & Development Specification

**Project:** Android Cargo Manifest App — Desktop/Windows  
**Document:** Desktop Development Plan & Product Specification  
**Status:** Planning / Requirements Definition  
**Implementation status:** Not started  
**Primary purpose:** Define what the Desktop application must do before coding begins.

---

## 1. Document Purpose

Dokumen ini adalah **blueprint resmi** untuk pengembangan versi Desktop/Windows dari Android Cargo Manifest App.

Dokumen ini digunakan untuk:

- menetapkan tujuan produk;
- menetapkan ruang lingkup;
- mendefinisikan perilaku aplikasi;
- menetapkan aturan data dan file;
- menetapkan aturan Manifest dan Excel;
- menentukan batas antara Android dan Desktop;
- menjadi acuan developer/AI sebelum melakukan perubahan kode.

**Tahap saat ini adalah perencanaan. Tidak ada requirement dalam dokumen ini yang berarti implementasi harus langsung dilakukan.**

---

# 2. Visi Produk

Aplikasi Desktop ditujukan sebagai aplikasi Cargo Manifest untuk laptop/Windows yang:

- mudah digunakan operator;
- dapat bekerja secara offline untuk fungsi utama;
- menggunakan data yang konsisten;
- dapat membuat dan mengarsipkan Manifest secara otomatis;
- menggunakan template Excel yang telah ditentukan;
- meminimalkan input manual yang berulang;
- menjaga data agar tidak hilang atau tertimpa;
- tetap selaras dengan aplikasi Android.

Target arsitektur produk:

```text
                 CARGO MANIFEST
                       |
          +------------+------------+
          |                         |
       Android                   Desktop
          |                         |
       Android UI              Desktop UI
          |                         |
          +------------+------------+
                       |
                Shared Business
                    & Domain
                     Logic
```

Desktop bukan aplikasi yang dibuat ulang secara terpisah tanpa mempertimbangkan project Android. Namun, Desktop juga **tidak boleh memaksa seluruh kode Android menjadi shared**.

---

# 3. Prinsip Utama

### 3.1 Single Source of Truth

Data utama aplikasi harus memiliki satu sumber kebenaran yang jelas.

Data Cargo/Manifest/BTB/Stowing tidak boleh tersebar tanpa kontrol di:

- SharedPreferences;
- file sementara;
- state UI;
- object memory yang tidak persistent.

### 3.2 Queryable Data

> Jangan menyimpan data penting di tempat yang tidak dapat di-query.

Data operasional harus dapat:

- dicari;
- difilter;
- diperbarui;
- dihapus sesuai aturan;
- dihubungkan dengan data terkait.

### 3.3 State Tidak Hilang

State UI dan data persistent harus dibedakan.

**Persistent data** → database.

**UI state** → mekanisme state management yang sesuai.

Jangan menggunakan database hanya untuk menyimpan state UI sementara tanpa alasan.

### 3.4 Heavy Task Tidak di UI Thread

Proses berat seperti:

- import/export Excel;
- PDF;
- pencarian besar;
- backup;
- scanning;
- cleanup;
- proses dokumen;

tidak boleh menghambat UI.

### 3.5 Android API Tidak Masuk Shared Layer

Kode `commonMain` tidak boleh bergantung pada Android-specific API seperti:

- `Context`;
- `Activity`;
- `Uri`;
- CameraX;
- Android permission API;
- Android-specific ML Kit.

---

# 4. Ruang Lingkup Desktop

## 4.1 Fitur inti

Desktop direncanakan memiliki:

- Dashboard;
- Cargo;
- Manifest;
- pencarian;
- Excel import/export;
- BTB;
- Stowing;
- pengelolaan file;
- pengaturan aplikasi;
- backup/recovery.

## 4.2 Prioritas

Fitur tidak dikembangkan sekaligus.

Prioritas produk:

```text
Cargo
  ↓
Manifest
  ↓
Excel
  ↓
Search
  ↓
BTB
  ↓
Stowing
  ↓
OCR / fitur tambahan
```

Urutan ini dapat berubah hanya setelah requirement direview kembali.

---

# 5. Desktop MVP

Desktop MVP harus terlebih dahulu membuktikan alur utama:

```text
Buka aplikasi
    ↓
Buat/Pilih data Cargo
    ↓
Simpan
    ↓
Tampilkan daftar
    ↓
Cari/Edit/Hapus
    ↓
Buat Manifest
    ↓
Generate file Excel
    ↓
Arsipkan
```

MVP **tidak wajib langsung memiliki seluruh fitur Android**.

Fokusnya adalah kestabilan:

- data;
- Cargo;
- Manifest;
- Excel;
- pencarian dasar;
- penyimpanan persistent.

---

# 6. Data & Persistence

## 6.1 Data utama

Data yang termasuk data operasional antara lain:

- Cargo;
- Manifest;
- Flight;
- PTI;
- BTB;
- Stowing;
- history terkait.

Data tersebut harus dapat di-query dan memiliki hubungan yang jelas.

## 6.2 Room / Database

Room menjadi pilihan utama untuk data persistent yang kompatibel dengan arsitektur project.

Target:

```text
UI
 ↓
ViewModel / Presentation
 ↓
Repository
 ↓
Database
```

UI tidak mengakses database secara langsung.

## 6.3 SharedPreferences

SharedPreferences **bukan tempat penyimpanan utama data Cargo/Manifest**.

Migrasi dari SharedPreferences harus dilakukan setelah seluruh key dan penggunaannya diaudit.

Jangan menghapus SharedPreferences secara membabi buta karena dapat menghilangkan data/fungsi yang masih diperlukan.

---

# 7. Arsitektur Multiplatform

Struktur target:

```text
Android-Cargo-Manifest-App/
│
├── app/
│   └── Android application
│
├── shared/
│   └── Shared domain/business layer
│       ├── commonMain/
│       │   ├── model/
│       │   ├── repository/
│       │   └── usecase/
│       ├── androidMain/
│       └── desktopMain/
│
└── desktopApp/
    └── Desktop/Windows application
```

## 7.1 `app/`

Khusus Android.

Contoh:

- Activity;
- CameraX;
- Android OCR;
- Android permissions;
- Android file picker;
- Android lifecycle-specific behavior.

## 7.2 `shared/`

Tempat:

- domain model;
- validation;
- business rules;
- calculations;
- repository contracts;
- use cases yang platform-independent.

## 7.3 `desktopApp/`

Tempat:

- Desktop UI;
- Windows/Desktop navigation;
- desktop file picker;
- desktop-specific persistence;
- desktop-specific integrations.

---

# 8. UI/UX Desktop

Desktop harus dirancang untuk layar laptop/monitor, bukan sekadar memperbesar UI Android.

Prinsip:

- tabel data jelas;
- input keyboard-friendly;
- navigasi desktop;
- pencarian mudah;
- informasi penting terlihat tanpa banyak perpindahan layar;
- dukungan mouse dan keyboard;
- dialog tidak menghalangi workflow secara berlebihan.

Fitur desktop yang layak dipertimbangkan:

- keyboard shortcut;
- multi-column table;
- sorting;
- filtering;
- resize column;
- drag & drop untuk file;
- window resizing.

Fitur tersebut masuk sebagai requirement UX dan **tidak harus langsung diimplementasikan pada MVP**.

---

# 9. Aturan Manifest Harian

Ini adalah requirement penting.

Setiap Manifest yang dibuat harus mengikuti **tanggal sistem Windows pada saat Manifest dibuat**.

Contoh:

```text
Tanggal: 21 Agustus 2026
Flight: 2
```

Nama file:

```text
MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
```

Tanggal tidak boleh diketik manual hanya untuk menentukan tanggal file.

---

# 10. Format Nama File Manifest

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

Nama bulan menggunakan Bahasa Indonesia:

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

---

# 11. Struktur Arsip Manifest

Manifest harus diarsipkan berdasarkan tahun dan bulan.

Contoh:

```text
MANIFEST/
└── 2026/
    └── AGUSTUS/
        ├── MANIFES 01 AGUSTUS 2026 FLIGHT 1.xlsx
        ├── MANIFES 01 AGUSTUS 2026 FLIGHT 2.xlsx
        ├── MANIFES 21 AGUSTUS 2026 FLIGHT 1.xlsx
        └── MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
```

Jika folder belum ada, aplikasi harus dapat membuatnya sesuai aturan.

Saat bulan berganti:

```text
MANIFEST/
└── 2026/
    ├── AGUSTUS/
    └── SEPTEMBER/
```

---

# 12. Template Excel

Template asli adalah **master template**.

Template tidak boleh:

- ditimpa;
- diubah menjadi file operasional;
- digunakan sebagai tempat menyimpan data harian.

Alur yang benar:

```text
Master Template
      ↓
COPY
      ↓
Manifest harian
      ↓
Isi data
      ↓
Simpan/arsip
```

Contoh:

```text
templates/
└── template_manifest.xlsx
```

kemudian:

```text
MANIFEST/2026/AGUSTUS/
└── MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
```

---

# 13. Duplikasi Manifest

Aplikasi **tidak boleh menimpa file Manifest yang sudah ada**.

Jika:

```text
MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
```

sudah ada, aplikasi harus mendeteksi konflik.

Pilihan minimal:

```text
[ Buka Manifest yang Ada ]
[ Batalkan ]
```

Aplikasi tidak boleh mengubah nama menjadi nama acak hanya untuk melewati konflik.

Tujuannya adalah menjaga identitas:

```text
Tanggal + Flight = Identitas Manifest
```

---

# 14. Perubahan Tanggal

Tanggal Manifest ditentukan ketika Manifest dibuat.

Jika tanggal sistem berubah:

```text
21 AGUSTUS 2026
        ↓
22 AGUSTUS 2026
```

Manifest baru menggunakan tanggal baru.

Manifest tanggal sebelumnya **tidak boleh diubah otomatis**.

---

# 15. Pembukaan Excel

Setelah Manifest baru berhasil dibuat, Desktop dapat membuka file menggunakan aplikasi spreadsheet default Windows.

Workflow:

```text
Buat Manifest
     ↓
Ambil tanggal sistem
     ↓
Pilih Flight
     ↓
Copy template
     ↓
Buat nama file
     ↓
Buat folder tahun/bulan
     ↓
Simpan
     ↓
Buka Excel/default spreadsheet
```

Pembukaan Excel harus dilakukan setelah file berhasil dibuat, bukan sebelum file valid tersedia.

---

# 16. Manifest File Service

Detail filesystem tidak boleh berada langsung di UI.

Target abstraction:

```text
ManifestFileService
```

Tanggung jawab konseptual:

```text
getCurrentManifestDate()
getMonthFolder()
buildManifestFileName()
checkExistingManifest()
createManifestFromTemplate()
openManifest()
```

Nama dan struktur class dapat berubah saat implementasi, tetapi tanggung jawab harus tetap terpisah dari UI.

---

# 17. Excel Processing

Excel processing harus dipisahkan dari UI.

Target:

```text
UI
 ↓
Manifest Use Case
 ↓
Excel Export Service
 ↓
Excel Library
 ↓
Manifest.xlsx
```

Template existing harus dipertahankan sejauh masih memenuhi requirement.

Hal yang harus diperhatikan:

- worksheet;
- merged cells;
- format;
- formula;
- header;
- PTI;
- cargo;
- koli;
- KG;
- flight;
- aircraft;
- FROM;
- TO;
- tanggal.

---

# 18. Search

Pencarian harus mendukung data yang relevan seperti:

- PTI;
- pengirim;
- penerima;
- flight;
- tanggal;
- jenis cargo.

FTS dapat digunakan ketika kebutuhan dan ukuran data sudah membenarkannya.

FTS bukan requirement yang harus dipaksakan ke MVP apabila pencarian database kecil masih memenuhi kebutuhan.

---

# 19. BTB

BTB merupakan fitur tahap berikutnya.

Cakupan:

- data BTB;
- bukti timbang;
- referensi BTB;
- status;
- pencarian;
- hubungan dengan Cargo/Manifest.

Semua data persistent harus mengikuti arsitektur repository/database.

---

# 20. Stowing

Stowing merupakan fitur tahap berikutnya.

Cakupan dapat meliputi:

- Stowing;
- PAG;
- checklist;
- perhitungan;
- status;
- export.

Data penting tidak boleh hanya berada pada UI state atau SharedPreferences.

---

# 21. OCR

OCR bukan prioritas Desktop MVP.

OCR Android yang bergantung pada CameraX/Android ML Kit tetap menjadi Android-specific implementation.

Jika Desktop membutuhkan OCR:

```text
Desktop OCR Adapter
       ↓
Recognized Text
       ↓
Shared Business Logic
```

Jangan membawa CameraX atau Android ML Kit ke `commonMain`.

---

# 22. Background Processing

Proses berat tidak boleh memblokir UI.

Contoh:

- Excel import;
- Excel export;
- PDF generation;
- backup;
- scanning;
- cleanup;
- database migration.

Android dapat menggunakan WorkManager untuk pekerjaan yang sesuai.

Desktop dapat menggunakan coroutine/background execution yang sesuai.

Teknologi bukan tujuan utama; **responsiveness dan reliability adalah requirement-nya**.

---

# 23. Cache & Temporary Files

File temporary harus dikelola secara otomatis.

Android:

- cleanup cache;
- file lama dapat dihapus berdasarkan retention policy.

Desktop:

- temporary Excel/PDF/OCR files harus memiliki lifecycle yang jelas;
- file Manifest final tidak boleh dianggap cache.

**Manifest final tidak boleh terhapus hanya karena cleanup cache.**

---

# 24. File System & User Data

Desktop harus membedakan:

```text
Template
Temporary
Manifest Final
Backup
Database
Logs
```

Contoh konseptual:

```text
CargoManifest/
├── templates/
├── MANIFEST/
├── backup/
├── data/
├── logs/
└── temp/
```

Lokasi final dapat ditentukan pada tahap implementasi.

---

# 25. Offline-First

Fungsi inti Desktop harus dapat berjalan tanpa internet.

Minimal:

- input Cargo;
- penyimpanan;
- pencarian;
- pembuatan Manifest;
- Excel export;
- pembukaan file lokal.

Internet hanya diperlukan jika fitur tertentu memang membutuhkan layanan eksternal.

---

# 26. Backup & Recovery

Karena Manifest adalah data operasional, aplikasi harus memiliki strategi backup.

Requirement awal:

- database dapat dicadangkan;
- Manifest final tidak boleh ikut terhapus oleh cleanup;
- proses recovery harus dapat dijelaskan;
- backup tidak boleh menimpa data secara diam-diam.

Detail frekuensi dan lokasi backup ditentukan pada tahap desain teknis.

---

# 27. Keamanan & Integritas Data

Aplikasi harus mencegah:

- overwrite Manifest tanpa konfirmasi;
- kehilangan data karena perubahan tanggal;
- template rusak;
- database rusak karena operasi tidak terkontrol;
- file sementara dianggap sebagai Manifest final.

Setiap operasi destruktif harus memiliki perlindungan yang sesuai.

---

# 28. Git & Repository

Selama tahap implementasi nanti, pengembangan harus menggunakan branch yang jelas.

Contoh:

```text
main
│
├── feature/desktop-foundation
├── feature/desktop-cargo
├── feature/desktop-manifest
├── feature/desktop-excel
├── feature/desktop-search
├── feature/desktop-btb
├── feature/desktop-stowing
└── feature/windows-build
```

Branch dan nama dapat disesuaikan dengan workflow repository.

Perubahan besar tidak boleh langsung dilakukan di `main` tanpa proses review.

---

# 29. Definition of Done — Planning

Dokumen planning dianggap matang apabila:

- [ ] Tujuan Desktop jelas
- [ ] Scope jelas
- [ ] Prioritas jelas
- [ ] Workflow Manifest jelas
- [ ] Aturan tanggal jelas
- [ ] Aturan nama file jelas
- [ ] Aturan folder jelas
- [ ] Aturan template jelas
- [ ] Aturan duplikasi jelas
- [ ] Data persistence jelas
- [ ] Batas Android/Desktop jelas
- [ ] Offline behavior jelas
- [ ] Backup/recovery sudah ditentukan secara prinsip
- [ ] Aturan Excel sudah ditentukan
- [ ] UX Desktop sudah memiliki prinsip
- [ ] Tidak ada requirement penting yang masih ambigu

**Setelah checklist ini disetujui, barulah masuk ke dokumen aturan pekerjaan/development rules.**

---

# 30. Definition of Done — Desktop MVP

Desktop MVP dianggap selesai jika:

- [ ] Aplikasi Desktop dapat dibuka
- [ ] Cargo dapat ditambahkan
- [ ] Cargo dapat diedit
- [ ] Cargo dapat dihapus
- [ ] Cargo dapat dicari
- [ ] Total Koli dapat dihitung
- [ ] Total KG dapat dihitung
- [ ] Data tetap ada setelah aplikasi ditutup
- [ ] Manifest dapat dibuat
- [ ] Tanggal Manifest otomatis mengikuti tanggal sistem
- [ ] Nama Manifest mengikuti format resmi
- [ ] Folder tahun/bulan dibuat otomatis
- [ ] Template asli tidak ditimpa
- [ ] Duplikasi Manifest dicegah
- [ ] Excel dapat dibuat
- [ ] Excel dapat dibuka dari Desktop
- [ ] Android tetap dapat dikembangkan secara independen
- [ ] Tidak ada operasi berat yang memblokir UI

---

# 31. Definition of Done — Windows Release

Release Windows baru dianggap siap jika:

- [ ] Desktop MVP stabil
- [ ] Windows build berhasil
- [ ] Executable/installer berhasil dibuat
- [ ] Database persistent
- [ ] Excel export stabil
- [ ] File Manifest mengikuti aturan arsip
- [ ] Backup/recovery tersedia
- [ ] Error handling tersedia
- [ ] Logging tersedia
- [ ] Dokumentasi instalasi tersedia
- [ ] Android project tetap dapat dibangun
- [ ] Tidak ada perubahan data yang tidak dapat dijelaskan

---

# 32. Roadmap Konseptual

Roadmap hanya digunakan sebagai arah, bukan perintah untuk langsung coding.

```text
REQUIREMENTS
     ↓
ARCHITECTURE
     ↓
DESKTOP FOUNDATION
     ↓
CARGO
     ↓
MANIFEST
     ↓
EXCEL
     ↓
SEARCH
     ↓
BTB
     ↓
STOWING
     ↓
OCR / OPTIONAL FEATURES
     ↓
WINDOWS RELEASE
```

Setiap tahap harus dapat direview sebelum tahap berikutnya dimulai.

---

# 33. Current Status

Status saat dokumen ini dibuat:

| Komponen | Status |
|---|---|
| Android application | Existing |
| Desktop scaffold | Existing |
| Shared scaffold | Existing |
| Desktop UI | Belum diimplementasikan |
| Desktop database architecture | Planning |
| Desktop Cargo MVP | Belum |
| Manifest workflow | Requirement defined |
| Excel Desktop workflow | Requirement defined |
| BTB Desktop | Belum |
| Stowing Desktop | Belum |
| OCR Desktop | Belum |
| Windows `.exe` | Belum |
| Development Rules | Belum difinalkan |

---

# 34. Next Planning Step

**Jangan mulai coding berdasarkan dokumen ini sebelum requirement selesai direview.**

Langkah berikutnya adalah membuat dokumen terpisah:

```text
DEVELOPMENT_RULES.md
```

Dokumen tersebut akan berisi **aturan bagaimana project harus dikerjakan**, bukan apa yang harus dibangun.

Contoh topik:

- aturan AI/developer;
- aturan perubahan kode;
- aturan Git/GitHub;
- aturan testing;
- aturan database migration;
- aturan backup;
- aturan review;
- aturan dokumentasi;
- aturan pengelolaan error;
- aturan perubahan file;
- aturan agar Android tidak rusak ketika Desktop dikembangkan.

Dengan demikian:

```text
DESKTOP_DEVELOPMENT_PLAN.md
        ↓
Apa yang dibangun?
        ↓
DEVELOPMENT_RULES.md
        ↓
Bagaimana membangunnya?
```

**Keduanya harus disepakati terlebih dahulu sebelum implementasi Desktop dimulai.**
