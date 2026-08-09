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

    // Mengambil data kargo secara real-time dari database
    val cargoList: StateFlow<List<CargoItem>> = cargoDao.getAllCargo()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Fungsi untuk menambah data kargo baru
    fun addCargo(
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
            val item = CargoItem(
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

    // Fungsi untuk mengupdate data kargo
    fun updateCargo(item: CargoItem) {
        viewModelScope.launch {
            cargoDao.update(item)
        }
    }

    // Fungsi untuk menghapus satu data kargo
    fun deleteCargo(item: CargoItem) {
        viewModelScope.launch {
            cargoDao.delete(item)
        }
    }

    // Fungsi untuk menghapus seluruh data kargo
    fun clearAll() {
        viewModelScope.launch {
            cargoDao.deleteAll()
        }
    }

    // Fungsi Export Excel ke sheet "Manifest" menggunakan template assets
    fun exportToExcel(context: Context) {
        viewModelScope.launch {
            try {
                val currentList = cargoList.value
                
                if (currentList.isEmpty()) {
                    Toast.makeText(context, "Tidak ada data untuk diexport!", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Membuka template dari folder assets menggunakan InputStream
                val inputStream = context.assets.open("manifest_template.xlsx")
                val workbook = WorkbookFactory.create(inputStream)
                inputStream.close()
                
                // Mengambil sheet bernama "Manifest" secara spesifik (dengan cadangan sheet pertama jika tidak ditemukan)
                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)

                // Baris awal data kargo pada template (indeks 14 = baris ke-15 di Excel)
                var rowIndex = 14 
                for ((index, item) in currentList.withIndex()) {
                    val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
                    
                    // Pemetaan sel kolom sesuai format tabel manifes:
                    // Kolom A (0): No Urut
                    (row.getCell(0) ?: row.createCell(0)).setCellValue((index + 1).toDouble())
                    // Kolom B (1): PTI
                    (row.getCell(1) ?: row.createCell(1)).setCellValue(item.pti)
                    // Kolom C (2): Pcs / Qty
                    (row.getCell(2) ?: row.createCell(2)).setCellValue(item.pcsQty.toDoubleOrNull() ?: 0.0)
                    // Kolom D (3): Weight (Pcs/Qty Wt)
                    (row.getCell(3) ?: row.createCell(3)).setCellValue(item.weight.toDoubleOrNull() ?: 0.0)
                    // Kolom E (4): Sub Total (Kg)
                    (row.getCell(4) ?: row.createCell(4)).setCellValue(item.subTotal.toDoubleOrNull() ?: 0.0)
                    // Kolom F (5): Description
                    (row.getCell(5) ?: row.createCell(5)).setCellValue(item.description)
                    // Kolom G (6): Customer / NO PAG
                    val customerText = if (item.noPag.isNotBlank()) "${item.customer} - ${item.noPag}" else item.customer
                    (row.getCell(6) ?: row.createCell(6)).setCellValue(customerText)

                    rowIndex++
                }

                // Menyimpan file hasil export ke direktori cache aplikasi
                val file = File(context.cacheDir, "Manifest_Cargo_Output.xlsx")
                val outputStream = FileOutputStream(file)
                workbook.write(outputStream)
                outputStream.close()
                workbook.close()

                // Membuka file Excel menggunakan FileProvider agar bisa dibaca aplikasi penampil Excel di HP
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
