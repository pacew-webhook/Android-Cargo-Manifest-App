package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.BorderStyle
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
    val daftarTimbangan: List<Double> = emptyList(),
    val photoUris: List<String> = emptyList()
) {
    val totalBerat: Double get() = daftarTimbangan.sum()
    val jumlahKoli: Int get() = daftarTimbangan.size
}

/**
 * Helper ekspor data ke template Excel (.xlsx).
 */
object BtbExcelWriter {

    /**
     * Helper single data (tetap dipertahankan)
     */
    fun fillBtbTemplate(
        context: Context,
        templateInputStream: InputStream,
        outputUri: Uri,
        data: BtbFormData
    ) {
        fillBtbTemplateMulti(context, templateInputStream, outputUri, listOf(data))
    }

    /**
     * Helper multi data (Revisi: hilangkan titik & tambahkan border pada data baru)
     */
    fun fillBtbTemplateMulti(
        context: Context,
        templateInputStream: InputStream,
        outputUri: Uri,
        listData: List<BtbFormData>
    ) {
        if (listData.isEmpty()) return

        val workbook = XSSFWorkbook(templateInputStream)
        val sheet = workbook.getSheetAt(0)

        // Style untuk Angka (Format General agar tidak ada titik di akhir angka bulat)
        val generalNumericStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("General")
            borderTop = BorderStyle.THIN
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        // Style untuk Header / Label Barang Baru (dengan Border)
        val headerLabelStyle = workbook.createCellStyle().apply {
            borderTop = BorderStyle.THIN
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        // Helper untuk menulis Teks (String) ke sel secara aman +opsional border
        fun setCellValue(rowIndex: Int, colIndex: Int, value: String, applyBorder: Boolean = false) {
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
            cell.setCellValue(value)
            if (applyBorder) {
                cell.cellStyle = headerLabelStyle
            }
        }

        // Helper untuk menulis Angka tanpa titik di akhir + dengan border
        fun setCellNumericValue(rowIndex: Int, colIndex: Int, value: Double, applyBorder: Boolean = true) {
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
            
            // Masukkan nilai angka murni
            cell.setCellValue(value)
            
            if (applyBorder) {
                cell.cellStyle = generalNumericStyle
            }
        }

        // =============================================================
        // 1. HEADER TRANSAKSI (Sel D3, D4, D5)
        // =============================================================
        val dataUtama = listData.first()
        setCellValue(2, 3, dataUtama.hariTanggal)  // Sel D3 (Hari/Tgl)
        setCellValue(3, 3, dataUtama.customerName) // Sel D4 (Customer)

        // Gabungkan semua Trademarks di Sel D5
        val combinedTrademarks = listData.map { it.trademarks }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
        setCellValue(4, 3, combinedTrademarks)    // Sel D5

        // =============================================================
        // 2. DATA TIMBANGAN & TOTAL PER DATA DI KOLOM F
        // =============================================================
        var currentRow = 8 // Baris 9 di Excel (Index 8 = A9)

        listData.forEachIndexed { index, btb ->
            // Melangkah 4 baris ke bawah untuk data ke-2 dan seterusnya
            if (index > 0) {
                currentRow += 4
            }

            // Input Trademark + Jenis Barang di Kolom A (A9, A14, dst.) dengan Border
            val labelBarang = if (btb.trademarks.isNotEmpty()) "${btb.trademarks} - ${btb.jenisBarang}" else btb.jenisBarang
            setCellValue(currentRow, 0, labelBarang, applyBorder = (index > 0))

            // Beri border untuk sel A s/d E pada baris label barang jika data baru
            if (index > 0) {
                for (c in 1..4) {
                    val row = sheet.getRow(currentRow) ?: sheet.createRow(currentRow)
                    val cell = row.getCell(c) ?: row.createCell(c)
                    cell.cellStyle = headerLabelStyle
                }
            }

            // Input Grid Data Timbangan tepat di bawah label barang
            var itemRow = currentRow + 1
            val firstDataRow = itemRow // Simpan baris pertama untuk meletakkan total di Kolom F
            val maxCols = 5 // Kolom A s/d E (Index 0..4)
            var colIndex = 0

            btb.daftarTimbangan.forEach { berat ->
                setCellNumericValue(itemRow, colIndex, berat, applyBorder = true)
                colIndex++
                if (colIndex >= maxCols) {
                    colIndex = 0
                    itemRow++
                }
            }

            // Pindahkan/Tulis total per data ke Kolom F (Col index 5) tanpa titik & dengan border
            setCellNumericValue(firstDataRow, 5, btb.totalBerat, applyBorder = true)

            // Update posisi currentRow ke baris terakhir yang terisi
            currentRow = if (colIndex > 0) itemRow else itemRow - 1
        }

        // =============================================================
        // 3. UPDATE FORMULA TOTAL KESELURUHAN (Sel E24)
        // =============================================================
        val rowTotal = sheet.getRow(23) ?: sheet.createRow(23)
        val cellTotal = rowTotal.getCell(4) ?: rowTotal.createCell(4)
        cellTotal.cellFormula = "SUM(A10:E23)"
        
        // Memakai format General agar angka total tidak memakai titik di akhir
        val totalStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("General")
        }
        cellTotal.cellStyle = totalStyle

        workbook.setForceFormulaRecalculation(true)

        // =============================================================
        // 4. SIMPAN KE OUTPUT STREAM
        // =============================================================
        try {
            val outputStream = context.contentResolver.openOutputStream(outputUri)
                ?: throw java.io.IOException("Tidak bisa membuka output stream untuk URI tujuan")
            outputStream.use { workbook.write(it) }
        } finally {
            templateInputStream.close()
            workbook.close()
        }
    }
}
