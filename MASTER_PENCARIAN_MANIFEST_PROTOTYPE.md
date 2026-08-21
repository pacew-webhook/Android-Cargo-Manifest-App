# MASTER_PENCARIAN_MANIFEST_PROTOTYPE.md

## 1. Tujuan

Dokumen ini menjelaskan prototype **Master Pencarian Manifest berbasis Excel**.

Tujuan prototype adalah menguji terlebih dahulu apakah kebutuhan pencarian arsip Manifest dapat diselesaikan dengan Excel tanpa membuat aplikasi Desktop.

## 2. Masalah yang Ingin Diselesaikan

Pengguna memiliki sekitar:

- 1 file Manifest per hari;
- sekitar 30–31 file per bulan;
- sekitar 365 file per tahun;
- arsip dapat mencakup beberapa tahun.

Kebutuhan pencarian bukan hanya mencari nama file. Pengguna ingin mencari **isi baris Manifest**, misalnya:

> TONY TRIPLEKS

dan mendapatkan baris seperti:

| NO PTI | KOLI | KG | DESCRIPTION | CUSTOMER |
|---|---:|---:|---|---|
| KAL001 | 20 | 500 | TRIPLEKS | TONY |

## 3. Konsep Prototype

Prototype menggunakan satu workbook:

`MASTER_PENCARIAN_MANIFEST_PROTOTYPE.xlsx`

Sheet utama:

```text
Pencarian
Hasil_Pencarian
Data_Arsip
Petunjuk
```

### Pencarian

Pengguna memasukkan kata pencarian pada:

`Pencarian!B3`

Contoh:

```text
TONY TRIPLEKS
```

### Data_Arsip

Sheet ini berisi data uji yang mewakili baris dari beberapa Manifest.

Prototype tidak mengubah file Manifest asli.

### Hasil_Pencarian

Sheet ini menampilkan baris yang cocok dari `Data_Arsip`.

Pencarian dirancang untuk memeriksa gabungan isi satu baris, sehingga:

```text
TONY TRIPLEKS
```

dapat menemukan baris yang memiliki kedua kata tersebut pada baris yang sama.

## 4. Struktur Hasil

Hasil yang ditargetkan:

| NO PTI | KOLI | KG | DESCRIPTION | CUSTOMER | TANGGAL | FLIGHT | FILE |
|---|---:|---:|---|---|---|---|---|
| KAL001 | 20 | 500 | TRIPLEKS | TONY | 21/08/2026 | 2 | Manifest... |

Metadata tanggal, flight, dan nama file ditambahkan supaya hasil dapat ditelusuri kembali ke Manifest sumber.

## 5. Template Manifest

Prototype dibuat berdasarkan struktur Template Manifest yang terdapat pada project terakhir.

Area Manifest utama yang ditemukan antara lain:

- No
- PTI
- Pcs/Cly
- Weight (Kg)
- Description
- Costumers

Template juga memiliki area Stowing Checklist. Untuk prototype awal, pencarian difokuskan pada **data Manifest Cargo**.

## 6. Batas Prototype

Prototype saat ini **belum**:

- membaca seluruh folder arsip secara otomatis;
- mengubah Manifest asli;
- membuat indeks database;
- menggunakan SQLite/FTS5;
- membuat aplikasi Desktop;
- mengubah struktur Template Manifest asli.

Ini disengaja. Tujuannya adalah menguji konsep paling sederhana terlebih dahulu.

## 7. Tahap Berikutnya Jika Prototype Berhasil

Jika pencarian pada `Data_Arsip` bekerja sesuai kebutuhan, tahap berikutnya:

```text
Folder Arsip Manifest
        ↓
Power Query
        ↓
Baca file Excel per folder Tahun/Bulan
        ↓
Ambil baris Manifest
        ↓
Gabungkan data
        ↓
Master Pencarian
        ↓
Cari kata
        ↓
Tampilkan baris yang cocok
        ↓
Buka Manifest sumber
```

Struktur folder target:

```text
ARSIP MANIFEST/
├── 2021/
│   ├── JANUARI/
│   ├── FEBRUARI/
│   └── ...
├── 2022/
├── 2023/
├── 2024/
├── 2025/
└── 2026/
```

## 8. Kriteria Keberhasilan

Prototype dianggap berhasil jika:

- pencarian dapat menemukan baris yang benar;
- pencarian `TONY TRIPLEKS` dapat menemukan data TONY dengan Description TRIPLEKS;
- hasil menampilkan PTI, KOLI, KG, Description, dan Customer;
- tanggal/flight/file sumber dapat ditampilkan;
- file Manifest asli tetap tidak berubah;
- konsep dapat diperluas dari beberapa file uji ke folder arsip sebenarnya.

## 9. Keputusan Setelah Pengujian

### Jika berhasil dan nyaman

Gunakan:

**Excel + Power Query**

dan jangan membuat aplikasi Desktop hanya untuk fungsi pencarian.

### Jika berhasil tetapi terlalu lambat

Evaluasi optimasi Power Query atau indeks lokal sederhana.

### Jika Excel tidak nyaman untuk kebutuhan nyata

Baru pertimbangkan aplikasi Desktop sebagai antarmuka pencarian.

## 10. Prinsip

> Jangan membuat aplikasi baru jika Excel sudah dapat menyelesaikan pekerjaan dengan lebih sederhana.

Prototype ini dibuat untuk membuktikan hal tersebut sebelum mengambil keputusan arsitektur Desktop.
