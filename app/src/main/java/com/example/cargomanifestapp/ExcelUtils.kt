package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

object ExcelUtils {

    // Daftar lokasi awal Baris untuk setiap Block PAG (Row index 0-based)
    // B1 -> index 0, B23 -> index 22, B34 -> index 33, B44 -> index 43, B55 -> index 54, dst.
    private val PAG_ROW_INDEXES = listOf(0, 22, 33, 43, 54, 65, 76)

    fun writeCargoListToExcel(context: Context, uri: Uri, cargoList: List<CargoItem>) {
        try {
            val inputStream: InputStream = context.assets.open("STOWINGAN_PAG_TEMPLATE.xlsx")
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            if (cargoList.isNotEmpty()) {
                // Grouping data berdasarkan NO PAG unik untuk ditempatkan pada blok PAG yang berbeda
                val groupedByPag = cargoList.groupBy { it.noPag }

                var pagBlockIndex = 0

                for ((noPag, itemsInPag) in groupedByPag) {
                    if (pagBlockIndex >= PAG_ROW_INDEXES.size) break

                    val startPagRowIndex = PAG_ROW_INDEXES[pagBlockIndex]

                    // 1. Tulis NO PAG di Kolom B (Index 1) pada baris PAG
                    val pagRow = sheet.getRow(startPagRowIndex) ?: sheet.createRow(startPagRowIndex)
                    val pagCell = pagRow.getCell(1) ?: pagRow.createCell(1)
                    pagCell.setCellValue(noPag)

                    // 2. Tulis TOTAL LOOT PAG di Kolom E (Index 4)
                    val totalPagKg = itemsInPag.sumOf { item -> item.subTotal.toDoubleOrNull() ?: 0.0 }
                    val totalCell = pagRow.getCell(4) ?: pagRow.createCell(4)
                    totalCell.setCellValue(totalPagKg)

                    // 3. Tulis Setiap Customer dalam PAG Ini (Menyamping / Horizontal)
                    // Customer 1: Kolom A (0), Customer 2: Kolom H (7) -> Lompat 2 kolom (A..E = 5 kolom, + 2 lompat = 7)
                    val customerStartRow = startPagRowIndex + 2 // Baris nama Customer (misal A3, A25, A36, A46)
                    var currentStartCol = 0                     // Kolom A

                    for (item in itemsInPag) {
                        // A. Tulis Nama Customer
                        val custRow = sheet.getRow(customerStartRow) ?: sheet.createRow(customerStartRow)
                        val custCell = custRow.getCell(currentStartCol) ?: custRow.createCell(currentStartCol)
                        custCell.setCellValue(item.customer)

                        // B. Tulis Daftar KG
                        val kgValues = item.weight.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                        var currentRow = customerStartRow + 1 // Baris pertama data KG
                        var colOffset = 0                     // 0..4 (5 kolom per baris)

                        for (kg in kgValues) {
                            val r = sheet.getRow(currentRow) ?: sheet.createRow(currentRow)
                            val targetCol = currentStartCol + colOffset

                            val c = r.getCell(targetCol) ?: r.createCell(targetCol)
                            c.setCellValue(kg)

                            colOffset++
                            if (colOffset >= 5) { // Jika sudah 5 kolom, pindah baris di bawahnya
                                colOffset = 0
                                currentRow++
                            }
                        }

                        // C. Pindah ke Customer Berikutnya: Lompat 5 kolom data + 2 kolom kosong = 7 kolom
                        currentStartCol += 7
                    }

                    pagBlockIndex++
                }
            }

            // Simpan file Excel yang telah diperbarui
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
