package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

object ExcelUtils {

    // Lokasi awal Baris untuk setiap Block PAG sesuai Template (0-based)
    // Blok 1: B1 (Index 0), Blok 2: B11 (Index 10), Blok 3: B23 (Index 22), dst.
    private val PAG_ROW_INDEXES = listOf(0, 10, 22, 33, 43, 56, 66, 77)

    fun writeCargoListToExcel(context: Context, uri: Uri, cargoList: List<CargoItem>) {
        try {
            // 1. WAJIB: Buka file template dari folder assets
            val inputStream: InputStream = context.assets.open("STOWINGAN_PAG_TEMPLATE.xlsx")
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            if (cargoList.isNotEmpty()) {
                // Grouping data berdasarkan NO PAG
                val groupedByPag = cargoList.groupBy { it.noPag }

                var pagBlockIndex = 0

                for ((noPag, itemsInPag) in groupedByPag) {
                    if (pagBlockIndex >= PAG_ROW_INDEXES.size) break

                    val startPagRowIndex = PAG_ROW_INDEXES[pagBlockIndex]

                    // A. Tulis NO PAG di Kolom B (Index 1)
                    val pagRow = sheet.getRow(startPagRowIndex) ?: sheet.createRow(startPagRowIndex)
                    val pagCell = pagRow.getCell(1) ?: pagRow.createCell(1)
                    pagCell.setCellValue(noPag)

                    // B. Tulis TOTAL LOOT/KG PAG di Kolom E (Index 4)
                    val totalPagKg = itemsInPag.sumOf { item -> item.subTotal.toDoubleOrNull() ?: 0.0 }
                    val totalCell = pagRow.getCell(4) ?: pagRow.createCell(4)
                    totalCell.setCellValue(totalPagKg)

                    // C. Tulis Customer & Nilai KG mengikuti grid Template
                    // Baris Customer berada 2 baris di bawah header PAG
                    val customerStartRow = startPagRowIndex + 2
                    var currentStartCol = 0 // Mulai dari Kolom A (Index 0)

                    for (item in itemsInPag) {
                        // 1. Tulis Nama Customer
                        val custRow = sheet.getRow(customerStartRow) ?: sheet.createRow(customerStartRow)
                        val custCell = custRow.getCell(currentStartCol) ?: custRow.createCell(currentStartCol)
                        custCell.setCellValue(item.customer)

                        // 2. Tulis Angka KG langsung ke bawah tanpa label "Koli"
                        val kgValues = item.weight.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                        var currentRow = customerStartRow + 1 // Baris di bawah nama customer

                        for (kg in kgValues) {
                            val r = sheet.getRow(currentRow) ?: sheet.createRow(currentRow)
                            val c = r.getCell(currentStartCol) ?: r.createCell(currentStartCol)
                            c.setCellValue(kg)
                            currentRow++
                        }

                        // *** PENTING: BERGESER TEPAT 2 KOLOM UNTUK CUSTOMER SAKELANNYA ***
                        currentStartCol += 2
                    }

                    pagBlockIndex++
                }
            }

            // Simpan file Excel
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
