# Cargo Excel Server — Android → n8n → Excel

Sistem sederhana untuk mengirim data **Stowing Cargo dari Android ke laptop** menggunakan **n8n**, kemudian menyimpan/memperbarui data pada file Excel.

## Alur Sistem

```text
Android
   │
   │ HTTP POST
   ▼
n8n Webhook (Laptop)
   │
   ▼
Proses Data
   │
   ▼
Save Cargo_Manifest.xlsx
   │
   ▼
C:\n8n-data\cargo\Cargo_Manifest.xlsx
```

Data yang dikirim dari aplikasi Android dapat berisi:

- No PAG
- Customer
- Description
- PTI
- Berat/KG
- Data Stowing lainnya

---

# 1. Persyaratan

Laptop Windows harus mempunyai:

- Node.js
- npm
- n8n
- Folder kerja:
  `C:\n8n-data\cargo`

Android dan laptop harus berada pada **jaringan yang sama** jika menggunakan IP lokal laptop.

Contoh IP laptop:

```text
10.18.242.83
```

> Jangan copy IP contoh di atas jika IP laptop Anda berbeda. Gunakan IP laptop saat ini.

---

# 2. Folder Project

Struktur folder yang digunakan:

```text
C:\n8n-data\
└── cargo\
    ├── Cargo_Manifest.xlsx
    └── workflow / file pendukung lainnya
```

Pastikan folder berikut sudah ada:

```text
C:\n8n-data\cargo
```

---

# 3. Menjalankan n8n

Buka **PowerShell**.

Jika perintah `n8n` sudah dikenali:

```powershell
n8n
```

Jika `n8n` tidak dikenali tetapi npm/npx tersedia:

```powershell
npx n8n
```

Setelah n8n berjalan, biasanya dapat dibuka melalui:

```text
http://localhost:5678
```

Biarkan jendela PowerShell yang menjalankan n8n tetap terbuka.

---

# 4. Mengizinkan n8n Menulis File Excel

Karena n8n membatasi akses filesystem, sebelum menjalankan n8n gunakan:

```powershell
$env:N8N_RESTRICT_FILE_ACCESS_TO="C:\n8n-data\cargo"
```

Kemudian jalankan n8n:

```powershell
n8n
```

Jika n8n sudah berjalan sebelum perintah environment variable tersebut dijalankan, **tutup n8n lalu jalankan kembali**.

## Penting

Environment variable tersebut berlaku untuk sesi PowerShell tersebut.

Jika laptop direstart dan membuka PowerShell baru, jalankan kembali:

```powershell
$env:N8N_RESTRICT_FILE_ACCESS_TO="C:\n8n-data\cargo"
n8n
```

---

# 5. Membuka n8n dari Android

`localhost` hanya berlaku untuk laptop itu sendiri.

Android harus menggunakan **IP laptop**.

Contoh:

```text
http://10.18.242.83:5678
```

Jika IP laptop berbeda, ganti `10.18.242.83` dengan IP laptop.

Untuk mengetahui IP laptop:

```powershell
ipconfig
```

Cari bagian:

```text
IPv4 Address
```

---

# 6. Webhook n8n

Workflow menggunakan Webhook untuk menerima data dari Android.

Contoh endpoint:

```text
http://IP-LAPTOP:5678/webhook/cargo/stowing-excel
```

Contoh:

```text
http://10.18.242.83:5678/webhook/cargo/stowing-excel
```

Pastikan URL pada aplikasi Android sama dengan URL Webhook yang aktif di n8n.

---

# 7. Node Penyimpanan Excel

Node yang digunakan untuk menyimpan file harus diarahkan ke:

```text
C:\n8n-data\cargo\Cargo_Manifest.xlsx
```

Property Name:

```text
data
```

Pastikan folder:

```text
C:\n8n-data\cargo
```

sudah diizinkan melalui:

```powershell
$env:N8N_RESTRICT_FILE_ACCESS_TO="C:\n8n-data\cargo"
```

---

# 8. Cara Penggunaan Harian

## Di laptop

1. Nyalakan laptop.
2. Buka PowerShell.
3. Jalankan:

```powershell
$env:N8N_RESTRICT_FILE_ACCESS_TO="C:\n8n-data\cargo"
```

4. Jalankan:

```powershell
n8n
```

5. Pastikan n8n dapat dibuka:

```text
http://localhost:5678
```

6. Pastikan workflow yang menerima data Android aktif.

## Di Android

1. Buka aplikasi Cargo.
2. Masukkan data PAG.
3. Masukkan Customer.
4. Masukkan Description.
5. Masukkan PTI jika diperlukan.
6. Masukkan berat/KG.
7. Simpan data Stowing.
8. Tekan:

```text
Kirim Excel ke Laptop (n8n)
```

9. Tunggu pesan berhasil.

File Excel akan diperbarui di:

```text
C:\n8n-data\cargo\Cargo_Manifest.xlsx
```

---

# 9. Jika Gagal Mengirim dari Android

## A. Error `Failed to connect`

Periksa:

### 1. n8n masih berjalan?

PowerShell harus masih menjalankan n8n.

### 2. IP laptop benar?

Jalankan:

```powershell
ipconfig
```

Kemudian periksa IPv4 Address.

### 3. Android dan laptop berada di jaringan yang sama?

Contoh:

```text
Laptop  : Wi-Fi kantor
Android : Wi-Fi kantor
```

Jika menggunakan jaringan berbeda, IP lokal laptop biasanya tidak dapat diakses langsung.

### 4. Port 5678 sedang aktif?

Jalankan:

```powershell
netstat -ano | findstr :5678
```

Jika tidak ada hasil, n8n kemungkinan tidak sedang listen pada port 5678.

---

# 10. Jika Muncul `Access to the file is not allowed`

Pastikan n8n dijalankan setelah:

```powershell
$env:N8N_RESTRICT_FILE_ACCESS_TO="C:\n8n-data\cargo"
```

Kemudian restart n8n.

Pastikan file tujuan:

```text
C:\n8n-data\cargo\Cargo_Manifest.xlsx
```

berada di dalam folder yang diizinkan.

---

# 11. Jika Muncul `Unused Respond to Webhook node`

Jika workflow menggunakan node **Respond to Webhook**, Webhook harus dikonfigurasi untuk menggunakan node tersebut.

Jika tidak membutuhkan respons khusus dari node tersebut, node `Respond to Webhook` dapat dihapus.

Jangan mengubah konfigurasi ini jika workflow yang sekarang sudah berhasil.

---

# 12. Jangan Membuka Excel Saat Proses Penulisan

Untuk menghindari masalah file terkunci:

```text
Cargo_Manifest.xlsx
```

sebaiknya ditutup ketika n8n sedang melakukan proses update.

Setelah proses pengiriman selesai, file dapat dibuka untuk memeriksa hasil.

---

# 13. Checklist Sebelum Mulai

```text
[ ] Laptop menyala
[ ] Android dan laptop terhubung ke jaringan yang sama
[ ] Folder C:\n8n-data\cargo tersedia
[ ] Cargo_Manifest.xlsx tersedia
[ ] Environment variable sudah dijalankan
[ ] n8n sudah berjalan
[ ] Workflow aktif
[ ] URL Webhook menggunakan IP laptop yang benar
[ ] Excel tidak sedang dikunci/dibuka
```

---

# 14. Keamanan

URL seperti:

```text
http://10.x.x.x:5678
```

merupakan alamat jaringan lokal.

**Jangan mempublikasikan IP lokal, credential, API key, atau webhook secret ke repository GitHub publik.**

Untuk repository GitHub, gunakan placeholder:

```text
http://IP-LAPTOP:5678/webhook/cargo/stowing-excel
```

Jangan menyimpan data operasional asli atau file `Cargo_Manifest.xlsx` ke repository publik jika berisi data perusahaan/pribadi.

---

# 15. Ringkasan Perintah

Perintah utama:

```powershell
$env:N8N_RESTRICT_FILE_ACCESS_TO="C:\n8n-data\cargo"
n8n
```

Cek port:

```powershell
netstat -ano | findstr :5678
```

Cek IP laptop:

```powershell
ipconfig
```

Buka n8n dari laptop:

```text
http://localhost:5678
```

Contoh akses dari Android:

```text
http://IP-LAPTOP:5678
```

---

# Status Project

**Status: BERHASIL / WORKING**

Alur yang sudah diuji:

```text
Android
   ↓
HTTP/Webhook
   ↓
n8n di Laptop
   ↓
Proses Data Stowing
   ↓
Cargo_Manifest.xlsx
```

Data yang dikirim dari Android telah berhasil diterima dan disimpan ke Excel dengan hasil yang sama seperti data input.

---

## Catatan

Dokumentasi ini dibuat untuk setup Windows + n8n + aplikasi Android Cargo.

Jika alamat IP laptop berubah, **update URL Webhook pada aplikasi Android** sesuai IPv4 laptop yang baru.
