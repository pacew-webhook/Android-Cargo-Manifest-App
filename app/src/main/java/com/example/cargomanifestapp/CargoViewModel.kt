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

    // --- IMPORT LOGIC ---
    fun importFromExcel(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream?.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                val importedList = mutableListOf<CargoItem>()
                val evaluator = workbook.creationHelper.createFormulaEvaluator()

                for (i in 12..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue

                    val pti = getCellString(row, 1, evaluator)
                    val pcsQty = getCellString(row, 2, evaluator)
                    val weight = getCellString(row, 4, evaluator)
                    val description = getCellString(row, 5, evaluator)
                    val customer = getCellString(row, 6, evaluator)
                    val noPag = getCellString(row, 8, evaluator)

                    if (pti.contains("TOTAL", ignoreCase = true) || 
                        description.contains("TOTAL", ignoreCase = true) || 
                        description.contains("Prepared by", ignoreCase = true)) {
                        break
                    }

                    if (pti.isBlank() && description.isBlank() && noPag.isBlank() && pcsQty.isBlank()) {
                        continue
                    }

                    importedList.add(
                        CargoItem(
                            id = System.currentTimeMillis() + i,
                            pti = pti,
                            pcsQty = pcsQty,
                            weight = weight,
                            subTotal = weight,
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

    // --- EXPORT LOGIC (SISTEM SHIFT + COPY STYLE SUPAYA TIDAK MERUSAK FOOTER) ---
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

                val stowingList = currentList.filter { it.noPag.isNotBlank() }
                val startRow = 12
                val templateMaxRows = 21 // Slot baris bawaan template (baris 12 s.d. 32)
                val maxDataCount = maxOf(currentList.size, stowingList.size)

                // Jika jumlah data melebihi slot bawaan, sisipkan baris baru di atas Footer (baris 33)
                if (maxDataCount > templateMaxRows) {
                    val additionalRows = maxDataCount - templateMaxRows
                    sheet.shiftRows(33, sheet.lastRowNum, additionalRows, true, true)

                    // Ambil contoh baris acuan (baris 12) untuk menduplikasi Style & Border
                    val sampleRow = sheet.getRow(startRow)
                    for (i in 0 until additionalRows) {
                        val newRowIdx = 33 + i
                        val newRow = sheet.createRow(newRowIdx)
                        if (sampleRow != null) {
                            for (c in 0 until 12) {
                                val sampleCell = sampleRow.getCell(c)
                                val newCell = newRow.createCell(c)
                                if (sampleCell?.cellStyle != null) {
                                    newCell.cellStyle = sampleCell.cellStyle
                                }
                            }
                        }
                    }
                }

                // Isi Header AWB dan Flight No
                sheet.getRow(1)?.getCell(7)?.setCellValue(awbNo)
                sheet.getRow(6)?.getCell(7)?.setCellValue(flightNo)

                // 1. TULIS TABEL MANIFEST (KIRI)
                currentList.forEachIndexed { index, item ->
                    val targetRowIdx = startRow + index
                    val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)

                    setNumericOrText(row, 0, (index + 1).toDouble()) // No
                    setTextCell(row, 1, item.pti)                    // PTI
                    setNumericOrText(row, 2, item.pcsQty)            // Pcs/Cly
                    setNumericOrText(row, 4, if (item.subTotal.isNotBlank()) item.subTotal else item.weight) // Weight
                    setTextCell(row, 5, item.description)            // Description
                    setTextCell(row, 6, item.customer)               // Customer
                }

                // 2. TULIS TABEL STOWING (KANAN)
                stowingList.forEachIndexed { index, item ->
                    val targetRowIdx = startRow + index
                    val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)

                    setNumericOrText(row, 7, (index + 1).toDouble()) // No
                    setTextCell(row, 8, item.noPag)                  // NO PAG
                    setTextCell(row, 9, item.description)            // Description
                    setNumericOrText(row, 10, if (item.subTotal.isNotBlank()) item.subTotal else item.weight) // Weight
                }

                // Recalculate rumus TOTAL jika ada
                workbook.creationHelper.createFormulaEvaluator().evaluateAll()

                // Simpan File dan Tampilkan
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

    private fun setNumericOrText(row: Row, col: Int, value: Any) {
        val cell = row.getCell(col) ?: row.createCell(col)
        when (value) {
            is Double -> cell.setCellValue(value)
            is String -> {
                val num = value.toDoubleOrNull()
                if (num != null) cell.setCellValue(num) else cell.setCellValue(value)
            }
        }
    }

    private fun setTextCell(row: Row, col: Int, value: String) {
        val cell = row.getCell(col) ?: row.createCell(col)
        cell.setCellValue(value)
    }
}
