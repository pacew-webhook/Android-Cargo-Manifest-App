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
    // TAMBAH DATA (MERGE HANYA JIKA PAG JUGA SAMA)
    // ==========================================
    fun addCargo(item: CargoItem) {
        val currentList = _cargoList.value.toMutableList()

        // PENTING: noPag HARUS SAMA agar bisa digabung. Jika noPag beda, JANGAN digabung!
        val existingIndex = currentList.indexOfFirst {
            it.description.equals(item.description, ignoreCase = true) &&
            it.customer.equals(item.customer, ignoreCase = true) &&
            it.pti.equals(item.pti, ignoreCase = true) &&
            it.noPag.equals(item.noPag, ignoreCase = true) // <-- Kunci Perbaikan Stowing
        }

        if (existingIndex != -1) {
            val existing = currentList[existingIndex]
            val newPcs = parseDoubleOrZero(existing.pcsQty) + parseDoubleOrZero(item.pcsQty)
            val newSubTotal = parseDoubleOrZero(existing.subTotal) + parseDoubleOrZero(item.subTotal)

            val updatedItem = existing.copy(
                pcsQty = formatNumber(newPcs),
                subTotal = formatNumber(newSubTotal)
            )
            currentList[existingIndex] = updatedItem
        } else {
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
    // IMPORT DATA LENGKAP DARI EXCEL
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

                    // Stop membaca jika sudah sampai footer total
                    if (noCol.contains("TOTAL", ignoreCase = true) ||
                        pti.contains("TOTAL", ignoreCase = true) ||
                        description.contains("TOTAL", ignoreCase = true) ||
                        description.contains("Prepared", ignoreCase = true)) {
                        break
                    }

                    // Lewati baris kosong
                    if (pti.isBlank() && description.isBlank() && noPag.isBlank() && pcsQty.isBlank()) {
                        continue
                    }

                    val newItem = CargoItem(
                        id = System.currentTimeMillis() + i + (0..1000).random(),
                        pti = pti,
                        pcsQty = pcsQty,
                        weight = pcsWeight,
                        subTotal = if (subTotal.isNotBlank()) subTotal else pcsWeight,
                        description = description,
                        customer = customer,
                        noPag = noPag
                    )

                    // Cek merge berdasarkan kriteria lengkap termasuk noPag
                    val existingIndex = importedList.indexOfFirst {
                        it.description.equals(newItem.description, ignoreCase = true) &&
                        it.customer.equals(newItem.customer, ignoreCase = true) &&
                        it.pti.equals(newItem.pti, ignoreCase = true) &&
                        it.noPag.equals(newItem.noPag, ignoreCase = true)
                    }

                    if (existingIndex != -1) {
                        val existing = importedList[existingIndex]
                        val newPcs = parseDoubleOrZero(existing.pcsQty) + parseDoubleOrZero(newItem.pcsQty)
                        val newSub = parseDoubleOrZero(existing.subTotal) + parseDoubleOrZero(newItem.subTotal)

                        importedList[existingIndex] = existing.copy(
                            pcsQty = formatNumber(newPcs),
                            subTotal = formatNumber(newSub)
                        )
                    } else {
                        importedList.add(newItem)
                    }
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

    // ==========================================
    // EXPORT EXCEL TANPA RUSAK TOTAL & LAYOUT
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
                val dataCount = currentList.size

                // Header Flight
                sheet.getRow(2)?.getCell(6)?.setCellValue(awbNo)
                sheet.getRow(8)?.getCell(6)?.setCellValue(flightNo)

                // 1. Grouping Data untuk Stowing Checklist (HANYA YANG PUNYA NO PAG)
                val stowingGrouped = mutableListOf<CargoItem>()
                currentList.filter { it.noPag.isNotBlank() }.forEach { item ->
                    val index = stowingGrouped.indexOfFirst { it.noPag.equals(item.noPag, ignoreCase = true) }
                    if (index != -1) {
                        val existing = stowingGrouped[index]
                        val combinedSub = parseDoubleOrZero(existing.subTotal) + parseDoubleOrZero(item.subTotal)
                        stowingGrouped[index] = existing.copy(subTotal = combinedSub.toString())
                    } else {
                        stowingGrouped.add(item)
                    }
                }

                val totalRowsToFill = maxOf(dataCount, stowingGrouped.size)

                // 2. Jika jumlah data melebihi slot default (25 baris), sisipkan baris baru agar footer tidak tertimpa
                val defaultCapacity = 25
                if (totalRowsToFill > defaultCapacity) {
                    val extraRowsNeeded = totalRowsToFill - defaultCapacity
                    val shiftStartRow = startRow + defaultCapacity
                    sheet.shiftRows(shiftStartRow, sheet.lastRowNum, extraRowsNeeded, true, true)
                }

                // 3. Tulis Data Ke Sheet
                for (i in 0 until totalRowsToFill) {
                    val targetRowIdx = startRow + i
                    val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)

                    // KIRI: MANIFEST
                    if (i < dataCount) {
                        val item = currentList[i]
                        val pcs = parseDoubleOrZero(item.pcsQty)
                        val wt = parseDoubleOrZero(item.weight)
                        val subTotalVal = if (item.subTotal.isNotBlank()) parseDoubleOrZero(item.subTotal) else (pcs * wt)

                        setNumericCell(row, 0, (i + 1).toDouble())
                        setTextCell(row, 1, item.pti)
                        setNumericCell(row, 2, pcs)
                        if (wt > 0) setNumericCell(row, 3, wt) else setTextCell(row, 3, "")
                        if (subTotalVal > 0) setNumericCell(row, 4, subTotalVal) else setTextCell(row, 4, "")
                        setTextCell(row, 5, item.description)
                        setTextCell(row, 6, item.customer)
                    }

                    // KANAN: STOWING CHECKLIST
                    if (i < stowingGrouped.size) {
                        val stowingItem = stowingGrouped[i]
                        val subTotalVal = parseDoubleOrZero(stowingItem.subTotal)

                        setNumericCell(row, 7, (i + 1).toDouble())
                        setTextCell(row, 8, stowingItem.noPag)
                        setTextCell(row, 9, stowingItem.description)
                        setNumericCell(row, 10, subTotalVal)
                        setFormulaCell(row, 11, "K${targetRowIdx + 1}+125")
                        setTextCell(row, 12, stowingItem.customer)
                    }
                }

                // 4. Update Rumus Total Sesuai Baris Akhir Data
                val lastRowExcelNum = startRow + totalRowsToFill
                val totalRowIdx = if (totalRowsToFill > defaultCapacity) {
                    startRow + totalRowsToFill + 1
                } else {
                    38 // Baris Total Default Template
                }

                val totalRow = sheet.getRow(totalRowIdx) ?: sheet.createRow(totalRowIdx)

                // Manifest Total Formulas
                setFormulaCell(totalRow, 2, "SUM(C14:C$lastRowExcelNum)")
                setFormulaCell(totalRow, 4, "SUM(E14:E$lastRowExcelNum)")

                // Stowing Total Formulas
                setFormulaCell(totalRow, 10, "SUM(K14:K$lastRowExcelNum)")
                setFormulaCell(totalRow, 11, "SUM(L14:L$lastRowExcelNum)")

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

    // Helper functions
    private fun getCellString(row: Row, colIdx: Int, evaluator: FormulaEvaluator): String {
        val cell = row.getCell(colIdx) ?: return ""
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

    private fun setTextCell(row: Row, col: Int, value: String) {
        val cell = row.getCell(col) ?: row.createCell(col)
        cell.setCellValue(value)
    }

    private fun setFormulaCell(row: Row, col: Int, formula: String) {
        val cell = row.getCell(col) ?: row.createCell(col)
        cell.cellFormula = formula
    }
}
