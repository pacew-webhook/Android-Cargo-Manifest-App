package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import kotlin.math.floor
import org.json.JSONArray
import org.json.JSONObject

/**
 * Satu hasil penimbangan BTB.
 * original = angka yang benar-benar dibaca dari timbangan/manual.
 * rounded  = pembulatan ke KG terdekat: .00-.49 turun, .50-.99 naik.
 * final    = nilai yang dipakai admin untuk dokumen.
 */
data class BtbWeight(
    val original: Double,
    val rounded: Double = roundBtbWeight(original),
    val final: Double = rounded
)

fun roundBtbWeight(value: Double): Double {
    if (!value.isFinite()) return 0.0
    return floor(value + 0.5)
}


fun btbWeightsToJson(weights: List<BtbWeight>): String {
    val array = JSONArray()
    weights.forEach { weight ->
        array.put(JSONObject().apply {
            put("original", weight.original)
            put("rounded", weight.rounded)
            put("final", weight.final)
        })
    }
    return array.toString()
}

fun btbWeightsFromJson(json: String): List<BtbWeight> {
    return try {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.opt(i)
                if (item is JSONObject) {
                    val original = item.optDouble("original", Double.NaN)
                    if (original.isFinite() && original > 0) {
                        val rounded = item.optDouble("rounded", roundBtbWeight(original))
                        val finalValue = item.optDouble("final", rounded)
                        add(BtbWeight(original, rounded, finalValue))
                    }
                } else if (item is Number) {
                    val original = item.toDouble()
                    if (original.isFinite() && original > 0) add(BtbWeight(original))
                }
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/** Model data utama untuk Bukti Timbang Barang. */
data class BtbFormData(
    val id: String = System.currentTimeMillis().toString(),
    val hariTanggal: String = "",
    val customerName: String = "",
    val trademarks: String = "",
    val jenisBarang: String = "",
    val daftarTimbangan: List<BtbWeight> = emptyList(),
    val photoUris: List<String> = emptyList()
) {
    val totalBeratAsli: Double get() = daftarTimbangan.sumOf { it.original }
    val totalBeratPembulatan: Double get() = daftarTimbangan.sumOf { it.rounded }
    val totalBeratFinal: Double get() = daftarTimbangan.sumOf { it.final }
    val jumlahKoli: Int get() = daftarTimbangan.size
}

/** Helper ekspor data ke template Excel BTB. */
object BtbExcelWriter {

    fun fillBtbTemplate(
        context: Context,
        templateInputStream: InputStream,
        outputUri: Uri,
        data: BtbFormData
    ) = fillBtbTemplateMulti(context, templateInputStream, outputUri, listOf(data))

    /**
     * Menulis seluruh hasil timbang sebagai baris dinamis.
     * Template asli tetap menjadi sumber style/layout; baris data akan digeser
     * bila jumlah timbang melebihi ruang awal sebelum TOTAL.
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

        val allWeights = listData.flatMap { data ->
            data.daftarTimbangan.map { weight ->
                Triple(data.trademarks.ifBlank { data.customerName }, data.jenisBarang, weight)
            }
        }

        val dataStartRow = 9 // Excel row 10
        val originalTotalRow = 23 // Excel row 24
        val requiredRows = allWeights.size.coerceAtLeast(1)
        val templateDataRows = originalTotalRow - dataStartRow
        val extraRows = (requiredRows - templateDataRows).coerceAtLeast(0)

        if (extraRows > 0) {
            sheet.shiftRows(originalTotalRow, sheet.lastRowNum, extraRows, true, false)
            copyRowStyle(sheet, originalTotalRow - 1, originalTotalRow + extraRows - 1, workbook)
        }

        val totalRow = originalTotalRow + extraRows
        val totalOriginalRow = totalRow + 1
        val totalRoundedRow = totalRow + 2

        // Header transaksi.
        val first = listData.first()
        setString(sheet, 2, 3, first.hariTanggal)
        setString(sheet, 3, 3, first.customerName)
        val combinedTrademarks = listData.map { it.trademarks }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
        setString(sheet, 4, 3, combinedTrademarks)
        setString(sheet, 7, 0, "JENIS BARANG : ${first.jenisBarang}")

        // Row 9 pada template sebelumnya merupakan area kosong/merged.
        // Kita pecah menjadi header tabel agar tiga jenis berat terlihat jelas.
        unmergeIfMerged(sheet, "A9:E9")
        val headerStyle = cloneStyle(workbook, sheet.getRow(9)?.getCell(0)?.cellStyle)
        val headers = listOf("NO", "PENERIMA", "BERAT ASLI (KG)", "PEMBULATAN (KG)", "BERAT FINAL (KG)")
        headers.forEachIndexed { col, text ->
            val cell = getCell(sheet, 9, col)
            cell.setCellValue(text)
            if (headerStyle != null) cell.cellStyle = headerStyle
        }

        val dataStyle = cloneStyle(workbook, sheet.getRow(dataStartRow)?.getCell(1)?.cellStyle)
        val numericStyle = cloneStyle(workbook, sheet.getRow(dataStartRow)?.getCell(2)?.cellStyle)

        allWeights.forEachIndexed { index, (recipient, _, weight) ->
            val rowIndex = dataStartRow + index
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            for (c in 0..4) {
                if (dataStyle != null && c < 2) row.getCell(c).cellStyle = dataStyle
                if (numericStyle != null && c >= 2) row.getCell(c).cellStyle = numericStyle
            }
            row.getCell(0).setCellValue((index + 1).toDouble())
            row.getCell(1).setCellValue(recipient)
            row.getCell(2).setCellValue(weight.original)
            row.getCell(3).setCellValue(weight.rounded)
            row.getCell(4).setCellValue(weight.final)
        }

        // Bersihkan sisa baris data template bila jumlah data lebih sedikit.
        for (r in dataStartRow + requiredRows until totalRow) {
            val row = sheet.getRow(r) ?: continue
            for (c in 0..4) row.getCell(c)?.setBlank()
        }

        // TOTAL final tetap di kolom E pada baris total.
        setString(sheet, totalRow, 0, "TOTAL :")
        setNumeric(sheet, totalRow, 4, allWeights.sumOf { it.third.final })

        // Dua total audit agar admin dapat membandingkan sumber dan pembulatan.
        ensureRow(sheet, totalOriginalRow)
        ensureRow(sheet, totalRoundedRow)
        setString(sheet, totalOriginalRow, 0, "TOTAL BERAT ASLI :")
        setNumeric(sheet, totalOriginalRow, 4, allWeights.sumOf { it.third.original })
        setString(sheet, totalRoundedRow, 0, "TOTAL PEMBULATAN :")
        setNumeric(sheet, totalRoundedRow, 4, allWeights.sumOf { it.third.rounded })

        workbook.setForceFormulaRecalculation(true)
        try {
            val outputStream = context.contentResolver.openOutputStream(outputUri)
                ?: throw java.io.IOException("Tidak bisa membuka output stream untuk URI tujuan")
            outputStream.use { workbook.write(it) }
        } finally {
            templateInputStream.close()
            workbook.close()
        }
    }

    private fun ensureRow(sheet: org.apache.poi.ss.usermodel.Sheet, rowIndex: Int): Row =
        sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)

    private fun getCell(sheet: org.apache.poi.ss.usermodel.Sheet, rowIndex: Int, colIndex: Int) =
        ensureRow(sheet, rowIndex).getCell(colIndex) ?: ensureRow(sheet, rowIndex).createCell(colIndex)

    private fun setString(sheet: org.apache.poi.ss.usermodel.Sheet, rowIndex: Int, colIndex: Int, value: String) {
        getCell(sheet, rowIndex, colIndex).setCellValue(value)
    }

    private fun setNumeric(sheet: org.apache.poi.ss.usermodel.Sheet, rowIndex: Int, colIndex: Int, value: Double) {
        val cell = getCell(sheet, rowIndex, colIndex)
        cell.setCellValue(value)
    }

    private fun unmergeIfMerged(sheet: org.apache.poi.ss.usermodel.Sheet, range: String) {
        sheet.mergedRegions.firstOrNull { it.formatAsString() == range }?.let { sheet.removeMergedRegion(sheet.mergedRegions.indexOf(it)) }
    }

    private fun cloneStyle(workbook: Workbook, source: CellStyle?): CellStyle? {
        if (source == null) return null
        val style = workbook.createCellStyle()
        style.cloneStyleFrom(source)
        return style
    }

    private fun copyRowStyle(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        sourceRowIndex: Int,
        targetRowIndex: Int,
        workbook: Workbook
    ) {
        val source = sheet.getRow(sourceRowIndex) ?: return
        val target = sheet.getRow(targetRowIndex) ?: sheet.createRow(targetRowIndex)
        target.height = source.height
        for (c in 0 until source.lastCellNum.coerceAtLeast(0)) {
            val src = source.getCell(c) ?: continue
            val dst = target.getCell(c) ?: target.createCell(c)
            dst.cellStyle = cloneStyle(workbook, src.cellStyle) ?: dst.cellStyle
        }
    }
}
