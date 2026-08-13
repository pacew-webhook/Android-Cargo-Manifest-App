package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFCellStyle
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
    val photoUris: List<String> = emptyList(),
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
     * Export satu BTB ke template.
     *
     * Layout template:
     * A = No, B = Berat Asli, C = Berat Pembulatan, D = Berat Final, E = Total.
     * Data mulai baris Excel 10 dan TOTAL awalnya berada di baris 24.
     * Jika data melebihi area template, baris baru disisipkan tepat sebelum TOTAL.
     */
    fun fillBtbTemplate(
        context: Context,
        templateInputStream: InputStream,
        outputUri: Uri,
        data: BtbFormData
    ) {
        val workbook = XSSFWorkbook(templateInputStream)
        val sheet = workbook.getSheetAt(0)
        fillSheet(sheet, workbook, data)

        context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
            workbook.write(outputStream)
        } ?: throw IllegalStateException("Output file tidak dapat dibuka")

        workbook.close()
        templateInputStream.close()
    }

    /**
     * Export SEMUA BTB tersimpan ke satu workbook.
     * Setiap penerima/BTB mendapat satu sheet agar data tidak saling menimpa.
     */
    fun fillAllBtbTemplates(
        context: Context,
        templateInputStream: InputStream,
        outputUri: Uri,
        dataList: List<BtbFormData>
    ) {
        require(dataList.isNotEmpty()) { "Tidak ada data BTB untuk diekspor" }

        val workbook = XSSFWorkbook(templateInputStream)
        val firstSheet = workbook.getSheetAt(0)
        val sheets = mutableListOf(firstSheet)
        repeat(dataList.size - 1) {
            sheets += workbook.cloneSheet(0)
        }
        val usedNames = mutableSetOf<String>()

        dataList.forEachIndexed { index, data ->
            val sheet = sheets[index]
            val baseName = "BTB ${index + 1} ${sanitizeSheetName(data.customerName.ifBlank { "Data" })}"
            sheet.sheetName = uniqueSheetName(baseName, usedNames)
            usedNames += sheet.sheetName
        }

        // Semua clone sudah dibuat dari template asli sebelum sheet pertama diubah.
        dataList.forEachIndexed { index, data ->
            fillSheet(sheets[index], workbook, data)
        }

        context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
            workbook.write(outputStream)
        } ?: throw IllegalStateException("Output file tidak dapat dibuka")

        workbook.close()
        templateInputStream.close()
    }

    private fun fillSheet(
        sheet: org.apache.poi.xssf.usermodel.XSSFSheet,
        workbook: XSSFWorkbook,
        data: BtbFormData
    ) {
        fun setText(rowIndex: Int, colIndex: Int, value: String) {
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
            cell.setCellValue(value)
        }

        fun setNumber(rowIndex: Int, colIndex: Int, value: Double) {
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
            cell.setCellValue(value)
            cell.cellStyle = workbook.createCellStyle().also { style ->
                val sourceStyle = cell.cellStyle
                if (sourceStyle is XSSFCellStyle) style.cloneStyleFrom(sourceStyle)
                style.dataFormat = workbook.createDataFormat().getFormat("0.00")
            }
        }

        // Header template tetap dipertahankan.
        setText(2, 3, data.hariTanggal)       // D3
        setText(3, 3, data.customerName)      // D4
        setText(4, 3, data.trademarks)        // D5
        setText(8, 0, data.jenisBarang)       // A9

        val startRow = 9                     // Excel row 10
        val templateDataRows = 14            // row 10..23
        val totalRowInitial = 23             // Excel row 24
        val requiredRows = data.daftarTimbangan.size
        val extraRows = (requiredRows - templateDataRows).coerceAtLeast(0)

        if (extraRows > 0) {
            // TOTAL dan seluruh bagian bawah template turun bersama data.
            sheet.shiftRows(totalRowInitial, sheet.lastRowNum, extraRows, true, false)
            for (i in 0 until extraRows) {
                val sourceRow = sheet.getRow(totalRowInitial - 1) ?: sheet.createRow(totalRowInitial - 1)
                val newRow = sheet.createRow(totalRowInitial + i)
                copyRowStyleAndHeight(sourceRow, newRow)
            }
        }

        val totalRow = totalRowInitial + extraRows
        val clearEnd = totalRow - 1

        // Bersihkan area data agar export ulang ke sheet hasil clone tidak menyisakan data lama.
        for (r in startRow..clearEnd) {
            val row = sheet.getRow(r) ?: sheet.createRow(r)
            for (c in 0..4) {
                row.getCell(c)?.setBlank()
            }
        }

        data.daftarTimbangan.forEachIndexed { index, asli ->
            val rowIndex = startRow + index
            val pembulatan = kotlin.math.floor(asli + 0.5)
            val finalWeight = pembulatan
            setNumber(rowIndex, 0, (index + 1).toDouble())
            setNumber(rowIndex, 1, asli)
            setNumber(rowIndex, 2, pembulatan)
            setNumber(rowIndex, 3, finalWeight)
        }

        // TOTAL = Berat Asli, sesuai kebutuhan admin: data asli tetap tersedia
        // dan admin masih bisa memilih kolom pembulatan/final saat diproses.
        val totalCell = sheet.getRow(totalRow)?.getCell(4)
            ?: sheet.getRow(totalRow)!!.createCell(4)
        totalCell.cellFormula = "SUM(B${startRow + 1}:B${startRow + requiredRows})"
        totalCell.cellStyle = workbook.createCellStyle().also { style ->
            val sourceStyle = sheet.getRow(totalRow)?.getCell(4)?.cellStyle
            if (sourceStyle is XSSFCellStyle) style.cloneStyleFrom(sourceStyle)
            style.dataFormat = workbook.createDataFormat().getFormat("0.00")
        }

        workbook.setForceFormulaRecalculation(true)
    }

    private fun copyRowStyleAndHeight(
        source: org.apache.poi.ss.usermodel.Row,
        target: org.apache.poi.ss.usermodel.Row
    ) {
        target.height = source.height
        for (c in 0 until source.lastCellNum.coerceAtLeast(0)) {
            val sourceCell = source.getCell(c) ?: continue
            val targetCell = target.createCell(c)
            targetCell.cellStyle = sourceCell.cellStyle
        }
    }

    private fun sanitizeSheetName(value: String): String {
        val cleaned = value.replace(Regex("[\\\\/?*\\[\\]:]"), " ").trim()
        return cleaned.ifBlank { "Data" }.take(25)
    }

    private fun uniqueSheetName(base: String, used: Set<String>): String {
        var candidate = base.take(31)
        var n = 2
        while (candidate in used) {
            val suffix = " ($n)"
            candidate = base.take(31 - suffix.length) + suffix
            n++
        }
        return candidate
    }
}
