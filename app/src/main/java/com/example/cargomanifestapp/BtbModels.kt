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
     * Helper multi data (Revisi total di kolom F & format angka tanpa .00)
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

        // Format angka "0.##" agar angka bulat tidak menampilkan .00 (contoh: 12 -> 12, 12.5 -> 12.5)
        val cleanNumericStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("0.##")
        }

        // Helper untuk menulis Teks (String) ke sel secara aman
        fun setCellValue(rowIndex: Int, colIndex: Int, value: String) {
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
            cell.setCellValue(value)
        }

        // Helper untuk menulis Angka dengan format tanpa .00
        fun setCellNumericValue(rowIndex: Int, colIndex: Int, value: Double) {
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
            cell.setCellValue(value)
            cell.cellStyle = cleanNumericStyle
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

            // Input Trademark + Jenis Barang di Kolom A (A9, A14, dst.)
            val labelBarang = if (btb.trademarks.isNotEmpty()) "${btb.trademarks} - ${btb.jenisBarang}" else btb.jenisBarang
            setCellValue(currentRow, 0, labelBarang)

            // Input Grid Data Timbangan tepat di bawah label barang
            var itemRow = currentRow + 1
            val firstDataRow = itemRow // Simpan baris pertama data untuk meletakkan total di Kolom F
            val maxCols = 5 // Kolom A s/d E (Index 0..4)
            var colIndex = 0

            btb.daftarTimbangan.forEach { berat ->
                setCellNumericValue(itemRow, colIndex, berat)
                colIndex++
                if (colIndex >= maxCols) {
                    colIndex = 0
                    itemRow++
                }
            }

            // REVISI: Pindahkan/Tulis total per data ke Kolom F (Col index 5)
            // di baris pertama tempat data timbangan dimasukkan
            setCellNumericValue(firstDataRow, 5, btb.totalBerat)

            // Update posisi currentRow ke baris terakhir yang terisi
            currentRow = if (colIndex > 0) itemRow else itemRow - 1
        }

        // =============================================================
        // 3. UPDATE FORMULA TOTAL KESELURUHAN (Sel E24)
        // =============================================================
        val rowTotal = sheet.getRow(23) ?: sheet.createRow(23)
        val cellTotal = rowTotal.getCell(4) ?: rowTotal.createCell(4)
        cellTotal.cellFormula = "SUM(A10:E23)"
        cellTotal.cellStyle = cleanNumericStyle

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
