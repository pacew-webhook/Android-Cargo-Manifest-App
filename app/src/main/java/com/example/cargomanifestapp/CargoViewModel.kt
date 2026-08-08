package com.example.cargomanifestapp

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class CargoViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = CargoDatabase.getDatabase(application).cargoDao()

    val cargoList: StateFlow<List<CargoItem>> = dao.getAllCargo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCargo(
        awbNo: String, flightNo: String, pti: String, pcsQty: String, 
        weight: String, subTotal: String, description: String, customer: String
    ) {
        viewModelScope.launch {
            dao.insertCargo(
                CargoItem(
                    awbNo = awbNo, flightNo = flightNo, pti = pti,
                    pcsQty = pcsQty, weight = weight, subTotal = subTotal,
                    description = description, customer = customer
                )
            )
        }
    }

    fun deleteCargo(cargo: CargoItem) {
        viewModelScope.launch {
            dao.deleteCargo(cargo)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            dao.deleteAll()
        }
    }

    // --- EKSPOR MENGGUNAKAN TEMPLATE EXCEL (.XLSX) ---
    fun exportToExcel(context: Context) {
        val currentList = cargoList.value
        if (currentList.isEmpty()) return

        try {
            // 1. Baca file template dari folder assets
            val inputStream: InputStream = context.assets.open("template_manifest.xlsx")
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0) // Ambil sheet pertama (Manifest)

            // 2. Isi Header (AWB & Flight No dari item pertama jika ada)
            val firstItem = currentList.firstOrNull()
            if (firstItem != null) {
                // Contoh: AWB No di Cell G3, Flight No di Cell D8 (Disesuaikan dengan sel template Anda)
                val rowAWB = sheet.getRow(2) ?: sheet.createRow(2)
                rowAWB.createCell(6).setCellValue(firstItem.awbNo)

                val rowFlight = sheet.getRow(7) ?: sheet.createRow(7)
                rowFlight.createCell(3).setCellValue(firstItem.flightNo)
            }

            // 3. Isi Data Barang Mulai dari Baris Ke-14 (Index 13 di POI)
            var startRow = 13 

            currentList.forEachIndexed { index, item ->
                val row = sheet.getRow(startRow) ?: sheet.createRow(startRow)

                row.createCell(0).setCellValue((index + 1).toDouble()) // No
                row.createCell(1).setCellValue(item.pti)               // PTI
                row.createCell(2).setCellValue(item.pcsQty.toDoubleOrNull() ?: 0.0) // Pcs/Qty
                row.createCell(3).setCellValue(item.weight.toDoubleOrNull() ?: 0.0) // Weight
                row.createCell(4).setCellValue(item.subTotal.toDoubleOrNull() ?: 0.0) // Sub Total
                row.createCell(5).setCellValue(item.description)      // Description
                row.createCell(6).setCellValue(item.customer)         // Customer

                startRow++
            }

            inputStream.close()

            // 4. Simpan hasil pengisian ke file sementara (.xlsx)
            val fileName = "MANIFEST_CARGO_${System.currentTimeMillis()}.xlsx"
            val outputFile = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(outputFile)
            workbook.write(outputStream)
            workbook.close()
            outputStream.close()

            // 5. Bagikan File Excel Hasil Olahan
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                outputFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Buka / Bagikan Manifest Cargo")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
