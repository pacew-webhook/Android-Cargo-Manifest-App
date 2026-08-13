package com.example.cargomanifestapp

import android.content.Context
import android.graphics.Bitmap
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
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import org.apache.poi.util.Units
import org.apache.poi.xssf.usermodel.XSSFClientAnchor

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


    /**
     * QR BTB: hanya menyimpan ID unik BTB, bukan seluruh data.
     * Contoh payload: BTB_ID:BTB-260814-123456
     */
    private fun createBtbQrId(data: BtbFormData): String {
        val datePart = try {
            SimpleDateFormat("yyMMdd", Locale.US).format(Date(data.id.toLong()))
        } catch (_: Exception) {
            SimpleDateFormat("yyMMdd", Locale.US).format(Date())
        }
        val uniquePart = data.id.takeLast(6).padStart(6, '0')
        return "BTB-$datePart-$uniquePart"
    }

    private fun createQrPng(payload: String, size: Int = 360): ByteArray {
        val matrix: BitMatrix = MultiFormatWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                com.google.zxing.EncodeHintType.MARGIN to 1,
                com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"
            )
        )
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x, y,
                    if (matrix[x, y]) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
                )
            }
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun insertBtbQrCode(
        workbook: XSSFWorkbook,
        sheet: Sheet,
        payload: String
    ) {
        val png = createQrPng(payload)
        val pictureIndex = workbook.addPicture(png, Workbook.PICTURE_TYPE_PNG)
        val drawing = sheet.createDrawingPatriarch()
        val anchor = XSSFClientAnchor()
        anchor.setCol1(6) // G
        anchor.setRow1(1) // Excel row 2
        anchor.setCol2(10) // K
        anchor.setRow2(9) // Excel row 10
        anchor.setDx1(0)
        anchor.setDy1(0)
        anchor.setDx2(0)
        anchor.setDy2(0)
        drawing.createPicture(anchor, pictureIndex).resize(1.0)
    }

    private fun createAndFillBtbSheet(
        workbook: XSSFWorkbook,
        sheet: Sheet,
        data: BtbFormData
    ) {
        require(data.daftarTimbangan.isNotEmpty()) { "Data timbangan BTB kosong" }

        // =============================================================
        // BTB EXCEL - LAYOUT FIX14 + QR
        // Mengikuti screenshot referensi dengan QR ID BTB:
        // A:B = JENIS BARANG, C = HASIL SCAN, D = spacer, E = PEMBULATAN.
        // TOTAL berada di A:D dan nilainya di E.
        // =============================================================
        sheet.setColumnWidth(0, 16 * 256) // A
        sheet.setColumnWidth(1, 16 * 256) // B
        sheet.setColumnWidth(2, 10 * 256) // C
        sheet.setColumnWidth(3, 10 * 256) // D
        sheet.setColumnWidth(4, 14 * 256) // E
        sheet.setColumnWidth(5, 3 * 256)  // F spacer
        sheet.setColumnWidth(6, 12 * 256) // G QR area
        sheet.setColumnWidth(7, 12 * 256) // H QR area
        sheet.setColumnWidth(8, 12 * 256) // I QR area
        sheet.setColumnWidth(9, 12 * 256) // J QR area
        sheet.setDisplayGridlines(true)

        val blue = org.apache.poi.ss.usermodel.IndexedColors.BLUE.index
        val white = org.apache.poi.ss.usermodel.IndexedColors.WHITE.index
        val black = org.apache.poi.ss.usermodel.IndexedColors.BLACK.index
        val grey = org.apache.poi.ss.usermodel.IndexedColors.GREY_50_PERCENT.index

        fun font(color: Short, bold: Boolean = false, size: Short = 11) =
            workbook.createFont().apply {
                this.color = color
                this.bold = bold
                fontHeightInPoints = size
            }

        fun baseStyle(
            alignment: HorizontalAlignment,
            bold: Boolean = false,
            fontColor: Short = black,
            fill: Short = white,
            size: Short = 11
        ) = workbook.createCellStyle().apply {
            this.alignment = alignment
            verticalAlignment = VerticalAlignment.CENTER
            fillForegroundColor = fill
            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
            setFont(font(fontColor, bold, size))
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            bottomBorderColor = grey
            topBorderColor = grey
            leftBorderColor = grey
            rightBorderColor = grey
        }

        val titleStyle = baseStyle(HorizontalAlignment.CENTER, bold = true, size = 14)
        val labelStyle = baseStyle(HorizontalAlignment.LEFT, bold = true)
        val valueStyle = baseStyle(HorizontalAlignment.LEFT)
        val sectionStyle = baseStyle(
            HorizontalAlignment.CENTER,
            bold = true,
            fontColor = white,
            fill = blue
        )
        val headerStyle = baseStyle(HorizontalAlignment.CENTER, bold = true)
        val numericStyle = baseStyle(HorizontalAlignment.CENTER).apply {
            dataFormat = workbook.createDataFormat().getFormat("0.00")
        }
        val blankDataStyle = baseStyle(HorizontalAlignment.CENTER)
        val totalLabelStyle = baseStyle(
            HorizontalAlignment.RIGHT,
            bold = true,
            size = 13
        )
        val totalValueStyle = baseStyle(
            HorizontalAlignment.CENTER,
            bold = true
        ).apply {
            dataFormat = workbook.createDataFormat().getFormat("0.00")
        }

        // Baris 1-5: identitas BTB.
        mergeAndStyle(sheet, 0, 0, 0, 4, titleStyle, "SLIP BUKTI TIMBANG BARANG (BTB)")
        mergeAndStyle(sheet, 2, 2, 0, 1, labelStyle, "Hari / Tgl")
        mergeAndStyle(sheet, 2, 2, 2, 4, valueStyle, data.hariTanggal)
        mergeAndStyle(sheet, 3, 3, 0, 1, labelStyle, "NAME CUSTOMER")
        mergeAndStyle(sheet, 3, 3, 2, 4, valueStyle, data.customerName)
        mergeAndStyle(sheet, 4, 4, 0, 1, labelStyle, "TRADEMARKS")
        mergeAndStyle(sheet, 4, 4, 2, 4, valueStyle, data.trademarks)

        // QR Code: hanya berisi ID BTB agar QR ringkas dan stabil.
        val btbQrId = createBtbQrId(data)
        setStyledText(sheet, 1, 5, "ID BTB", labelStyle)
        mergeAndStyle(sheet, 1, 6, 6, 9, valueStyle, btbQrId)
        insertBtbQrCode(workbook, sheet, btbQrId)

        // Baris 7: DATA TIMBANGAN.
        mergeAndStyle(sheet, 5, 5, 0, 4, sectionStyle, "DATA TIMBANGAN")

        // Baris 8: header sesuai screenshot terbaru. Tidak ada baris
        // "JENIS BARANG :" tambahan; nama barang langsung masuk ke A:B
        // pada baris data pertama.
        mergeAndStyle(sheet, 6, 6, 0, 1, headerStyle, "JENIS BARANG")
        setStyledText(sheet, 6, 2, "HASIL SCAN", headerStyle)
        setStyledText(sheet, 6, 3, "", headerStyle)
        setStyledText(sheet, 6, 4, "PEMBULATAN", headerStyle)

        // Baris data dimulai pada baris Excel 8. Minimal 13 baris data
        // (Excel 8-20), satu baris kosong (21), lalu TOTAL pada baris 22
        // seperti screenshot.
        val firstDataRow = 7
        val minimumDataRows = 13
        val dataRows = maxOf(minimumDataRows, data.daftarTimbangan.size)
        val totalRow = firstDataRow + dataRows + 1

        for (i in 0 until dataRows) {
            val rowIndex = firstDataRow + i
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            row.heightInPoints = 18f

            // A:B adalah satu area jenis barang.
            for (c in 0..1) {
                val cell = row.getCell(c) ?: row.createCell(c)
                cell.setBlank()
                cell.cellStyle = headerStyle
            }
            if (i < data.daftarTimbangan.size) {
                mergeAndStyle(sheet, rowIndex, rowIndex, 0, 1, headerStyle, data.jenisBarang)
            } else {
                // Tetap dua kolom dengan garis rapi pada baris kosong.
                row.getCell(0).cellStyle = blankDataStyle
                row.getCell(1).cellStyle = blankDataStyle
            }

            // C = hasil scan, D = kolom kosong/spacer, E = pembulatan.
            for (c in 2..4) {
                val cell = row.getCell(c) ?: row.createCell(c)
                cell.setBlank()
                cell.cellStyle = if (c == 3) blankDataStyle else numericStyle
            }

            if (i < data.daftarTimbangan.size) {
                row.getCell(2).setCellValue(data.daftarTimbangan[i])
                row.getCell(2).cellStyle = numericStyle
                row.getCell(4).setCellValue(roundWeight(data.daftarTimbangan[i]))
                row.getCell(4).cellStyle = numericStyle
            }
        }

        // TOTAL: label A:D, nilai E. Nilai TOTAL selalu hasil pembulatan.
        mergeAndStyle(sheet, totalRow, totalRow, 0, 3, totalLabelStyle, "TOTAL :")
        val totalCell = sheet.getRow(totalRow)?.getCell(4)
            ?: sheet.getRow(totalRow)!!.createCell(4)
        totalCell.cellStyle = totalValueStyle
        totalCell.setCellValue(data.totalBeratPembulatan)

        // Footer seperti screenshot.
        setStyledText(sheet, totalRow + 4, 0, "Petugas,", labelStyle)

        // Validasi keras agar desain baru tidak mengorbankan pembulatan.
        require(sheet.getRow(6)?.getCell(4)?.stringCellValue == "PEMBULATAN")
        data.daftarTimbangan.forEachIndexed { index, original ->
            val row = sheet.getRow(firstDataRow + index)
                ?: error("Baris data BTB tidak terbentuk")
            val rounded = row.getCell(4)
                ?: error("Cell PEMBULATAN tidak terbentuk")
            require(rounded.cellType == CellType.NUMERIC) {
                "Cell PEMBULATAN bukan NUMERIC"
            }
            require(kotlin.math.abs(rounded.numericCellValue - roundWeight(original)) < 0.000001) {
                "Nilai PEMBULATAN salah"
            }
        }
        require(kotlin.math.abs(totalCell.numericCellValue - data.totalBeratPembulatan) < 0.000001) {
            "TOTAL pembulatan tidak sesuai"
        }
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
