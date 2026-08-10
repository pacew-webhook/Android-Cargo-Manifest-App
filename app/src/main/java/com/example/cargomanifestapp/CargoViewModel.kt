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
                    val noStr = getCellString(row, 0, evaluator)
                    if (noStr.isEmpty()) break

                    importedList.add(
                        CargoItem(
                            id = System.currentTimeMillis() + i,
                            pti = getCellString(row, 1, evaluator),
                            pcsQty = getCellString(row, 2, evaluator),
                            weight = getCellString(row, 3, evaluator),
                            subTotal = getCellString(row, 4, evaluator),
                            description = getCellString(row, 5, evaluator),
                            customer = getCellString(row, 6, evaluator),
                            noPag = getCellString(row, 8, evaluator)
                        )
                    )
                }
                workbook.close()
                withContext(Dispatchers.Main) { _cargoList.value = importedList }
            } catch (e: Exception) {
                e.printStackTrace()
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

    fun exportToExcel(context: Context, awbNo: String, flightNo: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentList = cargoList.value
                if (currentList.isEmpty()) return@launch

                // 1. Grouping Data dengan tipe eksplisit untuk menghindari unresolved reference
                val groupedManifest = currentList.groupBy { item ->
                    Pair(item.description.trim(), item.customer.trim())
                }
                val groupedStowing = currentList.filter { it.noPag.isNotEmpty() }.groupBy { item ->
                    item.noPag.trim()
                }

                // 2. Load Template
                val inputStream = context.assets.open("template_manifest.xlsx")
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                inputStream.close()

                // 3. Bersihkan area data lama (Baris 12 s.d 30)
                for (i in 12..30) {
                    val row = sheet.getRow(i) ?: sheet.createRow(i)
                    for (j in 0..12) {
                        val cell = row.getCell(j) ?: row.createCell(j)
                        cell.setCellValue("")
                    }
                }

                // 4. Isi Header (Menggunakan toDouble() atau explicit casting untuk menghindari ambiguitas POI)
                val row1 = sheet.getRow(1) ?: sheet.createRow(1)
                (row1.getCell(7) ?: row1.createCell(7)).setCellValue(awbNo)

                val row6 = sheet.getRow(6) ?: sheet.createRow(6)
                (row6.getCell(7) ?: row6.createCell(7)).setCellValue(flightNo)

                // 5. Tulis Tabel Manifest (Kiri)
                var rowIdx = 12
                groupedManagerLoop@ for ((key, items) in groupedManifest) {
                    if (rowIdx > 30) break
                    val row = sheet.getRow(rowIdx) ?: sheet.createRow(rowIdx)
                    
                    val noCell = row.getCell(0) ?: row.createCell(0)
                    noCell.setCellValue((rowIdx - 11).toDouble())

                    val descCell = row.getCell(5) ?: row.createCell(5)
                    descCell.setCellValue(key.first)

                    val custCell = row.getCell(6) ?: row.createCell(6)
                    custCell.setCellValue(key.second)

                    val totalVal = items.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
                    val subCell = row.getCell(4) ?: row.createCell(4)
                    subCell.setCellValue(totalVal)

                    rowIdx++
                }

                // 6. Tulis Tabel Stowing (Kanan) - Mulai baris 12 kolom 8 (I)
                var rightRowIdx = 12
                var countRight = 0
                for ((pag, items) in groupedStowing) {
                    if (countRight >= 9) break
                    val row = sheet.getRow(rightRowIdx) ?: sheet.createRow(rightRowIdx)

                    val noRightCell = row.getCell(8) ?: row.createCell(8)
                    noRightCell.setCellValue((countRight + 1).toDouble())

                    val pagCell = row.getCell(9) ?: row.createCell(9)
                    pagCell.setCellValue(pag)

                    val totalPagVal = items.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
                    val weightCell = row.getCell(10) ?: row.createCell(10)
                    weightCell.setCellValue(totalPagVal)

                    rightRowIdx++
                    countRight++
                }

                // 7. Simpan & Intent
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
            }
        }
    }
}
