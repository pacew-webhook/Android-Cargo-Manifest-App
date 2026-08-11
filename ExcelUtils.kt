package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

object ExcelUtils {

    // Indeks baris header PAG sesuai template asli Excel Anda (0-based)
    private val PAG_ROW_INDEXES = listOf(0, 10, 22, 33, 43, 56, 66, 77)

    fun writeCargoListToExcel(context: Context, uri: Uri, cargoList: List<CargoItem>) {
        // 1. Membuka file template asli dari folder assets
        val inputStream: InputStream = context.assets.open("STOWINGAN_PAG_TEMPLATE.xlsx")
        val workbook = inputStream.use { XSSFWorkbook(it) }
        try {
            val sheet = workbook.getSheetAt(0)

            if (cargoList.isNotEmpty()) {
                val groupedByPag = cargoList.groupBy { it.noPag }
                var pagBlockIndex = 0

                for ((noPag, itemsInPag) in groupedByPag) {
                    if (pagBlockIndex >= PAG_ROW_INDEXES.size) break

                    val startPagRowIndex = PAG_ROW_INDEXES[pagBlockIndex]

                    // A. Tulis NO PAG di Kolom B (Index 1)
                    val pagRow = sheet.getRow(startPagRowIndex) ?: sheet.createRow(startPagRowIndex)
                    val pagCell = pagRow.getCell(1) ?: pagRow.createCell(1)
                    pagCell.setCellValue(noPag)

                    // B. Tulis TOTAL LOOT/KG di Kolom E (Index 4)
                    val totalPagKg = itemsInPag.sumOf { item -> item.subTotal.toDoubleOrNull() ?: 0.0 }
                    val totalCell = pagRow.getCell(4) ?: pagRow.createCell(4)
                    totalCell.setCellValue(totalPagKg)

                    // C. Tulis Data Customer
                    // Baris Nama Customer ada di baris ke-3 dari awal blok PAG (Index start + 2)
                    val customerStartRow = startPagRowIndex + 2
                    var currentStartCol = 0 // Customer pertama di Kolom A (Index 0)

                    for (item in itemsInPag) {
                        // 1. Nama Customer
                        val custRow = sheet.getRow(customerStartRow) ?: sheet.createRow(customerStartRow)
                        val custCell = custRow.getCell(currentStartCol) ?: custRow.createCell(currentStartCol)
                        custCell.setCellValue(item.customer)

                        // 2. Isi Angka KG (Maksimal 5 baris per kolom agar tidak menimpa header PAG bawahnya)
                        val kgValues = item.weight.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                        var currentRow = customerStartRow + 1 // Baris pertama tabel (Index start + 3)
                        var colOffset = 0                     // 0 = Kolom Kiri, 1 = Kolom Kanan
                        var rowCountInCol = 0

                        for (kg in kgValues) {
                            val r = sheet.getRow(currentRow) ?: sheet.createRow(currentRow)
                            val targetCol = currentStartCol + colOffset

                            val c = r.getCell(targetCol) ?: r.createCell(targetCol)
                            c.setCellValue(kg)

                            currentRow++
                            rowCountInCol++

                            // Jika sudah terisi 5 baris ke bawah, pindah ke kolom sebelahnya (misal dari A ke B)
                            if (rowCountInCol >= 5) {
                                rowCountInCol = 0
                                colOffset++
                                currentRow = customerStartRow + 1 // Kembali ke baris pertama tabel
                            }
                        }

                        // 3. Customer berikutnya pada PAG yang sama bergeser ke blok sebelah (Kolom G)
                        currentStartCol += 6
                    }

                    pagBlockIndex++
                }
            }

            // Simpan perubahan ke URI target. Jika stream null, lempar error
            // eksplisit agar caller tahu file GAGAL tersimpan (sebelumnya gagal diam-diam).
            val outputStream: OutputStream = context.contentResolver.openOutputStream(uri)
                ?: throw java.io.IOException("Tidak bisa membuka output stream untuk URI tujuan")
            outputStream.use { workbook.write(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            workbook.close()
        }
    }
}
