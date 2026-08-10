package com.example.cargomanifestapp

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
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

class CargoViewModel(application: Application) : AndroidViewModel(application) {

    private val _cargoList = MutableStateFlow<List<CargoItem>>(emptyList())
    val cargoList: StateFlow<List<CargoItem>> = _cargoList.asStateFlow()

    fun addCargo(item: CargoItem) {
        val currentList = _cargoList.value.toMutableList()
        currentList.add(item)
        _cargoList.value = currentList
    }

    fun addCargo(
        awbNo: String, flightNo: String, pti: String,
        pcsQty: String, weight: String, subTotal: String,
        description: String, customer: String, noPag: String
    ) {
        val newItem = CargoItem(
            id = System.currentTimeMillis(),
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
        addCargo(newItem)
    }

    fun updateCargo(item: CargoItem) {
        val currentList = _cargoList.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == item.id }
        if (index != -1) {
            currentList[index] = item
            _cargoList.value = currentList
        }
    }

    fun deleteCargo(item: CargoItem) {
        val currentList = _cargoList.value.toMutableList()
        currentList.removeAll { it.id == item.id }
        _cargoList.value = currentList
    }

    fun clearAll() {
        _cargoList.value = emptyList()
    }

    // ================= IMPORT EXCEL PRESISI TINGGI =================
    fun importFromExcel(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream?.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                val importedList = mutableListOf<CargoItem>()
                val evaluator = workbook.creationHelper.createFormulaEvaluator()

                fun getCellString(row: org.apache.poi.ss.usermodel.Row, colIdx: Int): String {
                    val cell = row.getCell(colIdx) ?: return ""
                    val evaluated = evaluator.evaluate(cell)
                    return when {
                        evaluated != null -> {
                            when (evaluated.cellType) {
                                org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                                    val num = evaluated.numberValue
                                    if (num % 1.0 == 0.0) num.toInt().toString() else num.toString()
                                }
                                org.apache.poi.ss.usermodel.CellType.STRING -> evaluated.stringValue?.trim() ?: ""
                                else -> ""
                            }
                        }
                        else -> {
                            val str = cell.toString().trim()
                            if (str.equals("null", ignoreCase = true) || str.startsWith("=")) "" else str
                        }
                    }
                }

                // Membaca baris data utama mulai dari baris ke-13 Excel (Indeks 12)
                for (i in 12..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue

                    val noStr = getCellString(row, 0)
                    val noInt = noStr.toIntOrNull()
                    if (noInt == null || noInt <= 0) {
                        continue
                    }

                    val pti = getCellString(row, 1)
                    val pcsQty = getCellString(row, 2)
                    val weight = getCellString(row, 3)
                    var subTotal = getCellString(row, 4)
                    val description = getCellString(row, 5)
                    val customer = getCellString(row, 6)
                    val noPag = getCellString(row, 8)

                    if (subTotal.isEmpty() || subTotal.contains("*")) {
                        val p = pcsQty.toDoubleOrNull() ?: 0.0
                        val w = weight.toDoubleOrNull() ?: 0.0
                        val res = p * w
                        if (res > 0.0) {
                            subTotal = if (res % 1.0 == 0.0) res.toInt().toString() else res.toString()
                        }
                    }

                    val item = CargoItem(
                        id = System.currentTimeMillis() + i,
                        awbNo = "",
                        flightNo = "",
                        pti = if (pti.isNotBlank()) pti else "-",
                        pcsQty = if (pcsQty.isNotBlank()) pcsQty else "0",
                        weight = if (weight.isNotBlank()) weight else "0",
                        subTotal = if (subTotal.isNotBlank()) subTotal else "0",
                        description = if (description.isNotBlank()) description else "-",
                        customer = if (customer.isNotBlank()) customer else "-",
                        noPag = if (noPag.isNotBlank()) noPag else ""
                    )
                    importedList.add(item)
                }
                workbook.close()

                withContext(Dispatchers.Main) {
                    if (importedList.isNotEmpty()) {
                        _cargoList.value = importedList
                        Toast.makeText(context, "Berhasil import ${importedList.size} data!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Tidak ada data valid ditemukan.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal import: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ================= FUNGSI EXPORT DATA EXCEL (TERPISAH & AMAN) =================
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
                    val uniquePti = items.map { it.pti }.filter { it.isNotBlank() && it != "-" }.distinct().joinToString(", ")
                    
                    GroupedManifestItem(
                        pti = if (uniquePti.isBlank()) "-" else uniquePti,
                        description = if (descKey.isBlank()) "-" else descKey,
                        customer = if (custKey.isBlank()) "-" else custKey,
                        pcsQty = items.sumOf { it.pcsQty.toDoubleOrNull() ?: 0.0 },
                        weight = items.sumOf { it.weight.toDoubleOrNull() ?: 0.0 },
                        subTotal = items.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
                    )
                }

                // 2. Grouping Data Stowing / PAG (Tabel Sisi Kanan - Hanya item yang punya No PAG valid)
                val groupedStowing = currentList.filter { it.noPag.isNotBlank() && !it.noPag.equals("-", ignoreCase = true) }.groupBy {
                    it.noPag.trim().uppercase()
                }.map { (pagKey, items) ->
                    val uniqueDescs = items.map { it.description }.filter { it.isNotBlank() && it != "-" }.distinct().joinToString("+")
                    val uniqueCusts = items.map { it.customer }.filter { it.isNotBlank() && it != "-" }.distinct().joinToString(", ")
                    GroupedStowingItem(
                        noPag = pagKey,
                        description = if (uniqueDescs.isBlank()) "-" else uniqueDescs,
                        customer = if (uniqueCusts.isBlank()) "-" else uniqueCusts,
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
                (row2.getCell(7) ?: row2.createCell(7)).setCellValue(awbNo.trim().uppercase())

                val row7 = sheet.getRow(6) ?: sheet.createRow(6)
                (row7.getCell(7) ?: row7.createCell(7)).setCellValue(flightNo.trim().uppercase())

                val startRow = 12 // Baris ke-13 di Excel (Indeks 12)

                // 3. Menulis Data ke Tabel Manifest Kiri (Kolom 0 s.d 6)
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

                // 4. Menulis Data ke Tabel Stowing / PAG Kanan secara Independen (Maksimal 9 baris sesuai template)
                for ((index, item) in groupedStowing.withIndex()) {
                    if (index >= 9) break // Batasi maksimal 9 baris agar tidak merusak baris tanda tangan di bawahnya
                    val currentRowIndex = startRow + index
                    val row = sheet.getRow(currentRowIndex) ?: sheet.createRow(currentRowIndex)

                    (row.getCell(7) ?: row.createCell(7)).setCellValue((index + 1).toDouble()) // Nomor urut kanan
                    (row.getCell(8) ?: row.createCell(8)).setCellValue(item.noPag)            // No PAG
                    (row.getCell(9) ?: row.createCell(9)).setCellValue(item.description)      // Description PAG
                    (row.getCell(10) ?: row.createCell(10)).setCellValue(item.subTotal)       // Weight Net PAG
                    (row.getCell(12) ?: row.createCell(12)).setCellValue(item.customer)       // Customer PAG
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
                    Toast.makeText(context, "Gagal Export: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

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
