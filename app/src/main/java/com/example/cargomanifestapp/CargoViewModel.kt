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
import org.apache.poi.ss.usermodel.*
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
        awbNo: String = "",
        flightNo: String = "",
        pti: String = "",
        pcsQty: String = "",
        weight: String = "",
        subTotal: String = "",
        description: String = "",
        customer: String = "",
        noPag: String = ""
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

    // --- IMPORT LOGIC DENGAN AKURASI KOKOH PADA ANGKA ---
    fun importFromExcel(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream?.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                val importedList = mutableListOf<CargoItem>()
                val evaluator = workbook.creationHelper.createFormulaEvaluator()

                // Pembacaan mulai dari baris 12 (indeks 12)
                for (i in 12..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue

                    val pti = getCellString(row, 1, evaluator)
                    val pcsQty = getCellString(row, 2, evaluator)
                    val weight = getCellString(row, 3, evaluator)    // Kolom D (Pcs/Qty Wt)
                    val subTotal = getCellString(row, 4, evaluator)  // Kolom E (WEIGHT Total)
                    val description = getCellString(row, 5, evaluator)
                    val customer = getCellString(row, 6, evaluator)
                    val noPag = getCellString(row, 8, evaluator)

                    // Berhenti jika sudah menyentuh baris Total
                    if (pti.contains("TOTAL", ignoreCase = true) || 
                        description.contains("TOTAL", ignoreCase = true) || 
                        description.contains("Prepared by", ignoreCase = true)) {
                        break
                    }

                    // Abaikan baris jika tidak ada data utama
                    if (pti.isBlank() && description.isBlank() && noPag.isBlank() && pcsQty.isBlank()) {
                        continue
                    }

                    importedList.add(
                        CargoItem(
                            id = System.currentTimeMillis() + i,
                            pti = pti,
                            pcsQty = pcsQty,
                            weight = if (weight.isNotBlank()) weight else subTotal,
                            subTotal = if (subTotal.isNotBlank()) subTotal else weight,
                            description = description,
                            customer = customer,
                            noPag = noPag
                        )
                    )
                }
                workbook.close()

                withContext(Dispatchers.Main) {
                    _cargoList.value = importedList
                    Toast.makeText(context, "Berhasil import ${importedList.size} data!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error Import: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getCellString(row: Row, colIdx: Int, evaluator: FormulaEvaluator): String {
        val cell = row.getCell(colIdx) ?: return ""
        val evaluated = evaluator.evaluate(cell)
        return when (evaluated?.cellType) {
            CellType.NUMERIC -> {
                val num = evaluated.numberValue
                if (num % 1.0 == 0.0) num.toInt().toString() else num.toString()
            }
            CellType.STRING -> evaluated.stringValue.trim()
            else -> cell.toString().trim()
        }
    }

    // --- EXPORT LOGIC: ISOLASI KHUSUS TABEL MANIFEST (STOWING TETAP STATIS) ---
    fun exportToExcel(context: Context, awbNo: String, flightNo: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentList = cargoList.value
                if (currentList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Tidak ada data untuk di-export", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val inputStream = context.assets.open("template_manifest.xlsx")
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                inputStream.close()

                val startRow = 12

                // Isi Header AWB dan Flight No
                sheet.getRow(1)?.getCell(7)?.setCellValue(awbNo)
                sheet.getRow(6)?.getCell(7)?.setCellValue(flightNo)

                // 1. TULIS TABEL MANIFEST (KIRI: KOLOM A - G)
                // Memastikan baris dibuat secara dinamis untuk Manifest tanpa mengganggu area Stowing
                val sampleRow = sheet.getRow(startRow)
                currentList.forEachIndexed { index, item ->
                    val targetRowIdx = startRow + index
                    val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)

                    // Duplikasi style jika baris baru melebihi template bawaan
                    if (targetRowIdx > 32 && sampleRow != null) {
                        for (c in 0..6) {
                            val sampleCell = sampleRow.getCell(c)
                            val cell = row.getCell(c) ?: row.createCell(c)
                            if (sampleCell?.cellStyle != null) {
                                cell.cellStyle = sampleCell.cellStyle
                            }
                        }
                    }

                    setNumericCell(row, 0, (index + 1).toDouble())                      // No
                    setTextCell(row, 1, item.pti)                                        // PTI
                    setNumericCell(row, 2, parseDoubleOrZero(item.pcsQty))               // Pcs/Cly
                    setNumericCell(row, 3, parseDoubleOrZero(item.weight))               // Pcs/Qty Wt
                    setNumericCell(row, 4, parseDoubleOrZero(item.subTotal))             // SubTotal (Kg)
                    setTextCell(row, 5, item.description)                                // Description
                    setTextCell(row, 6, item.customer)                                   // Customer
                }

                // Tulis Total Manifest di bawah data terakhir Manifest
                val totalRowIdx = startRow + currentList.size
                val totalRow = sheet.getRow(totalRowIdx) ?: sheet.createRow(totalRowIdx)
                setTextCell(totalRow, 1, "TOTAL WEIGHT")
                
                val totalPcs = currentList.sumOf { parseDoubleOrZero(it.pcsQty) }
                val totalWeight = currentList.sumOf { parseDoubleOrZero(it.subTotal) }
                setNumericCell(totalRow, 2, totalPcs)
                setNumericCell(totalRow, 4, totalWeight)

                // 2. TULIS TABEL STOWING (KANAN: KOLOM H - K) - POSISI STATIS TEMPLATE
                val stowingList = currentList.filter { it.noPag.isNotBlank() }
                stowingList.forEachIndexed { index, item ->
                    val targetRowIdx = startRow + index
                    // Hanya tulis selama masih di dalam area template stowing (sebelum footer stowing)
                    if (targetRowIdx < 32) {
                        val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)

                        setNumericCell(row, 7, (index + 1).toDouble())                   // No
                        setTextCell(row, 8, item.noPag)                                  // NO PAG
                        setTextCell(row, 9, item.description)                            // Description
                        setNumericCell(row, 10, parseDoubleOrZero(item.subTotal))        // Weight
                    }
                }

                // Recalculate formula Excel
                workbook.creationHelper.createFormulaEvaluator().evaluateAll()

                // Simpan File Output
                val file = File(context.cacheDir, "Manifest_Cargo_Output.xlsx")
                val outputStream = FileOutputStream(file)
                workbook.write(outputStream)
                outputStream.close()
                workbook.close()

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal Export: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun parseDoubleOrZero(value: String): Double {
        if (value.isBlank()) return 0.0
        val cleanValue = value.replace(",", ".").replace("[^0-9.]".toRegex(), "")
        return cleanValue.toDoubleOrNull() ?: 0.0
    }

    private fun setNumericCell(row: Row, col: Int, value: Double) {
        val cell = row.getCell(col) ?: row.createCell(col)
        cell.setCellValue(value)
    }

    private fun setTextCell(row: Row, col: Int, value: String) {
        val cell = row.getCell(col) ?: row.createCell(col)
        cell.setCellValue(value)
    }
}
