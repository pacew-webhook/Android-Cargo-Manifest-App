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
        customer: String
    ) {
        viewModelScope.launch {
            val cleanPti = pti.trim().uppercase()
            val existingItem = cargoList.value.find { it.pti.equals(cleanPti, ignoreCase = true) }

            if (existingItem != null) {
                // JIKA PTI SUDAH ADA -> JUMLAHKAN / AKUMULASIKAN DATA PCS DAN SUBTOTAL
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

                // Update item yang sudah ada di database
                val updatedItem = existingItem.copy(
                    awbNo = if (awbNo.isNotBlank()) awbNo.trim().uppercase() else existingItem.awbNo,
                    flightNo = if (flightNo.isNotBlank()) flightNo.trim().uppercase() else existingItem.flightNo,
                    pcsQty = updatedPcs,
                    weight = weight.trim().ifEmpty { existingItem.weight },
                    subTotal = updatedSubTotal,
                    description = if (description.isNotBlank()) description.trim().uppercase() else existingItem.description,
                    customer = if (customer.isNotBlank()) customer.trim().uppercase() else existingItem.customer
                )
                cargoDao.update(updatedItem)
            } else {
                // JIKA PTI BELUM ADA -> BUAT BARIS BARU
                cargoDao.insert(
                    CargoItem(
                        awbNo = awbNo.trim().uppercase(),
                        flightNo = flightNo.trim().uppercase(),
                        pti = cleanPti,
                        pcsQty = pcsQty.trim(),
                        weight = weight.trim(),
                        subTotal = subTotal.trim(),
                        description = description.trim().uppercase(),
                        customer = customer.trim().uppercase()
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
                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                val firstItem = list.first()

                // 1. AWB NO -> Baris 3 Excel, Kolom G (Row Index 2, Cell Index 6)
                val awbRow = sheet.getRow(2) ?: sheet.createRow(2)
                val awbCell = awbRow.getCell(6) ?: awbRow.createCell(6)
                if (firstItem.awbNo.isNotEmpty()) {
                    awbCell.setCellValue(firstItem.awbNo.uppercase())
                }

                // 2. FLIGHT NO -> Baris 9 Excel, Kolom G (Row Index 8, Cell Index 6)
                val flightRow = sheet.getRow(8) ?: sheet.createRow(8)
                val flightCell = flightRow.getCell(6) ?: flightRow.createCell(6)
                if (firstItem.flightNo.isNotEmpty()) {
                    flightCell.setCellValue(": ${firstItem.flightNo.uppercase()}")
                }

                // 3. TABEL DATA BARANG -> Mulai Baris 14 Excel (Row Index 13)
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
                    val pcsVal = item.pcsQty.toDoubleOrNull()
                    val cellPcs = row.getCell(2) ?: row.createCell(2)
                    if (pcsVal != null) {
                        cellPcs.setCellValue(pcsVal)
                    } else {
                        cellPcs.setCellValue("")
                    }

                    // Kolom D (3): Weight Pcs/Cly Wt (Kosong jika tidak diisi)
                    val weightVal = item.weight.toDoubleOrNull()
                    val cellWeight = row.getCell(3) ?: row.createCell(3)
                    if (weightVal != null) {
                        cellWeight.setCellValue(weightVal)
                    } else {
                        cellWeight.setCellValue("")
                    }

                    // Kolom E (4): Sub Total
                    val subTotalVal = item.subTotal.toDoubleOrNull()
                    val cellSubTotal = row.getCell(4) ?: row.createCell(4)
                    if (subTotalVal != null) {
                        cellSubTotal.setCellValue(subTotalVal)
                    } else {
                        cellSubTotal.setCellValue("")
                    }

                    // Kolom F (5): Description
                    (row.getCell(5) ?: row.createCell(5)).setCellValue(item.description.uppercase())

                    // Kolom G (6): Customer
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
