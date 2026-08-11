package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream

/**
 * Model data utama untuk Bukti Timbang Barang.
 */
data class BtbFormData(
    val id: String = System.currentTimeMillis().toString(),
    val hariTanggal: String = "",
    val customerName: String = "",
    val trademarks: String = "",
    val jenisBarang: String = "",
    val daftarTimbangan: List<Double> = emptyList()
) {
    val totalBerat: Double get() = daftarTimbangan.sum()
    val jumlahKoli: Int get() = daftarTimbangan.size
}

/**
 * Helper ekspor data ke template Excel (.xlsx).
 */
object BtbExcelWriter {

    fun fillBtbTemplate(
        context: Context,
        templateInputStream: InputStream,
        outputUri: Uri,
        data: BtbFormData
    ) {
        val workbook = XSSFWorkbook(templateInputStream)
        val sheet = workbook.getSheetAt(0)

        fun setCellValue(rowIndex: Int, colIndex: Int, value: String) {
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
            cell.setCellValue(value)
        }

        fun setCellNumericValue(rowIndex: Int, colIndex: Int, value: Double) {
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
            cell.setCellValue(value)
        }

        // 1. Header Transaksi (D3, D4, D5)
        setCellValue(2, 3, data.hariTanggal)  
        setCellValue(3, 3, data.customerName) 
        setCellValue(4, 3, data.trademarks)   

        // 2. Jenis Barang (C8)
        setCellValue(7, 2, data.jenisBarang)

        // 3. Grid Timbangan (A10:E23)
        val startRow = 9   // Baris 10
        val maxRows = 14   // Baris 10 s/d 23
        val maxCols = 5    // Kolom A s/d E

        var itemIndex = 0
        val totalData = data.daftarTimbangan.size

        for (r in 0 until maxRows) {
            val currentRowIndex = startRow + r
            for (c in 0 until maxCols) {
                if (itemIndex < totalData) {
                    setCellNumericValue(currentRowIndex, c, data.daftarTimbangan[itemIndex])
                    itemIndex++
                } else {
                    break
                }
            }
            if (itemIndex >= totalData) break
        }

        // 4. Update Formula Total (Sel E24)
        val rowTotal = sheet.getRow(23) ?: sheet.createRow(23)
        val cellTotal = rowTotal.getCell(4) ?: rowTotal.createCell(4)
        cellTotal.cellFormula = "SUM(A10:E23)"

        workbook.setForceFormulaRecalculation(true)

        // 5. Write ke Output Stream
        context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
            workbook.write(outputStream)
        }

        templateInputStream.close()
        workbook.close()
    }
}
