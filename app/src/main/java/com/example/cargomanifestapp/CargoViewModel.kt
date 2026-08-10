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

    // ... (Fungsi addCargo, updateCargo, deleteCargo, clearAll tetap sama) ...

    fun importFromExcel(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream?.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                val importedList = mutableListOf<CargoItem>()
                val evaluator = workbook.creationHelper.createFormulaEvaluator()

                // Membaca baris data mulai baris ke-13 (indeks 12)
                for (i in 12..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue
                    
                    // Validasi: Berhenti jika nomor urut kosong
                    val noStr = getCellString(row, 0, evaluator)
                    if (noStr.isEmpty()) break 

                    importedList.add(CargoItem(
                        id = System.currentTimeMillis() + i,
                        pti = getCellString(row, 1, evaluator),
                        pcsQty = getCellString(row, 2, evaluator),
                        weight = getCellString(row, 3, evaluator),
                        subTotal = getCellString(row, 4, evaluator),
                        description = getCellString(row, 5, evaluator),
                        customer = getCellString(row, 6, evaluator),
                        noPag = getCellString(row, 8, evaluator)
                    ))
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

                // 1. Grouping Data
                val groupedManifest = currentList.groupBy { Pair(it.description, it.customer) }
                val groupedStowing = currentList.filter { it.noPag.isNotEmpty() }.groupBy { it.noPag }

                // 2. Load Template
                val inputStream = context.assets.open("template_manifest.xlsx")
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheet("Manifest")
                inputStream.close()

                // 3. PENTING: Bersihkan area data lama (Baris 12 s.d 30) agar rapi
                for (i in 12..30) {
                    val row = sheet.getRow(i) ?: sheet.createRow(i)
                    for (j in 0..12) { row.createCell(j).setCellValue("") }
                }

                // 4. Isi Header
                sheet.getRow(1)?.getCell(7)?.setCellValue(awbNo)
                sheet.getRow(6)?.getCell(7)?.setCellValue(flightNo)

                // 5. Tulis Tabel Manifest (Kiri)
                var rowIdx = 12
                groupedManifest.forEachIndexed { index, (key, items) ->
                    if (rowIdx > 30) return@forEachIndexed
                    val row = sheet.getRow(rowIdx) ?: sheet.createRow(rowIdx)
                    row.getCell(0)?.setCellValue((index + 1).toDouble())
                    row.getCell(5)?.setCellValue(key.first)
                    row.getCell(6)?.setCellValue(key.second)
                    row.getCell(4)?.setCellValue(items.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 })
                    rowIdx++
                }

                // 6. Tulis Tabel Stowing (Kanan) - Mulai baris 12 kolom 8 (I)
                rowIdx = 12
                groupedStowing.forEachIndexed { index, (pag, items) ->
                    if (index >= 9) return@forEachIndexed // Maksimal 9 baris untuk PAG
                    val row = sheet.getRow(rowIdx) ?: sheet.createRow(rowIdx)
                    row.getCell(8)?.setCellValue((index + 1).toDouble())
                    row.getCell(9)?.setCellValue(pag)
                    row.getCell(10)?.setCellValue(items.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 })
                    rowIdx++
                }

                // 7. Simpan & Intent
                val file = File(context.cacheDir, "Manifest_Cargo_Output.xlsx")
                workbook.write(FileOutputStream(file))
                workbook.close()

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
