package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream

/**
 * Model data untuk Bukti Timbang Barang (BTB).
 */
data class BtbFormData(
    val hariTanggal: String = "",
    val customerName: String = "",
    val trademarks: String = "",
    val jenisBarang: String = "",
    val daftarTimbangan: List<Double> = emptyList()
)

/**
 * Helper untuk membaca template Excel Bukti Timbang Barang (.xlsx),
 * memasukkan data ke koordinat sel yang benar, dan menyimpannya.
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

        // Helper untuk menulis Teks (String) ke sel secara aman
        fun setCellValue(rowIndex: Int, colIndex: Int, value: String) {
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
            cell.setCellValue(value)
        }

        // Helper untuk menulis Angka (Numeric/Double) ke sel secara aman
        fun setCellNumericValue(rowIndex: Int, colIndex: Int, value: Double) {
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
            cell.setCellValue(value)
        }

        // =============================================================
        // 1. HEADER TRANSAKSI
        // =============================================================
        setCellValue(2, 3, data.hariTanggal)  // Sel D3 (Row 2, Col 3)
        setCellValue(3, 3, data.customerName) // Sel D4 (Row 3, Col 3)
        setCellValue(4, 3, data.trademarks)   // Sel D5 (Row 4, Col 3)

        // =============================================================
        // 2. JENIS BARANG
        // =============================================================
        // Ditulis di Sel C8 (Row 7, Col 2), di sebelah teks "JENIS BARANG :"
        setCellValue(7, 2, data.jenisBarang)

        // =============================================================
        // 3. DATA TIMBANGAN (A10:E23)
        // =============================================================
        // Baris 10 (Row index 9) s/d Baris 23 (Row index 22)
        // Kolom A s/d E (Col index 0..4) -> Maksimal 70 slot data
        val startRow = 9   // Baris 10
        val maxRows = 14   // 14 baris (Baris 10 - 23)
        val maxCols = 5    // 5 kolom (Kolom A - E)

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

        // =============================================================
        // 4. FORMULA TOTAL (Sel E24)
        // =============================================================
        val rowTotal = sheet.getRow(23) ?: sheet.createRow(23)        // Baris 24 (Row index 23)
        val cellTotal = rowTotal.getCell(4) ?: rowTotal.createCell(4) // Kolom E (Col index 4)
        cellTotal.cellFormula = "SUM(A10:E23)"

        // Memaksa Excel merelakukan perhitungan ulang saat file dibuka
        workbook.setForceFormulaRecalculation(true)

        // =============================================================
        // 5. SIMPAN KE OUTPUT STREAM
        // =============================================================
        context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
            workbook.write(outputStream)
        }

        templateInputStream.close()
        workbook.close()
    }
}
