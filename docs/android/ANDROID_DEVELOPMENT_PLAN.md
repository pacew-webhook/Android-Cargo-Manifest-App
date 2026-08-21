<!-- Dokumentasi ini menggunakan Bahasa Indonesia. -->

# ANDROID_DEVELOPMENT_PLAN.md

**Proyek:** Android Cargo Manifest App  
**Tujuan:** Menjadi rencana utama pengembangan aplikasi Android  
**Status:** Proyek yang sudah ada — baseline dokumentasi

## 1. Ruang Lingkup Saat Ini

Aplikasi Android saat ini memiliki beberapa area fungsi:

- Cargo Manifest
- Stowing / data palet
- Bukti Timbang Barang (BTB)
- OCR timbangan
- Pengelolaan label BTB
- Pencarian historis Manifest
- Flight Tracking opsional
- Integrasi n8n

Aplikasi Android tetap menjadi aplikasi operasional. Pengembangan Desktop didokumentasikan secara terpisah di `docs/desktop/`.

## 2. Prioritas Pengembangan

### Prioritas 1 — Stabilitas

- Jaga agar proyek tetap dapat di-Build.
- Perbaiki regresi compile/runtime sebelum menambahkan fitur yang tidak berkaitan.
- Pertahankan perilaku import/export Excel yang sudah berjalan.

### Prioritas 2 — Konsistensi Data

- Tentukan satu sumber kebenaran untuk setiap kelompok data.
- Kurangi duplikasi state antara Room, SharedPreferences, file, dan state di memori.
- Pertahankan data setelah proses aplikasi mati jika memang diperlukan.

### Prioritas 3 — Ketepatan Alur Kerja

- Manifest, BTB, Stowing, dan Pencarian harus mencerminkan alur kerja Cargo yang sebenarnya.
- Jangan memaksakan alur kerja ideal yang bertentangan dengan praktik di lapangan.

### Prioritas 4 — Performa

- Operasi Excel/OCR/Pencarian yang berat tidak boleh memblokir UI.
- Lakukan optimasi setelah bottleneck nyata diukur.

### Prioritas 5 — Kemudahan Pemeliharaan

- Pisahkan tanggung jawab UI, ViewModel, Repository/DAO, Basis Data, dan File/Excel.
- Jangan menambahkan mekanisme penyimpanan baru tanpa mendokumentasikan alasannya.

## 3. Baseline Arsitektur Saat Ini

Kode saat ini menggunakan:

- Jetpack Compose untuk sebagian besar tampilan UI utama.
- Activity untuk beberapa modul operasional.
- Room untuk penyimpanan data Cargo dan Manifest.
- Apache POI untuk pemrosesan Excel.
- CameraX + ML Kit untuk OCR.
- Integrasi HTTP dengan n8n.
- SharedPreferences untuk beberapa pengaturan dan state lama.

Ini adalah baseline yang akan diperbaiki, bukan berarti semua implementasi saat ini sudah final.

## 4. Hal yang Tidak Menjadi Tujuan

Jangan menambahkan kompleksitas hanya untuk kemungkinan di masa depan.

Contohnya:

- backend cloud tanpa kebutuhan yang jelas;
- ketergantungan online wajib untuk pekerjaan Cargo inti;
- tabel basis data baru tanpa kebutuhan query/penggunaan yang jelas;
- mengganti Excel sebelum alur kerja nyata memang membutuhkannya.

## 5. Urutan Pengembangan

```text
Pahami kode saat ini
        ↓
Tentukan kebutuhan
        ↓
Implementasikan perubahan terkecil yang aman
        ↓
Build
        ↓
Uji alur kerja yang terdampak
        ↓
Perbarui dokumentasi/Status
        ↓
Fitur berikutnya
```

## 6. Prinsip Rilis

Sebuah fitur dianggap selesai hanya jika perilakunya sudah diverifikasi terhadap kebutuhan dan alur kerja yang sudah ada tetap berfungsi.
