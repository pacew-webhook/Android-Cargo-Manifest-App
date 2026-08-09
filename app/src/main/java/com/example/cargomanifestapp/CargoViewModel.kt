package com.example.cargomanifestapp

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileOutputStream

class CargoViewModel(application: Application) : AndroidViewModel(application) {

    private val cargoDao: CargoDao = CargoDatabase.getDatabase(application).cargoDao()

    // Mengambil data kargo secara real-time dari Room Database
    val cargoList: StateFlow<List<CargoEntity>> = cargoDao.getAllCargo()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Fungsi untuk menambah data kargo baru
    fun insert(
        awbNo: String,
        flightNo: String,
        pti: String,
        pcsQty: String,
        weight: String,
        subTotal: String,
        description: String,
        customer: String,
        noPag: String
    ) {
        viewModelScope.launch {
            val item = CargoEntity(
                awbNo = awbNo,
                flightNo = flightNo,
                pti = pti,
                pcsQty = pcsQty,
                weight = weight,
                subTotal = subTotal,
                description = description,
                customer = customer,
                noPag = noPag
            )
            cargoDao.insert(item)
        }
    }

    // Fungsi untuk menghapus satu item kargo
    fun delete(cargo: CargoEntity) {
        viewModelScope.launch {
            cargoDao.delete(cargo)
        }
    }

    // Fungsi untuk menghapus seluruh data kargo (sesuai tombol "Hapus Semua" di UI)
    fun clearAll() {
        viewModelScope.launch {
            cargoDao.deleteAll()
        }
    }

    // Fungsi untuk Export data ke Excel menggunakan template dari folder assets
    fun exportToExcel(context: Context) {
        viewModelScope.launch {
            try {
                val currentList = cargoList.value
                
                if (currentList.isEmpty()) {
                    Toast.makeText(context, "Tidak ada data untuk diexport!", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Membuka template Excel dari folder assets (Pastikan file manifest_template.xlsx sudah ada di assets)
                val inputStream = context.assets.open("manifest_template.xlsx")
                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0) // Mengambil sheet pertama

                // Melakukan looping untuk mengisi baris tabel mulai dari baris indeks ke-14 (baris ke-15 di Excel)
                // Sesuaikan indeks ini dengan posisi tabel kosong pada template Anda
                var rowIndex = 14 
                for ((index, item) in currentList.withIndex()) {
                    val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
                    
                    // Kolom No (Indeks 0)
                    row.getCell(0, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue((index + 1).toDouble())
                    // Kolom PTI (Indeks 1)
                    row.getCell(1, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(item.pti)
                    // Kolom Pcs/Qty (Indeks 2)
                    row.getCell(2, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(item.pcsQty.toDoubleOrNull() ?: 0.0)
                    // Kolom Sub Total / Weight (Indeks 3)
                    row.getCell(3, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(item.weight.toDoubleOrNull() ?: 0.0)
                    // Kolom Sub Total Kg (Indeks 4)
                    row.getCell(4, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(item.subTotal.toDoubleOrNull() ?: 0.0)
                    // Kolom Description (Indeks 5)
                    row.getCell(5, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(item.description)
                    // Kolom Customer / PAG (Indeks 6)
                    row.getCell(6, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(item.customer)

                    rowIndex++
                }

                // Menyimpan file hasil modifikasi ke direktori cache aplikasi
                val file = File(context.cacheDir, "Manifest_Cargo_Output.xlsx")
                val outputStream = FileOutputStream(file)
                workbook.write(outputStream)
                outputStream.close()
                workbook.close()

                // Membuka/Membagikan file Excel menggunakan FileProvider
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                
                context.startActivity(Intent.createChooser(intent, "Buka File Excel dengan"))
                Toast.makeText(context, "Export Excel Berhasil!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Gagal export: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
