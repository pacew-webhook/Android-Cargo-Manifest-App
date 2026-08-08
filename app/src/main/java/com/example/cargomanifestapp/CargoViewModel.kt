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
        noPag: String
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
                
                // Ambil sheet "Manifest" secara spesifik atau fallback ke sheet pertama
                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
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

                val startRowIndex = 13 // Baris ke-14

                // --- 1. ISI TABEL MANIFEST (SEBELAH KIRI) ---
                for (i in list.indices) {
                    val item = list[i]
                    val rowIndex = startRowIndex + i
                    val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)

                    // Kolom A (Index 0): No Urut
                    (row.getCell(0) ?: row.createCell(0)).setCellValue((i + 1).toDouble())
                    // Kolom B (Index 1): PTI
                    (row.getCell(1) ?: row.createCell(1)).setCellValue(item.pti.uppercase())
                    // Kolom C (Index 2): Pcs/Qty
                    val pcsVal = item.pcsQty.toDoubleOrNull()
                    (row.getCell(2) ?: row.createCell(2)).setCellValue(pcsVal ?: 0.0)
                    // Kolom D (Index 3): Weight Net
                    val weightVal = item.weight.toDoubleOrNull()
                    (row.getCell(3) ?: row.createCell(3)).setCellValue(weightVal ?: 0.0)
                    // Kolom E (Index 4): Weight SubTotal
                    val subTotalVal = item.subTotal.toDoubleOrNull()
                    (row.getCell(4) ?: row.createCell(4)).setCellValue(subTotalVal ?: 0.0)
                    // Kolom F (Index 5): Description Manifest
                    (row.getCell(5) ?: row.createCell(5)).setCellValue(item.description.uppercase())
                    // Kolom G (Index 6): Costumers
                    (row.getCell(6) ?: row.createCell(6)).setCellValue(item.customer.uppercase())
                }

                // --- 2. ISI TABEL STOWING CHECKLIST (SEBELAH KANAN) DENGAN GROUPING NO PAG ---
                val groupedData = list.groupBy { it.noPag }
                var stowingRowIdx = startRowIndex
                var totalWeightStowing = 0.0

                for ((noPag, items) in groupedData) {
                    val row = sheet.getRow(stowingRowIdx) ?: sheet.createRow(stowingRowIdx)

                    // Gabungkan deskripsi yang memiliki No PAG sama (misal: "PAKET, AYAM HIDUP")
                    val combinedDesc = items.joinToString(", ") { it.description }
                    // Hitung total subtotal berat per No PAG
                    val totalWeightPerPag = items.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
                    totalWeightStowing += totalWeightPerPag

                    // Kolom H (Index 7): No Stowing
                    (row.getCell(7) ?: row.createCell(7)).setCellValue((stowingRowIdx - startRowIndex + 1).toDouble())
                    // Kolom I (Index 8): No PAG
                    (row.getCell(8) ?: row.createCell(8)).setCellValue(noPag.uppercase())
                    // Kolom J (Index 9): Description Stowing (Gabungan)
                    (row.getCell(9) ?: row.createCell(9)).setCellValue(combinedDesc.uppercase())

                    stowingRowIdx++
                }

                // --- 3. ISI TOTAL WEIGHT STOWING ---
                // Berdasarkan template, baris Total Weight Stowing berada di baris ke-37 (Index 36) dan Kolom K (Index 10)
                val totalRow = sheet.getRow(36) ?: sheet.createRow(36)
                val totalCell = totalRow.getCell(10) ?: totalRow.createCell(10)
                totalCell.setCellValue(totalWeightStowing)

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
