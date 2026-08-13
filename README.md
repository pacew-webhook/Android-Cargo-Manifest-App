# Android-Cargo-Manifest-App
Aplikasi Input Cargo Manifest Android dengan Jetpack Compose &amp; Room Database
# Cargo Manifest Mobile App 🚚📦

Aplikasi Android native sederhana untuk menginput dan merekap data **Cargo Manifest Penerbangan**, dibangun menggunakan **Jetpack Compose** dan **Room Database**.

## 🌟 Fitur Utama
- **Form Input Manifest**: Menginput AWB, Flight No, PTI, Pcs/Qty, Weight, Sub Total, Deskripsi Barang, dan Nama Customer.
- **Auto-Calculation / Structured View**: Menampilkan hasil inputan langsung ke dalam format tabel horizontal.
- **Penyimpanan Lokal (Room DB)**: Data yang diinput tersimpan secara permanen di memori lokal HP.
- **Manajemen Data**: Fitur menghapus baris item tertentu atau menghapus seluruh daftar data manifest.

## 🛠 Tech Stack
- **Language**: Kotlin
- **UI Toolkit**: Jetpack Compose (Material 3)
- **Local Database**: Room Database
- **Architecture**: MVVM (Model-View-ViewModel) + StateFlow
- 
Perubahan FIX3
✅ UI tidak diubah
✅ OCR dinamis tetap
✅ Mapping PAG / Customer / Description tetap
✅ Perhitungan KG tetap
✅ Kolom D (Pcs/Cly) Manifest dikosongkan agar tidak ada angka perkalian/angka nyasar
✅ C = jumlah koli/PCS
✅ E = total KG/Sub Total
✅ Stowing Checklist tetap menggunakan PAG + Customer + Description
✅ TOTAL Stowing otomatis turun jika data mencapai/menabrak K37:K38
✅ TOTAL Manifest otomatis turun jika data mencapai/menabrak C45:C46 / E45:E46
✅ Baris baru dibuat sebelum TOTAL, bukan setelah TOTAL
✅ Tidak lagi menggunakan batas templateCapacity = 24
✅ Berlaku untuk jumlah data yang jauh lebih banyak
✅ Format/style template tetap dipertahankan
✅ Area signature/template bawah tidak lagi ikut dibersihkan sembarangan
✅ versionCode dinaikkan ke 6
✅ versionName menjadi 1.13.5.4-FIX3
