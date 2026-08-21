# Desktop Manifest Manager — Product & Development Specification

**Project:** Android Cargo Manifest App — Desktop/Windows  
**Status:** Planning / Requirements Definition  
**Implementation:** Not started  
**Current concept:** Manifest Management + Historical Data Search

---

## 1. Tujuan

Desktop **bukan pengganti Excel** dan bukan sistem untuk mengatur seluruh workflow Cargo, PTI, BTB, atau Stowing.

Desktop memiliki dua tujuan utama:

1. Mempermudah pembuatan dan pengelolaan **Manifest harian**.
2. Mempermudah **pencarian Manifest lama**, termasuk arsip dari 2021 atau sebelumnya.

Tahap ini masih perencanaan. Tidak ada coding yang diwajibkan oleh dokumen ini.

---

## 2. Konsep Produk

### Desktop sebagai Manifest Assistant

Excel tetap menjadi tempat utama untuk:

- input data Manifest;
- edit data;
- pekerjaan yang memang sudah dilakukan di Excel;
- penyimpanan dokumen Manifest final.

Desktop bertugas menyiapkan dan mengelola file.

```text
Desktop
   ↓
Siapkan Manifest
   ↓
Copy Template
   ↓
Nama File Otomatis
   ↓
Arsip Otomatis
   ↓
Buka Excel
   ↓
Operator bekerja di Excel
```

**Prinsip:** jangan membuat operator menginput data yang sama dua kali.

---

## 3. Nilai Utama

Desktop harus memberikan manfaat nyata, bukan hanya tampilan UI yang berbeda.

Prioritas manfaat:

- mengurangi pekerjaan membuat file;
- mengurangi kesalahan nama file;
- mengurangi kesalahan folder;
- mencegah file tertimpa;
- menjaga aturan tanggal;
- mempercepat membuka Manifest;
- mempercepat pencarian arsip lama.

Jika suatu fitur hanya mempercantik UI tetapi tidak mengurangi pekerjaan atau meningkatkan kontrol, fitur tersebut bukan prioritas.

---

## 4. Scope Desktop MVP

### A. Manifest Harian

```text
Buat Manifest
├── Ambil tanggal Windows
├── Pilih Flight
├── Gunakan template
├── Buat nama file
├── Buat folder arsip
├── Cek duplikasi
└── Buka Excel
```

### B. Pencarian Arsip

```text
Cari Manifest
├── Kata kunci
├── Filter tahun/tanggal
├── Tampilkan hasil
└── Buka file Excel asli
```

---

## 5. Out of Scope untuk MVP

Desktop **tidak perlu menjadi**:

- pengganti Excel untuk input Cargo;
- sistem yang memaksa bagian timbangan mengubah cara kerja;
- sistem yang menentukan penggunaan PTI atau BTB;
- sistem administrasi PTI;
- sistem Stowing;
- sistem OCR;
- database Cargo baru yang kompleks.

Fitur tersebut hanya dipertimbangkan jika kebutuhan nyata di masa depan menunjukkan manfaatnya.

---

## 6. Aturan Manifest Harian

Tanggal Manifest mengikuti **tanggal sistem Windows saat file dibuat**.

Contoh:

```text
Tanggal : 21 Agustus 2026
Flight  : 2
```

Nama:

```text
MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
```

Tanggal tidak boleh diketik manual hanya untuk menentukan tanggal file.

---

## 7. Format Nama File

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

Bulan:

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

## 8. Struktur Arsip

Format:

```text
MANIFEST/
└── 2026/
    └── AGUSTUS/
        ├── MANIFES 01 AGUSTUS 2026 FLIGHT 1.xlsx
        ├── MANIFES 01 AGUSTUS 2026 FLIGHT 2.xlsx
        ├── MANIFES 21 AGUSTUS 2026 FLIGHT 1.xlsx
        └── MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx
```

Jika folder belum ada, Desktop membuatnya.

Manifest lama tidak dipindahkan hanya karena tanggal sistem berubah.

---

## 9. Template Excel

Template asli adalah **Master Template**.

Template:

- tidak boleh ditimpa;
- bukan file kerja harian;
- bukan arsip Manifest;
- tetap menjadi sumber pembuatan Manifest baru.

```text
MASTER TEMPLATE
      ↓
COPY
      ↓
MANIFEST HARIAN
      ↓
BUKA EXCEL
      ↓
OPERATOR INPUT DATA
```

Contoh:

```text
templates/
└── template_manifest.xlsx
```

---

## 10. Duplikasi Manifest

Desktop tidak boleh menimpa file yang sudah ada.

Identitas utama:

```text
Tanggal + Flight
```

Jika file sudah ada:

```text
Manifest sudah ada.

[ Buka Manifest ]
[ Batalkan ]
```

Desktop tidak boleh membuat nama acak hanya untuk melewati konflik.

---

## 11. Perubahan Tanggal

Contoh:

```text
21 AGUSTUS 2026
        ↓
22 AGUSTUS 2026
```

Manifest baru menggunakan tanggal 22.

Manifest tanggal 21 tetap tidak berubah.

---

## 12. Pembukaan Excel

Workflow:

```text
Klik "Buat Manifest"
        ↓
Ambil tanggal Windows
        ↓
Pilih Flight
        ↓
Cek duplikasi
        ↓
Copy Template
        ↓
Buat nama file
        ↓
Buat folder Tahun/Bulan
        ↓
Simpan
        ↓
Buka Excel
```

Excel dibuka hanya setelah file berhasil dibuat.

---

## 13. Arsip Manifest yang Sudah Ada

Desktop dapat:

- memilih tahun;
- memilih bulan;
- menampilkan daftar Manifest;
- membuka Manifest;
- mencari Manifest.

Membuka Manifest lama tidak boleh membuat salinan baru.

---

## 14. Pencarian Historis

Tujuan:

> Mencari data Manifest lama tanpa membuka file satu per satu.

Contoh:

```text
Search: ULIN
```

Hasil:

```text
2026
└── MANIFES 21 AGUSTUS 2026 FLIGHT 2.xlsx

2025
└── MANIFES 03 FEBRUARI 2025 FLIGHT 1.xlsx

2023
└── MANIFES 18 JULI 2023 FLIGHT 2.xlsx

2021
└── MANIFES 12 MARET 2021 FLIGHT 1.xlsx
```

Hasil pencarian harus dapat membuka file Excel aslinya.

---

## 15. Sumber Data Historis

Arsip Excel yang sudah ada menjadi sumber dokumen.

Konsep:

```text
ARSIP EXCEL
    ↓
DIBACA / DIINDEX
    ↓
SEARCH
    ↓
HASIL
    ↓
BUKA FILE ASLI
```

Desktop tidak boleh mengubah file historis hanya karena file tersebut di-index.

---

## 16. Search Index

Search index lokal boleh digunakan untuk mempercepat pencarian arsip besar.

Index **bukan pengganti Excel asli**.

```text
Excel asli
    ↓
Index
    ↓
Search
    ↓
Lokasi file Excel
```

Index harus dapat dibuat ulang dari arsip.

Jika index hilang, Manifest tidak boleh dianggap hilang.

---

## 17. Data yang Dicari

Tahap awal dapat mencari informasi yang memang tersedia di Manifest, misalnya:

- No. PTI;
- customer/trademark jika tercantum;
- pengirim/penerima jika tersedia;
- jenis cargo;
- flight;
- tanggal;
- informasi lain yang memang ada pada template.

Jangan mengasumsikan field yang tidak terdapat pada Excel.

---

## 18. Search Bersifat Read-Only

Pencarian historis pada tahap awal tidak mengedit Excel.

Contoh:

```text
Hasil:
MANIFES 18 JULI 2023 FLIGHT 2.xlsx

[ Buka Excel ]
```

---

## 19. Pengelolaan File

Bedakan:

```text
MASTER TEMPLATE
      ↓
MANIFEST FINAL
      ↓
SEARCH INDEX
```

Search index dan file sementara bukan Manifest final.

Manifest final tidak boleh dianggap cache.

---

## 20. Integritas File

Desktop harus mencegah:

- template tertimpa;
- Manifest tertimpa;
- Manifest lama berubah karena tanggal baru;
- arsip berubah hanya karena indexing;
- file sementara dianggap Manifest final.

---

## 21. Offline-First

Fungsi utama harus berjalan tanpa internet:

- membuat Manifest;
- membaca template;
- membuat folder;
- menyimpan Manifest;
- membuka Excel;
- mencari arsip lokal.

---

## 22. UI/UX

UI dirancang untuk laptop/Windows, bukan sekadar memperbesar UI Android.

Fokus:

```text
Dashboard
│
├── Buat Manifest Hari Ini
├── Manifest Hari Ini
├── Arsip Manifest
└── Cari Manifest
```

Tombol utama:

```text
[ + BUAT MANIFEST ]
[ 📂 ARSIP MANIFEST ]
[ 🔎 CARI DATA ]
```

---

## 23. Dashboard

Contoh:

```text
MANIFEST MANAGER

Tanggal: 21 AGUSTUS 2026

[ BUAT MANIFEST ]

Manifest Hari Ini:
Flight 1   ✓ Ada
Flight 2   ✓ Ada
Flight 3   — Belum ada

[ CARI ARSIP ]
```

Informasi harus berasal dari file/folder nyata.

---

## 24. Service Utama

Detail filesystem tidak boleh berada langsung di UI.

Konsep:

```text
ManifestFileService
```

Tanggung jawab:

```text
getCurrentDate()
getMonthFolder()
buildManifestFileName()
findExistingManifest()
createManifestFromTemplate()
openManifest()
listManifestFiles()
```

Pencarian arsip terpisah:

```text
ArchiveSearchService
```

Tanggung jawab:

```text
scanArchive()
buildIndex()
search()
filterByYear()
filterByDate()
findManifestFile()
openResult()
```

Nama class dapat berubah saat implementasi.

---

## 25. Excel Processing

Alur:

```text
UI
 ↓
Manifest Service
 ↓
Excel/Template Service
 ↓
File System
 ↓
Manifest.xlsx
```

Template harus dipertahankan.

Struktur seperti worksheet, merged cells, formula, format, header, PTI, cargo, koli, KG, flight, aircraft, FROM, TO, dan tanggal harus mengikuti template Excel yang benar-benar digunakan.

---

## 26. Performa

Scanning banyak Excel tidak boleh membekukan UI.

```text
Scan 2021 → 2026
        ↓
Background Processing
        ↓
Progress
        ↓
Search Ready
```

---

## 27. File Rusak

Jika satu file historis gagal dibaca:

```text
File ditemukan
     ↓
Gagal dibaca
     ↓
Tandai error
     ↓
Lanjutkan file berikutnya
```

Satu file bermasalah tidak boleh menghentikan seluruh indexing.

---

## 28. File Dipindahkan

Jika file dipindahkan atau dihapus di luar aplikasi:

- index dapat menjadi tidak valid;
- Desktop harus dapat mendeteksi file yang tidak ditemukan;
- index dapat diperbarui/rebuild.

Desktop tidak membuat salinan palsu untuk menggantikan file yang hilang.

---

## 29. Android vs Desktop

Desktop tidak harus memiliki seluruh fitur Android.

Fokus Desktop:

```text
Manifest
+
Excel
+
Archive Search
```

Android tetap dapat memiliki Cargo, BTB, Stowing, OCR, dan fitur lain sesuai kebutuhan Android.

Kedua platform tidak harus memiliki UI atau workflow identik.

---

## 30. Database Desktop

Database bukan kebutuhan utama untuk input Cargo.

Database lokal hanya dipertimbangkan untuk:

- search index;
- metadata file;
- cache hasil indexing;
- konfigurasi aplikasi;
- riwayat indexing.

File Excel tetap menjadi sumber dokumen Manifest.

---

## 31. Roadmap Konseptual

```text
REQUIREMENTS
      ↓
DESKTOP FOUNDATION
      ↓
MANIFEST DAILY MANAGER
      ↓
EXCEL TEMPLATE WORKFLOW
      ↓
ARCHIVE MANAGER
      ↓
HISTORICAL SEARCH
      ↓
SEARCH INDEX OPTIMIZATION
      ↓
WINDOWS RELEASE
```

Cargo/BTB/Stowing/OCR tidak masuk Desktop MVP.

---

## 32. Definition of Done — Planning

- [ ] Tujuan Desktop jelas
- [ ] Desktop bukan pengganti Excel
- [ ] Manifest harian terdefinisi
- [ ] Format nama file terdefinisi
- [ ] Struktur folder terdefinisi
- [ ] Template Excel terdefinisi
- [ ] Duplikasi terdefinisi
- [ ] Perubahan tanggal terdefinisi
- [ ] Pembukaan Excel terdefinisi
- [ ] Arsip terdefinisi
- [ ] Pencarian historis terdefinisi
- [ ] Search index memiliki batas jelas
- [ ] File Excel tetap menjadi sumber dokumen
- [ ] File historis tidak diubah oleh indexing
- [ ] Scope Desktop tidak terlalu luas
- [ ] Batas Android/Desktop jelas
- [ ] Offline behavior jelas

---

## 33. Definition of Done — Desktop MVP

- [ ] Aplikasi Desktop dapat dibuka
- [ ] Template dapat dikonfigurasi
- [ ] Manifest harian dapat dibuat
- [ ] Tanggal otomatis mengikuti Windows
- [ ] Flight dapat ditentukan
- [ ] Nama file sesuai format
- [ ] Folder tahun/bulan dibuat otomatis
- [ ] Template tidak ditimpa
- [ ] Duplikasi terdeteksi
- [ ] Manifest lama dapat dibuka
- [ ] Excel dapat dibuka otomatis
- [ ] Arsip dapat dijelajahi
- [ ] Arsip lama dapat dicari
- [ ] Hasil pencarian membuka file asli
- [ ] Indexing tidak membekukan UI
- [ ] Aplikasi dapat berjalan offline

---

## 34. Current Status

| Komponen | Status |
|---|---|
| Android application | Existing |
| Desktop concept | Defined |
| Desktop UI | Belum |
| Manifest Daily Manager | Requirement defined |
| Excel Template Workflow | Requirement defined |
| Archive Manager | Requirement defined |
| Historical Search | Requirement defined |
| Search Index | Concept defined |
| Cargo Desktop Management | Out of scope MVP |
| BTB Desktop Management | Out of scope MVP |
| Stowing Desktop | Out of scope MVP |
| OCR Desktop | Out of scope MVP |
| Windows `.exe` | Belum |
| Development Rules | Belum difinalkan |

---

## 35. Next Planning Step

Setelah konsep ini disetujui, buat dokumen:

```text
DEVELOPMENT_RULES.md
```

Dokumen tersebut menjawab:

> **Bagaimana project harus dikerjakan?**

Bukan:

> **Apa yang harus dibuat?**

Topik dapat meliputi:

- aturan AI/developer;
- aturan perubahan kode;
- Git/GitHub;
- testing;
- backup;
- dokumentasi;
- review;
- perubahan file;
- menjaga Android tetap aman.

---

## 36. Prinsip Akhir

> **Desktop tidak dibuat untuk menggantikan Excel.**
>
> **Desktop dibuat untuk menghilangkan pekerjaan administratif di sekitar Excel.**

Fokus:

```text
Membuat Manifest
       ↓
Mengatur File
       ↓
Mengarsipkan
       ↓
Mencari Arsip
       ↓
Membuka Excel
```

Sedangkan input/edit data Manifest tetap dilakukan di Excel.

**Status dokumen: Planning — belum untuk implementasi.**
