<!-- Dokumentasi ini menggunakan Bahasa Indonesia. -->

# ANDROID_PROJECT_STATUS.md

**Proyek:** Android Cargo Manifest App  
**Status:** Proyek yang sudah ada — baseline audit  
**Terakhir Diperbarui:** 21 Agustus 2026

## 1. Baseline Saat Ini

Proyek yang diunggah berisi:

- UI Jetpack Compose;
- beberapa Activity;
- basis data Room untuk Cargo dan Pencarian Manifest;
- Entity/DAO/Repository BTB;
- pemrosesan Excel dengan Apache POI;
- OCR CameraX + ML Kit;
- client n8n;
- Activity Flight Tracking terpisah;
- fitur Pencarian Manifest yang sudah ada;
- template Excel yang disertakan dalam aplikasi.

## 2. Status Dokumentasi

- [x] Rencana Pengembangan Android dibuat
- [x] Dokumentasi Arsitektur Android dibuat
- [x] Spesifikasi Alur Kerja Android dibuat
- [x] Aturan Pengembangan Android dibuat
- [x] Status Proyek Android dibuat
- [ ] Review final Arsitektur
- [ ] Verifikasi Build setelah Gradle Wrapper lengkap

## 3. Utang Arsitektur yang Diketahui

### Duplikasi Penyimpanan

Room sudah digunakan, tetapi SharedPreferences masih digunakan untuk beberapa state operasional yang terstruktur.

Area SharedPreferences yang teramati:

```text
stowing_prefs
btb_reference
btb_reference_status
cargo_photos
stowing_draft
cargo_archive
manifest_settings
```

Ini tidak berarti semuanya harus langsung dihapus.

Langkah berikutnya: petakan setiap penggunaan berdasarkan Tujuannya, lalu tentukan:

```text
Pertahankan sebagai pengaturan
Migrasikan ke Room
Ganti dengan penyimpanan File
Hapus karena sudah tidak diperlukan
```

### Banyak Penyimpanan Data

Proyek memiliki basis data Room terpisah:

```text
CargoDatabase
ManifestDatabase
```

Hal ini tidak otomatis salah, tetapi perlu ditinjau untuk memastikan batas tanggung jawabnya memang disengaja dan query tidak membutuhkan sinkronisasi yang tidak perlu antara kedua basis data.

## 4. Fitur Saat Ini

| Area | Tersedia |
|---|---|
| Menu Utama | Ya |
| Manifest Cargo | Ya |
| Stowing | Ya |
| BTB | Ya |
| OCR Timbangan | Ya |
| Label BTB | Ya |
| Pencarian Manifest | Ya |
| Flight Tracking | Ya |
| Integrasi n8n | Ya |
| Template Excel | Ya |

## 5. Verifikasi Build

ZIP yang diunggah berisi `gradlew`, tetapi file JAR Gradle Wrapper tidak terdapat di dalam arsip.

Karena itu proyek belum dapat diverifikasi dengan:

```text
./gradlew assembleDebug
```

Percobaan Build gagal karena:

```text
org.gradle.wrapper.GradleWrapperMain
```

tidak tersedia.

Ini merupakan masalah kelengkapan paket/repository, bukan bukti bahwa source code Kotlin pasti gagal dikompilasi.

## 6. Langkah Berikutnya

1. Review dokumentasi baseline ini.
2. Lengkapi/verifikasi Gradle Wrapper.
3. Build proyek saat ini tanpa perubahan fitur.
4. Catat error compiler/runtime yang benar-benar muncul.
5. Petakan seluruh penggunaan penyimpanan.
6. Tentukan rencana migrasi Room/SharedPreferences.
7. Setelah itu baru implementasikan fitur berikutnya yang diminta.

## 7. Checkpoint Saat Ini

```text
AUDIT SOURCE       ✅
DOKUMENTASI        ✅
VERIFIKASI BUILD   ⚠️ TERHAMBAT KARENA WRAPPER JAR TIDAK ADA
PENGEMBANGAN FITUR ⏸️ MENUNGGU BUILD BASELINE
```

## 8. Prosedur Melanjutkan Proyek

Ketika proyek ini diunggah kembali:

```text
Baca ANDROID_PROJECT_STATUS.md
        ↓
Baca Spesifikasi yang relevan
        ↓
Periksa source code
        ↓
Verifikasi Build
        ↓
Lanjutkan dari checkpoint saat ini
```

Jangan menganggap sebuah fitur sudah selesai hanya karena file source-nya sudah ada.
