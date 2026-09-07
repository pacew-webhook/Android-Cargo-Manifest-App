# PRODUCT REQUIREMENTS DOCUMENT (PRD)
## Inventory Warehouse Management System
### Cargo Warehouse – Excel-Based Integrated Inventory

| | |
|---|---|
| **Version** | Draft v1.0 |
| **Platform Utama** | Microsoft Excel + Power Query |
| **Integrasi** | File Manifest Harian |
| **Python** | Tidak diperlukan |
| **Android** | Tetap sebagai aplikasi input operasional, tidak menjadi pusat Inventory |

---

## 1. Latar Belakang

Saat ini proses operasional cargo menggunakan beberapa file Excel berbeda setiap hari. Contoh:

- `Manifest_07-09-2026_Flight-2.xlsx`
- `Manifest_08-09-2026_Flight-1.xlsx`
- `Manifest_09-09-2026_Flight-2.xlsx`

Setiap Manifest mencatat barang yang digunakan/dikirim.

**Permasalahan utama:**

- Sulit mengetahui total barang yang masih berada di gudang.
- Barang masuk berasal dari Surat Jalan.
- Penerimaan barang dapat dilakukan oleh orang yang berbeda.
- Pemilik/pengawas tidak selalu berada di gudang.
- Barang dapat memiliki BTB + PTI.
- Sebagian barang hanya memiliki BTB tanpa PTI.
- Data barang keluar tersebar di banyak file Manifest harian.
- Sulit mengingat berapa barang yang sudah keluar.
- Inventory saat ini belum memiliki riwayat mutasi masuk dan keluar yang terpusat.

---

## 2. Tujuan Sistem

Membangun sistem Inventory Warehouse yang mampu:

- Mencatat seluruh barang masuk berdasarkan Surat Jalan.
- Mengetahui siapa yang menerima barang.
- Menyimpan waktu penerimaan.
- Melacak BTB dan/atau PTI.
- Membaca seluruh file Manifest harian secara otomatis.
- Menghitung barang keluar.
- Menghitung sisa stok gudang.
- Menampilkan riwayat pergerakan barang.
- Tetap kompatibel dengan sistem Excel dan Android Cargo Manifest yang sudah digunakan.

---

## 3. Prinsip Utama Sistem

```
BARANG MASUK
     │
     ▼
SURAT JALAN / PENERIMAAN
     │
     ▼
INVENTORY MASTER
     │
     ├───────────────┐
     │               │
     ▼               ▼
BTB / PTI         STOK GUDANG
     │               │
     └───────┬───────┘
             │
             ▼
       STOWING / MANIFEST
             │
             ▼
        BARANG KELUAR
             │
             ▼
          SISA STOK
```

**Rumus utama:**

```
STOK SISA = TOTAL BARANG MASUK - TOTAL BARANG KELUAR
```

---

## 4. Arsitektur Sistem

### Struktur Folder

```
C:\Cargo\
│
├── INVENTORY\
│   └── Inventory_Master.xlsx
│
├── PENERIMAAN\
│   └── Penerimaan_Barang.xlsx
│
├── MANIFEST\
│   ├── Manifest_07-09-2026_Flight-2.xlsx
│   ├── Manifest_08-09-2026_Flight-1.xlsx
│   ├── Manifest_09-09-2026_Flight-2.xlsx
│   └── dst...
│
├── STOWING\
│   ├── Stowing_07-09-2026.xlsx
│   └── Stowing_08-09-2026.xlsx
│
└── BACKUP\
```

---

## 5. Komponen Sistem

Sistem terdiri dari:

1. Penerimaan Barang
2. Surat Jalan
3. BTB/PTI Tracking
4. Inventory Master
5. Manifest Scanner
6. Barang Keluar
7. Dashboard Inventory
8. Riwayat Mutasi

---

## 6. Modul Penerimaan Barang

**Tujuan:** Mencatat setiap barang yang masuk ke gudang.

**Sumber utama:** Surat Jalan

### Data Penerimaan

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
| Status | Status penerimaan |
| Keterangan | Catatan tambahan |

**Contoh:**

| Receiving ID | Tanggal | SJ | Customer | PCS | KG | Diterima Oleh |
|---|---|---|---|---|---|---|
| REC-0001 | 07/09/2026 | SJ-001 | ULIN | 300 | 5000 | BUDI |

---

## 7. Identitas Petugas Penerima

Setiap penerimaan wajib mencatat: **DITERIMA OLEH**

Tujuannya agar ketika pemilik tidak berada di gudang, tetap dapat diketahui:

- Siapa menerima barang?
- Kapan diterima?
- Surat Jalan nomor berapa?
- Barang apa?
- Berapa jumlahnya?

**Contoh riwayat:**

| Waktu | Surat Jalan | Customer | Total | Diterima Oleh |
|---|---|---|---|---|
| 09:45 | SJ-001 | ULIN | 300 Koli | BUDI |
| 10:30 | SJ-002 | ABC | 50 Koli | ANDI |

---

## 8. Modul Inventory Master

Inventory Master adalah pusat data stok gudang.

### Struktur Data

| Field | Keterangan |
|---|---|
| Inventory ID | ID internal sistem |
| Receiving ID | Hubungan ke Surat Jalan |
| No Surat Jalan | Nomor Surat Jalan |
| BTB | Nomor BTB |
| PTI | Nomor PTI |
| Customer | Nama Customer |
| Description | Nama Barang |
| PCS Masuk | Jumlah Koli masuk |
| KG Masuk | Berat masuk |
| PCS Keluar | Jumlah Koli keluar |
| KG Keluar | Berat keluar |
| Sisa PCS | Stok Koli |
| Sisa KG | Stok KG |
| Status | Status Inventory |

---

## 9. Dukungan BTB dan PTI

Sistem wajib mendukung tiga kondisi:

**Kondisi A — BTB + PTI**
- BTB: BTB001
- PTI: MYI001

**Kondisi B — Hanya BTB**
- BTB: BTB002
- PTI: Kosong

**Kondisi C — PTI Ditambahkan Kemudian**

Awal:
- BTB: BTB003
- PTI: -

Kemudian:
- BTB: BTB003
- PTI: MYI003

Record Inventory tetap sama.

---

## 10. Aturan Identitas Inventory

Sistem tidak boleh bergantung hanya pada PTI.

**Prioritas identitas:**

```
INVENTORY ID
      │
      ├── BTB
      │
      └── PTI (Opsional)
```

**Contoh:**

| Inventory ID | BTB | PTI | Status |
|---|---|---|---|
| INV-0001 | BTB001 | MYI001 | Aktif |
| INV-0002 | BTB002 | - | Aktif |

---

## 11. Hubungan Surat Jalan dengan BTB/PTI

Satu Surat Jalan dapat memiliki banyak detail cargo.

```
SURAT JALAN SJ-001
TOTAL 300 KOLI
       │
       ▼
 ┌─────┼─────┐
 ▼     ▼     ▼
BTB01 BTB02 BTB03
100   100   100 Koli
```

Hubungan menggunakan: **RECEIVING ID**

```
REC-001
   │
   ├── BTB001 / PTI001
   ├── BTB002 / PTI002
   └── BTB003 / PTI003
```

---

## 12. Barang Keluar

Barang dianggap keluar berdasarkan data Manifest.

**Sumber:** Folder Manifest

- `Manifest_07-09-2026_Flight-2.xlsx`
- `Manifest_08-09-2026_Flight-1.xlsx`
- `Manifest_09-09-2026_Flight-2.xlsx`

---

## 13. Power Query Manifest Scanner

Inventory Master menggunakan Power Query.

**Fungsi:**

```
SCAN FOLDER MANIFEST
        │
        ▼
BACA SEMUA FILE EXCEL
        │
        ▼
AMBIL DATA SHEET MANIFEST
        │
        ▼
GABUNGKAN SELURUH DATA
        │
        ▼
HITUNG BARANG KELUAR
```

---

## 14. Format Data Manifest

Mengikuti struktur Cargo Manifest yang sudah digunakan:

| Kolom | Data |
|---|---|
| B | PTI |
| C | PCS/Cly |
| D | Weight PCS/Cly |
| E | Sub Total KG |
| F | Description |
| G | Customer |

Data dimulai dari: **ROW 14**

Power Query membaca:

- PTI
- PCS
- TOTAL KG
- DESCRIPTION
- CUSTOMER
- NAMA FILE

---

## 15. Data Barang Keluar

Power Query menghasilkan tabel:

| Tanggal | File | PTI | Customer | Description | PCS | KG |
|---|---|---|---|---|---|---|
| 07/09/2026 | Flight 2 | MYI001 | ULIN | OLI | 20 | 1000 |
| 08/09/2026 | Flight 1 | MYI001 | ULIN | OLI | 30 | 1500 |

---

## 16. Logika Inventory

- **Barang Masuk** = SUM seluruh Penerimaan Barang
- **Barang Keluar** = SUM seluruh Manifest
- **Stok Sisa**:
  ```
  PCS SISA = PCS MASUK - PCS KELUAR
  KG SISA  = KG MASUK  - KG KELUAR
  ```

---

## 17. Contoh Perhitungan

**Barang Masuk**
- ULIN - OLI
- BTB001 / MYI001
- PCS MASUK: 100
- KG MASUK: 5.000

**Barang Keluar**
- Manifest 07 September: 20 PCS / 1.000 KG
- Manifest 08 September: 30 PCS / 1.500 KG

**Inventory**
```
PCS MASUK  : 100
PCS KELUAR : 50
PCS SISA   : 50

KG MASUK   : 5.000
KG KELUAR  : 2.500
KG SISA    : 2.500
```

---

## 18. Status Inventory

Setiap data Inventory memiliki status:

- **AKTIF** — Masih tersedia di gudang.
- **SEBAGIAN KELUAR** — Sebagian barang sudah digunakan.
- **HABIS** — Seluruh stok sudah keluar.

**Contoh:**

| Inventory | Masuk | Keluar | Sisa | Status |
|---|---|---|---|---|
| BTB001 | 100 | 0 | 100 | Aktif |
| BTB002 | 100 | 40 | 60 | Sebagian Keluar |
| BTB003 | 100 | 100 | 0 | Habis |

---

## 19. Dashboard Inventory

Dashboard menampilkan informasi utama:

```
════════════════════════════
     INVENTORY WAREHOUSE
════════════════════════════

TOTAL BARANG MASUK
1.500 Koli
25.000 KG

TOTAL BARANG KELUAR
900 Koli
15.500 KG

SISA DI GUDANG
600 Koli
9.500 KG
════════════════════════════
```

---

## 20. Detail Stock per BTB/PTI

| BTB | PTI | Customer | Barang | Masuk | Keluar | Sisa |
|---|---|---|---|---|---|---|
| BTB001 | MYI001 | ULIN | OLI | 100 | 20 | 80 |
| BTB002 | - | ABC | KERAMIK | 50 | 10 | 40 |

---

## 21. Riwayat Mutasi

Sistem harus menyimpan riwayat:

| Tanggal | Jenis | BTB/PTI | PCS | KG | Referensi |
|---|---|---|---|---|---|
| 07/09 | MASUK | BTB001 | 100 | 5000 | SJ-001 |
| 08/09 | KELUAR | MYI001 | 20 | 1000 | Manifest F2 |
| 09/09 | KELUAR | MYI001 | 30 | 1500 | Manifest F1 |

**Jenis mutasi:**
- MASUK
- KELUAR
- PENYESUAIAN (jika diperlukan)

---

## 22. Status Verifikasi Penerimaan

Setiap penerimaan memiliki:

- BELUM DIVERIFIKASI, atau
- SUDAH DIVERIFIKASI

Tujuannya agar pemilik dapat memeriksa penerimaan yang dilakukan saat tidak berada di gudang.

---

## 23. Workflow Operasional

### Barang Masuk

```
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
DATA MASUK INVENTORY
```

### Barang Keluar

```
BARANG DIGUNAKAN
      │
      ▼
STOWING
      │
      ▼
MANIFEST HARIAN
      │
      ▼
SIMPAN FILE KE FOLDER MANIFEST
      │
      ▼
REFRESH INVENTORY MASTER
      │
      ▼
STOK BERKURANG OTOMATIS
```

---

## 24. Peran Pengguna

### Petugas Gudang

Hak:
- Input penerimaan barang.
- Mencatat Surat Jalan.
- Input BTB/PTI.
- Melihat stok.

### Supervisor/Pengawas

Hak:
- Melihat seluruh penerimaan.
- Melihat siapa yang menerima.
- Verifikasi Surat Jalan.
- Melihat seluruh Inventory.
- Melihat riwayat barang keluar.

---

## 25. Requirement Non-Functional

Sistem harus:

- Berjalan tanpa Python.
- Menggunakan Microsoft Excel.
- Menggunakan Power Query.
- Mendukung banyak file Manifest.
- Tidak mengubah format Manifest yang sudah ada.
- Tidak mengganggu workflow Android Cargo Manifest.
- Mudah dioperasikan oleh petugas gudang.
- Mendukung data dalam jumlah besar.

---

## 26. Batasan Sistem Versi 1

Versi awal tidak mencakup:

- Sistem akuntansi.
- Harga barang.
- Invoice.
- Pembayaran.
- Tracking GPS.
- Barcode otomatis.

**Fokus utama:**

```
BARANG MASUK
      +
BTB / PTI
      +
BARANG KELUAR
      =
SISA STOCK GUDANG
```

---

## 27. Roadmap Implementasi

**Phase 1 — Receiving**
- Penerimaan Barang
- Surat Jalan
- Petugas Penerima
- Tanggal/Jam

**Phase 2 — Inventory Master**
- BTB
- PTI
- PCS Masuk
- KG Masuk

**Phase 3 — Manifest Integration**
- Power Query
- Scan Folder Manifest
- Barang Keluar

**Phase 4 — Dashboard**
- Total Masuk
- Total Keluar
- Sisa Gudang
- Status Stock

**Phase 5 — Advanced (Opsional)**
- Barcode
- Foto Surat Jalan
- User Login
- Android Receiving
- Notifikasi Stok

---

## Kesimpulan Arsitektur Final

```
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
              ┌─────────┴─────────┐
              │                   │
             BTB                 PTI
              │                   │
              └─────────┬─────────┘
                        │
                  STOK GUDANG
                        │
                        ▼
                MANIFEST HARIAN
                        │
                        ▼
                  POWER QUERY
                        │
                        ▼
                   BARANG KELUAR
                        │
                        ▼
                    SISA STOCK
```

**Prinsip terpenting sistem ini:**

> Surat Jalan adalah bukti barang masuk. BTB/PTI adalah identitas barang. Manifest adalah bukti barang keluar. Inventory Master adalah pusat yang menghubungkan semuanya.

PRD ini sudah cukup kuat untuk dijadikan dasar pembuatan sistem Excel Inventory Warehouse tanpa mengubah sistem Android Cargo Manifest yang saat ini sudah berjalan.
