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

fun serializeBtbWeightsToJson(weights: List<Double>): String =
    org.json.JSONArray().apply { weights.forEach { put(it) } }.toString()

fun btbWeightsFromJson(json: String): List<Double> {
    return try {
        val array = org.json.JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val value = array.optDouble(i, Double.NaN)
                if (value.isFinite() && value > 0.0) add(value)
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

object BtbExcelWriter {

    /**
     * Mengisi template BTB tanpa mengubah desain/template dasar.
     * Struktur data FIX7:
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
        val base = requested.map { ch -> if (ch in "\\/?*[]:") '_' else ch }.joinToString("").take(31).ifBlank { "BTB" }
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
        val headerRowIndex = 9       // Excel row 10
        val firstDataRowIndex = 10  // Excel row 11
        val initialTotalRowIndex = findTotalRow(sheet, headerRowIndex + 1)
            ?: 23                    // Excel row 24 fallback

        val dataCount = data.daftarTimbangan.size
        require(dataCount > 0) { "Data timbangan BTB kosong" }

        val existingDataRows = (initialTotalRowIndex - firstDataRowIndex).coerceAtLeast(1)
        val extraRows = (dataCount - existingDataRows).coerceAtLeast(0)

        if (extraRows > 0) {
            sheet.shiftRows(
                initialTotalRowIndex,
                sheet.lastRowNum,
                extraRows,
                true,
                false
            )

            val sourceRowIndex = initialTotalRowIndex - 1
            for (i in 0 until extraRows) {
                copyRowStyle(sheet, sourceRowIndex, initialTotalRowIndex - 1 + i)
            }
        }

        val totalRowIndex = initialTotalRowIndex + extraRows
        val lastDataRowIndex = firstDataRowIndex + dataCount - 1

        // Header BTB.
        setHeaderText(workbook, sheet, headerRowIndex, 0, "HASIL SCAN")
        setHeaderText(workbook, sheet, headerRowIndex, 1, "PEMBULATAN")
        clearCell(sheet, headerRowIndex, 2)
        clearCell(sheet, headerRowIndex, 3)
        clearCell(sheet, headerRowIndex, 4)

        // Informasi utama.
        setText(sheet, 2, 3, data.hariTanggal)
        setText(sheet, 3, 3, data.customerName)
        setText(sheet, 4, 3, data.trademarks)
        setText(sheet, 8, 0, data.jenisBarang)

        /*
         * PENTING:
         * Template BTB lama mempunyai formula TOTAL di E24 dan beberapa sel
         * data yang sudah berisi style/formula. Jangan hanya setBlank() pada
         * cell tersebut karena pada beberapa versi POI/Google Sheets formula
         * atau cached value dari template masih dapat terbawa.
         *
         * Karena itu cell data A:B dan TOTAL E diganti secara eksplisit,
         * sambil mempertahankan style cell lama.
         */
        for (rowIndex in firstDataRowIndex..lastDataRowIndex) {
            clearCell(sheet, rowIndex, 0)
            clearCell(sheet, rowIndex, 1)
            clearCell(sheet, rowIndex, 2)
            clearCell(sheet, rowIndex, 3)
            clearCell(sheet, rowIndex, 4)
        }

        // Bersihkan baris kosong sebelum TOTAL.
        val afterLastData = lastDataRowIndex + 1
        if (afterLastData < totalRowIndex) {
            for (r in afterLastData until totalRowIndex) {
                for (c in 0..4) clearCell(sheet, r, c)
            }
        }

        // Tulis HASIL SCAN dan PEMBULATAN sebagai angka Excel sungguhan.
        data.daftarTimbangan.forEachIndexed { index, original ->
            val rowIndex = firstDataRowIndex + index
            val rounded = roundWeight(original)

            replaceWithNumericCell(
                workbook = workbook,
                sheet = sheet,
                rowIndex = rowIndex,
                colIndex = 0,
                value = original,
                format = "0.00"
            )

            // Gunakan style kolom A sebagai basis agar nilai PEMBULATAN
            // tidak mewarisi format/warna cell B template yang pada beberapa
            // viewer terlihat kosong. Nilainya tetap NUMERIC agar bisa dihitung.
            replaceWithNumericCell(
                workbook = workbook,
                sheet = sheet,
                rowIndex = rowIndex,
                colIndex = 1,
                value = rounded,
                format = "0.00",
                styleSourceColIndex = 1,
                horizontalAlignment = HorizontalAlignment.CENTER
            )
        }

        // TOTAL selalu dihitung dari nilai pembulatan di aplikasi.
        // Tidak menggunakan formula Excel agar Google Sheets langsung membaca
        // nilai yang benar walaupun belum melakukan recalculation.
        setText(sheet, totalRowIndex, 0, "TOTAL :")
        clearCell(sheet, totalRowIndex, 1)
        clearCell(sheet, totalRowIndex, 2)
        clearCell(sheet, totalRowIndex, 3)

        replaceWithNumericCell(
            workbook = workbook,
            sheet = sheet,
            rowIndex = totalRowIndex,
            colIndex = 4,
            value = data.totalBeratPembulatan,
            format = "0.00"
        )

        // FIX2: jangan pernah membaca cellFormula setelah cell TOTAL
        // diganti menjadi NUMERIC. Mengakses cellFormula pada cell NUMERIC
        // menyebabkan: "Cannot get a FORMULA value from a NUMERIC cell".
        // replaceWithNumericCell() di atas sudah menghapus cell lama
        // beserta formula/cached value-nya.
    }

    /**
     * Paksa header Excel dibuat ulang sebagai STRING + style header asli.
     * Ini mencegah header PEMBULATAN hilang ketika template memiliki cache
     * atau struktur cell yang berbeda pada versi Google Sheets/POI tertentu.
     */
    private fun setHeaderText(
        workbook: Workbook,
        sheet: Sheet,
        rowIndex: Int,
        colIndex: Int,
        value: String
    ) {
        val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
        val oldCell = row.getCell(colIndex)
        val oldStyle = oldCell?.cellStyle
        if (oldCell != null) row.removeCell(oldCell)
        val cell = row.createCell(colIndex, CellType.STRING)
        if (oldStyle != null) cell.cellStyle = oldStyle
        cell.setCellValue(value)
    }

    private fun findTotalRow(sheet: Sheet, fromRow: Int): Int? {
        for (r in fromRow..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            for (c in 0..4) {
                val cell = row.getCell(c) ?: continue
                val value = when (cell.cellType) {
                    CellType.STRING -> cell.stringCellValue.trim()
                    CellType.FORMULA -> cell.cellFormula.trim()
                    else -> ""
                }
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

    private fun replaceWithNumericCell(
        workbook: Workbook,
        sheet: Sheet,
        rowIndex: Int,
        colIndex: Int,
        value: Double,
        format: String,
        styleSourceColIndex: Int? = null,
        horizontalAlignment: HorizontalAlignment? = null
    ) {
        val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
        val oldCell = row.getCell(colIndex)
        val styleCell = if (styleSourceColIndex != null) row.getCell(styleSourceColIndex) else oldCell
        val oldStyle = styleCell?.cellStyle

        if (oldCell != null) {
            row.removeCell(oldCell)
        }

        val cell = row.createCell(colIndex, CellType.NUMERIC)
        if (oldStyle != null) {
            cell.cellStyle = cloneWithFormat(workbook, oldStyle, format)
        } else {
            val style = workbook.createCellStyle()
            style.dataFormat = workbook.createDataFormat().getFormat(format)
            cell.cellStyle = style
        }
        if (horizontalAlignment != null) {
            cell.cellStyle = cloneWithAlignment(workbook, cell.cellStyle, horizontalAlignment)
        }
        // setCellValue(Double) membuat cell NUMERIC secara langsung.
        // Jangan memanggil setCellType() lagi karena pada beberapa versi POI
        // perubahan tipe setelah value ditulis dapat membuat hasil ekspor
        // tidak terbaca konsisten oleh Google Sheets.
        cell.setCellValue(value)
    }

    private fun clearCell(sheet: Sheet, rowIndex: Int, colIndex: Int) {
        val row = sheet.getRow(rowIndex) ?: return
        clearCell(row, colIndex)
    }

    private fun clearCell(row: Row, colIndex: Int) {
        val cell = row.getCell(colIndex) ?: return
        cell.setBlank()
    }


    private fun cloneWithAlignment(
        workbook: Workbook,
        source: org.apache.poi.ss.usermodel.CellStyle,
        alignment: HorizontalAlignment
    ): org.apache.poi.ss.usermodel.CellStyle {
        val style = workbook.createCellStyle()
        style.cloneStyleFrom(source)
        style.alignment = alignment
        return style
    }

    private fun cloneWithFormat(workbook: Workbook, source: org.apache.poi.ss.usermodel.CellStyle, format: String): org.apache.poi.ss.usermodel.CellStyle {
        val style = workbook.createCellStyle()
        style.cloneStyleFrom(source)
        style.dataFormat = workbook.createDataFormat().getFormat(format)
        return style
    }
}
