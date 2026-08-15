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
AWB No
Date
A/C Reg
From
Flight No
To
FLT FREQ
```

Dengan pendekatan ini, format Manifest yang sudah digunakan project tetap dipertahankan dan tidak perlu dibuat ulang.
