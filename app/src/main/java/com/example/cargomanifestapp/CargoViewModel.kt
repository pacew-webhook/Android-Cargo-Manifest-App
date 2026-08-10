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

    // --- MANAJEMEN DATA ---
    fun addCargo(item: CargoItem) {
        val currentList = _cargoList.value.toMutableList()
        currentList.add(item)
        _cargoList.value = currentList
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

    // --- IMPORT LOGIC (Disesuaikan dengan struktur file Anda) ---
    fun importFromExcel(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream?.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                val importedList = mutableListOf<CargoItem>()
                val evaluator = workbook.creationHelper.createFormulaEvaluator()

                // Mulai dari baris ke-13 (indeks 12)
                for (i in 12..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue
                    
                    // Gunakan kolom 1 (PTI) sebagai validasi utama
                    val pti = getCellString(row, 1, evaluator)
                    if (pti.isEmpty()) continue 

                    importedList.add(CargoItem(
                        id = System.currentTimeMillis() + i,
                        pti = pti,
                        pcsQty = getCellString(row, 2, evaluator),
                        weight = getCellString(row, 4, evaluator), // Kolom E
                        subTotal = getCellString(row, 4, evaluator),
                        description = getCellString(row, 5, evaluator),
                        customer = getCellString(row, 6, evaluator),
                        noPag = getCellString(row, 8, evaluator) // Kolom I
                    ))
                }
                workbook.close()
                withContext(Dispatchers.Main) { 
                    _cargoList.value = importedList
                    Toast.makeText(context, "Berhasil import ${importedList.size} data!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
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

    // --- EXPORT LOGIC (Sangat Rapi & Terpisah) ---
    fun exportToExcel(context: Context, awbNo: String, flightNo: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentList = cargoList.value
                if (currentList.isEmpty()) return@launch

                val groupedManifest = currentList.groupBy { Pair(it.description.trim(), it.customer.trim()) }
                val groupedStowing = currentList.filter { it.noPag.isNotEmpty() }.groupBy { it.noPag.trim() }

                val inputStream = context.assets.open("template_manifest.xlsx")
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                inputStream.close()

                // Bersihkan area data lama (Baris 12 s.d 35)
                for (i in 12..35) {
                    val row = sheet.getRow(i) ?: sheet.createRow(i)
                    for (j in 0..12) { row.getCell(j)?.setCellValue("") }
                }

                // Isi Header
                sheet.getRow(1)?.getCell(7)?.setCellValue(awbNo)
                sheet.getRow(6)?.getCell(7)?.setCellValue(flightNo)

                // 1. Tulis Tabel Manifest (Kiri)
                var rowIdx = 12
                for ((key, items) in groupedManifest) {
                    if (rowIdx > 30) break
                    val row = sheet.getRow(rowIdx) ?: sheet.createRow(rowIdx)
                    row.getCell(0)?.setCellValue((rowIdx - 11).toDouble())
                    row.getCell(5)?.setCellValue(key.first)
                    row.getCell(6)?.setCellValue(key.second)
                    row.getCell(4)?.setCellValue(items.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 })
                    rowIdx++
                }

                // 2. Tulis Tabel Stowing (Kanan) - Kolom I ke atas (Indeks 8)
                var rightRowIdx = 12
                var countRight = 0
                for ((pag, items) in groupedStowing) {
                    if (countRight >= 9) break
                    val row = sheet.getRow(rightRowIdx) ?: sheet.createRow(rightRowIdx)
                    row.getCell(8)?.setCellValue((countRight + 1).toDouble())
                    row.getCell(9)?.setCellValue(pag)
                    row.getCell(10)?.setCellValue(items.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 })
                    rightRowIdx++
                    countRight++
                }

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
