package com.example.cargomanifestapp

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
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

    // State untuk menyimpan Header Manifest saat di-import
    private val _importedAwbNo = MutableStateFlow("")
    val importedAwbNo: StateFlow<String> = _importedAwbNo.asStateFlow()

    private val _importedFlightNo = MutableStateFlow("")
    val importedFlightNo: StateFlow<String> = _importedFlightNo.asStateFlow()

    // ==========================================
    // 1. KELOLA DATA LOCAL
    // ==========================================
    fun addCargo(item: CargoItem) {
        val currentList = _cargoList.value.toMutableList()
        currentList.add(item)
        _cargoList.value = currentList
    }

    fun addCargo(
        awbNo: String = "", flightNo: String = "", pti: String = "",
        pcsQty: String = "", weight: String = "", subTotal: String = "",
        description: String = "", customer: String = "", noPag: String = ""
    ) {
        val newItem = CargoItem(
            id = System.currentTimeMillis() + (0..1000).random(),
            awbNo = awbNo, flightNo = flightNo, pti = pti,
            pcsQty = pcsQty, weight = weight, subTotal = subTotal,
            description = description, customer = customer, noPag = noPag
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
        _importedAwbNo.value = ""
        _importedFlightNo.value = ""
    }

    // ==========================================
    // 2. IMPORT DATA (MEMBACA HEADER & FILTER FOOTER)
    // ==========================================
    fun importFromExcel(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream?.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                val importedList = mutableListOf<CargoItem>()
                val evaluator = workbook.creationHelper.createFormulaEvaluator()

                // A. BACA HEADER (AWB & FLIGHT NO)
                val awbCell = sheet.getRow(2)?.getCell(6)
                val flightCell = sheet.getRow(8)?.getCell(6)
                
                val extractedAwb = getCellStringFromCell(awbCell, evaluator)
                val extractedFlight = getCellStringFromCell(flightCell, evaluator)

                // B. BACA DATA ITEM (Mulai baris 14 / Indeks 13)
                for (i in 13..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue

                    val noCol = getCellString(row, 0, evaluator)
                    val pti = getCellString(row, 1, evaluator)
                    val pcsQty = getCellString(row, 2, evaluator)
                    val pcsWeight = getCellString(row, 3, evaluator)
                    val subTotal = getCellString(row, 4, evaluator)
                    val description = getCellString(row, 5, evaluator)
                    val customer = getCellString(row, 6, evaluator)
                    val noPag = getCellString(row, 8, evaluator)

                    // ABAIKAN BARIS TOTAL, FOOTER, DAN TANDA TANGAN
                    val isTotalRow = noCol.contains("TOTAL", true) ||
                            pti.contains("TOTAL", true) ||
                            description.contains("TOTAL", true) ||
                            description.contains("Prepared", true) ||
                            description.contains("Approved", true) ||
                            customer.contains("Approved", true)

                    if (isTotalRow) continue

                    // Abaikan baris kosong
                    if (pti.isBlank() && description.isBlank() && pcsQty.isBlank()) continue

                    val newItem = CargoItem(
                        id = System.currentTimeMillis() + i + (0..1000).random(),
                        awbNo = extractedAwb,
                        flightNo = extractedFlight,
                        pti = pti,
                        pcsQty = pcsQty,
                        weight = pcsWeight,
                        subTotal = if (subTotal.isNotBlank()) subTotal else pcsWeight,
                        description = description,
                        customer = customer,
                        noPag = noPag
                    )

                    importedList.add(newItem)
                }
                workbook.close()

                withContext(Dispatchers.Main) {
                    _importedAwbNo.value = extractedAwb
                    _importedFlightNo.value = extractedFlight
                    _cargoList.value = importedList
                    Toast.makeText(context, "Berhasil Import ${importedList.size} Data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal Import: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ==========================================
    // 3. EXPORT EXCEL (PRESISI, RAPI & SUPPORT > 24 ITEM)
    // ==========================================
    fun exportToExcel(context: Context, awbNo: String, flightNo: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentList = cargoList.value
                if (currentList.isEmpty()) return@launch

                val inputStream = context.assets.open("template_manifest.xlsx")
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                inputStream.close()

                val startRow = 13 // Baris 14 di Excel (0-indexed)
                val templateDataCapacity = 24 // Kapasitas default template (Baris 14-37)

                // Header (Gunakan parameter atau hasil import)
                val finalAwb = if (awbNo.isNotBlank()) awbNo else _importedAwbNo.value
                val finalFlight = if (flightNo.isNotBlank()) flightNo else _importedFlightNo.value

                sheet.getRow(2)?.getCell(6)?.setCellValue(finalAwb)
                sheet.getRow(8)?.getCell(6)?.setCellValue(finalFlight)

                // Filter Stowing Checklist (Item yang punya NO PAG)
                val stowingList = currentList.filter { it.noPag.isNotBlank() }
                val maxRows = maxOf(currentList.size, stowingList.size)

                // Sampel row untuk menyalin style border & font
                val sampleRow = sheet.getRow(startRow)

                // Clean-up slot data bawaan (Baris 14-37)
                for (r in startRow until (startRow + templateDataCapacity)) {
                    val targetRow = sheet.getRow(r)
                    if (targetRow != null) {
                        for (c in 0..12) {
                            targetRow.getCell(c)?.setCellValue("")
                        }
                    }
                }

                // Geser Footer jika data barang > 24 baris
                if (maxRows > templateDataCapacity) {
                    val extraRowsNeeded = maxRows - templateDataCapacity
                    sheet.shiftRows(37, sheet.lastRowNum, extraRowsNeeded, true, true)
                }

                var totalManifestPcs = 0.0
                var totalManifestWeight = 0.0
                var totalStowingNet = 0.0
                var totalStowingGross = 0.0

                // Isi Data Barang
                for (i in 0 until maxRows) {
                    val rowIdx = startRow + i
                    var row = sheet.getRow(rowIdx)
                    if (row == null) {
                        row = sheet.createRow(rowIdx)
                        sampleRow?.let { row.height = it.height }
                    }

                    // SISI MANIFEST
                    if (i < currentList.size) {
                        val item = currentList[i]
                        val pcs = parseDoubleOrZero(item.pcsQty)
                        val subTotal = parseDoubleOrZero(item.subTotal)

                        totalManifestPcs += pcs
                        totalManifestWeight += subTotal

                        setStyledNumericCell(row, 0, (i + 1).toDouble(), sampleRow?.getCell(0))
                        setStyledTextCell(row, 1, item.pti, sampleRow?.getCell(1))
                        setStyledNumericCell(row, 2, pcs, sampleRow?.getCell(2))
                        setStyledNumericCell(row, 3, parseDoubleOrZero(item.weight), sampleRow?.getCell(3))
                        setStyledNumericCell(row, 4, subTotal, sampleRow?.getCell(4))
                        setStyledTextCell(row, 5, item.description, sampleRow?.getCell(5))
                        setStyledTextCell(row, 6, item.customer, sampleRow?.getCell(6))
                    }

                    // SISI STOWING
                    if (i < stowingList.size) {
                        val stowing = stowingList[i]
                        val net = parseDoubleOrZero(stowing.subTotal)
                        val gross = net + 125.0

                        totalStowingNet += net
                        totalStowingGross += gross

                        setStyledNumericCell(row, 7, (i + 1).toDouble(), sampleRow?.getCell(7))
                        setStyledTextCell(row, 8, stowing.noPag, sampleRow?.getCell(8))
                        setStyledTextCell(row, 9, stowing.description, sampleRow?.getCell(9))
                        setStyledNumericCell(row, 10, net, sampleRow?.getCell(10))
                        setStyledNumericCell(row, 11, gross, sampleRow?.getCell(11))
                        setStyledTextCell(row, 12, stowing.customer, sampleRow?.getCell(12))
                    }
                }

                // ISI BARIS TOTAL UTAMA PADA POSISI AKHIR
                val totalRowIdx = if (maxRows <= templateDataCapacity) 37 else (startRow + maxRows)
                val totalRow = sheet.getRow(totalRowIdx) ?: sheet.createRow(totalRowIdx)

                setNumericCell(totalRow, 2, totalManifestPcs)
                setNumericCell(totalRow, 4, totalManifestWeight)
                setNumericCell(totalRow, 10, totalStowingNet)
                setNumericCell(totalRow, 11, totalStowingGross)

                // Output File
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
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal Export: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ==========================================
    // HELPER UTILITY
    // ==========================================
    private fun getCellString(row: Row, colIdx: Int, evaluator: FormulaEvaluator): String {
        val cell = row.getCell(colIdx) ?: return ""
        return getCellStringFromCell(cell, evaluator)
    }

    private fun getCellStringFromCell(cell: Cell?, evaluator: FormulaEvaluator): String {
        if (cell == null) return ""
        val evaluated = evaluator.evaluate(cell)
        return when (evaluated?.cellType) {
            CellType.NUMERIC -> formatNumber(evaluated.numberValue)
            CellType.STRING -> evaluated.stringValue.trim()
            else -> cell.toString().trim()
        }
    }

    private fun parseDoubleOrZero(value: String): Double {
        if (value.isBlank()) return 0.0
        val cleanValue = value.replace(",", ".").replace("[^0-9.]".toRegex(), "")
        return cleanValue.toDoubleOrNull() ?: 0.0
    }

    private fun formatNumber(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }

    private fun setNumericCell(row: Row, col: Int, value: Double) {
        val cell = row.getCell(col) ?: row.createCell(col)
        cell.setCellValue(value)
    }

    private fun setStyledNumericCell(row: Row, col: Int, value: Double, sampleCell: Cell?) {
        val cell = row.getCell(col) ?: row.createCell(col)
        cell.setCellValue(value)
        sampleCell?.cellStyle?.let { cell.cellStyle = it }
    }

    private fun setStyledTextCell(row: Row, col: Int, value: String, sampleCell: Cell?) {
        val cell = row.getCell(col) ?: row.createCell(col)
        cell.setCellValue(value)
        sampleCell?.cellStyle?.let { cell.cellStyle = it }
    }
}
