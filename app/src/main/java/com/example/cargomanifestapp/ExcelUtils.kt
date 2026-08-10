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
                val firstItem = cargoList.first()

                // 1. NO PAG di B1 (Baris Index 0, Kolom Index 1 / B)
                val row1 = sheet.getRow(0) ?: sheet.createRow(0)
                row1.createCell(1).setCellValue(firstItem.noPag)

                // 2. TOTAL LOOT di E1 (Baris Index 0, Kolom Index 4 / E)
                val grandTotalKg = cargoList.sumOf { item ->
                    item.subTotal.toDoubleOrNull() ?: 0.0
                }
                row1.createCell(4).setCellValue(grandTotalKg)

                // 3. Customer di A3 (Baris Index 2, Kolom Index 0 / A)
                val row3 = sheet.getRow(2) ?: sheet.createRow(2)
                row3.createCell(0).setCellValue(firstItem.customer)

                // 4. Input Nilai KG per Sel (Mulai dari A4 -> Row Index 3)
                // Mengumpulkan semua nilai KG individual dari data stowing
                var currentBatchStartRow = 3 // Baris A4

                for (item in cargoList) {
                    // Ambil list angka KG (pisahkan berdasarkan koma)
                    val kgValues = item.weight.split(",").mapNotNull { it.trim().toDoubleOrNull() }

                    var currentRowIndex = currentBatchStartRow
                    var currentColIndex = 0 // 0 = Kolom A, 1 = B, 2 = C, 3 = D, 4 = E

                    for (kg in kgValues) {
                        val row = sheet.getRow(currentRowIndex) ?: sheet.createRow(currentRowIndex)
                        row.createCell(currentColIndex).setCellValue(kg)

                        currentColIndex++

                        // Jika sudah mencapai 5 kolom (A-E / index 4), pindah ke baris di bawahnya
                        if (currentColIndex > 4) {
                            currentColIndex = 0
                            currentRowIndex++
                        }
                    }

                    // Menyiapkan offset baris untuk batch berikutnya jika ada
                    currentBatchStartRow = if (currentColIndex == 0) currentRowIndex else currentRowIndex + 1
                }
            }

            // 5. Simpan perubahan ke file Excel
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
