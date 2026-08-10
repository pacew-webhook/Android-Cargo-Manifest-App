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

    // ==========================================
    // TAMBAH DATA (OTOMATIS MERGE JIKA SAMA)
    // ==========================================
    fun addCargo(item: CargoItem) {
        val currentList = _cargoList.value.toMutableList()

        // Cari apakah sudah ada data dengan Description, Customer, dan PTI yang sama
        val existingIndex = currentList.indexOfFirst {
            it.description.equals(item.description, ignoreCase = true) &&
            it.customer.equals(item.customer, ignoreCase = true) &&
            it.pti.equals(item.pti, ignoreCase = true)
        }

        if (existingIndex != -1) {
            // JIKA SAMA: GABUNGKAN DATA
            val existing = currentList[existingIndex]
            val newPcs = parseDoubleOrZero(existing.pcsQty) + parseDoubleOrZero(item.pcsQty)
            val newSubTotal = parseDoubleOrZero(existing.subTotal) + parseDoubleOrZero(item.subTotal)

            val updatedItem = existing.copy(
                pcsQty = if (newPcs % 1.0 == 0.0) newPcs.toInt().toString() else newPcs.toString(),
                subTotal = if (newSubTotal % 1.0 == 0.0) newSubTotal.toInt().toString() else newSubTotal.toString(),
                noPag = if (item.noPag.isNotBlank()) item.noPag else existing.noPag
            )
            currentList[existingIndex] = updatedItem
        } else {
            // JIKA BEDA: TAMBAH BARIS BARU
            currentList.add(item)
        }
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
    // IMPORT DATA DARI EXCEL (DENGAN FILTER & MERGE)
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

                    val noCol = getCellString(row, 0, evaluator)
                    val pti = getCellString(row, 1, evaluator)
                    val pcsQty = getCellString(row, 2, evaluator)
                    val pcsWeight = getCellString(row, 3, evaluator)
                    val subTotal = getCellString(row, 4, evaluator)
                    val description = getCellString(row, 5, evaluator)
                    val customer = getCellString(row, 6, evaluator)
                    val noPag = getCellString(row, 8, evaluator)

                    // ABANGKAN BARIS TOTAL ATAU TANDA TANGAN DARI TEMPLATE
                    if (noCol.contains("TOTAL", ignoreCase = true) ||
                        pti.contains("TOTAL", ignoreCase = true) ||
                        description.contains("TOTAL", ignoreCase = true) ||
                        description.contains("Prepared", ignoreCase = true) ||
                        customer.contains("Approved", ignoreCase = true)) {
                        break
                    }

                    if (pti.isBlank() && description.isBlank() && noPag.isBlank() && pcsQty.isBlank()) {
                        continue
                    }

                    val newItem = CargoItem(
                        id = System.currentTimeMillis() + i,
                        pti = pti,
                        pcsQty = pcsQty,
                        weight = pcsWeight,
                        subTotal = if (subTotal.isNotBlank()) subTotal else pcsWeight,
                        description = description,
                        customer = customer,
                        noPag = noPag
                    )

                    // MERGE JIKA SUDAH ADA DATA YANG SAMA DARI HASIL IMPORT
                    val existingIndex = importedList.indexOfFirst {
                        it.description.equals(newItem.description, ignoreCase = true) &&
                        it.customer.equals(newItem.customer, ignoreCase = true) &&
                        it.pti.equals(newItem.pti, ignoreCase = true)
                    }

                    if (existingIndex != -1) {
                        val existing = importedList[existingIndex]
                        val newPcs = parseDoubleOrZero(existing.pcsQty) + parseDoubleOrZero(newItem.pcsQty)
                        val newSub = parseDoubleOrZero(existing.subTotal) + parseDoubleOrZero(newItem.subTotal)

                        importedList[existingIndex] = existing.copy(
                            pcsQty = if (newPcs % 1.0 == 0.0) newPcs.toInt().toString() else newPcs.toString(),
                            subTotal = if (newSub % 1.0 == 0.0) newSub.toInt().toString() else newSub.toString(),
                            noPag = if (newItem.noPag.isNotBlank()) newItem.noPag else existing.noPag
                        )
                    } else {
                        importedList.add(newItem)
                    }
                }
                workbook.close()

                withContext(Dispatchers.Main) {
                    _cargoList.value = importedList
                    Toast.makeText(context, "Berhasil import ${importedList.size} data terkelompok!", Toast.LENGTH_SHORT).show()
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
    // EXPORT DATA KE EXCEL (LAYOUT KUNCI/FIXED)
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

                val startRow = 13 // Indeks 13 = Row 14 Excel
                val maxRows = 25  // Slot maksimum per lembar manifest (Row 14 - 38)

                // 1. Header Flight
                sheet.getRow(2)?.getCell(6)?.setCellValue(awbNo)     // Row 3 Col G
                sheet.getRow(8)?.getCell(6)?.setCellValue(flightNo)  // Row 9 Col G

                // 2. ISI TABEL MANIFEST (KIRI: KOLOM A - G)
                for (i in 0 until maxRows) {
                    val targetRowIdx = startRow + i
                    val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)

                    if (i < currentList.size) {
                        val item = currentList[i]
                        val pcs = parseDoubleOrZero(item.pcsQty)
                        val wt = parseDoubleOrZero(item.weight)
                        val subTotalVal = if (item.subTotal.isNotBlank()) parseDoubleOrZero(item.subTotal) else (pcs * wt)

                        setNumericCell(row, 0, (i + 1).toDouble())                      // Col A: No
                        setTextCell(row, 1, item.pti)                                    // Col B: PTI
                        setNumericCell(row, 2, pcs)                                      // Col C: Pcs/Cly
                        if (wt > 0) setNumericCell(row, 3, wt) else setTextCell(row, 3, "") // Col D: Pcs/Qty Wt
                        if (subTotalVal > 0) setNumericCell(row, 4, subTotalVal) else setTextCell(row, 4, "") // Col E: Sub Total
                        setTextCell(row, 5, item.description)                            // Col F: Description
                        setTextCell(row, 6, item.customer)                               // Col G: Customer
                    } else {
                        // Bersihkan sisa baris kosong manifest
                        setTextCell(row, 0, "")
                        setTextCell(row, 1, "")
                        setTextCell(row, 2, "")
                        setTextCell(row, 3, "")
                        setTextCell(row, 4, "")
                        setTextCell(row, 5, "")
                        setTextCell(row, 6, "")
                    }
                }

                // 3. SET RUMUS TOTAL MANIFEST (KIRI) PADA ROW 39 (INDEKS 38)
                val manifestTotalRow = sheet.getRow(38) ?: sheet.createRow(38)
                setTextCell(manifestTotalRow, 1, "TOTAL WEIGHT")
                setFormulaCell(manifestTotalRow, 2, "SUM(C14:C38)")
                setFormulaCell(manifestTotalRow, 4, "SUM(E14:E38)")

                // 4. KELOMPOKKAN DATA STOWING BERDASARKAN NO PAG (DENGAN SUM NET WEIGHT)
                val stowingGrouped = mutableListOf<CargoItem>()
                val rawStowingList = currentList.filter { it.noPag.isNotBlank() }

                rawStowingList.forEach { item ->
                    val index = stowingGrouped.indexOfFirst { it.noPag.equals(item.noPag, ignoreCase = true) }
                    if (index != -1) {
                        val existing = stowingGrouped[index]
                        val combinedSubTotal = parseDoubleOrZero(existing.subTotal) + parseDoubleOrZero(item.subTotal)
                        stowingGrouped[index] = existing.copy(
                            subTotal = combinedSubTotal.toString()
                        )
                    } else {
                        stowingGrouped.add(item)
                    }
                }

                // 5. ISI TABEL STOWING CHECKLIST (KANAN: KOLOM H - M)
                for (i in 0 until maxRows) {
                    val targetRowIdx = startRow + i
                    val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)

                    if (i < stowingGrouped.size) {
                        val item = stowingGrouped[i]
                        val subTotalVal = parseDoubleOrZero(item.subTotal)

                        setNumericCell(row, 7, (i + 1).toDouble())            // Col H: No
                        setTextCell(row, 8, item.noPag)                        // Col I: NO PAG
                        setTextCell(row, 9, item.description)                  // Col J: Description
                        setNumericCell(row, 10, subTotalVal)                    // Col K: Net Weight
                        setFormulaCell(row, 11, "K${targetRowIdx + 1}+125")    // Col L: Gross Weight (+125)
                        setTextCell(row, 12, item.customer)                    // Col M: Customer
                    } else {
                        // Bersihkan baris kosong stowing
                        setTextCell(row, 7, "")
                        setTextCell(row, 8, "")
                        setTextCell(row, 9, "")
                        setTextCell(row, 10, "")
                        setTextCell(row, 11, "")
                        setTextCell(row, 12, "")
                    }
                }

                // 6. SET RUMUS TOTAL STOWING (KANAN) PADA ROW 39 (INDEKS 38)
                val stowingTotalRow = sheet.getRow(38) ?: sheet.createRow(38)
                setFormulaCell(stowingTotalRow, 10, "SUM(K14:K38)") // Total Net
                setFormulaCell(stowingTotalRow, 11, "SUM(L14:L38)") // Total Gross

                // Evaluasi Semua Rumus Excel
                workbook.creationHelper.createFormulaEvaluator().evaluateAll()

                // Simpan & Buka File
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

    // Helper Function
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
