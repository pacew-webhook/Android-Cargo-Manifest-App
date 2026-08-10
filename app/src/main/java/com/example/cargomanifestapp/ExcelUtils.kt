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
                    var currentStartCol = 0                     // Mulai Kolom A

                    for (item in itemsInPag) {
                        // A. Tulis Nama Customer
                        val custRow = sheet.getRow(customerStartRow) ?: sheet.createRow(customerStartRow)
                        val custCell = custRow.getCell(currentStartCol) ?: custRow.createCell(currentStartCol)
                        custCell.setCellValue(item.customer)

                        // B. Tulis Daftar KG bergeser ke kanan, setiap 5 data pindah ke bawah
                        val kgValues = item.weight.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                        var currentRow = customerStartRow + 1 // Baris pertama data KG
                        var colOffset = 0                     // Offset kolom (0 sampai 4)

                        for (kg in kgValues) {
                            val r = sheet.getRow(currentRow) ?: sheet.createRow(currentRow)
                            val targetCol = currentStartCol + colOffset

                            val c = r.getCell(targetCol) ?: r.createCell(targetCol)
                            c.setCellValue(kg)

                            colOffset++
                            // Jika sudah 5 kolom (0..4), reset ke 0 dan turun 1 baris ke bawah
                            if (colOffset >= 5) {
                                colOffset = 0
                                currentRow++
                            }
                        }

                        // C. Customer berikutnya melompat 7 kolom (5 kolom grid + 2 kolom sela)
                        currentStartCol += 7
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
