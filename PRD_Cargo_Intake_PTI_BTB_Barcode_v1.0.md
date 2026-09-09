# PRD — Cargo Intake, PTI/BTB Registry & Central Barcode
## Penambahan Fitur Android Cargo Manifest App
### Versi 1.0

## 1. Ringkasan
Dokumen ini mendefinisikan penambahan fitur pada Android Cargo Manifest App yang sudah ada. Tujuannya adalah membuat proses PTI/BTB → barcode/label → Manifest lebih cepat, terstruktur, dan dapat disinkronkan dengan laptop sebagai Cargo Server.

Fitur baru tidak menggantikan sistem kasir atau sistem penimbangan. Sistem menjadi lapisan digital untuk menyimpan, mencari, memvalidasi, dan menghubungkan data cargo.

## 2. Alur Operasional
### Alur normal
Barang datang → karyawan timbang → dicatat di PTI/BTB → diberikan ke admin/kasir → kasir membuat invoice → data PTI/BTB diberikan ke operator Manifest → input Manifest → PAG/Stowing.

### Alur khusus
Admin/kasir terkadang menerima barang langsung dan membuat catatan sederhana terlebih dahulu. Catatan tersebut adalah informasi awal, bukan pengganti PTI/BTB dan bukan sumber berat resmi.

## 3. Tujuan
- Mempercepat input PTI/BTB ke Manifest.
- Mengurangi pengetikan manual.
- Menyediakan registry PTI/BTB terpusat.
- Menambahkan barcode scanner.
- Menyimpan referensi dokumen asli.
- Menghubungkan Android dengan laptop melalui jaringan lokal.
- Mencegah duplikasi input.
- Mempertahankan fitur Manifest dan Stowing existing.

### Bukan tujuan V1
- Menggantikan sistem kasir.
- Menggantikan sistem timbang.
- Membuat PTI resmi.
- Mengotomatisasi pembayaran.
- Mengandalkan OCR tanpa verifikasi.
- CCTV barcode jarak jauh.
- Integrasi load cell.

## 4. Arsitektur
```text
                    LAPTOP
             +-------------------+
             |   CARGO SERVER    |
             | API / Database    |
             | PTI/BTB Registry  |
             | Barcode Registry  |
             | OCR / Audit Log   |
             +---------+---------+
                       | Wi-Fi/LAN
                 +-----+------+
                 |            |
          Android App    Web Monitor
          Existing
```

Android tetap menjadi aplikasi utama. Laptop menjadi database/API terpusat.

## 5. Modul Android Baru

### 5.1 PTI/BTB Registry
- Daftar PTI/BTB.
- Pencarian dan filter.
- Detail dokumen.
- Link PTI/BTB dengan cargo.
- Melihat status Manifest.
- Menyimpan referensi/foto dokumen.

Status:
`RECEIVED`, `PROCESSING`, `READY_MANIFEST`, `MANIFESTED`, `HOLD`, `CANCELLED`.

### 5.2 Scan PTI/BTB
Alur:
Foto → preprocessing → OCR → ekstraksi field → review pengguna → simpan.

Field yang dicoba:
- nomor PTI;
- nomor BTB;
- tanggal;
- customer/pengirim;
- penerima;
- jenis barang;
- jumlah koli;
- berat;
- keterangan.

OCR hanya membantu input. Pengguna wajib memeriksa hasil.

### 5.3 Barcode Scanner
Kamera Android membaca barcode label lalu meminta data ke server.

Hasil scan menampilkan:
- barcode;
- Cargo ID;
- PTI;
- BTB;
- customer;
- koli;
- berat;
- status Manifest;
- PAG jika sudah tersedia.

Jika barcode tidak ditemukan, tampilkan pilihan untuk mendaftarkan barcode.

## 6. Integrasi Manifest
Dari detail PTI/cargo tersedia tombol **Tambahkan ke Manifest**.

Validasi:
1. Data minimum tersedia.
2. User memiliki hak akses.
3. Cargo belum ada pada Manifest yang sama.
4. Identifier tidak duplikat.

Setelah berhasil, status menjadi `MANIFESTED`.

## 7. Cargo ID
Setiap cargo memiliki ID internal unik:
`CARGO-YYYYMMDD-XXXXXX`

Cargo ID menghubungkan:
- PTI;
- BTB;
- barcode;
- invoice reference;
- scan log;
- Manifest;
- PAG/Stowing.

PTI tetap menjadi nomor dokumen operasional resmi.

## 8. Sumber Data
Setiap record mencatat sumber:
- `PTI`
- `BTB`
- `WEIGHING_INVOICE`
- `ADMIN_NOTE`
- `BARCODE`
- `MANUAL`

Data dari `ADMIN_NOTE` tidak otomatis dianggap sebagai berat resmi.

## 9. Matching
Sistem dapat memberikan kandidat pencocokan berdasarkan customer, nomor dokumen, koli, berat, tanggal, barcode, dan data lain yang tersedia.

Contoh:
```text
PTI KAL004686
Candidate: REC-000123
Customer: APP HERDIYANA
Koli: 1
Weight: 7 KG
Match: 98%
[ HUBUNGKAN ]
```
Matching penting harus dikonfirmasi pengguna.

## 10. Database
Tabel minimum:

### users
`id, username, password_hash, role, active, created_at`

### cargo
`id, cargo_id, customer, description, qty, weight, weight_status, source_type, source_reference, status, created_at, updated_at`

### pti
`id, pti_number, btb_number, date, document_path, status, created_at`

### cargo_pti
`cargo_id, pti_id`

### barcode
`id, barcode_value, cargo_id, label_type, active, created_at`

### manifest
`id, flight_no, date, status, created_at`

### manifest_cargo
`manifest_id, cargo_id, qty, weight`

### scan_log
`id, barcode_value, cargo_id, operator_id, device_id, result, timestamp`

### activity_log
`id, user_id, action, entity_type, entity_id, detail, timestamp`

## 11. API V1
Base path:
`/api/v1`

### Auth
`POST /auth/login`
`POST /auth/refresh`

### PTI
`GET /pti`
`POST /pti`
`GET /pti/{id}`
`PUT /pti/{id}`

### Cargo
`GET /cargo`
`POST /cargo`
`GET /cargo/{id}`
`PUT /cargo/{id}`

### Barcode
`POST /barcode/scan`
`POST /barcode/register`
`GET /barcode/{value}`

### Manifest
`GET /manifest`
`POST /manifest`
`POST /manifest/{id}/cargo`
`DELETE /manifest/{id}/cargo/{cargoId}`

### Sync
`POST /sync/push`
`GET /sync/pull`

## 12. Contoh Barcode API
Request:
```json
{
  "barcode": "8991234567890",
  "device_id": "ANDROID-01",
  "operator_id": "USR-001"
}
```

Response:
```json
{
  "success": true,
  "found": true,
  "cargo_id": "CARGO-20260909-000123",
  "pti_number": "KAL004686",
  "qty": 4,
  "weight": 125.0,
  "status": "READY_MANIFEST"
}
```

## 13. Offline Mode
Jika server tidak tersedia:
`Scan → Local Queue → Network Available → Sync → Server`

Setiap event memiliki `client_event_id` unik untuk mencegah duplikasi.

## 14. Keamanan
- Password menggunakan hash.
- Token autentikasi.
- Role-based access control.
- Audit log.
- Backup database.
- Tidak menyimpan password di source code Android.

## 15. Role
### Admin
Mengelola data, PTI/BTB, cargo, dan log.

### Operator Manifest
Scan, review PTI, dan memasukkan cargo ke Manifest.

### Supervisor
Fungsi operator + koreksi, approval, dan monitoring.

## 16. Dashboard Laptop
Minimal menampilkan:
```text
CARGO CONTROL

PTI Hari Ini       87
Ready Manifest     15
Manifested         72
Barcode Scanned    64
Duplicate Scan      3
Pending Review      8
```

Aktivitas scan menampilkan waktu, barcode, PTI, operator, dan status.

## 17. Penyimpanan Dokumen
Contoh:
```text
documents/
  2026/
    09/
      CARGO-20260909-000123/
        pti.jpg
        btb.jpg
```

Database menyimpan metadata dan path/referensi.

## 18. Integrasi Excel Existing
Fitur Excel existing tidak boleh rusak.

Alur:
`Database → Manifest Existing → ExcelUtils.kt → template_manifest.xlsx`

Mapping existing:
- PTI → B14+
- Qty → C14+
- Qty Wt → D14+
- Sub Total → E14+
- Description → F14+
- Customer → G14+
- NO PAG → T14+

AWB:
- AWB No → A7
- Date → C8
- A/C Reg → G8
- From → C9
- Flight No → G9
- To → C10
- FLT FREQ → G10

## 19. Duplicate Prevention
Server memvalidasi duplikasi PTI, barcode, dan cargo pada Manifest.

Jika sudah ada:
```text
DATA SUDAH ADA
Manifest: GA123
Tanggal: 2026-09-09
Status: ACTIVE
```

## 20. Audit Trail
Contoh:
```text
09:10 User01 CREATE CARGO CARGO-000123
09:15 User01 REGISTER BARCODE 899...
09:22 User01 LINK PTI KAL004686
09:30 User01 ADD TO MANIFEST GA123
09:31 User01 SCAN BARCODE RESULT:DUPLICATE
```

## 21. Non-Functional Requirements
- Scan result ideal < 2 detik pada LAN.
- Pencarian PTI ideal < 2 detik.
- Mendukung ribuan record.
- Tidak kehilangan data saat koneksi sementara putus.
- Sinkronisasi dapat diulang.
- Backup terjadwal.
- UI sederhana untuk operasi cepat.

## 22. Tahapan Implementasi
### Phase 1
Cargo Server, SQLite, auth, schema, API, Android network client.

### Phase 2
PTI/BTB Registry, upload dokumen, search/filter, link cargo.

### Phase 3
Barcode scanner, registry, lookup, history, duplicate detection.

### Phase 4
Integrasi Manifest existing dan validasi.

### Phase 5
OCR PTI/BTB + review/edit.

### Phase 6
Dashboard laptop dan audit monitoring.

### Phase 7
Backup, offline sync, conflict handling, security/performance testing.

## 23. Acceptance Criteria
### PTI/BTB
- Dapat disimpan dan dicari.
- Dapat melihat dokumen asli.
- Dapat dikaitkan dengan cargo.

### Barcode
- Android dapat membaca barcode kompatibel.
- Server dapat mencari barcode.
- Duplicate terdeteksi.
- Scan log tersimpan.

### Manifest
- Cargo dapat ditambahkan dari PTI/barcode.
- Duplikasi dicegah.
- Fitur existing tetap berjalan.

### OCR
- Foto dokumen dapat diproses.
- Hasil dapat diedit.
- User wajib review sebelum final.

### Server
- Android terhubung melalui Wi-Fi/LAN.
- Data tersimpan di laptop.
- Beberapa Android dapat memakai server yang sama.
- Backup dapat dibuat.

## 24. Out of Scope V1
- Integrasi langsung kasir.
- Integrasi langsung mesin timbang.
- Load cell 2–3 ton.
- CCTV barcode.
- Computer vision.
- WhatsApp automation.
- Cloud server.
- Multi-cabang.
- AI anomaly detection.

## 25. Roadmap V2
```text
Cargo Intake
    ↓
PTI / BTB
    ↓
Barcode
    ↓
Manifest
    ↓
PAG
    ↓
Weighing
    ↓
Stowing
    ↓
Loading
```

## 26. Kesimpulan
Fitur ini merupakan **upgrade terhadap Android Cargo Manifest App**, bukan aplikasi baru.

Prioritas V1:
1. PTI/BTB Registry.
2. Barcode Scanner.
3. Cargo ID.
4. Central database laptop.
5. Integrasi Manifest existing.
6. Duplicate detection.
7. Audit log.
8. OCR assisted input.
9. Offline sync.
10. Dashboard monitoring sederhana.

Sistem tidak mengubah proses kasir, penimbangan, atau format PTI maskapai. Sistem menjadi lapisan digital yang menghubungkan dokumen dan data sampai masuk ke Manifest.
