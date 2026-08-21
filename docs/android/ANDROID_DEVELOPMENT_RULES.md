<!-- Dokumentasi ini menggunakan Bahasa Indonesia. -->

# ANDROID_DEVELOPMENT_RULES.md

**Proyek:** Android Cargo Manifest App

## 1. Sumber Kebenaran

Sebelum mengubah kode:

1. Baca spesifikasi yang relevan.
2. Periksa kode sumber yang benar-benar ada saat ini.
3. Pastikan implementasi aktual.
4. Lakukan perubahan terkecil yang aman.

Jangan melakukan coding hanya berdasarkan ingatan atau ZIP lama.

## 2. Build Sebelum dan Sesudah Perubahan

Untuk perubahan kode yang bermakna:

```text
Kondisi Build saat ini
    ↓
Perubahan
    ↓
Build
    ↓
Uji fitur yang terdampak
```

Jika proyek tidak dapat di-Build karena Gradle Wrapper atau toolchain tidak lengkap, dokumentasikan keterbatasan tersebut. Jangan menyatakan Build berhasil jika belum benar-benar berhasil dijalankan.

## 3. Jangan Menghapus File Secara Sembarangan

Jangan menghapus file Kotlin hanya karena terlihat ada screen lain yang menggantikannya.

Sebelum menghapus:

- cari semua referensi;
- periksa pendaftaran di Manifest;
- periksa navigasi;
- pastikan tidak ada import/penggunaan yang tersisa;
- pastikan file tersebut benar-benar sudah tidak diperlukan.

## 4. Aturan Penyimpanan Data

Jangan menambahkan atau mempertahankan penyimpanan data ganda tanpa alasan yang jelas.

Saat ini proyek menggunakan Room dan SharedPreferences. Migrasi harus diperlakukan sebagai pekerjaan proyek yang disengaja, bukan sebagai efek samping dari pengerjaan fitur lain.

## 5. Aturan Room

Room harus diprioritaskan untuk data aplikasi yang terstruktur dan perlu dicari melalui query.

SharedPreferences masih boleh digunakan untuk pengaturan kecil yang memang sesuai, sampai migrasi yang terdokumentasi selesai.

## 6. Aturan State UI

State UI sementara tetap dikelola pada UI/ViewModel sesuai kebutuhan.

State penting yang harus bertahan setelah perubahan konfigurasi atau proses aplikasi dibuat ulang harus memiliki strategi penyimpanan yang jelas.

Jangan menganggap `remember` saja sudah cukup untuk state yang harus bertahan lama.

## 7. Aturan Pekerjaan di Latar Belakang

Jangan menjalankan pekerjaan berat di UI thread.

Contohnya:

- pemrosesan workbook Apache POI;
- import/export Excel berukuran besar;
- pemrosesan OCR;
- pemindaian filesystem berukuran besar;
- operasi basis data;
- pemanggilan jaringan.

## 8. Keamanan Excel

Jangan sampai Master Template berubah secara tidak sengaja.

Jangan menimpa Manifest yang sudah ada tanpa persetujuan yang jelas.

Operasi baca/pencarian harus bersifat read-only.

## 9. Aturan Penyelesaian Fitur

Sebuah fitur hanya boleh ditandai `[x] Selesai` setelah:

```text
Kebutuhan jelas
↓
Implementasi
↓
Build
↓
Uji fungsional
↓
Pemeriksaan regresi
↓
Dokumentasi diperbarui
```

## 10. Aturan Ruang Lingkup

Jangan menambahkan fitur yang tidak berhubungan saat sedang memperbaiki bug.

Pisahkan dengan jelas:

- perbaikan bug;
- refactor;
- fitur;
- optimasi.

## 11. Aturan Dokumentasi

Setelah perubahan arsitektur atau alur kerja yang bermakna:

- perbarui MD yang relevan;
- perbarui `ANDROID_PROJECT_STATUS.md`;
- catat alasannya jika perubahan memengaruhi Arsitektur atau Ruang Lingkup.

## 12. Aturan Error

Jangan pernah menyatakan Build atau pengujian berhasil jika memang belum berhasil dijalankan.

Laporkan kendala secara tepat dan spesifik.

## 13. Aturan GitHub

Jangan memasukkan output hasil generate/Build ke source control kecuali memang diperlukan.

Gunakan GitHub Actions sebagai sarana verifikasi/rilis, bukan sebagai pengganti pemahaman terhadap kondisi proyek lokal.
