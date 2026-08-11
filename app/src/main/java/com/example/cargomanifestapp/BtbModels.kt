package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream

// Data class untuk Bukti Timbang Barang
data class BtbFormData(
    val hariTanggal: String = "",
    val customerName: String = "",
    val trademarks: String = "",
    val jenisBarang: String = "",
    val daftarTimbangan: List<Double> = emptyList()
)

// Helper untuk export Excel Bukti Timbang Barang
object BtbExcelWriter {

    /**
     * Mengisi template Bukti Timbang Barang (BTB) yang sudah ada di assets / URI.
     *
     * @param context Context Android
     * @param templateInputStream InputStream dari file template Bukti_Timbang_Barang_BTB.xlsx
     * @param outputUri Uri tujuan penyimpanan file hasil ekspor
     * @param data Data BtbFormData yang akan dimasukkan
     */
    fun fillBtbTemplate(
        context: Context,
        templateInputStream: InputStream,
        outputUri: Uri,
        data: BtbFormData
    ) {
        // 1. Load template workbook yang sudah ada
        val workbook = XSSFWorkbook(templateInputStream)
        val sheet = workbook.getSheetAt(0)

        // Helper safe-write agar sel dibuat jika belum ada
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

        // 2. Isi Header sesuai koordinat template (Indeks 0-based: Row = Baris - 1, Col = A=0, B=1, dst)
        setCellValue(2, 3, data.hariTanggal)  // Sel D3 (Row index 2, Col index 3)
        setCellValue(3, 3, data.customerName) // Sel D4 (Row index 3, Col index 3)
        setCellValue(4, 3, data.trademarks)   // Sel D5 (Row index 4, Col index 3)

        // 3. Isi Jenis Barang pada sel A9
        setCellValue(8, 0, data.jenisBarang)  // Sel A9 (Row index 8, Col index 0)

        // 4. Isi Data Timbangan dari A10 (Row index 9) sampai E23 (Row index 22)
        // Maksimal 14 baris x 5 kolom = 70 data
        val startRow = 9   // Baris 10 (0-based)
        val maxRows = 14   // Baris 10 s/d 23
        val maxCols = 5    // Kolom A s/d E (0..4)

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

        // 5. Update formula TOTAL pada sel E24 jika menggunakan grid A10:E23
        val rowTotal = sheet.getRow(23) ?: sheet.createRow(23)
        val cellTotal = rowTotal.getCell(4) ?: rowTotal.createCell(4)
        cellTotal.cellFormula = "SUM(A10:E23)"

        // Force recalculation saat Excel dibuka
        workbook.setForceFormulaRecalculation(true)

        // 6. Simpan workbook ke Output URI
        context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
            workbook.write(outputStream)
        }

        templateInputStream.close()
        workbook.close()
    }
}
