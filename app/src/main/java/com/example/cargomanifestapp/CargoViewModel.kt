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
    // 1. TAMBAH DATA / INPUT MANUAL
    // ==========================================
    fun addCargo(item: CargoItem) {
        val currentList = _cargoList.value.toMutableList()

        // Pengecekan ketat: Beda NO PAG = BARIS BARU (Tidak Boleh Di-merge)
        val existingIndex = currentList.indexOfFirst {
            it.description.equals(item.description, ignoreCase = true) &&
            it.customer.equals(item.customer, ignoreCase = true) &&
            it.pti.equals(item.pti, ignoreCase = true) &&
            it.noPag.equals(item.noPag, ignoreCase = true)
        }

        if (existingIndex != -1) {
            val existing = currentList[existingIndex]
            val newPcs = parseDoubleOrZero(existing.pcsQty) + parseDoubleOrZero(item.pcsQty)
            val newSubTotal = parseDoubleOrZero(existing.subTotal) + parseDoubleOrZero(item.subTotal)

            currentList[existingIndex] = existing.copy(
                pcsQty = formatNumber(newPcs),
                subTotal = formatNumber(newSubTotal)
            )
        } else {
            currentList.add(item)
        }
        _cargoList.value = currentList
    }

    fun addCargo(
        awbNo: String = "", flightNo: String = "", pti: String = "",
        pcsQty: String = "", weight: String = "", subTotal: String = "",
        description: String = "", customer: String = "", noPag: String = ""
    ) {
        val newItem = CargoItem(
            id = System.currentTimeMillis(),
            awbNo = awbNo, flightNo = flightNo, pti = pti,
            pcsQty = pcsQty, weight = weight, subTotal = subTotal,
            description = description, customer = customer, noPag = noPag
        )
        addCargo(newItem)
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
    // 2. IMPORT DATA DARI EXCEL
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
                    val pcsWeight = getCellString(row, 3, evaluator)
                    val subTotal = getCellString(row, 4, evaluator)
                    val description = getCellString(row, 5, evaluator)
                    val customer = getCellString(row, 6, evaluator)
                    val noPag = getCellString(row, 8, evaluator)

                    // Stop jika menemukan teks TOTAL atau Footer
                    if (pti.contains("TOTAL", true) || description.contains("TOTAL", true) || description.contains("Prepared", true)) {
                        break
                    }

                    if (pti.isBlank() && description.isBlank() && pcsQty.isBlank()) continue

                    val newItem = CargoItem(
                        id = System.currentTimeMillis() + i + (0..500).random(),
                        pti = pti,
                        pcsQty = pcsQty,
                        weight = pcsWeight,
                        subTotal = if (subTotal.isNotBlank()) subTotal else pcsWeight,
                        description = description,
                        customer = customer,
                        noPag = noPag
                    )

                    // Pengecekan Merge saat import
                    val existingIndex = importedList.indexOfFirst {
                        it.description.equals(newItem.description, true) &&
                        it.customer.equals(newItem.customer, true) &&
                        it.pti.equals(newItem.pti, true) &&
                        it.noPag.equals(newItem.noPag, true)
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
    // 3. EXPORT EXCEL (HITUNG TOTAL MANDIRI)
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

                val startRow = 13 // Baris 14 di Excel
                
                // Header
                sheet.getRow(2)?.getCell(6)?.setCellValue(awbNo)
                sheet.getRow(8)?.getCell(6)?.setCellValue(flightNo)

                // Grouping Stowing (HANYA YANG ADA NO PAG)
                val stowingList = mutableListOf<CargoItem>()
                currentList.filter { it.noPag.isNotBlank() }.forEach { item ->
                    val idx = stowingList.indexOfFirst { it.noPag.equals(item.noPag, true) }
                    if (idx != -1) {
                        val exist = stowingList[idx]
                        val combinedSub = parseDoubleOrZero(exist.subTotal) + parseDoubleOrZero(item.subTotal)
                        stowingList[idx] = exist.copy(subTotal = combinedSub.toString())
                    } else {
                        stowingList.add(item)
                    }
                }

                // Variabel Hitung Total Langsung dari App (Bukan Formula Excel)
                var totalManifestPcs = 0.0
                var totalManifestWeight = 0.0
                var totalStowingNet = 0.0
                var totalStowingGross = 0.0

                val maxRows = maxOf(currentList.size, stowingList.size)

                for (i in 0 until maxRows) {
                    val rowIdx = startRow + i
                    val row = sheet.getRow(rowIdx) ?: sheet.createRow(rowIdx)

                    // ISI MANIFEST (KIRI)
                    if (i < currentList.size) {
                        val item = currentList[i]
                        val pcs = parseDoubleOrZero(item.pcsQty)
                        val subTotal = parseDoubleOrZero(item.subTotal)

                        totalManifestPcs += pcs
                        totalManifestWeight += subTotal

                        setNumericCell(row, 0, (i + 1).toDouble())
                        setTextCell(row, 1, item.pti)
                        setNumericCell(row, 2, pcs)
                        setNumericCell(row, 3, parseDoubleOrZero(item.weight))
                        setNumericCell(row, 4, subTotal)
                        setTextCell(row, 5, item.description)
                        setTextCell(row, 6, item.customer)
                    }

                    // ISI STOWING (KANAN)
                    if (i < stowingList.size) {
                        val stowing = stowingList[i]
                        val net = parseDoubleOrZero(stowing.subTotal)
                        val gross = net + 125.0

                        totalStowingNet += net
                        totalStowingGross += gross

                        setNumericCell(row, 7, (i + 1).toDouble())
                        setTextCell(row, 8, stowing.noPag)
                        setTextCell(row, 9, stowing.description)
                        setNumericCell(row, 10, net)
                        setNumericCell(row, 11, gross)
                        setTextCell(row, 12, stowing.customer)
                    }
                }

                // ISI BARIS TOTAL LANGSUNG DENGAN ANGKA AKURAT (Baris setelah data terakhir atau Baris 38)
                val totalRowIdx = maxOf(startRow + maxRows, 38)
                val totalRow = sheet.getRow(totalRowIdx) ?: sheet.createRow(totalRowIdx)

                setTextCell(totalRow, 1, "TOTAL WEIGHT")
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
}
