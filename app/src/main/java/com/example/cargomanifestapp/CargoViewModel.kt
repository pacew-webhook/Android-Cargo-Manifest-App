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

    // --- IMPORT LOGIC REVISI (MENGAMBIL SELURUH DATA TANPA TERPOTONG) ---
    fun importFromExcel(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream?.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                val importedList = mutableListOf<CargoItem>()
                val evaluator = workbook.creationHelper.createFormulaEvaluator()

                // Mulai membaca dari baris indeks 12 (baris ke-13 Excel)
                for (i in 12..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue

                    val pti = getCellString(row, 1, evaluator)
                    val pcsQty = getCellString(row, 2, evaluator)
                    val weight = getCellString(row, 4, evaluator)
                    val description = getCellString(row, 5, evaluator)
                    val customer = getCellString(row, 6, evaluator)
                    val noPag = getCellString(row, 8, evaluator)

                    // Berhenti jika sudah menyentuh teks Total atau Footer
                    if (pti.contains("TOTAL", ignoreCase = true) || description.contains("TOTAL", ignoreCase = true) || description.contains("Prepared by", ignoreCase = true)) {
                        break
                    }

                    // Hanya lewati jika SELURUH kolom penting bernilai kosong
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

    // --- EXPORT LOGIC REVISI (PRESISI SESUAI TEMPLATE MY INDO AIRLINES) ---
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

                val startRow = 12

                // Isi Header AWB dan Flight No
                sheet.getRow(1)?.getCell(7)?.setCellValue(awbNo)
                sheet.getRow(6)?.getCell(7)?.setCellValue(flightNo)

                // 1. TULIS TABEL MANIFEST (KIRI)
                currentList.forEachIndexed { index, item ->
                    val targetRowIdx = startRow + index
                    val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)

                    // Kolom A (0): No
                    (row.getCell(0) ?: row.createCell(0)).setCellValue((index + 1).toDouble())
                    // Kolom B (1): PTI
                    (row.getCell(1) ?: row.createCell(1)).setCellValue(item.pti)
                    // Kolom C (2): Pcs/Cly
                    val pcs = item.pcsQty.toDoubleOrNull()
                    if (pcs != null) {
                        (row.getCell(2) ?: row.createCell(2)).setCellValue(pcs)
                    } else {
                        (row.getCell(2) ?: row.createCell(2)).setCellValue(item.pcsQty)
                    }
                    // Kolom E (4): WEIGHT (Kg)
                    val weight = item.subTotal.toDoubleOrNull() ?: item.weight.toDoubleOrNull()
                    if (weight != null) {
                        (row.getCell(4) ?: row.createCell(4)).setCellValue(weight)
                    } else {
                        (row.getCell(4) ?: row.createCell(4)).setCellValue(item.weight)
                    }
                    // Kolom F (5): DESCRIPTION
                    (row.getCell(5) ?: row.createCell(5)).setCellValue(item.description)
                    // Kolom G (6): COSTUMERS
                    (row.getCell(6) ?: row.createCell(6)).setCellValue(item.customer)
                }

                // 2. TULIS TABEL STOWING (KANAN) - HANYA ITEM YANG MEMILIKI NO PAG
                val stowingList = currentList.filter { it.noPag.isNotBlank() }
                stowingList.forEachIndexed { index, item ->
                    val targetRowIdx = startRow + index
                    val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)

                    // Kolom H (7): No Stowing
                    (row.getCell(7) ?: row.createCell(7)).setCellValue((index + 1).toDouble())
                    // Kolom I (8): NO PAG
                    (row.getCell(8) ?: row.createCell(8)).setCellValue(item.noPag)
                    // Kolom J (9): DESCRIPTION
                    (row.getCell(9) ?: row.createCell(9)).setCellValue(item.description)
                    // Kolom K (10): WEIGHT (Kg)
                    val weight = item.subTotal.toDoubleOrNull() ?: item.weight.toDoubleOrNull()
                    if (weight != null) {
                        (row.getCell(10) ?: row.createCell(10)).setCellValue(weight)
                    } else {
                        (row.getCell(10) ?: row.createCell(10)).setCellValue(item.weight)
                    }
                }

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
}
