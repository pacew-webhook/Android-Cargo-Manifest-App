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

        // Helper untuk menulis Teks (String) ke sel secara aman
        fun setCellValue(rowIndex: Int, colIndex: Int, value: String) {
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
            cell.setCellValue(value)
        }

        // Helper untuk menulis Angka (Numeric/Double) ke sel dengan format General
        fun setCellNumericValue(rowIndex: Int, colIndex: Int, value: Double) {
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
            cell.setCellValue(value)

            // Format sel menjadi 'General' agar angka bulat tidak menampilkan .00
            val style = workbook.createCellStyle()
            if (cell.cellStyle != null) {
                style.cloneStyleFrom(cell.cellStyle)
            }
            style.dataFormat = workbook.createDataFormat().getFormat("General")
            cell.cellStyle = style
        }

        // =============================================================
        // 1. HEADER TRANSAKSI (Sel D3, D4, D5)
        // =============================================================
        setCellValue(2, 3, data.hariTanggal)  // Sel D3 (Row 2, Col 3)
        setCellValue(3, 3, data.customerName) // Sel D4 (Row 3, Col 3)
        setCellValue(4, 3, data.trademarks)   // Sel D5 (Row 4, Col 3)

        // =============================================================
        // 2. JENIS BARANG (Di Bawah Label -> Sel A9 / Index Row 8, Col 0)
        // =============================================================
        setCellValue(8, 0, data.jenisBarang)  // Row 8 (Baris 9 di Excel), Col 0 (Kolom A)

        // =============================================================
        // 3. DATA TIMBANGAN (Grid A10:E23 - Maksimal 70 item)
        // =============================================================
        val startRow = 9   // Baris 10 di Excel (Index 9)
        val maxRows = 14   // Baris 10 s/d 23
        val maxCols = 5    // Kolom A s/d E (Index 0..4)

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
        // 4. UPDATE FORMULA TOTAL (Sel E24)
        // =============================================================
        val rowTotal = sheet.getRow(23) ?: sheet.createRow(23)        // Baris 24 (Row index 23)
        val cellTotal = rowTotal.getCell(4) ?: rowTotal.createCell(4) // Kolom E (Col index 4)
        cellTotal.cellFormula = "SUM(A10:E23)"

        // Memaksa Excel melakukan perhitungan ulang saat file dibuka
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
