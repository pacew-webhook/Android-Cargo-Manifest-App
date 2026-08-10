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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import java.io.File
import java.io.FileOutputStream

class CargoViewModel(application: Application) : AndroidViewModel(application) {

    private val _cargoList = MutableStateFlow<List<CargoItem>>(emptyList())
    val cargoList: StateFlow<List<CargoItem>> = _cargoList.asStateFlow()

    // State Header Manifest
    private val _importedAwbNo = MutableStateFlow("")
    val importedAwbNo: StateFlow<String> = _importedAwbNo.asStateFlow()

    private val _importedFlightNo = MutableStateFlow("")
    val importedFlightNo: StateFlow<String> = _importedFlightNo.asStateFlow()

    // State Query Pencarian Data
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Filter List berdasarkan Query Search
    val filteredCargoList: StateFlow<List<CargoItem>> = combine(cargoList, searchQuery) { list, query ->
        if (query.isBlank()) list
        else list.filter {
            it.pti.contains(query, ignoreCase = true) ||
            it.customer.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true) ||
            it.noPag.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Summary Real-time untuk UI
    val totalPcs: StateFlow<Int> = cargoList.map { list ->
        list.sumOf { it.pcsQty.toIntOrNull() ?: 0 }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val totalWeight: StateFlow<Double> = cargoList.map { list ->
        list.sumOf { parseDoubleOrZero(it.subTotal) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // ==========================================
    // 1. KELOLA DATA LOCAL
    // ==========================================
    fun addCargo(item: CargoItem) {
        val currentList = _cargoList.value.toMutableList()
        // Cek duplikasi sebelum tambah manual
        val isDuplicate = currentList.any { isSameItem(it, item) }
        if (!isDuplicate) {
            currentList.add(item)
            _cargoList.value = currentList
        }
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
    // 2. IMPORT DATA (ANTI-DOUBLE / ANTI-DUPLIKAT)
    // ==========================================
    fun importFromExcel(context: Context, uri: Uri, replaceExisting: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream?.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                val importedList = mutableListOf<CargoItem>()
                val evaluator = workbook.creationHelper.createFormulaEvaluator()

                // A. BACA HEADER
                val awbCell = sheet.getRow(2)?.getCell(6)
                val flightCell = sheet.getRow(8)?.getCell(6)
                
                val extractedAwb = getCellStringFromCell(awbCell, evaluator)
                val extractedFlight = getCellStringFromCell(flightCell, evaluator)

                // Set penampung untuk kunci unik guna mencegah duplikasi di internal file Excel
                val uniqueKeysInImport = mutableSetOf<String>()

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
                    if (pti.isBlank() && description.isBlank() && pcsQty.isBlank()) continue

                    // KUNCI UNIK ANTI DUPLIKAT (PTI + DESCRIPTION + PCS + COSTUMER + NOPAG)
                    val uniqueKey = "${pti.trim()}_${description.trim()}_${pcsQty.trim()}_${customer.trim()}_${noPag.trim()}"
                    
                    // Cek jika baris ini ganda di dalam file Excel yang sama
                    if (uniqueKeysInImport.contains(uniqueKey)) {
                        continue
                    }
                    uniqueKeysInImport.add(uniqueKey)

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

                    if (replaceExisting) {
                        // Opsi Default: Timpa data lama agar tidak menumpuk double saat re-import
                        _cargoList.value = importedList
                    } else {
                        // Opsi Gabung: Hanya tambahkan item yang belum ada di list saat ini
                        val currentList = _cargoList.value.toMutableList()
                        for (item in importedList) {
                            if (currentList.none { isSameItem(it, item) }) {
                                currentList.add(item)
                            }
                        }
                        _cargoList.value = currentList
                    }

                    Toast.makeText(context, "Berhasil Import ${_cargoList.value.size} Data (Anti-Double)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal Import: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Helper Pembanding Item Identik/Double
    private fun isSameItem(item1: CargoItem, item2: CargoItem): Boolean {
        return item1.pti.equals(item2.pti, ignoreCase = true) &&
                item1.description.equals(item2.description, ignoreCase = true) &&
                item1.pcsQty.equals(item2.pcsQty, ignoreCase = true) &&
                item1.customer.equals(item2.customer, ignoreCase = true) &&
                item1.noPag.equals(item2.noPag, ignoreCase = true)
    }

    // ==========================================
    // 3. EXPORT EXCEL
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
                val templateDataCapacity = 24

                val finalAwb = if (awbNo.isNotBlank()) awbNo else _importedAwbNo.value
                val finalFlight = if (flightNo.isNotBlank()) flightNo else _importedFlightNo.value

                sheet.getRow(2)?.getCell(6)?.setCellValue(finalAwb)
                sheet.getRow(8)?.getCell(6)?.setCellValue(finalFlight)

                val stowingList = currentList.filter { it.noPag.isNotBlank() }
                val maxRows = maxOf(currentList.size, stowingList.size)
                val sampleRow = sheet.getRow(startRow)

                // Clean-up slot bawaan
                for (r in startRow until (startRow + templateDataCapacity)) {
                    val targetRow = sheet.getRow(r)
                    if (targetRow != null) {
                        for (c in 0..12) {
                            targetRow.getCell(c)?.setCellValue("")
                        }
                    }
                }

                // Shift row jika data > 24
                if (maxRows > templateDataCapacity) {
                    val extraRowsNeeded = maxRows - templateDataCapacity
                    sheet.shiftRows(37, sheet.lastRowNum, extraRowsNeeded, true, true)
                }

                var totalManifestPcs = 0.0
                var totalManifestWeight = 0.0
                var totalStowingNet = 0.0
                var totalStowingGross = 0.0

                for (i in 0 until maxRows) {
                    val rowIdx = startRow + i
                    var row = sheet.getRow(rowIdx)
                    if (row == null) {
                        row = sheet.createRow(rowIdx)
                        sampleRow?.let { row.height = it.height }
                    }

                    // Manifest
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

                    // Stowing
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

                // Set Total
                val totalRowIdx = if (maxRows <= templateDataCapacity) 37 else (startRow + maxRows)
                val totalRow = sheet.getRow(totalRowIdx) ?: sheet.createRow(totalRowIdx)

                setNumericCell(totalRow, 2, totalManifestPcs)
                setNumericCell(totalRow, 4, totalManifestWeight)
                setNumericCell(totalRow, 10, totalStowingNet)
                setNumericCell(totalRow, 11, totalStowingGross)

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
    // HELPER FUNCTIONS
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
