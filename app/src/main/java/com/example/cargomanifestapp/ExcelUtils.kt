package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

object ExcelUtils {

    // Lokasi awal Baris untuk setiap Block PAG sesuai Template
    private val PAG_ROW_INDEXES = listOf(0, 10, 22, 33, 43, 56, 66, 77)

    fun writeCargoListToExcel(context: Context, uri: Uri, cargoList: List<CargoItem>) {
        try {
            val inputStream: InputStream = context.assets.open("STOWINGAN_PAG_TEMPLATE.xlsx")
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            if (cargoList.isNotEmpty()) {
                val groupedByPag = cargoList.groupBy { it.noPag }

                var pagBlockIndex = 0

                for ((noPag, itemsInPag) in groupedByPag) {
                    if (pagBlockIndex >= PAG_ROW_INDEXES.size) break

                    val startPagRowIndex = PAG_ROW_INDEXES[pagBlockIndex]

                    // 1. Tulis NO PAG di Kolom B (Index 1)
                    val pagRow = sheet.getRow(startPagRowIndex) ?: sheet.createRow(startPagRowIndex)
                    val pagCell = pagRow.getCell(1) ?: pagRow.createCell(1)
                    pagCell.setCellValue(noPag)

                    // 2. Tulis TOTAL LOOT PAG di Kolom E (Index 4)
                    val totalPagKg = itemsInPag.sumOf { item -> item.subTotal.toDoubleOrNull() ?: 0.0 }
                    val totalCell = pagRow.getCell(4) ?: pagRow.createCell(4)
                    totalCell.setCellValue(totalPagKg)

                    // 3. Tulis Customer & Grid KG
                    val customerStartRow = startPagRowIndex + 2 // Baris nama Customer
                    var currentStartCol = 0                     // Mulai dari Kolom A (Index 0)

                    for (item in itemsInPag) {
                        // A. Tulis Nama Customer
                        val custRow = sheet.getRow(customerStartRow) ?: sheet.createRow(customerStartRow)
                        val custCell = custRow.getCell(currentStartCol) ?: custRow.createCell(currentStartCol)
                        custCell.setCellValue(item.customer)

                        // B. Tulis Daftar KG (2 Kolom ke samping: Kolom 0 & Kolom 1 dari posisi customer)
                        val kgValues = item.weight.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                        var currentRow = customerStartRow + 1 // Baris pertama data KG
                        var colOffset = 0                     // Offset 0 dan 1 (2 kolom ke samping)

                        for (kg in kgValues) {
                            val r = sheet.getRow(currentRow) ?: sheet.createRow(currentRow)
                            val targetCol = currentStartCol + colOffset

                            val c = r.getCell(targetCol) ?: r.createCell(targetCol)
                            c.setCellValue(kg)

                            colOffset++
                            // Jika sudah terisi 2 kolom ke samping, pindah ke baris di bawahnya
                            if (colOffset >= 2) {
                                colOffset = 0
                                currentRow++
                            }
                        }

                        // C. Customer berikutnya hanya bergeser 2 kolom (Kolom A-B -> Kolom C-D -> Kolom E-F)
                        // Ubah ke += 3 jika ingin memberikan jarak 1 kolom kosong antar customer
                        currentStartCol += 2 
                    }

                    pagBlockIndex++
                }
            }

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
