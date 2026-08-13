package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import kotlin.math.floor

/**
 * Model data utama untuk Bukti Timbang Barang.
 * daftarTimbangan menyimpan HASIL SCAN/asli. Nilai pembulatan dihitung
 * deterministik saat dibutuhkan dan tidak mengubah nilai asli.
 */
data class BtbFormData(
    val id: String = System.currentTimeMillis().toString(),
    val hariTanggal: String = "",
    val customerName: String = "",
    val trademarks: String = "",
    val jenisBarang: String = "",
    val photoUris: List<String> = emptyList(),
    val daftarTimbangan: List<Double> = emptyList()
) {
    val totalBerat: Double get() = daftarTimbangan.sum()
    val pembulatanTimbangan: List<Double> get() = daftarTimbangan.map { roundWeight(it) }
    val totalBeratPembulatan: Double get() = pembulatanTimbangan.sum()
    val jumlahKoli: Int get() = daftarTimbangan.size
}

/** Aturan BTB: pecahan < .50 turun, >= .50 naik ke kilogram berikutnya. */
fun roundWeight(weight: Double): Double = floor(weight + 0.5)

object BtbExcelWriter {

    /**
     * Mengisi template BTB tanpa mengubah desain/template dasar.
     * Struktur data FIX6:
     *   A = HASIL SCAN
     *   B = PEMBULATAN
     *   TOTAL = jumlah kolom B
     * Tidak ada kolom No dan tidak ada Berat Final.
     * Baris data diperpanjang otomatis sebelum TOTAL bila diperlukan.
     */
    fun fillBtbWorkbook(
        context: Context,
        outputUri: Uri,
        dataList: List<BtbFormData>
    ) {
        require(dataList.isNotEmpty()) { "Data BTB kosong" }
        val templateInputStream = context.assets.open("Bukti_Timbang_Barang_BTB.xlsx")
        val workbook = XSSFWorkbook(templateInputStream)
        try {
            val sheets = mutableListOf<org.apache.poi.ss.usermodel.Sheet>()
            sheets += workbook.getSheetAt(0)
            repeat(dataList.size - 1) { sheets += workbook.cloneSheet(0) }

            dataList.forEachIndexed { index, data ->
                val sheet = sheets[index]
                val baseName = "BTB ${index + 1} ${data.customerName.ifBlank { "DATA" }}"
                workbook.setSheetName(workbook.getSheetIndex(sheet), safeSheetName(workbook, baseName))
                prepareAndFillSheet(workbook, sheet, data)
            }
            workbook.setForceFormulaRecalculation(true)
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                workbook.write(outputStream)
            } ?: error("Tidak dapat membuka file output BTB")
        } finally {
            workbook.close()
            templateInputStream.close()
        }
    }

    private fun safeSheetName(workbook: XSSFWorkbook, requested: String): String {
        val base = requested.replace(Regex("[\\/?*\[\]:]"), "_").take(31).ifBlank { "BTB" }
        var name = base
        var n = 2
        while (workbook.getSheet(name) != null) {
            val suffix = "-$n"
            name = base.take(31 - suffix.length) + suffix
            n++
        }
        return name
    }

    fun fillBtbTemplate(
        context: Context,
        templateInputStream: InputStream,
        outputUri: Uri,
        data: BtbFormData
    ) {
        val workbook = XSSFWorkbook(templateInputStream)
        try {
            val sheet = workbook.getSheetAt(0)
            prepareAndFillSheet(workbook, sheet, data)
            workbook.setForceFormulaRecalculation(true)

            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                workbook.write(outputStream)
            } ?: error("Tidak dapat membuka file output BTB")
        } finally {
            workbook.close()
            templateInputStream.close()
        }
    }

    private fun prepareAndFillSheet(
        workbook: XSSFWorkbook,
        sheet: Sheet,
        data: BtbFormData
    ) {
        val headerRowIndex = 9       // Excel 10
        val firstDataRowIndex = 10  // Excel 11
        val initialTotalRowIndex = findTotalRow(sheet, headerRowIndex + 1)
            ?: 23                    // Excel 24 fallback

        val dataCount = data.daftarTimbangan.size
        require(dataCount > 0) { "Data timbangan BTB kosong" }

        val existingDataRows = (initialTotalRowIndex - firstDataRowIndex).coerceAtLeast(1)
        val extraRows = (dataCount - existingDataRows).coerceAtLeast(0)

        if (extraRows > 0) {
            // TOTAL dan seluruh bagian di bawahnya digeser ke bawah. Ini menjaga
            // tanda tangan/Petugas tetap ikut bergerak bersama template.
            sheet.shiftRows(
                initialTotalRowIndex,
                sheet.lastRowNum,
                extraRows,
                true,
                false
            )

            // Salin format baris data terakhir sebelum TOTAL ke baris baru.
            val sourceRowIndex = initialTotalRowIndex - 1
            for (i in 0 until extraRows) {
                copyRowStyle(sheet, sourceRowIndex, initialTotalRowIndex - 1 + i)
            }
        }

        val totalRowIndex = initialTotalRowIndex + extraRows
        val lastDataRowIndex = firstDataRowIndex + dataCount - 1

        // Header baru sesuai template FIX6.
        setText(sheet, headerRowIndex, 0, "HASIL SCAN")
        setText(sheet, headerRowIndex, 1, "PEMBULATAN")
        clearCell(sheet, headerRowIndex, 2)
        clearCell(sheet, headerRowIndex, 3)
        clearCell(sheet, headerRowIndex, 4)

        // Header transaksi. D3/D4/D5 tetap mengikuti template lama.
        setText(sheet, 2, 3, data.hariTanggal)
        setText(sheet, 3, 3, data.customerName)
        setText(sheet, 4, 3, data.trademarks)
        setText(sheet, 8, 0, data.jenisBarang)

        // Bersihkan seluruh area data lama dari isi/formula agar tidak ada
        // data lama yang tertinggal di kolom C/D/E.
        for (rowIndex in firstDataRowIndex..lastDataRowIndex) {
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            for (col in 0..4) {
                if (col >= 2) clearCell(row, col)
            }
        }

        // Tulis HASIL SCAN dan PEMBULATAN saja.
        data.daftarTimbangan.forEachIndexed { index, original ->
            val rowIndex = firstDataRowIndex + index
            setNumeric(workbook, sheet, rowIndex, 0, original, "0.00")
            setNumeric(workbook, sheet, rowIndex, 1, roundWeight(original), "0.00")
        }

        // Pastikan label TOTAL tetap menggunakan merge template A:D.
        // Jika template pernah diedit manual dan merge hilang, kita tetap
        // tidak membuat struktur baru selain mengisi sel label yang ada.
        setText(sheet, totalRowIndex, 0, "TOTAL :")
        clearCell(sheet, totalRowIndex, 1)
        clearCell(sheet, totalRowIndex, 2)
        clearCell(sheet, totalRowIndex, 3)
        setFormula(workbook, sheet, totalRowIndex, 4, "SUM(B${firstDataRowIndex + 1}:B${lastDataRowIndex + 1})", "0.00")

        // Kosongkan formula lama di baris data yang tersisa jika template awal
        // memiliki formula lama di kolom D.
        val afterLastData = lastDataRowIndex + 1
        if (afterLastData < totalRowIndex) {
            for (r in afterLastData until totalRowIndex) {
                for (c in 0..4) clearCell(sheet, r, c)
            }
        }
    }

    private fun findTotalRow(sheet: Sheet, fromRow: Int): Int? {
        for (r in fromRow..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            for (c in 0..4) {
                val value = row.getCell(c)?.stringCellValue?.trim().orEmpty()
                if (value.equals("TOTAL :", ignoreCase = true) || value.equals("TOTAL", ignoreCase = true)) {
                    return r
                }
            }
        }
        return null
    }

    private fun copyRowStyle(sheet: Sheet, sourceIndex: Int, targetIndex: Int) {
        val source = sheet.getRow(sourceIndex) ?: return
        val target = sheet.getRow(targetIndex) ?: sheet.createRow(targetIndex)
        target.height = source.height
        target.heightInPoints = source.heightInPoints
        for (c in 0 until maxOf(source.lastCellNum.toInt(), 5)) {
            val src = source.getCell(c)
            val dst = target.getCell(c) ?: target.createCell(c)
            if (src != null) {
                dst.cellStyle = src.cellStyle
            }
        }
    }

    private fun setText(sheet: Sheet, rowIndex: Int, colIndex: Int, value: String) {
        val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
        val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
        cell.setCellValue(value)
    }

    private fun setNumeric(workbook: Workbook, sheet: Sheet, rowIndex: Int, colIndex: Int, value: Double, format: String) {
        val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
        val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
        cell.setCellValue(value)
        cell.cellStyle = cloneWithFormat(workbook, cell.cellStyle, format)
    }

    private fun setFormula(workbook: Workbook, sheet: Sheet, rowIndex: Int, colIndex: Int, formula: String, format: String) {
        val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
        val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
        cell.cellFormula = formula
        cell.cellStyle = cloneWithFormat(workbook, cell.cellStyle, format)
    }

    private fun clearCell(sheet: Sheet, rowIndex: Int, colIndex: Int) {
        val row = sheet.getRow(rowIndex) ?: return
        clearCell(row, colIndex)
    }

    private fun clearCell(row: Row, colIndex: Int) {
        val cell = row.getCell(colIndex) ?: return
        cell.setBlank()
    }

    private fun cloneWithFormat(workbook: Workbook, source: org.apache.poi.ss.usermodel.CellStyle, format: String): org.apache.poi.ss.usermodel.CellStyle {
        val style = workbook.createCellStyle()
        style.cloneStyleFrom(source)
        style.dataFormat = workbook.createDataFormat().getFormat(format)
        return style
    }
}
