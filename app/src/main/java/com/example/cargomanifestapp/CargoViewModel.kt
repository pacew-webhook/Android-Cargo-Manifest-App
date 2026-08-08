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
            val cleanNoPag = noPag.trim().uppercase()

            // DI APLIKASI: Cek HANYA berdasarkan Customer dan Description
            // Jika sama, data digabung/diakumulasi meskipun No PAG nya berbeda
            val existingItem = cargoList.value.find { 
                it.customer.equals(cleanCustomer, ignoreCase = true) && 
                it.description.equals(cleanDescription, ignoreCase = true) &&
                cleanCustomer.isNotEmpty()
            }

            if (existingItem != null) {
                // Akumulasi Pcs dan SubTotal
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

                // Gabungkan string No PAG agar tercatat di database (misal: "PAG 001 MYI, PAG 002 MYI")
                val existingPags = existingItem.noPag.split(",").map { it.trim() }.toMutableSet()
                if (cleanNoPag.isNotEmpty()) {
                    existingPags.add(cleanNoPag)
                }
                val updatedNoPag = existingPags.filter { it.isNotEmpty() }.joinToString(", ")

                val updatedItem = existingItem.copy(
                    awbNo = if (awbNo.isNotBlank()) awbNo.trim().uppercase() else existingItem.awbNo,
                    flightNo = if (flightNo.isNotBlank()) flightNo.trim().uppercase() else existingItem.flightNo,
                    pti = if (pti.isNotBlank()) pti.trim().uppercase() else existingItem.pti,
                    pcsQty = updatedPcs,
                    weight = weight.trim().ifEmpty { existingItem.weight },
                    subTotal = updatedSubTotal,
                    noPag = updatedNoPag
                )
                cargoDao.update(updatedItem)
            } else {
                // Jika belum ada sama sekali, buat data baru
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
                        noPag = cleanNoPag
                    )
                )
            }
        }
    }

    fun updateCargo(cargoItem: CargoItem) {
        viewModelScope.launch { cargoDao.update(cargoItem) }
    }

    fun deleteCargo(cargoItem: CargoItem) {
        viewModelScope.launch { cargoDao.delete(cargoItem) }
    }

    fun clearAll() {
        viewModelScope.launch { cargoDao.deleteAll() }
    }

    fun exportToExcel(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = cargoList.value
            if (list.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Data masih kosong!", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            try {
                val inputStream: InputStream = context.assets.open("template_manifest.xlsx")
                val workbook = XSSFWorkbook(inputStream)
                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                val firstItem = list.first()

                // Header Flight / AWB
                sheet.getRow(2)?.getCell(6)?.setCellValue(firstItem.awbNo.uppercase())
                sheet.getRow(8)?.getCell(6)?.setCellValue(": ${firstItem.flightNo.uppercase()}")

                val startRowIndex = 13

                // --- 1. ISI TABEL MANIFEST (SEBELAH KIRI) ---
                for (i in list.indices) {
                    val item = list[i]
                    val row = sheet.getRow(startRowIndex + i) ?: sheet.createRow(startRowIndex + i)
                    
                    (row.getCell(0) ?: row.createCell(0)).setCellValue((i + 1).toDouble())
                    (row.getCell(1) ?: row.createCell(1)).setCellValue(item.pti.uppercase())
                    (row.getCell(2) ?: row.createCell(2)).setCellValue(item.pcsQty.toDoubleOrNull() ?: 0.0)
                    (row.getCell(3) ?: row.createCell(3)).setCellValue(item.weight.toDoubleOrNull() ?: 0.0)
                    (row.getCell(4) ?: row.createCell(4)).setCellValue(item.subTotal.toDoubleOrNull() ?: 0.0)
                    (row.getCell(5) ?: row.createCell(5)).setCellValue(item.description.uppercase())
                    (row.getCell(6) ?: row.createCell(6)).setCellValue(item.customer.uppercase())
                }

                // --- 2. ISI TABEL STOWING CHECKLIST (SEBELAH KANAN) ---
                // Di sini kita bongkar/pecah kembali No PAG agar di Excel muncul terpisah barisnya
                val expandedStowingList = mutableListOf<Pair<String, CargoItem>>()
                for (item in list) {
                    val pags = item.noPag.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (pags.isEmpty()) {
                        expandedStowingList.add(Pair("", item))
                    } else {
                        for (pag in pags) {
                            expandedStowingList.add(Pair(pag, item))
                        }
                    }
                }

                // Kelompokkan berdasarkan No PAG yang sudah dipecah
                val groupedByPag = expandedStowingList.groupBy { it.first }
                var stowingRowIdx = startRowIndex
                var totalNet = 0.0
                var totalGross = 0.0

                for ((noPag, pairItems) in groupedByPag) {
                    val row = sheet.getRow(stowingRowIdx) ?: sheet.createRow(stowingRowIdx)
                    
                    val itemsInPag = pairItems.map { it.second }
                    val combinedDesc = itemsInPag.joinToString(" + ") { it.description }
                    val combinedCust = itemsInPag.map { it.customer }.distinct().joinToString(" + ")
                    val totalWeightPerPag = itemsInPag.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
                    
                    totalNet += totalWeightPerPag
                    totalGross += totalWeightPerPag

                    (row.getCell(7) ?: row.createCell(7)).setCellValue((stowingRowIdx - startRowIndex + 1).toDouble())
                    (row.getCell(8) ?: row.createCell(8)).setCellValue(noPag.uppercase())
                    (row.getCell(9) ?: row.createCell(9)).setCellValue(combinedDesc.uppercase())
                    (row.getCell(10) ?: row.createCell(10)).setCellValue(totalWeightPerPag) // Net
                    (row.getCell(11) ?: row.createCell(11)).setCellValue(totalWeightPerPag) // Gross
                    (row.getCell(12) ?: row.createCell(12)).setCellValue(combinedCust.uppercase()) // Customer

                    stowingRowIdx++
                }

                // --- 3. ISI TOTAL WEIGHT ---
                val totalRow = sheet.getRow(36) ?: sheet.createRow(36)
                (totalRow.getCell(10) ?: totalRow.createCell(10)).setCellValue(totalNet)
                (totalRow.getCell(11) ?: totalRow.createCell(11)).setCellValue(totalGross)

                val file = File(context.cacheDir, "MANIFEST_CARGO.xlsx")
                workbook.write(FileOutputStream(file))
                workbook.close()

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) { context.startActivity(intent) }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }
}
