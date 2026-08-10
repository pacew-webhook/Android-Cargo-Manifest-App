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

    // ================= IMPLEMENTASI IMPORT EXCEL DINAMIS & PINTAR =================
    fun importFromExcel(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream?.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                val importedList = mutableListOf<CargoItem>()
                val evaluator = workbook.creationHelper.createFormulaEvaluator()

                // 1. Deteksi letak indeks kolom secara otomatis berdasarkan baris Header
                var colPti = 1
                var colPcs = 2
                var colWeight = 3
                var colSubTotal = 4
                var colDesc = 5
                var colCust = 6
                var colPag = 8

                for (rIdx in 9..11) {
                    val headRow = sheet.getRow(rIdx) ?: continue
                    for (cIdx in 0 until headRow.lastCellNum) {
                        val cellText = headRow.getCell(cIdx)?.toString()?.trim()?.uppercase() ?: ""
                        when {
                            cellText.contains("PTI") -> colPti = cIdx
                            cellText.contains("PCS") || cellText.contains("QTY") -> colPcs = cIdx
                            cellText.contains("WEIGHT") || cellText.contains("KG") -> colWeight = cIdx
                            cellText.contains("SUB") -> colSubTotal = cIdx
                            cellText.contains("DESC") -> colDesc = cIdx
                            cellText.contains("COST") || cellText.contains("CUST") -> colCust = cIdx
                            cellText.contains("PAG") || cellText.contains("PAJ") -> colPag = cIdx
                        }
                    }
                }

                // 2. Membaca baris data mulai dari baris ke-13 (indeks 12)
                for (i in 12..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue
                    
                    fun getVal(colIdx: Int): String {
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

                    val pti = getVal(colPti)
                    val description = getVal(colDesc)
                    val customer = getVal(colCust)

                    // Jika baris benar-benar kosong, lewati
                    if (pti.isEmpty() && description.isEmpty() && customer.isEmpty()) continue

                    val pcsQty = getVal(colPcs)
                    val weight = getVal(colWeight)
                    var subTotal = getVal(colSubTotal)

                    // Kalkulasi otomatis subtotal jika kosong atau berupa rumus
                    if (subTotal.isEmpty() || subTotal.contains("*")) {
                        val p = pcsQty.toDoubleOrNull() ?: 0.0
                        val w = weight.toDoubleOrNull() ?: 0.0
                        val res = p * w
                        if (res > 0.0) {
                            subTotal = if (res % 1.0 == 0.0) res.toInt().toString() else res.toString()
                        }
                    }

                    val noPag = getVal(colPag)

                    val item = CargoItem(
                        id = System.currentTimeMillis() + i,
                        awbNo = "",
                        flightNo = "",
                        pti = if (pti.isNotBlank()) pti else "KAL00$i",
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
                        Toast.makeText(context, "Berhasil import ${importedList.size} data secara rapi!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Tidak ada data valid yang ditemukan.", Toast.LENGTH_SHORT).show()
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

    // ================= FUNGSI EXPORT DATA EXCEL =================
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
                (row2.getCell(7) ?: row2.createCell(7)).setCellValue(awbNo.trim().uppercase())

                val row7 = sheet.getRow(6) ?: sheet.createRow(6)
                (row7.getCell(7) ?: row7.createCell(7)).setCellValue(flightNo.trim().uppercase())

                val startRow = 13 // Baris awal data (baris ke-14 pada Excel)

                // 3. Menulis Data ke Tabel Manifest
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

                // 4. Menulis Data ke Tabel Stowing / PAG
                for ((index, item) in groupedStowing.withIndex()) {
                    val currentRowIndex = startRow + index
                    val row = sheet.getRow(currentRowIndex) ?: sheet.createRow(currentRowIndex)

                    (row.getCell(8) ?: row.createCell(8)).setCellValue((index + 1).toDouble())
                    (row.getCell(9) ?: row.createCell(9)).setCellValue(item.noPag)
                    (row.getCell(10) ?: row.createCell(10)).setCellValue(item.description)
                    (row.getCell(11) ?: row.createCell(11)).setCellValue(item.subTotal)
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
