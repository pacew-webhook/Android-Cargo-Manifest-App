# PRODUCT REQUIREMENTS DOCUMENT (PRD)

# Inventory Warehouse Management System
## Cargo Warehouse – Excel-Based Integrated Inventory

**Version:** Draft v2.0 (Revisi)  
**Platform Utama:** Microsoft Excel + Power Query  
**Integrasi:** Existing Android Cargo Manifest + Excel Stowing Master + Manifest Harian  
**Python:** Tidak diperlukan  
**Status:** Revised Architecture

---

# 1. LATAR BELAKANG

Operasional cargo saat ini menggunakan struktur data yang berbeda antara Stowing dan Manifest.

## Struktur yang digunakan saat ini

### Stowing

Stowing tidak dibuat berdasarkan tanggal.

Seluruh data Stowing disimpan dalam satu file Excel master yang terus digunakan dan ditambahkan dari waktu ke waktu.

```text
STOWING_MASTER.xlsx
│
├── PAG / Flight sebelumnya
├── PAG / Flight hari ini
├── PAG / Flight berikutnya
└── Riwayat Stowing lainnya
```

### Manifest

Manifest dibuat berdasarkan kebutuhan operasional, tanggal, dan Flight.

Contoh:

```text
Manifest_07-09-2026_Flight-1.xlsx
Manifest_07-09-2026_Flight-2.xlsx
Manifest_08-09-2026_Flight-1.xlsx
```

Karena itu, sistem Inventory tidak boleh memperlakukan Stowing dan Manifest sebagai sumber data yang sama.

---

# 2. PERMASALAHAN UTAMA

1. Sulit mengetahui total barang yang masih berada di gudang.
2. Barang masuk berasal dari Surat Jalan.
3. Penerimaan barang dapat dilakukan oleh orang yang berbeda.
4. Pengawas tidak selalu berada di gudang.
5. Barang dapat memiliki BTB + PTI.
6. Sebagian barang hanya memiliki BTB tanpa PTI.
7. Data Stowing berada dalam satu file master.
8. Data Manifest tersebar dalam banyak file.
9. Sulit mengetahui berapa barang yang sudah benar-benar keluar.
10. Barang yang sudah masuk Stowing belum tentu sudah berangkat.
11. Inventory belum memiliki pusat riwayat barang masuk dan keluar.

---

# 3. TUJUAN SISTEM

Membangun sistem Inventory Warehouse yang mampu:

- Mencatat seluruh barang masuk berdasarkan Surat Jalan.
- Mengetahui siapa yang menerima barang.
- Menyimpan tanggal dan waktu penerimaan.
- Melacak BTB dan/atau PTI.
- Menjadikan Inventory sebagai pusat kontrol stok.
- Menghubungkan data dengan Stowing Master.
- Membaca seluruh file Manifest harian.
- Menghitung barang yang benar-benar keluar berdasarkan Manifest.
- Menghitung sisa stok gudang.
- Menampilkan status perjalanan cargo.
- Tetap kompatibel dengan workflow Android Cargo Manifest yang sudah digunakan.

---

# 4. PRINSIP UTAMA SISTEM

```text
SURAT JALAN
     │
     ▼
PENERIMAAN BARANG
     │
     ▼
INVENTORY MASTER
     │
     ▼
STATUS: DI GUDANG
     │
     ▼
STOWING MASTER
     │
     ▼
STATUS: DI STOWING
     │
     ▼
MANIFEST HARIAN
     │
     ▼
STATUS: SUDAH KELUAR
     │
     ▼
STOK INVENTORY BERKURANG
```

Prinsip penting:

> **Stowing bukan bukti barang keluar. Manifest adalah bukti barang keluar.**

---

# 5. SUMBER DATA UTAMA

| Sistem | Fungsi |
|---|---|
| Surat Jalan | Bukti barang datang |
| Penerimaan Barang | Catatan barang masuk |
| Inventory Master | Pusat stok dan status cargo |
| Stowing Master | Data persiapan/operasional cargo |
| Manifest Harian | Bukti barang keluar |

---

# 6. ARSITEKTUR FINAL SISTEM

```text
                    GUDANG CARGO
                         │
                         ▼
                    SURAT JALAN
                         │
                         ▼
                 PENERIMAAN BARANG
                         │
                    RECEIVING ID
                         │
                         ▼
                  INVENTORY MASTER
                         │
              ┌──────────┴──────────┐
              │                     │
              ▼                     ▼
             BTB                   PTI
              │                     │
              └──────────┬──────────┘
                         │
                         ▼
                  STATUS CARGO
                         │
             ┌───────────┼───────────┐
             ▼           ▼           ▼
          DI GUDANG   DI STOWING  SUDAH KELUAR
                         │           ▲
                         ▼           │
                  STOWING MASTER     │
                         │           │
                         └───────────┘
                             MANIFEST
```

---

# 7. STRUKTUR FOLDER

```text
C:\CARGO\
│
├── INVENTORY\
│   └── Inventory_Master.xlsx
│
├── PENERIMAAN\
│   └── Penerimaan_Barang.xlsx
│
├── STOWING\
│   └── STOWING_MASTER.xlsx
│
├── MANIFEST\
│   ├── 2026\
│   │   ├── 09-SEPTEMBER\
│   │   │   ├── Manifest_07-09-2026_Flight-1.xlsx
│   │   │   ├── Manifest_07-09-2026_Flight-2.xlsx
│   │   │   └── Manifest_08-09-2026_Flight-1.xlsx
│
└── BACKUP\
```

---

# 8. MODUL PENERIMAAN BARANG

## Tujuan

Mencatat setiap barang yang datang ke gudang berdasarkan Surat Jalan.

## Data Penerimaan

| Field | Keterangan |
|---|---|
| Receiving ID | ID otomatis penerimaan |
| Tanggal | Tanggal barang diterima |
| Jam | Jam penerimaan |
| No Surat Jalan | Nomor Surat Jalan |
| Customer | Nama Customer |
| Total PCS | Total Koli |
| Total KG | Total Berat |
| Diterima Oleh | Nama petugas |
| Status Verifikasi | Status pemeriksaan |
| Keterangan | Catatan tambahan |

Contoh:

| Receiving ID | Tanggal | SJ | Customer | PCS | KG | Diterima Oleh |
|---|---|---|---|---:|---:|---|
| REC-0001 | 07/09/2026 | SJ-001 | ULIN | 300 | 5000 | BUDI |

---

# 9. IDENTITAS PETUGAS PENERIMA

Setiap penerimaan wajib mencatat:

```text
DITERIMA OLEH
```

Tujuannya:

- Mengetahui siapa yang menerima barang.
- Mengetahui kapan barang diterima.
- Mengetahui Surat Jalan yang diterima.
- Mengetahui jumlah barang masuk.
- Memudahkan pengawasan saat supervisor tidak berada di gudang.

---

# 10. INVENTORY MASTER

Inventory Master adalah pusat seluruh status dan stok cargo.

| Field | Keterangan |
|---|---|
| Inventory ID | ID internal sistem |
| Receiving ID | Hubungan ke penerimaan |
| No Surat Jalan | Nomor Surat Jalan |
| BTB | Nomor BTB |
| PTI | Nomor PTI |
| Customer | Nama Customer |
| Description | Nama barang |
| PCS Masuk | Jumlah Koli masuk |
| KG Masuk | Berat masuk |
| PCS Keluar | Jumlah Koli keluar |
| KG Keluar | Berat keluar |
| Sisa PCS | Sisa Koli |
| Sisa KG | Sisa Berat |
| Status Cargo | Posisi/status barang |

---

# 11. DUKUNGAN BTB DAN PTI

Sistem wajib mendukung tiga kondisi.

## Kondisi A — BTB + PTI

```text
BTB: BTB001
PTI: MYI001
```

## Kondisi B — Hanya BTB

```text
BTB: BTB002
PTI: Kosong
```

## Kondisi C — PTI Ditambahkan Kemudian

```text
Awal:
BTB: BTB003
PTI: -

Kemudian:
BTB: BTB003
PTI: MYI003
```

Inventory tidak boleh bergantung hanya pada PTI.

---

# 12. IDENTITAS INVENTORY

```text
INVENTORY ID
      │
      ├── RECEIVING ID
      │
      ├── BTB
      │
      └── PTI (Opsional)
```

Contoh:

| Inventory ID | Receiving ID | BTB | PTI | Status |
|---|---|---|---|---|
| INV-0001 | REC-0001 | BTB001 | MYI001 | Di Gudang |
| INV-0002 | REC-0002 | BTB002 | - | Di Gudang |

---

# 13. HUBUNGAN SURAT JALAN DENGAN BTB/PTI

Satu Surat Jalan dapat memiliki banyak barang atau detail BTB/PTI.

```text
SURAT JALAN SJ-001
TOTAL 300 KOLI
       │
       ▼
 ┌─────┼─────┐
 ▼     ▼     ▼
BTB01 BTB02 BTB03
100   100   100 Koli
```

Seluruh detail tetap terhubung menggunakan:

```text
RECEIVING ID
```

---

# 14. STOWING MASTER

Stowing menggunakan satu file master.

```text
STOWING_MASTER.xlsx
```

Data tidak dibuat ulang berdasarkan tanggal.

File ini berfungsi sebagai:

- Data operasional Stowing.
- Riwayat PAG/Flight.
- Data cargo yang dipersiapkan.
- Sumber data Manifest Cargo.
- Referensi status cargo.

---

# 15. PERAN STOWING DALAM INVENTORY

Data yang masuk ke Stowing tidak otomatis dianggap barang keluar.

Alasannya:

```text
BARANG MASUK STOWING
        ≠
BARANG SUDAH BERANGKAT
```

Barang bisa:

- Sudah di-Stowing.
- Masih berada di gudang.
- Menunggu Flight.
- Menunggu Manifest.
- Mengalami perubahan rencana.

Karena itu:

```text
STOWING
   │
   ▼
STATUS = DI STOWING
```

Stok belum dikurangi secara permanen.

---

# 16. STATUS CARGO

Sistem menggunakan tiga status utama.

## 1. DI GUDANG

```text
Barang sudah diterima
Belum masuk Stowing
Belum masuk Manifest
```

## 2. DI STOWING

```text
Barang sudah masuk Stowing
Masih dalam proses operasional
Belum dianggap keluar
```

## 3. SUDAH KELUAR

```text
Barang sudah tercatat dalam Manifest
Barang dianggap keluar dari gudang
Stok Inventory dikurangi
```

Diagram:

```text
DI GUDANG
    │
    ▼
DI STOWING
    │
    ▼
SUDAH KELUAR
```

---

# 17. MANIFEST SEBAGAI BUKTI BARANG KELUAR

Barang hanya dianggap keluar jika sudah tercatat dalam Manifest.

```text
STOWING
   │
   ▼
MANIFEST HARIAN
   │
   ▼
BARANG KELUAR
```

Prinsip:

> **Inventory dikurangi berdasarkan Manifest, bukan berdasarkan Stowing.**

---

# 18. FORMAT FILE MANIFEST

Manifest tetap mengikuti format yang sudah digunakan.

| Kolom | Data |
|---|---|
| B | PTI |
| C | PCS/Cly |
| D | Weight PCS/Cly |
| E | Sub Total KG |
| F | Description |
| G | Customer |

Data dimulai dari:

```text
ROW 14
```

---

# 19. POWER QUERY MANIFEST SCANNER

Inventory Master membaca seluruh file Manifest dari folder.

```text
FOLDER MANIFEST
       │
       ▼
SCAN SEMUA FILE EXCEL
       │
       ▼
BACA SHEET MANIFEST
       │
       ▼
AMBIL DATA CARGO
       │
       ▼
GABUNGKAN DATA
       │
       ▼
TOTAL BARANG KELUAR
```

Data yang dibaca:

```text
PTI
PCS
TOTAL KG
DESCRIPTION
CUSTOMER
NAMA FILE
```

---

# 20. DATA BARANG KELUAR

Power Query menghasilkan tabel:

| Tanggal | File | PTI | Customer | Description | PCS | KG |
|---|---|---|---|---|---:|---:|
| 07/09/2026 | Flight 2 | MYI001 | ULIN | OLI | 20 | 1000 |
| 08/09/2026 | Flight 1 | MYI001 | ULIN | OLI | 30 | 1500 |

---

# 21. LOGIKA INVENTORY

## Barang Masuk

```text
TOTAL PENERIMAAN BARANG
```

## Barang di Stowing

```text
REFERENSI DARI STOWING MASTER

STATUS:
DI STOWING
```

Tidak mengurangi stok permanen.

## Barang Keluar

```text
TOTAL DARI MANIFEST HARIAN
```

## Sisa Stok

```text
PCS SISA = PCS MASUK - PCS KELUAR

KG SISA = KG MASUK - KG KELUAR
```

---

# 22. CONTOH PERHITUNGAN

```text
ULIN - OLI
BTB001 / MYI001

PCS MASUK: 100
PCS DI STOWING: 50
PCS KELUAR: 30

PCS FISIK TERSEDIA:
100 - 30 = 70

STATUS:
Sebagian Di Stowing
```

Catatan:

Barang yang sudah masuk Stowing tetap harus dikontrol agar tidak terjadi penggunaan ganda.

---

# 23. STATUS INVENTORY

Status stok:

- **AKTIF** — Stok masih tersedia.
- **SEBAGIAN KELUAR** — Sebagian barang sudah keluar.
- **HABIS** — Seluruh stok sudah keluar.

Status posisi:

- **DI GUDANG**
- **DI STOWING**
- **SUDAH KELUAR**

Kedua status dapat digunakan bersama.

Contoh:

| BTB | Stok | Status Stok | Status Posisi |
|---|---:|---|---|
| BTB001 | 100 | Aktif | Di Gudang |
| BTB002 | 60 | Sebagian Keluar | Di Stowing |
| BTB003 | 0 | Habis | Sudah Keluar |

---

# 24. DASHBOARD INVENTORY

```text
════════════════════════════
     INVENTORY WAREHOUSE
════════════════════════════

TOTAL BARANG MASUK
1.500 Koli
25.000 KG

TOTAL DI STOWING
400 Koli
6.000 KG

TOTAL BARANG KELUAR
900 Koli
15.500 KG

SISA INVENTORY
600 Koli
9.500 KG
════════════════════════════
```

---

# 25. DETAIL STOCK

| BTB | PTI | Customer | Barang | Masuk | Stowing | Keluar | Sisa |
|---|---|---|---|---:|---:|---:|---:|
| BTB001 | MYI001 | ULIN | OLI | 100 | 50 | 30 | 70 |
| BTB002 | - | ABC | KERAMIK | 50 | 0 | 10 | 40 |

---

# 26. RIWAYAT MUTASI

| Tanggal | Jenis | BTB/PTI | PCS | KG | Referensi |
|---|---|---|---:|---:|---|
| 07/09 | MASUK | BTB001 | 100 | 5000 | SJ-001 |
| 08/09 | STOWING | MYI001 | 50 | 2500 | PAG-014 |
| 09/09 | KELUAR | MYI001 | 30 | 1500 | Manifest F2 |

Jenis mutasi:

```text
MASUK
STOWING
KELUAR
PENYESUAIAN
```

---

# 27. WORKFLOW OPERASIONAL

## Barang Masuk

```text
TRUK DATANG
    │
    ▼
SURAT JALAN
    │
    ▼
PETUGAS MENERIMA
    │
    ▼
INPUT PENERIMAAN
    │
    ▼
INVENTORY MASTER
    │
    ▼
STATUS: DI GUDANG
```

## Barang Masuk Stowing

```text
INVENTORY
    │
    ▼
STOWING MASTER
    │
    ▼
STATUS: DI STOWING
```

## Barang Keluar

```text
STOWING
    │
    ▼
MANIFEST HARIAN
    │
    ▼
BARANG KELUAR
    │
    ▼
INVENTORY BERKURANG
    │
    ▼
STATUS: SUDAH KELUAR
```

---

# 28. PERAN PENGGUNA

## Petugas Gudang

Hak:

- Input penerimaan barang.
- Mencatat Surat Jalan.
- Input BTB/PTI.
- Melihat stok.
- Melihat status cargo.

## Petugas Stowing

Hak:

- Menggunakan data Inventory sebagai referensi.
- Menambahkan cargo ke Stowing.
- Memperbarui status menjadi Di Stowing.

## Supervisor/Pengawas

Hak:

- Melihat seluruh penerimaan.
- Melihat siapa yang menerima.
- Melihat stok gudang.
- Melihat barang yang sedang Stowing.
- Melihat barang yang sudah keluar.
- Melihat riwayat mutasi.

---

# 29. HUBUNGAN DENGAN APLIKASI ANDROID CARGO MANIFEST

Aplikasi Android tetap digunakan sebagai alat input operasional.

Prinsip existing system tetap dipertahankan:

```text
STOWING CARGO = MASTER / SOURCE DATA
```

Sedangkan:

```text
MANIFEST CARGO = DATA TURUNAN DARI STOWING
```

Inventory Warehouse tidak menggantikan aplikasi Android.

Inventory berfungsi sebagai:

```text
PUSAT KONTROL BARANG MASUK
+
STOK
+
STATUS CARGO
+
RIWAYAT BARANG KELUAR
```

---

# 30. REQUIREMENT NON-FUNCTIONAL

Sistem harus:

- Berjalan tanpa Python.
- Menggunakan Microsoft Excel.
- Menggunakan Power Query.
- Mendukung satu file Stowing Master.
- Mendukung banyak file Manifest.
- Tidak mengubah format Stowing yang sudah digunakan.
- Tidak mengubah format Manifest yang sudah digunakan.
- Tidak mengganggu workflow Android Cargo Manifest.
- Mudah digunakan oleh petugas gudang.
- Mendukung data dalam jumlah besar.

---

# 31. BATASAN SISTEM VERSI 1

Versi awal tidak mencakup:

- Sistem akuntansi.
- Harga barang.
- Invoice.
- Pembayaran.
- Tracking GPS.
- Barcode otomatis.
- Multi-user database online.

Fokus utama:

```text
BARANG MASUK
      +
INVENTORY
      +
STOWING MASTER
      +
MANIFEST
      =
STATUS CARGO + SISA STOCK
```

---

# 32. ROADMAP IMPLEMENTASI

## Phase 1 — Penerimaan Barang

```text
Surat Jalan
Receiving ID
Petugas Penerima
Tanggal/Jam
```

## Phase 2 — Inventory Master

```text
BTB
PTI
PCS Masuk
KG Masuk
Sisa Stock
```

## Phase 3 — Stowing Integration

```text
Hubungkan Stowing Master
Status Di Stowing
Referensi PAG/Flight
```

## Phase 4 — Manifest Integration

```text
Power Query
Scan Folder Manifest
Barang Keluar
Pengurangan Stock
```

## Phase 5 — Dashboard

```text
Total Masuk
Total Di Gudang
Total Di Stowing
Total Keluar
Sisa Gudang
Status Cargo
```

## Phase 6 — Advanced

Opsional:

```text
Barcode
Foto Surat Jalan
User Login
Android Receiving
Notifikasi Stok
```

---

# KESIMPULAN ARSITEKTUR FINAL

```text
                       GUDANG CARGO
                            │
                            ▼
                       SURAT JALAN
                            │
                            ▼
                    PENERIMAAN BARANG
                            │
                       RECEIVING ID
                            │
                            ▼
                     INVENTORY MASTER
                            │
                   STATUS: DI GUDANG
                            │
                            ▼
                      STOWING MASTER
                    (SATU FILE UTAMA)
                            │
                   STATUS: DI STOWING
                            │
                            ▼
                     MANIFEST HARIAN
                    (BANYAK FILE/FLIGHT)
                            │
                            ▼
                    STATUS: SUDAH KELUAR
                            │
                            ▼
                     INVENTORY BERKURANG
                            │
                            ▼
                         SISA STOCK
```

# PRINSIP UTAMA

> **Surat Jalan adalah bukti barang masuk.**

> **Stowing Master adalah data persiapan dan operasional cargo.**

> **Manifest adalah bukti barang keluar.**

> **Inventory Master adalah pusat yang menghubungkan barang masuk, status Stowing, Manifest, dan sisa stok gudang.**

---

**Status Dokumen:** Draft PRD v2.1 — Technical Specification / Implementation Ready  
**Revisi Utama:** Stowing menggunakan satu file master, sedangkan Manifest menggunakan banyak file berdasarkan tanggal/Flight.  
**Project:** Cargo Warehouse Inventory Management System  
**Basis Sistem:** Microsoft Excel + Power Query + Existing Android Cargo Manifest Workflow

# 33. STRUKTUR WORKBOOK INVENTORY MASTER

## 33.1 Tujuan

`Inventory_Master.xlsx` menjadi pusat pengolahan dan monitoring inventory warehouse.

File ini tidak menggantikan:
- `STOWING_MASTER.xlsx`
- File Manifest harian
- Dokumen Surat Jalan

Inventory Master mengambil data dari sumber tersebut dan menggabungkannya menjadi informasi stok dan status cargo.

## 33.2 Struktur Workbook

```text
Inventory_Master.xlsx
│
├── DASHBOARD
├── RECEIVING
├── INVENTORY
├── MOVEMENT_LOG
├── MANIFEST_DATA
├── STOWING_DATA
├── EXCEPTION
├── REFERENCE
└── CONFIG
```

## 33.3 Sheet DASHBOARD

Digunakan untuk monitoring supervisor.

Informasi minimum:
- TOTAL MASUK
- TOTAL DI GUDANG
- TOTAL DI STOWING
- TOTAL KELUAR
- TOTAL SISA
- JUMLAH BTB AKTIF
- JUMLAH PTI AKTIF
- JUMLAH CARGO DI STOWING
- JUMLAH EXCEPTION

Dashboard harus dapat difilter berdasarkan:
- Tanggal
- Customer
- BTB
- PTI
- Flight
- Status Cargo

## 33.4 Sheet RECEIVING

| Field | Tipe | Wajib |
|---|---|---|
| Receiving ID | Text | Ya |
| Tanggal | Date | Ya |
| Jam | Time | Ya |
| No Surat Jalan | Text | Ya |
| Customer | Text | Ya |
| Diterima Oleh | Text | Ya |
| Total PCS | Number | Ya |
| Total KG | Number | Ya |
| Status Verifikasi | Text | Ya |
| Keterangan | Text | Tidak |

## 33.5 Sheet INVENTORY

| Field | Keterangan |
|---|---|
| Inventory ID | ID unik inventory |
| Receiving ID | Relasi ke penerimaan |
| Surat Jalan | Nomor Surat Jalan |
| BTB | Nomor BTB |
| PTI | Nomor PTI |
| Customer | Customer |
| Description | Nama barang |
| PCS Masuk | Jumlah masuk |
| KG Masuk | Berat masuk |
| PCS Stowing | Jumlah masuk Stowing |
| KG Stowing | Berat masuk Stowing |
| PCS Keluar | Jumlah pada Manifest |
| KG Keluar | Berat pada Manifest |
| PCS Sisa | Sisa stok |
| KG Sisa | Sisa berat |
| Status Stok | Aktif/Sebagian/Habis |
| Status Posisi | Gudang/Stowing/Keluar |

## 33.6 Sheet MOVEMENT_LOG

| Field | Keterangan |
|---|---|
| Movement ID | ID mutasi |
| Timestamp | Waktu transaksi |
| Jenis Mutasi | MASUK/STOWING/KELUAR/PENYESUAIAN |
| Inventory ID | ID inventory |
| BTB | Nomor BTB |
| PTI | Nomor PTI |
| PCS | Jumlah |
| KG | Berat |
| Referensi | SJ/PAG/Manifest |
| Source File | File sumber |
| User/Petugas | Petugas |
| Keterangan | Catatan |

---

# 34. ATURAN IDENTITAS DAN MATCHING CARGO

Sistem tidak boleh menggunakan PTI sebagai satu-satunya identitas cargo karena sebagian cargo dapat belum memiliki PTI.

Prioritas identifikasi:

```text
Inventory ID
      ↓
Receiving ID
      ↓
BTB
      ↓
PTI (Opsional)
```

### 34.1 BTB + PTI

Keduanya dicatat jika tersedia.

### 34.2 BTB tanpa PTI

Cargo tetap valid sebagai inventory.

### 34.3 PTI Ditambahkan Kemudian

Jika awalnya:

```text
BTB003
PTI = NULL
```

kemudian menjadi:

```text
BTB003
PTI = MYI003
```

sistem memperbarui record inventory yang sama dan tidak membuat inventory baru.

### 34.4 Duplicate

Jika kombinasi:

```text
BTB sama
+
PTI sama
+
Receiving sama
```

ditemukan dalam transaksi yang sama, sistem memberikan warning dan tidak otomatis menambah stok baru.

---

# 35. ATURAN PEMBAGIAN BARANG

Satu Surat Jalan dapat mempunyai beberapa BTB.

```text
SJ-001
│
├── BTB001
│   └── PTI001
├── BTB002
│   └── PTI002
└── BTB003
    └── PTI003
```

Satu BTB juga dapat mempunyai beberapa detail cargo.

Setiap detail harus mempunyai record inventory sendiri apabila jumlah atau identitas barang perlu dilacak secara terpisah.

---

# 36. ATURAN PERHITUNGAN STOK

## 36.1 Barang Masuk

```text
PCS MASUK = Total PCS hasil penerimaan
```

## 36.2 Barang Keluar

```text
PCS KELUAR = Total PCS dari Manifest aktif
```

## 36.3 Sisa

```text
PCS SISA = PCS MASUK - PCS KELUAR
KG SISA  = KG MASUK - KG KELUAR
```

## 36.4 Stowing

Stowing tidak mengurangi stok fisik permanen.

Contoh:

```text
PCS MASUK    = 100
PCS STOWING  = 50
PCS MANIFEST = 30
PCS SISA     = 70
```

Stowing digunakan sebagai indikator posisi operasional.

---

# 37. STATUS INVENTORY DAN STATUS POSISI

## 37.1 Status Stok

```text
AKTIF
SEBAGIAN KELUAR
HABIS
```

- AKTIF: PCS SISA > 0 dan PCS Keluar = 0.
- SEBAGIAN KELUAR: PCS Masuk > PCS Keluar dan PCS Keluar > 0.
- HABIS: PCS Sisa = 0.

## 37.2 Status Posisi

```text
DI GUDANG
DI STOWING
SUDAH KELUAR
```

- DI GUDANG: sudah diterima, belum tercatat pada Stowing/Manifest.
- DI STOWING: ditemukan pada Stowing dan belum seluruhnya tercatat keluar.
- SUDAH KELUAR: tercatat pada Manifest aktif.

---

# 38. INTEGRASI STOWING MASTER

## 38.1 Sumber

```text
STOWING_MASTER.xlsx
```

Stowing tetap menggunakan satu file master.

## 38.2 Data yang Dibaca

Minimal:
- PTI
- BTB jika tersedia
- PCS
- KG
- PAG
- FLIGHT
- CUSTOMER
- DESCRIPTION

Jika field tidak tersedia pada Stowing Master, sistem tidak boleh menebaknya.

## 38.3 Status Stowing

Jika cargo ditemukan pada Stowing:

```text
Status Posisi = DI STOWING
```

PCS/KG Sisa tetap dihitung berdasarkan:

```text
PCS Masuk - PCS Keluar
KG Masuk - KG Keluar
```

## 38.4 Referensi PAG

Inventory dapat menyimpan:
- PAG
- Flight
- Tanggal/operasional

---

# 39. INTEGRASI MANIFEST HARIAN

Manifest berasal dari banyak file Excel pada folder Manifest.

```text
C:\CARGO\MANIFEST\
└── 2026\
    └── 09-SEPTEMBER\
        ├── Manifest_07-09-2026_Flight-1.xlsx
        ├── Manifest_07-09-2026_Flight-2.xlsx
        └── Manifest_08-09-2026_Flight-1.xlsx
```

Kolom existing:
- B = PTI
- C = PCS/Cly
- D = Weight PCS/Cly
- E = Sub Total KG
- F = Description
- G = Customer

Data cargo dimulai dari ROW 14.

Power Query juga harus menyimpan metadata:
- Source File
- Source Folder
- Tanggal Manifest
- Flight

---

# 40. POWER QUERY PIPELINE

Power Query menjadi mesin integrasi utama.

```text
RECEIVING
   ↓
PQ_RECEIVING

STOWING_MASTER
   ↓
PQ_STOWING

MANIFEST_FOLDER
   ↓
PQ_MANIFEST
   ↓
PQ_MANIFEST_DATA

          ↓
   PQ_NORMALIZATION
          ↓
     PQ_MATCHING
          ↓
     PQ_MOVEMENT
          ↓
 PQ_INVENTORY_FINAL
      ↙       ↘
DASHBOARD   EXCEPTION
```

Query minimum:
- PQ_Receiving
- PQ_Stowing
- PQ_Manifest_Folder
- PQ_Manifest_Data
- PQ_Normalization
- PQ_Matching
- PQ_Movement
- PQ_Inventory_Final
- PQ_Exception

---

# 41. NORMALISASI DATA

Sebelum matching:

- Text: TRIM dan CLEAN.
- PTI: dinormalisasi agar format konsisten.
- BTB: dinormalisasi.
- PCS: dikonversi menjadi angka.
- KG: dikonversi menjadi angka.

Data angka yang gagal dikonversi menghasilkan:

```text
Exception = INVALID_NUMBER
```

---

# 42. PENCEGAHAN DOUBLE COUNT MANIFEST

Requirement ini bersifat kritis.

Setiap file harus mempunyai metadata:

```text
Source File
```

Contoh:

```text
Manifest_07-09-2026_Flight-1.xlsx
```

File yang sama tidak boleh dihitung lebih dari satu kali.

Manifest juga harus mendukung status:

```text
ACTIVE
REVISION
CANCELLED
```

Prinsip:

```text
1 TRANSAKSI MANIFEST
=
1 KALI PERHITUNGAN
```

---

# 43. MANIFEST REVISI DAN CANCEL

Metadata minimum:

```text
Manifest ID
Source File
Flight
Tanggal
Version
Status
```

Contoh:

| Manifest ID | Version | Status |
|---|---:|---|
| MAN-001 | 1 | REVISION |
| MAN-001 | 2 | ACTIVE |

Untuk satu Manifest ID hanya version ACTIVE yang digunakan dalam perhitungan barang keluar.

Jika status CANCELLED, transaksi keluar dari Manifest tersebut tidak dihitung sebagai transaksi aktif.

---

# 44. MATCHING MANIFEST DENGAN INVENTORY

Matching menggunakan key yang tersedia.

Prioritas:

```text
BTB + PTI
      ↓
PTI
      ↓
BTB
```

### 44.1 PTI tersedia

Manifest PTI dicari pada Inventory.

Jika ditemukan:

```text
MATCHED
```

### 44.2 PTI tidak ditemukan

Menghasilkan:

```text
EXCEPTION = UNKNOWN_PTI
```

Cargo tidak langsung dianggap valid sebagai stok keluar tanpa pemeriksaan.

### 44.3 BTB sebagai fallback

BTB dapat digunakan sebagai key tambahan jika tersedia dan hubungan datanya dapat dibuktikan.

---

# 45. EXCEPTION MANAGEMENT

Sheet:

```text
EXCEPTION
```

Jenis minimum:

```text
DUPLICATE_FILE
DUPLICATE_MANIFEST
UNKNOWN_PTI
UNKNOWN_BTB
MISSING_PTI
INVALID_PCS
INVALID_KG
MANIFEST_OVER_STOCK
MISSING_CUSTOMER
MISSING_DESCRIPTION
UNMATCHED_RECEIVING
```

Struktur:

| Field | Keterangan |
|---|---|
| Exception ID | ID masalah |
| Timestamp | Waktu ditemukan |
| Type | Jenis |
| Severity | Tingkat |
| Source | Sumber data |
| BTB | BTB |
| PTI | PTI |
| PCS | Jumlah |
| Description | Penjelasan |
| Status | OPEN/RESOLVED |
| Resolution | Penyelesaian |

Severity:

```text
INFO
WARNING
ERROR
CRITICAL
```

---

# 46. REKONSILIASI INVENTORY

Sistem harus memeriksa keseimbangan inventory.

```text
STOK TEORITIS
=
TOTAL MASUK - TOTAL KELUAR
```

Kemudian dibandingkan dengan stok sistem/fisik yang tersedia.

Contoh:

```text
MASUK       = 100
KELUAR      = 30
STOK TEORITIS = 70
STOK SISTEM  = 70
SELISIH      = 0
```

Jika terjadi selisih, sistem membuat exception.

Penyesuaian menggunakan:

```text
PENYESUAIAN
```

dan wajib memiliki:
- Tanggal
- Petugas
- Alasan
- Referensi
- Jumlah

---

# 47. AUDIT TRAIL

Transaksi penting harus dapat ditelusuri:

```text
Siapa
Kapan
Apa
Dari mana
Referensi apa
```

Receiving minimal menyimpan:
- Receiving ID
- Diterima oleh
- Tanggal
- Surat Jalan
- PCS
- KG

Manifest minimal menyimpan:
- Manifest ID
- Source File
- Flight
- Tanggal
- PTI
- PCS
- KG

---

# 48. DASHBOARD SUPERVISOR

Dashboard menampilkan KPI:

```text
TOTAL BARANG MASUK
TOTAL DI GUDANG
TOTAL DI STOWING
TOTAL BARANG KELUAR
TOTAL SISA INVENTORY
```

Tambahan:
- BTB aktif
- PTI aktif
- Cargo di Stowing
- Exception terbuka

Filter:
- Tanggal
- Customer
- BTB
- PTI
- PAG
- Flight
- Status

---

# 49. KEAMANAN, OPERASIONAL, DAN BACKUP

## 49.1 Perlindungan Workbook

Pisahkan:

```text
INPUT
  ↓
DATA
  ↓
QUERY
  ↓
OUTPUT
```

Hasil Power Query tidak boleh digunakan sebagai area input manual.

## 49.2 Backup

```text
C:\CARGO\BACKUP\
```

Backup dapat dikelompokkan berdasarkan tanggal:

```text
BACKUP\
├── 2026-09-07\
├── 2026-09-08\
└── ...
```

Backup dilakukan sebelum perubahan besar terhadap Inventory Master, Stowing Master, atau struktur folder Manifest.

## 49.3 Integritas Data

Sistem harus mencegah:
- Penghapusan transaksi tanpa jejak.
- Duplikasi transaksi.
- Perubahan hasil query secara manual.
- Perubahan struktur Manifest tanpa pemeriksaan.

---

# 50. ACCEPTANCE CRITERIA DAN IMPLEMENTATION CHECKLIST

## 50.1 Receiving

- Petugas dapat mencatat Tanggal, Jam, Surat Jalan, Customer, PCS, KG, dan Diterima Oleh.
- Receiving ID dibuat unik.
- Satu Surat Jalan dapat mempunyai beberapa BTB/detail cargo.

## 50.2 Inventory

- Setiap cargo memiliki Inventory ID.
- BTB tanpa PTI tetap valid.
- PTI dapat ditambahkan kemudian tanpa membuat inventory baru.
- PCS Sisa dan KG Sisa dihitung otomatis.

## 50.3 Stowing

- Sistem dapat membaca STOWING_MASTER.xlsx.
- Cargo yang ditemukan mendapat Status Posisi DI STOWING.
- Stowing tidak mengurangi stok fisik permanen.

## 50.4 Manifest

- Power Query membaca seluruh file Manifest dari folder.
- Data dibaca mulai ROW 14.
- PTI, PCS, KG, Description, Customer, dan Source File tersedia.
- File yang sama tidak dihitung dua kali.
- Revision/Cancel tidak menyebabkan double count.

## 50.5 Barang Keluar

- Barang hanya dianggap keluar apabila tercatat pada Manifest aktif.
- PCS/KG keluar berasal dari Manifest aktif.
- Sisa dihitung dari Masuk - Keluar.

## 50.6 Exception

Sistem menghasilkan exception untuk:
- PTI tidak ditemukan.
- BTB tidak ditemukan.
- Manifest melebihi stok.
- Duplicate Manifest.
- Data angka invalid.

## 50.7 Rekonsiliasi

- Total Masuk - Total Keluar menghasilkan Stok Teoritis.
- Selisih ditampilkan.
- Penyesuaian mempunyai alasan dan referensi.

## 50.8 Dashboard

Dashboard menampilkan:
- Total Masuk
- Total Di Gudang
- Total Di Stowing
- Total Keluar
- Total Sisa
- Exception

Dashboard dapat difilter berdasarkan tanggal, customer, BTB, PTI, PAG, Flight, dan status.

## 50.9 FINAL SYSTEM FLOW

```text
SURAT JALAN
     ↓
PENERIMAAN BARANG
     ↓
RECEIVING
     ↓
INVENTORY MASTER
     │
     ├──────────────→ STOWING MASTER
     │                       ↓
     │                  STOWING DATA
     │
     └──────────────→ MANIFEST FOLDER
                             ↓
                        POWER QUERY
                             ↓
                       MANIFEST DATA
                             ↓
                          MATCHING
                             ↓
                  ┌──────────┴──────────┐
                  ↓                     ↓
             MOVEMENT LOG           EXCEPTION
                  ↓
            INVENTORY FINAL
                  │
             ┌────┴────┐
             ↓         ↓
        DASHBOARD   REKONSILIASI
```

## 50.10 DEFINITION OF DONE

```text
[ ] Receiving dapat mencatat barang masuk
[ ] Receiving ID otomatis
[ ] BTB dapat digunakan tanpa PTI
[ ] PTI dapat ditambahkan kemudian
[ ] Inventory Master berfungsi
[ ] Stowing Master dapat dibaca
[ ] Manifest folder dapat dipindai Power Query
[ ] Manifest dapat dinormalisasi
[ ] Matching cargo berjalan
[ ] Duplicate Manifest dicegah
[ ] Revision Manifest ditangani
[ ] Barang keluar dihitung dari Manifest
[ ] Stowing tidak mengurangi stok permanen
[ ] Sisa PCS otomatis
[ ] Sisa KG otomatis
[ ] Status stok otomatis
[ ] Status posisi otomatis
[ ] Movement Log tersedia
[ ] Exception Log tersedia
[ ] Rekonsiliasi tersedia
[ ] Dashboard tersedia
[ ] Backup tersedia
[ ] Existing Android Cargo Manifest tetap berjalan
```

## 50.11 PRIORITAS IMPLEMENTASI

```text
PHASE 1
Receiving
     ↓
PHASE 2
Inventory Master
     ↓
PHASE 3
Stowing Integration
     ↓
PHASE 4
Manifest Power Query
     ↓
PHASE 5
Matching + Movement Log
     ↓
PHASE 6
Exception + Rekonsiliasi
     ↓
PHASE 7
Dashboard
     ↓
PHASE 8
Testing + Backup
```

---

# STATUS DOKUMEN SETELAH UPDATE

**Version:** Draft v2.1  
**Status:** Technical Specification / Implementation Ready

PRD sekarang mencakup kebutuhan bisnis, arsitektur, struktur workbook, data model, matching, Power Query pipeline, pencegahan double count, Manifest revision/cancel, exception management, rekonsiliasi, audit trail, dashboard, backup, acceptance criteria, dan definition of done.

**Basis sistem:**

```text
Microsoft Excel
+
Power Query
+
Existing Android Cargo Manifest Workflow
+
STOWING_MASTER.xlsx
+
Manifest Folder
```

**Prinsip final:**

> Surat Jalan adalah bukti barang masuk.

> Receiving adalah pencatatan resmi penerimaan barang.

> Inventory Master adalah pusat kontrol stok.

> Stowing Master adalah sumber data persiapan dan operasional cargo.

> Stowing tidak otomatis berarti barang keluar.

> Manifest aktif adalah bukti barang keluar.

> Power Query menjadi mesin integrasi data.

> Exception dan Rekonsiliasi menjadi mekanisme pengawasan.

> Dashboard menjadi pusat monitoring supervisor.
