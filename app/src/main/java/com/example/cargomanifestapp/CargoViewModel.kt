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

    fun addCargo(
        awbNo: String,
        flightNo: String,
        pti: String,
        pcsQty: String,
        weight: String,
        subTotal: String,
        description: String,
        customer: String,
        noPag: String // <--- PARAMETER BARU
    ) {
        viewModelScope.launch {
            val cleanCustomer = customer.trim().uppercase()
            val cleanDescription = description.trim().uppercase()

            val existingItem = cargoList.value.find { 
                it.customer.equals(cleanCustomer, ignoreCase = true) && 
                it.description.equals(cleanDescription, ignoreCase = true) &&
                cleanCustomer.isNotEmpty()
            }

            if (existingItem != null) {
                val currentPcs = existingItem.pcsQty.toIntOrNull() ?: 0
                val newPcs = pcsQty.trim().toIntOrNull() ?: 0
                val updatedPcs = (currentPcs + newPcs).toString()

                val currentSubTotal = existingItem.subTotal.toDoubleOrNull() ?: 0.0
                val newSubTotal = subTotal.trim().toDoubleOrNull() ?: 0.0
                val updatedSubTotal = if ((currentSubTotal + newSubTotal) % 1.0 == 0.0) {
                    (currentSubTotal + newSubTotal).toLong().toString()
                } else {
                    (currentSubTotal + newSubTotal).toString()
                }

                val updatedItem = existingItem.copy(
                    awbNo = if (awbNo.isNotBlank()) awbNo.trim().uppercase() else existingItem.awbNo,
                    flightNo = if (flightNo.isNotBlank()) flightNo.trim().uppercase() else existingItem.flightNo,
                    pti = if (pti.isNotBlank()) pti.trim().uppercase() else existingItem.pti,
                    pcsQty = updatedPcs,
                    weight = weight.trim().ifEmpty { existingItem.weight },
                    subTotal = updatedSubTotal,
                    noPag = if (noPag.isNotBlank()) noPag.trim().uppercase() else existingItem.noPag
                )
                cargoDao.update(updatedItem)
            } else {
                cargoDao.insert(
                    CargoItem(
                        awbNo = awbNo.trim().uppercase(),
                        flightNo = flightNo.trim().uppercase(),
                        pti = pti.trim().uppercase(),
                        pcsQty = pcsQty.trim(),
                        weight = weight.trim(),
                        subTotal = subTotal.trim(),
                        description = cleanDescription,
                        customer = cleanCustomer,
                        noPag = noPag.trim().uppercase()
                    )
                )
            }
        }
    }

    fun updateCargo(cargoItem: CargoItem) {
        viewModelScope.launch {
            cargoDao.update(cargoItem)
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
                
                // --- PENGISIAN SHEET MANIFEST ATAU SHEET STOWING CHECKLIST ---
                for (sheetIndex in 0 until workbook.numberOfSheets) {
                    val sheet = workbook.getSheetAt(sheetIndex)
                    val firstItem = list.first()

                    // Header Flight / AWB
                    val awbRow = sheet.getRow(2) ?: sheet.createRow(2)
                    val awbCell = awbRow.getCell(6) ?: awbRow.createCell(6)
                    if (firstItem.awbNo.isNotEmpty()) {
                        awbCell.setCellValue(firstItem.awbNo.uppercase())
                    }

                    val flightRow = sheet.getRow(8) ?: sheet.createRow(8)
                    val flightCell = flightRow.getCell(6) ?: flightRow.createCell(6)
                    if (firstItem.flightNo.isNotEmpty()) {
                        flightCell.setCellValue(": ${firstItem.flightNo.uppercase()}")
                    }

                    // Baris Data Mulai dari index 13 (Baris 14 Excel)
                    val startRowIndex = 13
                    for (i in list.indices) {
                        val item = list[i]
                        val rowIndex = startRowIndex + i
                        val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)

                        // Kolom A (0): No
                        (row.getCell(0) ?: row.createCell(0)).setCellValue((i + 1).toDouble())

                        // PENGISIAN KHUSUS SHEET STOWING CHEKLIST (KOLOM I ATAU B DENGAN INDEX Disesuaikan)
                        // Jika sheet memiliki kolom NO PAG di Kolom B (index 1):
                        val pagCell = row.getCell(1) ?: row.createCell(1)
                        if (sheet.sheetName.contains("STOWING", ignoreCase = true)) {
                            pagCell.setCellValue(item.noPag.uppercase())
                        } else {
                            pagCell.setCellValue(item.pti.uppercase())
                        }

                        // Kolom C (2): Pcs/Cly
                        val pcsVal = item.pcsQty.toDoubleOrNull()
                        val cellPcs = row.getCell(2) ?: row.createCell(2)
                        if (pcsVal != null) cellPcs.setCellValue(pcsVal) else cellPcs.setCellValue("")

                        // Kolom D (3): Weight Pcs
                        val weightVal = item.weight.toDoubleOrNull()
                        val cellWeight = row.getCell(3) ?: row.createCell(3)
                        if (weightVal != null) cellWeight.setCellValue(weightVal) else cellWeight.setCellValue("")

                        // Kolom E (4): Sub Total / Gross
                        val subTotalVal = item.subTotal.toDoubleOrNull()
                        val cellSubTotal = row.getCell(4) ?: row.createCell(4)
                        if (subTotalVal != null) cellSubTotal.setCellValue(subTotalVal) else cellSubTotal.setCellValue("")

                        // Kolom F (5): Description
                        (row.getCell(5) ?: row.createCell(5)).setCellValue(item.description.uppercase())

                        // Kolom G (6): Customer
                        (row.getCell(6) ?: row.createCell(6)).setCellValue(item.customer.uppercase())
                    }
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
                        Toast.makeText(context, "Export Berhasil! File tersimpan.", Toast.LENGTH_LONG).show()
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
