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

        // FIX7: jangan lagi memakai template XLSX sebagai sumber struktur BTB.
        // Template lama membawa merge/style yang pada Google Sheets dapat membuat
        // kolom B (PEMBULATAN) hilang walaupun cell NUMERIC sudah ditulis oleh POI.
        // Workbook dibuat dari nol sehingga A dan B benar-benar merupakan dua
        // kolom Excel yang independen.
        val workbook = XSSFWorkbook()
        try {
            dataList.forEachIndexed { index, data ->
                val sheet = workbook.createSheet(
                    safeSheetName(workbook, "BTB ${index + 1} ${data.customerName.ifBlank { "DATA" }}")
                )
                createAndFillBtbSheet(workbook, sheet, data)
            }

            workbook.setForceFormulaRecalculation(false)
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                workbook.write(outputStream)
            } ?: error("Tidak dapat membuka file output BTB")
        } finally {
            workbook.close()
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
        // Abaikan struktur template lama untuk alasan yang sama dengan FIX7.
        // Parameter templateInputStream dipertahankan agar API lama tetap kompatibel.
        templateInputStream.close()
        val workbook = XSSFWorkbook()
        try {
            val sheet = workbook.createSheet(
                safeSheetName(workbook, "BTB 1 ${data.customerName.ifBlank { "DATA" }}")
            )
            createAndFillBtbSheet(workbook, sheet, data)
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                workbook.write(outputStream)
            } ?: error("Tidak dapat membuka file output BTB")
        } finally {
            workbook.close()
        }
    }

    private fun createAndFillBtbSheet(
        workbook: XSSFWorkbook,
        sheet: Sheet,
        data: BtbFormData
    ) {
        require(data.daftarTimbangan.isNotEmpty()) { "Data timbangan BTB kosong" }

        // Ukuran kolom dibuat eksplisit. Kolom A dan B tidak pernah di-merge/hidden.
        sheet.setColumnWidth(0, 18 * 256)
        sheet.setColumnWidth(1, 18 * 256)
        sheet.setColumnWidth(2, 10 * 256)
        sheet.setColumnWidth(3, 10 * 256)
        sheet.setColumnWidth(4, 14 * 256)
        sheet.setColumnWidth(5, 12 * 256)
        sheet.setColumnWidth(6, 12 * 256)
        sheet.setColumnWidth(7, 12 * 256)
        sheet.setColumnHidden(0, false)
        sheet.setColumnHidden(1, false)

        val titleStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.BLACK.index
            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
            font = workbook.createFont().apply {
                color = org.apache.poi.ss.usermodel.IndexedColors.LIGHT_BLUE.index
                bold = true
                fontHeightInPoints = 14
            }
        }
        val labelStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.LEFT
            verticalAlignment = VerticalAlignment.CENTER
            fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.BLACK.index
            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
            font = workbook.createFont().apply { color = org.apache.poi.ss.usermodel.IndexedColors.WHITE.index; bold = true }
            borderBottom = BorderStyle.THIN; borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN; borderRight = BorderStyle.THIN
        }
        val valueStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.LEFT
            verticalAlignment = VerticalAlignment.CENTER
            fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.BLACK.index
            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
            font = workbook.createFont().apply { color = org.apache.poi.ss.usermodel.IndexedColors.WHITE.index }
            borderBottom = BorderStyle.THIN; borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN; borderRight = BorderStyle.THIN
        }
        val sectionStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.LIGHT_BLUE.index
            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
            font = workbook.createFont().apply { color = org.apache.poi.ss.usermodel.IndexedColors.BLACK.index; bold = true }
            borderBottom = BorderStyle.THIN; borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN; borderRight = BorderStyle.THIN
        }
        val headerStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.BLACK.index
            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
            font = workbook.createFont().apply { color = org.apache.poi.ss.usermodel.IndexedColors.WHITE.index; bold = true }
            borderBottom = BorderStyle.THIN; borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN; borderRight = BorderStyle.THIN
        }
        val numericStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.BLACK.index
            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
            font = workbook.createFont().apply { color = org.apache.poi.ss.usermodel.IndexedColors.WHITE.index }
            dataFormat = workbook.createDataFormat().getFormat("0.00")
            borderBottom = BorderStyle.THIN; borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN; borderRight = BorderStyle.THIN
        }
        val totalLabelStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.RIGHT
            verticalAlignment = VerticalAlignment.CENTER
            fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.BLACK.index
            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
            font = workbook.createFont().apply { color = org.apache.poi.ss.usermodel.IndexedColors.LIGHT_BLUE.index; bold = true; fontHeightInPoints = 13 }
            borderBottom = BorderStyle.THIN; borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN; borderRight = BorderStyle.THIN
        }

        mergeAndStyle(sheet, 0, 1, 0, 4, titleStyle, "SLIP BUKTI TIMBANG BARANG (BTB)")
        mergeAndStyle(sheet, 2, 2, 0, 2, labelStyle, "Hari / Tgl")
        mergeAndStyle(sheet, 2, 2, 3, 6, valueStyle, data.hariTanggal)
        mergeAndStyle(sheet, 3, 3, 0, 2, labelStyle, "NAME CUSTOMER")
        mergeAndStyle(sheet, 3, 3, 3, 6, valueStyle, data.customerName)
        mergeAndStyle(sheet, 4, 4, 0, 2, labelStyle, "TRADEMARKS")
        mergeAndStyle(sheet, 4, 4, 3, 6, valueStyle, data.trademarks)
        mergeAndStyle(sheet, 6, 6, 0, 4, sectionStyle, "DATA TIMBANGAN")
        mergeAndStyle(sheet, 7, 7, 0, 4, sectionStyle, "JENIS BARANG :")
        mergeAndStyle(sheet, 8, 8, 0, 4, sectionStyle, data.jenisBarang)

        setStyledText(sheet, 9, 0, "HASIL SCAN", headerStyle)
        setStyledText(sheet, 9, 1, "PEMBULATAN", headerStyle)
        for (c in 2..4) setStyledText(sheet, 9, c, "", headerStyle)

        data.daftarTimbangan.forEachIndexed { index, original ->
            val row = 10 + index
            val r = sheet.getRow(row) ?: sheet.createRow(row)
            r.getCell(0) ?: r.createCell(0)
            r.getCell(1) ?: r.createCell(1)
            r.getCell(0).cellStyle = numericStyle
            r.getCell(1).cellStyle = numericStyle
            r.getCell(0).setCellValue(original)
            r.getCell(1).setCellValue(roundWeight(original))
            for (c in 2..4) {
                r.getCell(c) ?: r.createCell(c)
                r.getCell(c).cellStyle = numericStyle
            }
        }

        // Pertahankan posisi TOTAL pada Excel row 24 untuk data sampai 13 koli,
        // dan otomatis turun jika jumlah koli lebih banyak.
        val totalRow = maxOf(23, 10 + data.daftarTimbangan.size + 1)
        val total = sheet.getRow(totalRow) ?: sheet.createRow(totalRow)
        for (c in 0..4) total.getCell(c) ?: total.createCell(c)
        mergeAndStyle(sheet, totalRow, totalRow, 0, 3, totalLabelStyle, "TOTAL :")
        total.getCell(4).cellStyle = numericStyle
        total.getCell(4).setCellValue(data.totalBeratPembulatan)

        val footer = totalRow + 3
        setStyledText(sheet, footer, 0, "Petugas,", labelStyle)

        // Tidak ada formula sama sekali di area BTB. Semua hasil sudah berupa nilai numeric.
        require(sheet.getRow(9).getCell(1).stringCellValue == "PEMBULATAN")
        data.daftarTimbangan.forEachIndexed { index, original ->
            val row = sheet.getRow(10 + index)
            require(row.getCell(1).cellType == CellType.NUMERIC)
            require(kotlin.math.abs(row.getCell(1).numericCellValue - roundWeight(original)) < 0.000001)
        }
        require(kotlin.math.abs(sheet.getRow(totalRow).getCell(4).numericCellValue - data.totalBeratPembulatan) < 0.000001)
    }

    private fun mergeAndStyle(
        sheet: Sheet,
        firstRow: Int,
        lastRow: Int,
        firstCol: Int,
        lastCol: Int,
        style: org.apache.poi.ss.usermodel.CellStyle,
        value: String
    ) {
        val row = sheet.getRow(firstRow) ?: sheet.createRow(firstRow)
        for (c in firstCol..lastCol) {
            val cell = row.getCell(c) ?: row.createCell(c)
            cell.cellStyle = style
            if (c == firstCol) cell.setCellValue(value) else cell.setBlank()
        }
        if (firstRow != lastRow || firstCol != lastCol) {
            sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(firstRow, lastRow, firstCol, lastCol))
        }
    }

    private fun setStyledText(
        sheet: Sheet,
        rowIndex: Int,
        colIndex: Int,
        value: String,
        style: org.apache.poi.ss.usermodel.CellStyle
    ) {
        val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
        val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
        cell.cellStyle = style
        cell.setCellValue(value)
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

        // FIX6: jangan bergantung pada struktur/merge/cache template untuk
        // area BTB. Google Sheets terbukti masih membuka hasil sebelumnya
        // tanpa kolom PEMBULATAN. Karena itu area A10:E(total) dinormalisasi
        // terlebih dahulu: merge yang menyentuh area data dilepas, lalu cell
        // A/B dibuat ulang secara eksplisit. Desain di luar area ini tetap.
        normalizeBtbDataArea(sheet, headerRowIndex, totalRowIndex)
        ensureBtbHeader(workbook, sheet, headerRowIndex)
        sheet.setColumnHidden(0, false)
        sheet.setColumnHidden(1, false)
        if (sheet.getColumnWidth(0) < 12 * 256) sheet.setColumnWidth(0, 16 * 256)
        if (sheet.getColumnWidth(1) < 12 * 256) sheet.setColumnWidth(1, 16 * 256)
        setText(sheet, headerRowIndex, 0, "HASIL SCAN")
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

            // FIX5: tulis PEMBULATAN langsung ke cell B yang baru dibuat.
            // Jangan memakai formula dan jangan memakai setCellType setelah
            // setCellValue. Cell harus benar-benar NUMERIC.
            writeBtbRoundedCell(
                workbook = workbook,
                sheet = sheet,
                rowIndex = rowIndex,
                value = rounded
            )
        }

        // TOTAL selalu dihitung dari nilai pembulatan di aplikasi.
        // Tidak menggunakan formula Excel agar Google Sheets langsung membaca
        // nilai yang benar walaupun belum melakukan recalculation.
        setText(sheet, totalRowIndex, 0, "TOTAL :")
        clearCell(sheet, totalRowIndex, 1)
        clearCell(sheet, totalRowIndex, 2)
        clearCell(sheet, totalRowIndex, 3)
        // Pulihkan merge TOTAL yang dilepas oleh normalisasi.
        try { sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(totalRowIndex, totalRowIndex, 0, 3)) } catch (_: Exception) { }

        replaceWithNumericCell(
            workbook = workbook,
            sheet = sheet,
            rowIndex = totalRowIndex,
            colIndex = 4,
            value = data.totalBeratPembulatan,
            format = "0.00"
        )

        // FIX5: validasi sebelum workbook ditulis. Kalau kolom PEMBULATAN
        // tidak benar-benar berisi nilai NUMERIC, export dihentikan sehingga
        // aplikasi tidak menghasilkan file yang tampak berhasil tetapi salah.
        validateBtbExport(sheet, firstDataRowIndex, totalRowIndex, data)
    }


    private fun normalizeBtbDataArea(sheet: Sheet, headerRowIndex: Int, totalRowIndex: Int) {
        // Lepas semua merged region yang menyentuh area A10:E(total).
        // Merge di luar area BTB tidak disentuh.
        val toRemove = sheet.mergedRegions.filter { region ->
            region.firstRow <= totalRowIndex &&
                region.lastRow >= headerRowIndex &&
                region.firstColumn <= 4 &&
                region.lastColumn >= 0
        }
        toRemove.asReversed().forEach { region ->
            sheet.removeMergedRegion(sheet.mergedRegions.indexOf(region))
        }

        // Pastikan row/cell A:E untuk seluruh area benar-benar ada.
        for (r in headerRowIndex..totalRowIndex) {
            val row = sheet.getRow(r) ?: sheet.createRow(r)
            for (c in 0..4) {
                if (row.getCell(c) == null) row.createCell(c)
            }
        }
    }

    /**
     * Paksa header Excel dibuat ulang sebagai STRING + style header asli.
     * Ini mencegah header PEMBULATAN hilang ketika template memiliki cache
     * atau struktur cell yang berbeda pada versi Google Sheets/POI tertentu.
     */
    private fun ensureBtbHeader(
        workbook: Workbook,
        sheet: Sheet,
        rowIndex: Int
    ) {
        val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)

        val sourceStyle = row.getCell(0)?.cellStyle
        val oldB = row.getCell(1)
        if (oldB != null) row.removeCell(oldB)

        val header = row.createCell(1, CellType.STRING)
        if (sourceStyle != null) {
            header.cellStyle = cloneWithAlignment(workbook, sourceStyle, HorizontalAlignment.CENTER)
        } else {
            val style = workbook.createCellStyle()
            style.alignment = HorizontalAlignment.CENTER
            style.borderBottom = BorderStyle.THIN
            style.borderTop = BorderStyle.THIN
            style.borderLeft = BorderStyle.THIN
            style.borderRight = BorderStyle.THIN
            header.cellStyle = style
        }
        header.setCellValue("PEMBULATAN")
    }

    private fun writeBtbRoundedCell(
        workbook: Workbook,
        sheet: Sheet,
        rowIndex: Int,
        value: Double
    ) {
        val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
        val oldCell = row.getCell(1)
        val sourceStyle = oldCell?.cellStyle ?: row.getCell(0)?.cellStyle
        if (oldCell != null) row.removeCell(oldCell)

        val cell = row.createCell(1, CellType.NUMERIC)
        if (sourceStyle != null) {
            cell.cellStyle = cloneWithFormat(
                workbook,
                sourceStyle,
                "0.00"
            )
            cell.cellStyle = cloneWithAlignment(
                workbook,
                cell.cellStyle,
                HorizontalAlignment.CENTER
            )
        } else {
            val style = workbook.createCellStyle()
            style.dataFormat = workbook.createDataFormat().getFormat("0.00")
            style.alignment = HorizontalAlignment.CENTER
            cell.cellStyle = style
        }
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


    private fun validateBtbExport(
        sheet: Sheet,
        firstDataRowIndex: Int,
        totalRowIndex: Int,
        data: BtbFormData
    ) {
        val header = sheet.getRow(9)?.getCell(1)
            ?: error("Kolom PEMBULATAN tidak terbentuk")
        require(header.cellType == CellType.STRING && header.stringCellValue.trim() == "PEMBULATAN") {
            "Header PEMBULATAN tidak terbentuk dengan benar"
        }

        data.daftarTimbangan.forEachIndexed { index, original ->
            val cell = sheet.getRow(firstDataRowIndex + index)?.getCell(1)
                ?: error("Cell PEMBULATAN pada baris ${firstDataRowIndex + index + 1} tidak terbentuk")
            require(cell.cellType == CellType.NUMERIC) {
                "Cell PEMBULATAN pada baris ${firstDataRowIndex + index + 1} bukan NUMERIC"
            }
            require(!cell.cellType.equals(CellType.FORMULA)) {
                "Cell PEMBULATAN pada baris ${firstDataRowIndex + index + 1} masih FORMULA"
            }
            val expected = roundWeight(original)
            require(kotlin.math.abs(cell.numericCellValue - expected) < 0.000001) {
                "Nilai PEMBULATAN salah pada baris ${firstDataRowIndex + index + 1}"
            }
        }

        val totalCell = sheet.getRow(totalRowIndex)?.getCell(4)
            ?: error("Cell TOTAL tidak terbentuk")
        require(totalCell.cellType == CellType.NUMERIC) {
            "Cell TOTAL bukan NUMERIC"
        }
        require(kotlin.math.abs(totalCell.numericCellValue - data.totalBeratPembulatan) < 0.000001) {
            "TOTAL pembulatan tidak sesuai"
        }
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
