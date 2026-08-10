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

    // ==========================================
    // LOGIKA IMPORT DATA DARI EXCEL
    // ==========================================
    fun importFromExcel(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream?.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                val importedList = mutableListOf<CargoItem>()
                val evaluator = workbook.creationHelper.createFormulaEvaluator()

                for (i in 13..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue

                    val pti = getCellString(row, 1, evaluator)
                    val pcsQty = getCellString(row, 2, evaluator)
                    val pcsWeight = getCellString(row, 3, evaluator) // Col D: Pcs/Qty Wt
                    val subTotal = getCellString(row, 4, evaluator)  // Col E: Sub Total Weight
                    val description = getCellString(row, 5, evaluator)
                    val customer = getCellString(row, 6, evaluator)
                    val noPag = getCellString(row, 8, evaluator)     // Col I: NO PAG

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
                            weight = pcsWeight,
                            subTotal = if (subTotal.isNotBlank()) subTotal else pcsWeight,
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

    // ==========================================
    // LOGIKA EXPORT DATA KE EXCEL (REVISI FINAL)
    // ==========================================
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

                val startRow = 13 // Baris 14 di Excel
                val templateRows = 25 
                val dataSize = currentList.size

                // Header Penerbangan
                sheet.getRow(2)?.getCell(6)?.setCellValue(awbNo)     // Row 3 Col G
                sheet.getRow(8)?.getCell(6)?.setCellValue(flightNo)  // Row 9 Col G

                // 1. TAMBAH BARIS JIKA DATA MANIFEST MEMBENGKAK (> 25)
                if (dataSize > templateRows) {
                    val extraRowsNeeded = dataSize - templateRows
                    val insertAt = startRow + templateRows
                    sheet.shiftRows(insertAt, sheet.lastRowNum, extraRowsNeeded, true, false)
                }

                val sampleRow = sheet.getRow(startRow)

                // 2. ISI TABEL MANIFEST (KOLOM A - G)
                currentList.forEachIndexed { index, item ->
                    val targetRowIdx = startRow + index
                    val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)

                    if (sampleRow != null) {
                        for (c in 0..6) {
                            val sampleCell = sampleRow.getCell(c)
                            val cell = row.getCell(c) ?: row.createCell(c)
                            if (sampleCell?.cellStyle != null) {
                                cell.cellStyle = sampleCell.cellStyle
                            }
                        }
                    }

                    val pcs = parseDoubleOrZero(item.pcsQty)
                    val wt = parseDoubleOrZero(item.weight)
                    val subTotalVal = if (item.subTotal.isNotBlank()) {
                        parseDoubleOrZero(item.subTotal)
                    } else if (pcs > 0 && wt > 0) {
                        pcs * wt
                    } else {
                        0.0
                    }

                    setNumericCell(row, 0, (index + 1).toDouble())  // Col A: No
                    setTextCell(row, 1, item.pti)                    // Col B: PTI
                    setNumericCell(row, 2, pcs)                      // Col C: Pcs/Cly
                    
                    if (wt > 0) setNumericCell(row, 3, wt) else setTextCell(row, 3, "") // Col D: Wt/Pcs
                    if (subTotalVal > 0) setNumericCell(row, 4, subTotalVal) else setTextCell(row, 4, "") // Col E: Sub Total
                    
                    setTextCell(row, 5, item.description)            // Col F: Description
                    setTextCell(row, 6, item.customer)               // Col G: Customer
                }

                // 3. BARIS TOTAL WEIGHT MANIFEST
                val lastDataRowExcel = startRow + dataSize
                val totalRowIdx = startRow + dataSize
                val totalRow = sheet.getRow(totalRowIdx) ?: sheet.createRow(totalRowIdx)

                setTextCell(totalRow, 1, "TOTAL WEIGHT")
                setFormulaCell(totalRow, 2, "SUM(C14:C$lastDataRowExcel)")
                setFormulaCell(totalRow, 4, "SUM(E14:E$lastDataRowExcel)")

                // 4. ISI TABEL STOWING CHECKLIST (KOLOM H - M) & BERSIHKAN BARIS KOSONG
                val stowingList = currentList.filter { it.noPag.isNotBlank() }
                val maxStowingRows = 25

                for (i in 0 until maxStowingRows) {
                    val targetRowIdx = startRow + i
                    val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)

                    if (i < stowingList.size) {
                        val item = stowingList[i]
                        val pcs = parseDoubleOrZero(item.pcsQty)
                        val wt = parseDoubleOrZero(item.weight)
                        val subTotalVal = if (item.subTotal.isNotBlank()) parseDoubleOrZero(item.subTotal) else (pcs * wt)

                        setNumericCell(row, 7, (i + 1).toDouble())            // Col H: No
                        setTextCell(row, 8, item.noPag)                        // Col I: NO PAG
                        setTextCell(row, 9, item.description)                  // Col J: Description
                        setNumericCell(row, 10, subTotalVal)                    // Col K: Net
                        setFormulaCell(row, 11, "K${targetRowIdx + 1}+125")    // Col L: Gross
                        setTextCell(row, 12, item.customer)                    // Col M: Customer
                    } else {
                        // Bersihkan agar tidak muncul angka 125 siluman
                        setTextCell(row, 7, "")
                        setTextCell(row, 8, "")
                        setTextCell(row, 9, "")
                        setTextCell(row, 10, "")
                        setTextCell(row, 11, "")
                        setTextCell(row, 12, "")
                    }
                }

                // 5. RUMUS TOTAL STOWING CHECKLIST
                val stowingTotalRowIdx = 38 
                val stowingTotalRow = sheet.getRow(stowingTotalRowIdx) ?: sheet.createRow(stowingTotalRowIdx)
                setFormulaCell(stowingTotalRow, 10, "SUM(K14:K38)") // Total Net Stowing
                setFormulaCell(stowingTotalRow, 11, "SUM(L14:L38)") // Total Gross Stowing

                workbook.creationHelper.createFormulaEvaluator().evaluateAll()

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

    private fun setFormulaCell(row: Row, col: Int, formula: String) {
        val cell = row.getCell(col) ?: row.createCell(col)
        cell.cellFormula = formula
    }
}
