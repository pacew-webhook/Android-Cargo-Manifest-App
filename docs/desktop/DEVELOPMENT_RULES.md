# DEVELOPMENT_RULES.md

**Project:** Android Cargo Manifest App — Desktop/Windows  
**Document Type:** Development Rules & Engineering Governance  
**Status:** Planning / Rules Definition  
**Applies to:** Desktop development and its interaction with the existing Android project

---

# 1. Tujuan Dokumen

Dokumen ini adalah aturan resmi pengembangan versi Desktop/Windows.

Tujuannya:

- menjaga project tetap sederhana;
- mencegah scope melebar tanpa alasan;
- menjaga Excel tetap sebagai dokumen Manifest utama;
- menjaga arsip lama tetap aman;
- menjaga project Android tetap stabil;
- memastikan setiap perubahan memiliki alasan dan dapat diuji.

Dokumen ini harus dibaca sebelum melakukan perubahan besar pada project.

---

# 2. Prinsip Utama

## Rule 2.1 — Sederhana Lebih Diutamakan

Jika dua solusi dapat menyelesaikan masalah yang sama, pilih solusi yang:

1. lebih sederhana;
2. lebih mudah dipelihara;
3. lebih mudah dipahami;
4. memiliki risiko lebih kecil terhadap data.

Jangan memilih solusi kompleks hanya karena secara teknis lebih canggih.

---

## Rule 2.2 — Jangan Membuat Fitur Tanpa Masalah Nyata

Setiap fitur baru harus menjawab:

> Masalah apa yang diselesaikan?

Jika tidak ada jawaban yang jelas, fitur tidak boleh menjadi prioritas.

---

## Rule 2.3 — Jangan Mengikuti Kekacauan Workflow

Desktop tidak dibuat untuk meniru semua ketidakkonsistenan workflow manusia.

Jika workflow di lapangan berbeda-beda, Desktop tidak perlu memodelkan seluruh variasi tersebut kecuali memang diperlukan.

Desktop fokus pada proses yang dapat distandarkan:

```text
Manifest
→ File
→ Folder
→ Arsip
→ Search
→ Excel
```

---

## Rule 2.4 — Excel Tetap Menjadi Dokumen Utama

Manifest Excel adalah dokumen kerja/final.

Desktop tidak boleh menganggap database internal sebagai pengganti file Manifest.

Jika database atau search index hilang, file Excel harus tetap utuh.

---

# 3. Scope Desktop

Desktop MVP fokus pada:

- Manifest harian;
- Template Excel;
- format nama file;
- struktur folder;
- pencegahan duplikasi;
- pembukaan Excel;
- pengelolaan arsip;
- pencarian Manifest lama.

Desktop MVP tidak fokus pada:

- menggantikan Excel;
- PTI management;
- BTB management;
- Stowing management;
- OCR;
- mengatur workflow bagian lain;
- Cargo database kompleks.

---

# 4. Aturan Perubahan Scope

## Rule 4.1 — Scope Tidak Boleh Berubah Diam-Diam

Fitur baru yang tidak terdapat dalam plan harus direview terlebih dahulu.

Alur:

```text
Ide fitur
   ↓
Masalah yang diselesaikan
   ↓
Dampak terhadap scope
   ↓
Review
   ↓
Keputusan
   ↓
Baru implementasi
```

---

## Rule 4.2 — Fitur Besar Harus Masuk Dokumen

Jika fitur baru disetujui, update dokumen:

```text
DESKTOP_DEVELOPMENT_PLAN.md
```

sebelum implementasi besar dimulai.

---

## Rule 4.3 — Jangan Menambah Fitur "Sekalian"

Jangan menambahkan fitur yang tidak berkaitan dengan pekerjaan saat ini hanya karena sedang berada di file atau modul yang sama.

---

# 5. Aturan Excel

## Rule 5.1 — Master Template Tidak Boleh Ditimpa

Template asli harus tetap aman.

```text
Master Template
      ↓
Copy
      ↓
Manifest Baru
```

Tidak boleh:

```text
Master Template
      ↓
Input langsung
      ↓
Template rusak
```

---

## Rule 5.2 — Jangan Mengubah Struktur Template Tanpa Review

Perubahan pada:

- worksheet;
- header;
- formula;
- merged cells;
- format;
- kolom;
- ukuran;
- nama sheet;

harus diuji terhadap template yang sebenarnya digunakan.

---

## Rule 5.3 — Jangan Membuat Salinan Ganda yang Tidak Perlu

Jika Manifest sudah dibuat, gunakan file tersebut.

Jangan membuat file sementara yang kemudian dianggap sebagai Manifest final.

---

## Rule 5.4 — Excel Harus Tetap Bisa Dibuka Secara Normal

Manifest yang dibuat Desktop harus dapat dibuka dengan aplikasi spreadsheet yang kompatibel di Windows.

---

# 6. Aturan Manifest

## Rule 6.1 — Tanggal Mengikuti Sistem

Tanggal Manifest mengikuti tanggal sistem Windows saat Manifest dibuat.

---

## Rule 6.2 — Format Nama File Konsisten

Format:

```text
MANIFES {DD} {BULAN} {YYYY} FLIGHT {N}.xlsx
```

Jangan membuat format nama baru tanpa perubahan spesifikasi.

---

## Rule 6.3 — File Lama Tidak Boleh Berubah Karena Tanggal Baru

Perubahan hari hanya memengaruhi Manifest baru.

Manifest lama tetap pada tanggal dan nama aslinya.

---

# 7. Aturan Duplikasi

Desktop tidak boleh menimpa file yang sudah ada.

Jika kombinasi tanggal + flight sudah memiliki Manifest:

```text
Manifest sudah ada.

[ Buka Manifest ]
[ Batalkan ]
```

Jangan otomatis:

- overwrite;
- rename menjadi acak;
- membuat copy tersembunyi;
- menghapus file lama.

---

# 8. Aturan Struktur Arsip

Format:

```text
MANIFEST/
└── YYYY/
    └── BULAN/
        └── MANIFES ...
```

Folder dibuat otomatis jika belum tersedia.

Jangan memindahkan arsip lama secara otomatis tanpa alasan yang jelas.

---

# 9. Aturan Historical Search

## Rule 9.1 — Search Tidak Mengubah Data

Indexing dan pencarian harus bersifat read-only terhadap file Manifest.

---

## Rule 9.2 — File Excel adalah Source of Truth

Search index hanya membantu menemukan file.

```text
Excel
  ↑
Source of Truth

Index
  ↑
Helper
```

---

## Rule 9.3 — Index Harus Bisa Dibuat Ulang

Jika index rusak:

```text
Hapus / Reset Index
        ↓
Scan Arsip
        ↓
Build Index Baru
```

Kehilangan index tidak boleh berarti kehilangan Manifest.

---

## Rule 9.4 — File Rusak Tidak Menghentikan Semua Search

Jika satu file gagal dibaca:

```text
File Error
   ↓
Catat Error
   ↓
Lanjutkan File Berikutnya
```

---

# 10. Aturan File System

Desktop harus membedakan:

```text
Template
Manifest
Index
Temporary Files
Logs
```

Jangan mencampurkan semuanya dalam satu folder.

---

# 11. Aturan Data dan Database

Database lokal hanya digunakan jika memang memberikan manfaat.

Contoh penggunaan:

- search index;
- metadata file;
- konfigurasi;
- status indexing.

Database tidak boleh menjadi alasan untuk menduplikasi seluruh isi Excel tanpa kebutuhan.

---

# 12. Aturan UI/UX

## Rule 12.1 — UI Harus Mengurangi Langkah

Prioritaskan:

```text
Buat Manifest
↓
Buka Excel
```

bukan:

```text
Buka Desktop
↓
Isi banyak form
↓
Konfirmasi berulang
↓
Input ulang data
↓
Buka Excel
```

---

## Rule 12.2 — Jangan Meniru Android Secara Paksa

Desktop memiliki karakter penggunaan berbeda.

UI harus dioptimalkan untuk:

- mouse;
- keyboard;
- layar laptop/monitor;
- penggunaan berulang;
- akses cepat.

---

## Rule 12.3 — Status Harus Jelas

Pengguna harus mudah mengetahui:

- Manifest sudah ada atau belum;
- file berhasil dibuat atau tidak;
- lokasi file;
- proses indexing berjalan atau selesai;
- file tidak ditemukan atau bermasalah.

---

# 13. Aturan Coding

## Rule 13.1 — Separation of Concerns

UI tidak boleh mengurus seluruh filesystem dan Excel secara langsung.

Pisahkan tanggung jawab:

```text
UI
 ↓
Service
 ↓
File / Excel / Search
```

---

## Rule 13.2 — Jangan Membuat God Class

Hindari satu class yang menangani:

- UI;
- Excel;
- filesystem;
- search;
- database;
- logging;

sekaligus.

---

## Rule 13.3 — Nama Kode Harus Jelas

Gunakan nama yang menggambarkan tanggung jawab.

Contoh:

```text
ManifestFileService
ExcelTemplateService
ArchiveSearchService
```

Hindari nama seperti:

```text
Helper
Utils2
ManagerFinal
TestNew
```

kecuali memang memiliki fungsi yang jelas.

---

## Rule 13.4 — Perubahan Kecil Lebih Aman

Jangan mengubah banyak bagian project sekaligus jika masalah dapat diselesaikan dengan perubahan kecil.

---

# 14. Aturan Android

## Rule 14.1 — Desktop Tidak Boleh Merusak Android

Pengembangan Desktop harus menjaga Android tetap dapat:

- dibuild;
- diuji;
- dikembangkan secara terpisah.

---

## Rule 14.2 — Jangan Berbagi Kode Secara Paksa

Kode hanya boleh dibagikan antara Android dan Desktop jika:

- memang kompatibel;
- mengurangi duplikasi;
- tidak meningkatkan kompleksitas.

Kesamaan nama fitur bukan alasan untuk memaksa shared code.

---

## Rule 14.3 — Perubahan Android Harus Terukur

Jika perubahan Desktop membutuhkan perubahan Android:

1. jelaskan alasannya;
2. tentukan dampaknya;
3. build/test Android;
4. dokumentasikan perubahan.

---

# 15. Aturan Git & GitHub

## Rule 15.1 — Commit Harus Memiliki Tujuan Jelas

Contoh:

```text
feat: add manifest creation workflow
fix: prevent manifest overwrite
feat: add archive search
docs: update desktop development plan
```

Hindari commit seperti:

```text
update
fix
test
coba
final
final2
```

---

## Rule 15.2 — Jangan Commit File Build

File hasil build sementara tidak boleh masuk repository kecuali memang diperlukan.

---

## Rule 15.3 — Dokumen Penting Harus Di-version

Dokumen seperti:

```text
DESKTOP_DEVELOPMENT_PLAN.md
DEVELOPMENT_RULES.md
DESKTOP_TECHNICAL_SPEC.md
```

harus disimpan di repository agar perubahan dapat dilacak.

---

# 16. Aturan Testing

Setiap fitur penting harus memiliki pengujian minimal.

### Manifest Creation

Uji:

- tanggal benar;
- flight benar;
- nama file benar;
- folder benar;
- template tidak berubah.

### Duplicate

Uji:

- file belum ada;
- file sudah ada;
- user memilih buka;
- user memilih batal.

### Archive

Uji:

- folder kosong;
- banyak tahun;
- banyak bulan;
- file lama;
- file tidak dapat dibaca.

### Search

Uji:

- hasil ditemukan;
- tidak ditemukan;
- banyak hasil;
- file hasil sudah dipindahkan;
- satu file rusak.

---

# 17. Aturan Error Handling

Error harus dijelaskan dengan bahasa yang dapat dipahami pengguna.

Hindari hanya menampilkan:

```text
IOException
NullPointerException
IndexOutOfBoundsException
```

Lebih baik:

```text
Manifest tidak dapat dibuat.

Kemungkinan penyebab:
- Template tidak ditemukan
- Folder tidak dapat ditulis
- File sedang digunakan aplikasi lain
```

Detail teknis tetap dicatat di log.

---

# 18. Aturan Logging

Logging digunakan untuk membantu diagnosis.

Minimal log:

- waktu;
- operasi;
- file;
- status;
- error jika ada.

Jangan mencatat data sensitif yang tidak diperlukan.

---

# 19. Aturan Backup

Manifest final harus diperlakukan sebagai data penting.

Desktop tidak boleh:

- menghapus Manifest lama secara otomatis;
- membersihkan folder arsip seperti cache;
- menganggap arsip sebagai file sementara.

Jika fitur backup ditambahkan, backup harus memiliki aturan sendiri.

---

# 20. Aturan AI-Assisted Development

AI boleh membantu:

- merancang kode;
- menjelaskan error;
- membuat draft kode;
- mereview struktur;
- membuat dokumentasi;
- membuat test;
- membantu refactoring.

Namun:

> **Kode yang dihasilkan AI tidak otomatis dianggap benar.**

Setiap perubahan harus:

1. dipahami;
2. direview;
3. dibuild;
4. diuji;
5. diverifikasi terhadap requirement.

---

# 21. Aturan Perubahan File

Sebelum mengubah file penting:

```text
Identifikasi file
      ↓
Pahami fungsi
      ↓
Periksa dependency
      ↓
Ubah seminimal mungkin
      ↓
Build
      ↓
Test
```

Jangan menghapus file hanya karena terlihat tidak digunakan tanpa memastikan dependency-nya.

---

# 22. Aturan Dokumentasi

Setiap perubahan besar harus memperbarui dokumentasi yang relevan.

Dokumen utama:

```text
DESKTOP_DEVELOPMENT_PLAN.md
DEVELOPMENT_RULES.md
DESKTOP_TECHNICAL_SPEC.md
```

Dokumentasi tidak boleh tertinggal jauh dari implementasi.

---

# 23. Aturan Review Sebelum Coding

Untuk fitur besar, gunakan checklist:

```text
[ ] Masalahnya jelas
[ ] Solusinya jelas
[ ] Scope sesuai
[ ] Tidak menduplikasi fungsi Excel tanpa alasan
[ ] Tidak merusak Android
[ ] Tidak mengubah arsip lama
[ ] Ada cara testing
[ ] Ada cara rollback jika gagal
```

Jika beberapa poin tidak terpenuhi, jangan langsung coding.

---

# 24. Aturan Sebelum Merge

Sebelum merge ke branch utama:

```text
[ ] Build berhasil
[ ] Test berhasil
[ ] Tidak ada error baru
[ ] Tidak ada file penting yang hilang
[ ] Dokumentasi diperbarui jika diperlukan
[ ] Perubahan sesuai scope
```

---

# 25. Aturan Release Windows

Sebelum membuat release `.exe`:

```text
[ ] Manifest creation tested
[ ] Template tested
[ ] Duplicate protection tested
[ ] Archive tested
[ ] Search tested
[ ] Excel opening tested
[ ] Error handling tested
[ ] Existing Android project unaffected
[ ] Clean build successful
[ ] Installer/EXE tested on Windows
```

---

# 26. Aturan Perubahan Besar

Perubahan besar harus memiliki:

```text
WHY
Apa masalahnya?

WHAT
Apa yang akan diubah?

IMPACT
Apa yang terdampak?

RISK
Apa risikonya?

TEST
Bagaimana membuktikan perubahan benar?

ROLLBACK
Bagaimana kembali jika gagal?
```

---

# 27. Prinsip Anti-Overengineering

Project ini **tidak boleh menjadi kompleks hanya karena teknologi memungkinkan**.

Hindari tanpa kebutuhan nyata:

- microservices;
- cloud backend;
- sinkronisasi real-time;
- server database;
- login online;
- API eksternal;
- AI/OCR;
- arsitektur enterprise berlebihan.

Untuk Desktop MVP, aplikasi lokal yang stabil lebih penting daripada arsitektur yang terlihat canggih.

---

# 28. Prinsip Data

Gunakan prinsip:

> **Jangan membuat salinan data jika file asli sudah cukup.**

> **Jangan menyimpan data di tempat yang tidak dapat dipulihkan.**

> **Jangan mengubah data historis tanpa perintah pengguna.**

> **Jangan menganggap index sebagai data utama.**

---

# 29. Prinsip Performance

Jangan menjalankan pekerjaan berat di UI thread.

Pekerjaan seperti:

- scan ribuan file;
- membaca banyak Excel;
- membuat search index;
- rebuild index;

harus berjalan di background.

UI harus tetap responsif.

---

# 30. Prinsip Security & Safety

Desktop harus:

- meminta konfirmasi sebelum operasi destruktif;
- tidak menghapus arsip tanpa perintah;
- tidak menimpa file secara diam-diam;
- tidak menyimpan password/API key di source code;
- tidak mengirim data Manifest ke internet tanpa kebutuhan dan persetujuan.

---

# 31. Prinsip Maintainability

Kode harus dapat dipahami kembali beberapa bulan kemudian.

Prioritaskan:

```text
Readable
Predictable
Testable
Maintainable
```

daripada:

```text
Clever
Complex
Over-optimized
```

---

# 32. Aturan Prioritas

Jika terdapat konflik antara fitur dan stabilitas:

```text
Stabilitas
    >
Data Integrity
    >
Correctness
    >
Maintainability
    >
Performance
    >
Convenience
    >
Visual Enhancement
```

Urutan dapat berubah hanya jika kebutuhan bisnis nyata membuktikan sebaliknya.

---

# 33. Golden Rules

10 aturan yang tidak boleh dilanggar tanpa review:

1. **Excel tetap menjadi dokumen Manifest utama.**
2. **Desktop tidak boleh menggantikan Excel tanpa alasan nyata.**
3. **Manifest lama tidak boleh berubah secara otomatis.**
4. **Template asli tidak boleh tertimpa.**
5. **File yang sudah ada tidak boleh ditimpa diam-diam.**
6. **Search index bukan sumber data utama.**
7. **Pekerjaan berat tidak boleh membekukan UI.**
8. **Desktop tidak boleh merusak Android.**
9. **Fitur baru tidak boleh masuk diam-diam tanpa review scope.**
10. **Sederhana dan stabil lebih penting daripada kompleks dan canggih.**

---

# 34. Checklist Pengembangan Harian

Sebelum mulai:

```text
[ ] Apa masalah yang ingin diselesaikan?
[ ] Apakah fitur ini memang diperlukan?
[ ] Apakah sudah ada di plan?
[ ] File apa yang akan berubah?
[ ] Apa risikonya?
```

Setelah selesai:

```text
[ ] Build berhasil
[ ] Test berhasil
[ ] Tidak ada regresi
[ ] File penting aman
[ ] Dokumentasi sesuai
[ ] Git diff diperiksa
```

---

# 35. Prinsip Akhir

Project ini dibangun dengan filosofi:

> **"Buat aplikasi membantu pekerjaan, bukan membuat pekerjaan mengikuti aplikasi."**

Untuk Desktop:

```text
Excel = Dokumen Manifest
Desktop = Asisten Manifest
Archive = Sumber data historis
Search Index = Alat bantu pencarian
```

Tujuan akhirnya bukan membuat aplikasi yang paling banyak fitur.

Tujuannya adalah:

> **Membuat pekerjaan Manifest lebih cepat, lebih rapi, lebih aman, dan lebih mudah mencari data lama — dengan kompleksitas seminimal mungkin.**

**Status:** Planning Rulebook — berlaku sebagai pedoman sebelum implementasi Desktop dimulai.
