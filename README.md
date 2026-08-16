# Android Cargo Manifest App

Aplikasi Android untuk input dan pengelolaan data **Cargo Manifest** menggunakan Jetpack Compose dan Room Database.

Project ini juga disiapkan untuk integrasi dengan **n8n** agar data bagian Manifest dapat dikirim dari Android ke laptop dan ditulis ke template Excel Manifest yang digunakan oleh project.

---

## 📱 Fitur Android

Aplikasi digunakan untuk:

- Input data Cargo Manifest.
- Menyimpan data secara lokal menggunakan Room Database.
- Mengelola data Manifest.
- Mengelola data barang/cargo berdasarkan:
  - PTI
  - Pcs / Qty
  - Weight (Kg)
  - Sub Total
  - Description
  - Customer
  - NO PAG
- Import dan Export menggunakan template Excel Manifest.
- Mendukung pengelolaan data PAG dan Stowing sesuai kebutuhan project.

---

## 📊 Template Excel Manifest

Template utama yang digunakan project:

`template_manifest.xlsx`

Template ini merupakan template Excel Manifest yang digunakan oleh aplikasi.

### Mapping Data Barang

Integrasi Android → n8n → Excel menggunakan mapping berikut:

| Data Android | Cell Excel |
|---|---|
| PTI | B14+ |
| Pcs/Qty | C14+ |
| Pcs/Qty Wt | D14+ |
| Sub Total | E14+ |
| Description | F14+ |
| Customer | G14+ |
| NO PAG | T14+ |

Tanda `+` berarti data dapat mengisi baris berikutnya, misalnya B14, B15, B16, dan seterusnya.

### Data Header Manifest

Data berikut tetap diisi secara manual pada Excel dan **tidak diubah oleh workflow n8n**:

| Cell | Data |
|---|---|
| A7 | AWB No |
| C8 | Date |
| G8 | A/C Reg |
| C9 | From |
| G9 | Flight No |
| C10 | To |
| G10 | FLT FREQ |

---

# ⚙️ Integrasi n8n

Integrasi n8n digunakan sebagai penghubung antara Android dan Excel pada laptop.

Alur dasarnya:

```text
📱 Android
   │
   │ Data Barang Manifest
   ▼
⚙️ n8n Webhook
   │
   ├── Validasi data
   │
   ├── Proses data
   │
   ▼
📊 template_manifest.xlsx
   │
   ▼
💻 Laptop
```

Android mengirim data melalui HTTP POST ke Webhook n8n.

n8n kemudian memproses data dan menjalankan script Python untuk menulis data ke Excel.

---

## 📦 File Integrasi n8n

File yang berhubungan dengan integrasi n8n:

```text
n8n_manifest_api_windows.json
write_manifest.py
template_manifest.xlsx
```

### `n8n_manifest_api_windows.json`

Workflow n8n yang menerima data dari Android.

### `write_manifest.py`

Script Python yang digunakan untuk menulis data ke template Excel.

### `template_manifest.xlsx`

Template Excel Manifest yang menjadi format output.

---

## 📡 Data yang Dikirim Android

Format data yang digunakan untuk endpoint n8n:

```json
{
  "items": [
    {
      "pti": "KAL001",
      "pcsQty": 4,
      "weight": 50,
      "subTotal": 200,
      "description": "CARGO",
      "customer": "ULIN",
      "noPag": "PAG002 MYI"
    }
  ]
}
```

Satu request dapat berisi beberapa item Manifest.

Contoh:

```json
{
  "items": [
    {
      "pti": "KAL001",
      "pcsQty": 4,
      "weight": 50,
      "subTotal": 200,
      "description": "CARGO",
      "customer": "ULIN",
      "noPag": "PAG002 MYI"
    },
    {
      "pti": "KAL002",
      "pcsQty": 3,
      "weight": 50,
      "subTotal": 150,
      "description": "CARGO",
      "customer": "YYN",
      "noPag": "PAG002 MYI"
    }
  ]
}
```

---

## 💻 Persiapan Laptop

Integrasi n8n saat ini dirancang untuk laptop Windows.

Komponen yang diperlukan:

1. **n8n**
2. **Python**
3. Template Excel Manifest
4. Workflow n8n
5. Script `write_manifest.py`

Contoh struktur folder:

```text
C:
8n\manifest│
├── n8n_manifest_api_windows.json
├── write_manifest.py
└── template_manifest.xlsx
```

> Path tersebut adalah contoh. Path sebenarnya dapat disesuaikan dengan lokasi project di laptop.

---

## 🔐 Keamanan

Repository project dapat berisi source code dan template.

**Jangan memasukkan ke repository public:**

- API key
- Password
- Token
- Credential n8n
- File `.env`
- Database berisi data operasional
- Excel Manifest yang sudah berisi data nyata
- Foto atau dokumen cargo yang bersifat pribadi

File hasil pengolahan seperti `manifest_database.xlsx` sebaiknya tidak di-upload ke repository public.

Tambahkan file data lokal ke `.gitignore` jika diperlukan.

Contoh:

```gitignore
.env
manifest_database.xlsx
*.db
```

---

## 🚧 Status Integrasi

### Android

- [x] Form Manifest
- [x] Room Database
- [x] Template Excel Manifest
- [x] Import / Export
- [ ] Pengiriman data langsung ke n8n

### n8n

- [x] Workflow dasar Webhook
- [x] Mapping data Android → Excel
- [x] Script Python untuk Excel
- [ ] Instalasi dan pengujian di laptop
- [ ] Pengujian Webhook dari Android
- [ ] Integrasi final Android → n8n

---

## 🛠️ Rencana Pengembangan

Tahapan integrasi:

```text
1. Android
      ↓
2. n8n Webhook
      ↓
3. Validasi Data
      ↓
4. Python
      ↓
5. Excel Manifest
      ↓
6. Pengujian di Laptop
      ↓
7. Android terhubung ke n8n
```

Integrasi berikutnya dapat dikembangkan untuk bagian:

- Stowing Cargo
- PAG
- Bukti Timbang Barang (BTB)
- Sinkronisasi data Android dan laptop
- Import / Export Excel
- Backup data

---

## 📁 Struktur Repository

File n8n ditempatkan di **root repository**, sejajar dengan file project Android:

```text
Android-Cargo-Manifest-App/
│
├── app/
├── gradle/
│
├── n8n_manifest_api_windows.json
├── write_manifest.py
├── template_manifest.xlsx
│
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
└── README.md
```

---

## 📌 Catatan

Project ini menggunakan **template Excel Manifest yang sama dengan yang digunakan aplikasi Android**.

Untuk tahap integrasi n8n saat ini, hanya **Data Barang** yang dikirim dari Android ke Excel.

Header Manifest tetap dapat diisi manual:

```text
Android Cargo Manifest & Stowing

Aplikasi Android untuk mengelola data Cargo Manifest dan Stowing Cargo, dengan dukungan export Excel serta pengiriman data dari Android ke laptop melalui n8n.

Overview

Project ini dibuat untuk membantu proses pencatatan cargo di Android dan mengintegrasikan data dengan sistem di laptop.

Data Stowing dapat digunakan melalui dua jalur:

Android Form Stowing
        │
        ├──────────────► Export Excel Android
        │
        └──────────────► n8n
                          │
                          ▼
                       Python
                          │
                          ▼
                  Cargo_Manifest.xlsx

Dengan pendekatan ini, Android tidak perlu mengirim file Excel. Android cukup mengirim data "cargoList" dalam format JSON, kemudian n8n meneruskan data tersebut ke Python di laptop untuk menghasilkan file Excel.

Features

- Cargo Manifest management
- Stowing Cargo management
- Input data PAG
- Customer dan cargo information
- PTI / quantity / weight management
- Detail berat cargo
- Export data ke Excel
- Stowing checklist berdasarkan NO PAG
- Pengelompokan data berdasarkan PAG
- Dynamic handling untuk jumlah PAG
- Android → n8n → Python integration
- Automatic Excel generation di laptop

System Architecture

┌─────────────────────┐
│      Android        │
│  Cargo Manifest App │
└──────────┬──────────┘
           │
           │ HTTP / JSON
           ▼
┌─────────────────────┐
│        n8n          │
│       :5678         │
└──────────┬──────────┘
           │
           │ HTTP
           ▼
┌─────────────────────┐
│   Python Server     │
│       :5000         │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Cargo_Manifest.xlsx │
└─────────────────────┘

Network

Android dan laptop harus berada pada jaringan Wi-Fi yang sama.

Contoh:

Laptop IP:
10.18.242.83

n8n:

http://10.18.242.83:5678

Python:

http://127.0.0.1:5000

Node n8n untuk Stowing menggunakan:

http://127.0.0.1:5000/cargo/stowing

Python Server

Python server berada di:

n8n-data/cargo/

File utama:

cargo_excel_server_FINAL_CHECKLIST_GROUP_BY_PAG_V2.py

Endpoint:

POST /cargo/stowing
POST /cargo/manifest/items

Output:

Cargo_Manifest.xlsx

Menjalankan Python Server

Buka PowerShell:

cd C:\n8n-data\cargo
python cargo_excel_server_FINAL_CHECKLIST_GROUP_BY_PAG_V2.py

Jika "python" tidak tersedia:

py cargo_excel_server_FINAL_CHECKLIST_GROUP_BY_PAG_V2.py

Server akan berjalan pada:

http://0.0.0.0:5000

Jangan tutup PowerShell selama server digunakan.

Menjalankan n8n

Buka PowerShell kedua:

cd "C:\Users\asus4\AppData\Roaming\npm"

Kemudian:

$env:N8N_SECURE_COOKIE="false"
.\n8n.cmd start

n8n dapat dibuka dari laptop melalui:

http://127.0.0.1:5678

Android mengakses n8n menggunakan IP laptop:

http://IP-LAPTOP:5678

Contoh:

http://10.18.242.83:5678

n8n Workflow

Workflow Stowing:

Webhook Stowing
       ↓
Prepare Stowing Payload
       ↓
Send to Python Server
       ↓
Python Excel Server
       ↓
Cargo_Manifest.xlsx
       ↓
Response Android

Data yang dikirim Android menggunakan JSON.

Contoh struktur:

{
  "source": "android-stowing",
  "selectedPag": "PAG 001",
  "items": [
    {
      "noPag": "PAG 001",
      "pti": "PTI001",
      "customer": "CUSTOMER",
      "description": "CARGO",
      "pcsQty": 3,
      "weight": "10,10,50",
      "subTotal": 70
    }
  ]
}

Excel Logic

Logika pembuatan Excel di laptop dibuat mengikuti konsep Export Excel pada Android.

Data Stowing digunakan untuk menghasilkan:

- "STOWING_DATA"
- Stowing Checklist
- STOWINGAN PAG
- Total KG berdasarkan PAG
- Detail KG cargo
- Customer dan informasi cargo

Prinsip utama:

cargoList
    ↓
NO PAG
    ↓
Cargo Data
    ↓
Weight Detail
    ↓
Total PAG

Folder Structure

Android-Cargo-Manifest-App/
│
├── app/
│   └── src/
│       └── main/
│
├── n8n-data/
│   ├── cargo/
│   │   ├── cargo_excel_server_FINAL_CHECKLIST_GROUP_BY_PAG_V2.py
│   │   └── Cargo_Manifest.xlsx
│   │
│   └── workflow/
│       └── Cargo_Excel_Unified_FINAL_Checklist_Group_By_PAG.json
│
├── N8N_STOWING_SETUP.md
└── README.md

Troubleshooting

n8n tidak bisa dibuka

Cek port:

netstat -ano | findstr :5678

Jika tidak ada "LISTENING", jalankan kembali n8n:

cd "C:\Users\asus4\AppData\Roaming\npm"
$env:N8N_SECURE_COOKIE="false"
.\n8n.cmd start

Python tidak menerima data

Cek port:

netstat -ano | findstr :5000

Kemudian jalankan:

cd C:\n8n-data\cargo
python cargo_excel_server_FINAL_CHECKLIST_GROUP_BY_PAG_V2.py

Android tidak bisa mengakses n8n

Pastikan Android dan laptop berada pada Wi-Fi yang sama.

Cek IP laptop:

ipconfig

Gunakan IPv4 Address laptop sebagai alamat n8n.

Contoh:

http://10.18.242.83:5678

Important Notes

- Jangan menutup PowerShell Python selama fitur pengiriman data digunakan.
- Jangan menutup PowerShell n8n selama workflow digunakan.
- IP laptop dapat berubah ketika berpindah jaringan.
- Jangan commit password, API key, token, credential n8n, atau data cargo asli ke repository publik.
- Gunakan data contoh/dummy untuk repository GitHub publik.

Project Status

Current Status: Working

Current integration:

Android
   ↓
n8n
   ↓
Python
   ↓
Excel

Tested flow:

Android Stowing
      ↓
n8n Webhook
      ↓
Python Server :5000
      ↓
Cargo_Manifest.xlsx
