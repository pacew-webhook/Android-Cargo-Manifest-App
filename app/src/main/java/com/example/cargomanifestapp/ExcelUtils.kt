package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

object ExcelUtils {

    // Daftar lokasi awal Baris untuk setiap Block PAG pada Template (Index 0-based):
    // B1 -> index 0, B11 -> index 10, B23 -> index 22, B34 -> index 33, B44 -> index 43, B57 -> index 56, dst.
    private val PAG_ROW_INDEXES = listOf(0, 10, 22, 33, 43, 56, 66, 77)

    fun writeCargoListToExcel(context: Context, uri: Uri, cargoList: List<CargoItem>) {
        try {
            // 1. Membuka template baku dari folder assets
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

                    // 2. Tulis TOTAL LOOT/KG PAG di Kolom E (Index 4)
                    val totalPagKg = itemsInPag.sumOf { item -> item.subTotal.toDoubleOrNull() ?: 0.0 }
                    val totalCell = pagRow.getCell(4) ?: pagRow.createCell(4)
                    totalCell.setCellValue(totalPagKg)

                    // 3. Tulis Setiap Customer dalam PAG Ini
                    val customerStartRow = startPagRowIndex + 2 // Baris nama Customer (A3, A13, A25, dst)
                    var currentStartCol = 0                     // Mulai dari Kolom A (Index 0)

                    for (item in itemsInPag) {
                        // A. Tulis Nama Customer
                        val custRow = sheet.getRow(customerStartRow) ?: sheet.createRow(customerStartRow)
                        val custCell = custRow.getCell(currentStartCol) ?: custRow.createCell(currentStartCol)
                        custCell.setCellValue(item.customer)

                        // B. Tulis Daftar KG ke bawah secara vertikal
                        val kgValues = item.weight.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                        var currentRow = customerStartRow + 1 // Baris pertama data KG

                        for (kg in kgValues) {
                            val r = sheet.getRow(currentRow) ?: sheet.createRow(currentRow)
                            val c = r.getCell(currentStartCol) ?: r.createCell(currentStartCol)
                            c.setCellValue(kg)
                            currentRow++
                        }

                        // C. Pindah ke Customer Berikutnya: BERGESER TEPAT 2 KOLOM (A -> C -> E -> G)
                        currentStartCol += 2
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
