package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.Font
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object ExcelUtils {

    fun writeCargoListToExcel(context: Context, uri: Uri, cargoList: List<CargoItem>) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Stowing Report")

            // Styles
            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.VIOLET.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                setFont(workbook.createFont().apply {
                    bold = true
                    color = IndexedColors.WHITE.index
                })
            }

            val boldStyle = workbook.createCellStyle().apply {
                setFont(workbook.createFont().apply { bold = true })
            }

            // Grouping data berdasarkan NO PAG
            val groupedCargo = cargoList.groupBy { it.noPag }

            var currentRow = 1

            groupedCargo.forEach { (noPag, itemsInPag) ->
                // Baris Header PAG
                val pagRow = sheet.getRow(currentRow) ?: sheet.createRow(currentRow)
                
                val cellLabel = pagRow.createCell(0)
                cellLabel.setCellValue("NO PAG:")
                cellLabel.cellStyle = boldStyle

                val cellPagValue = pagRow.createCell(1)
                cellPagValue.setCellValue(noPag)
                cellPagValue.cellStyle = headerStyle

                currentRow += 2

                // Kita mulai entry customer dari Kolom 0 (A), lalu setiap customer baru BERGESER 2 KOLOM
                var currentCol = 0

                itemsInPag.forEach { item ->
                    // 1. Tulis Nama Customer & Koli/Subtotal
                    val custRow = sheet.getRow(currentRow) ?: sheet.createRow(currentRow)
                    
                    val cellCust = custRow.createCell(currentCol)
                    cellCust.setCellValue(item.customer)
                    cellCust.cellStyle = boldStyle

                    val cellInfo = custRow.createCell(currentCol + 1)
                    cellInfo.setCellValue("${item.pcsQty} Koli (${item.subTotal} KG)")
                    cellInfo.cellStyle = boldStyle

                    // 2. Tulis Rincian KG ke bawah di bawah customer masing-masing
                    val parsedKg = item.weight.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                    
                    parsedKg.forEachIndexed { index, kg ->
                        val kgRowIndex = currentRow + 1 + index
                        val kgRow = sheet.getRow(kgRowIndex) ?: sheet.createRow(kgRowIndex)
                        
                        val cellKgLabel = kgRow.createCell(currentCol)
                        cellKgLabel.setCellValue("Koli ${index + 1}")
                        
                        val cellKgValue = kgRow.createCell(currentCol + 1)
                        cellKgValue.setCellValue(kg)
                    }

                    // *** PENTING: BERGESER TEPAT 2 KOLOM UNTUK CUSTOMER/ENTRY BERIKUTNYA ***
                    currentCol += 2
                }

                // Hitung baris tertinggi yang terpakai di group ini sebelum lanjut ke PAG berikutnya
                val maxKgCount = itemsInPag.maxOfOrNull { 
                    it.weight.split(",").size 
                } ?: 0

                currentRow += (maxKgCount + 3) // Tambahkan jarak antar PAG
            }

            workbook.write(outputStream)
            workbook.close()
        }
    }
}
