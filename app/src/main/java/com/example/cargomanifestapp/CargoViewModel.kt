package com.example.cargomanifestapp

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileOutputStream

// Model Data Pendukung
data class CargoItem(
    val id: String = "",
    val pti: String = "",
    val pcsQty: String = "",
    val weight: String = "",
    val subTotal: String = "",
    val description: String = "",
    val customer: String = "",
    val noPag: String = ""
)

data class GroupedManifestItem(
    val pti: String,
    val description: String,
    val customer: String,
    val pcsQty: Double,
    val weight: Double,
    val subTotal: Double
)

data class GroupedStowingItem(
    val noPag: String,
    val description: String,
    val customer: String,
    val subTotal: Double
)

class CargoViewModel : ViewModel() {

    // State utama untuk menampung list data kargo
    private val _cargoList = MutableStateFlow<List<CargoItem>>(emptyList())
    val cargoList: StateFlow<List<CargoItem>> = _cargoList.asStateFlow()

    // ================= FUNGSI TAMBAHAN / STATE LAINNYA =================
    
    fun addCargo(item: CargoItem) {
        val currentList = _cargoList.value.toMutableList()
        currentList.add(item)
        _cargoList.value = currentList
    }

    fun updateCargo(index: Int, item: CargoItem) {
        val currentList = _cargoList.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = item
            _cargoList.value = currentList
        }
    }

    fun deleteCargo(index: Int) {
        val currentList = _cargoList.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _cargoList.value = currentList
        }
    }

    fun clearData() {
        _cargoList.value = emptyList()
    }

    // ================= FUNGSI EXPORT DATA EXCEL (YANG DIPERBAIKI) =================
    fun exportToExcel(context: Context, awbNo: String, flightNo: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentList = cargoList.value

                if (currentList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Tidak ada data untuk diexport!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 1. Grouping Data Manifest (Tabel Sisi Kiri)
                val groupedManifest = currentList.groupBy {
                    Pair(it.description.trim().uppercase(), it.customer.trim().uppercase())
                }.map { (keyPair, items) ->
                    val descKey = keyPair.first
                    val custKey = keyPair.second
                    val uniquePti = items.map { it.pti }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                    
                    GroupedManifestItem(
                        pti = uniquePti,
                        description = if (descKey.isBlank()) "-" else descKey,
                        customer = if (custKey.isBlank()) "-" else custKey,
                        pcsQty = items.sumOf { it.pcsQty.toDoubleOrNull() ?: 0.0 },
                        weight = items.sumOf { it.weight.toDoubleOrNull() ?: 0.0 },
                        subTotal = items.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
                    )
                }

                // 2. Grouping Data Stowing / PAG (Tabel Sisi Kanan)
                val groupedStowing = currentList.groupBy {
                    it.noPag.trim().uppercase()
                }.map { (pagKey, items) ->
                    val uniqueDescs = items.map { it.description }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                    val uniqueCusts = items.map { it.customer }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                    GroupedStowingItem(
                        noPag = if (pagKey.isBlank()) "-" else pagKey,
                        description = uniqueDescs,
                        customer = uniqueCusts,
                        subTotal = items.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
                    )
                }

                // Membuka Template Excel dari Assets
                val inputStream = context.assets.open("template_manifest.xlsx")
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)

                // Mengisi Header AWB & Flight No
                val row2 = sheet.getRow(1) ?: sheet.createRow(1)
                (row2.getCell(6) ?: row2.createCell(6)).setCellValue(awbNo.trim().uppercase())

                val row8 = sheet.getRow(7) ?: sheet.createRow(7)
                (row8.getCell(6) ?: row8.createCell(6)).setCellValue(": ${flightNo.trim().uppercase()}")

                val startRow = 12 // Baris awal data dimulai (Baris ke-13 pada Excel)

                // 3. Menulis Data ke Tabel Manifest (Kolom A s.d G / Indeks 0 sampai 6)
                for ((index, item) in groupedManifest.withIndex()) {
                    val currentRowIndex = startRow + index
                    val row = sheet.getRow(currentRowIndex) ?: sheet.createRow(currentRowIndex)

                    (row.getCell(0) ?: row.createCell(0)).setCellValue((index + 1).toDouble())
                    (row.getCell(1) ?: row.createCell(1)).setCellValue(item.pti)
                    (row.getCell(2) ?: row.createCell(2)).setCellValue(item.pcsQty)
                    (row.getCell(3) ?: row.createCell(3)).setCellValue(item.weight)
                    (row.getCell(4) ?: row.createCell(4)).setCellValue(item.subTotal)
                    (row.getCell(5) ?: row.createCell(5)).setCellValue(item.description)
                    (row.getCell(6) ?: row.createCell(6)).setCellValue(item.customer)
                }

                // 4. Menulis Data ke Tabel Stowing / PAG (Kolom H s.d M / Indeks 7 sampai 12 secara presisi)
                for ((index, item) in groupedStowing.withIndex()) {
                    val currentRowIndex = startRow + index
                    val row = sheet.getRow(currentRowIndex) ?: sheet.createRow(currentRowIndex)

                    (row.getCell(7) ?: row.createCell(7)).setCellValue((index + 1).toDouble())
                    (row.getCell(8) ?: row.createCell(8)).setCellValue(item.noPag)
                    (row.getCell(9) ?: row.createCell(9)).setCellValue(item.description)
                    (row.getCell(10) ?: row.createCell(10)).setCellValue(item.subTotal) // Kolom Net
                    (row.getCell(11) ?: row.createCell(11)).setCellValue(item.subTotal) // Kolom Gross
                    (row.getCell(12) ?: row.createCell(12)).setCellValue(item.customer)  // Kolom Costumers
                }

                // Menyimpan File Hasil Export ke Cache Internal
                val file = File(context.cacheDir, "Manifest_Cargo_Output.xlsx")
                val outputStream = FileOutputStream(file)
                workbook.write(outputStream)
                outputStream.close()
                workbook.close()

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )

                // Membuka File Excel Secara Otomatis
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }

                    context.startActivity(Intent.createChooser(intent, "Buka File Excel dengan"))
                    Toast.makeText(context, "Export Excel Berhasil & Rapi!", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Gagal Export: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
