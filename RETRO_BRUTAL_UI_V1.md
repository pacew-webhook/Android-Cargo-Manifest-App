# Cargo Manifest - Retro Brutal UI V1

Implementasi UI eksperimen berdasarkan referensi Neo-Brutalism / Retro Web / Y2K.

## Yang diubah
- Menambahkan `CargoRetroTheme.kt` sebagai design system global.
- Warna utama: cream, biru, pink, hijau, cyan, purple, orange.
- Typography default monospace/terminal.
- Shapes dibuat lebih tegas dan kotak.
- Main Menu dirombak dengan:
  - border hitam tebal
  - hard shadow
  - blok warna kontras
  - gaya tombol/kartu retro
- Root Compose Activity menggunakan `CargoRetroTheme`.

## Yang tidak diubah
- Database
- Model data
- Stowing logic
- Manifest grouping
- Crew Loot logic
- BTB
- Backup/Restore
- n8n
- Excel export/import

Catatan: Ini V1 untuk membangun fondasi tema. Screen detail masih mempertahankan struktur dan logika lama, tetapi sekarang berada di bawah color scheme baru.
