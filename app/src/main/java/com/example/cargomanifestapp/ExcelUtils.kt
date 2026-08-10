package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

object ExcelUtils {

    fun writeCargoListToExcel(context: Context, uri: Uri, cargoList: List<CargoItem>) {
        try {
            val inputStream: InputStream = context.assets.open("STOWINGAN_PAG_TEMPLATE.xlsx")
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            if (cargoList.isNotEmpty()) {
                // 1. Set NO PAG Header di Sel B1 (Row index 0, Column index 1)
                val headerRow1 = sheet.getRow(0) ?: sheet.createRow(0)
                headerRow1.createCell(1).setCellValue(cargoList.first().noPag)

                // 2. Hitung Grand Total KG dari seluruh CargoItem untuk Sel E1
                val totalLoot = cargoList.sumOf { item ->
                    item.subTotal.toDoubleOrNull() ?: 0.0
                }
                headerRow1.createCell(4).setCellValue(totalLoot)
            }

            // 3. Tulis Data Cargo Item mulai dari Baris A5 (Row index 4)
            var startRow = 4

            for ((index, item) in cargoList.withIndex()) {
                val row = sheet.getRow(startRow) ?: sheet.createRow(startRow)

                row.createCell(0).setCellValue((index + 1).toDouble())                 // Kolom A: No
                row.createCell(1).setCellValue(item.noPag)                             // Kolom B: NO PAG
                row.createCell(2).setCellValue(item.customer)                          // Kolom C: Customer
                row.createCell(3).setCellValue(item.weight)                            // Kolom D: Rincian KG
                row.createCell(4).setCellValue(item.subTotal.toDoubleOrNull() ?: 0.0)  // Kolom E: Total KG

                startRow++
            }

            // 4. Simpan ke file Output Excel
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                workbook.write(outputStream)
                outputStream.close()
                workbook.close()
                inputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
