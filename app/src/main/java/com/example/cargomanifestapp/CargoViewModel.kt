package com.example.cargomanifestapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class CargoViewModel(private val cargoDao: CargoDao) : ViewModel() {

    val cargoList: StateFlow<List<CargoItem>> = cargoDao.getAllCargo()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Fungsi insert: Semua teks otomatis diubah jadi KAPITAL (.uppercase())
    fun addCargo(
        awbNo: String,
        flightNo: String,
        pti: String,
        pcsQty: String,
        weight: String,
        subTotal: String,
        description: String,
        customer: String
    ) {
        viewModelScope.launch {
            cargoDao.insert(
                CargoItem(
                    awbNo = awbNo.trim().uppercase(),
                    flightNo = flightNo.trim().uppercase(),
                    pti = pti.trim().uppercase(),
                    pcsQty = pcsQty.trim(),
                    weight = weight.trim(),
                    subTotal = subTotal.trim(),
                    description = description.trim().uppercase(),
                    customer = customer.trim().uppercase()
                )
            )
        }
    }

    fun deleteCargo(cargoItem: CargoItem) {
        viewModelScope.launch {
            cargoDao.delete(cargoItem)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            cargoDao.deleteAll()
        }
    }

    fun exportToExcel(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = cargoList.value
            if (list.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Data tabel masih kosong!", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            try {
                val inputStream: InputStream = try {
                    context.assets.open("template_manifest.xlsx")
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "File template_manifest.xlsx tidak ditemukan di assets!", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val workbook = XSSFWorkbook(inputStream)
                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                val firstItem = list.first()

                // 1. AWB NO -> Baris 3, Kolom G (Row Index 2, Cell Index 6)
                val awbRow = sheet.getRow(2) ?: sheet.createRow(2)
                val awbCell = awbRow.getCell(6) ?: awbRow.createCell(6)
                if (firstItem.awbNo.isNotEmpty()) {
                    awbCell.setCellValue(firstItem.awbNo.uppercase())
                }

                // 2. FLIGHT NO -> Baris 10, Kolom G (Row Index 9, Cell Index 6)
                val flightRow = sheet.getRow(9) ?: sheet.createRow(9)
                val flightCell = flightRow.getCell(6) ?: flightRow.createCell(6)
                if (firstItem.flightNo.isNotEmpty()) {
                    flightCell.setCellValue(": ${firstItem.flightNo.uppercase()}")
                }

                // 3. TABEL DATA BARANG -> Mulai Baris 14 (Row Index 13)
                val startRowIndex = 13
                for (i in list.indices) {
                    val item = list[i]
                    val rowIndex = startRowIndex + i
                    val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)

                    // Kolom A (0): No
                    (row.getCell(0) ?: row.createCell(0)).setCellValue((i + 1).toDouble())

                    // Kolom B (1): PTI
                    (row.getCell(1) ?: row.createCell(1)).setCellValue(item.pti.uppercase())

                    // Kolom C (2): Pcs/Cly
                    (row.getCell(2) ?: row.createCell(2)).setCellValue(item.pcsQty.toDoubleOrNull() ?: 0.0)

                    // Kolom D (3): Weight Pcs/Cly Wt
                    (row.getCell(3) ?: row.createCell(3)).setCellValue(item.weight.toDoubleOrNull() ?: 0.0)

                    // Kolom E (4): Sub Total
                    (row.getCell(4) ?: row.createCell(4)).setCellValue(item.subTotal.toDoubleOrNull() ?: 0.0)

                    // Kolom F (5): Description
                    (row.getCell(5) ?: row.createCell(5)).setCellValue(item.description.uppercase())

                    // Kolom G (6): Costumers
                    (row.getCell(6) ?: row.createCell(6)).setCellValue(item.customer.uppercase())
                }

                workbook.setForceFormulaRecalculation(true)
                inputStream.close()

                val file = File(context.cacheDir, "MANIFEST_CARGO.xlsx")
                val outputStream = FileOutputStream(file)
                workbook.write(outputStream)
                outputStream.close()
                workbook.close()

                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        uri,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                withContext(Dispatchers.Main) {
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Export Berhasil! File tersimpan, namun tidak ada aplikasi pembaca Excel.", Toast.LENGTH_LONG).show()
                    }
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
