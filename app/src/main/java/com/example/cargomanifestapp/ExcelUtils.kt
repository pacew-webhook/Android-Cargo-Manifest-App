package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

object ExcelUtils {

    private val PAG_ROW_INDEXES = listOf(0, 10, 22, 33, 43, 56, 66, 77)

    /**
     * Export lama untuk kebutuhan Stowing saja. Tetap dipertahankan agar kompatibel.
     */
    fun writeCargoListToExcel(context: Context, uri: Uri, cargoList: List<CargoItem>) {
        val inputStream: InputStream = context.assets.open("STOWINGAN_PAG_TEMPLATE.xlsx")
        val workbook = inputStream.use { XSSFWorkbook(it) }
        try {
            fillStowingPagSheet(workbook.getSheetAt(0), cargoList)
            saveWorkbook(context, uri, workbook)
        } finally {
            workbook.close()
        }
    }

    /**
     * Export utama V4:
     * - 1 file Excel
     * - Sheet Manifest tetap memakai template aplikasi
     * - Manifest otomatis di-group berdasarkan CUSTOMER + DESCRIPTION
     * - Stowing Checklist di sisi kanan Manifest diisi dari data PAG
     * - Template STOWINGAN PAG ikut dimasukkan ke workbook yang sama
     * - Detail per PAG tetap dipertahankan untuk pengecekan LOOT
     */
    fun writeCombinedCargoWorkbook(context: Context, uri: Uri, cargoList: List<CargoItem>) {
        require(cargoList.isNotEmpty()) { "Data Stowing kosong" }

        val manifestInput = context.assets.open("template_manifest.xlsx")
        val workbook = manifestInput.use { XSSFWorkbook(it) }

        try {
            val manifestSheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
            fillManifestSheet(manifestSheet, cargoList)

            // Salin seluruh workbook template Stowingan PAG ke workbook Manifest.
            // Prefix nama sheet mencegah benturan dengan Sheet1/Sheet2 milik template Manifest.
            val pagInput = context.assets.open("STOWINGAN_PAG_TEMPLATE.xlsx")
            val pagWorkbook = pagInput.use { XSSFWorkbook(it) }
            try {
                val names = listOf(
                    "STOWINGAN PAG",
                    "PAG LOOT",
                    "PAG DATA",
                    "STOWING CHECK",
                    "BARANG KOLIAN"
                )
                pagWorkbook.sheetIterator().asSequence().forEachIndexed { index, sourceSheet ->
                    val targetName = names.getOrElse(index) { "PAG TEMPLATE ${index + 1}" }
                    val targetSheet = workbook.createSheet(targetName)
                    copySheet(sourceSheet, targetSheet, workbook)
                    if (index == 0) fillStowingPagSheet(targetSheet, cargoList)
                }
            } finally {
                pagWorkbook.close()
            }

            saveWorkbook(context, uri, workbook)
        } finally {
            workbook.close()
        }
    }

    private fun fillManifestSheet(sheet: XSSFSheet, cargoList: List<CargoItem>) {
        val startRow = 13 // Excel row 14, 0-based
        val templateCapacity = 24

        // Grouping mengikuti proses kerja: Customer + Description menjadi 1 baris Manifest.
        val groupedManifest = cargoList
            .filter { it.customer.isNotBlank() && it.description.isNotBlank() }
            .groupBy {
                "${normalize(it.customer)}|${normalize(it.description)}"
            }
            .map { (_, items) ->
                val pcs = items.sumOf { it.pcsQty.toDoubleOrNull() ?: 0.0 }
                val totalWeight = items.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
                val ptis = items.map { it.pti.trim() }.filter { it.isNotBlank() }.distinct()
                items.first().copy(
                    pti = ptis.firstOrNull() ?: "",
                    pcsQty = formatNumber(pcs),
                    subTotal = formatNumber(totalWeight),
                    weight = if (pcs > 0) formatNumber(totalWeight / pcs) else ""
                )
            }

        // Stowing checklist: 1 baris per PAG + Customer + Description.
        val groupedStowing = cargoList
            .filter { it.noPag.isNotBlank() }
            .groupBy {
                "${normalize(it.noPag)}|${normalize(it.customer)}|${normalize(it.description)}"
            }
            .map { (_, items) ->
                val totalNet = items.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
                items.first().copy(subTotal = formatNumber(totalNet))
            }

        // Bersihkan area data lama agar export tidak mencampur data sebelumnya.
        val clearUntil = maxOf(sheet.lastRowNum, startRow + templateCapacity + 40)
        for (r in startRow..clearUntil) {
            val row = sheet.getRow(r) ?: continue
            for (c in 0..12) row.getCell(c)?.setBlank()
        }

        val maxRows = maxOf(groupedManifest.size, groupedStowing.size)
        if (maxRows > templateCapacity) {
            val extra = maxRows - templateCapacity
            // Geser footer mulai dari baris 38. Ini memperbaiki masalah output lama yang
            // membuat footer/merged cells bertabrakan ketika data lebih dari kapasitas template.
            sheet.shiftRows(startRow + templateCapacity, sheet.lastRowNum, extra, true, false)
        }

        val sampleRow = sheet.getRow(startRow)
        var totalManifestPcs = 0.0
        var totalManifestWeight = 0.0
        var totalStowingNet = 0.0
        var totalStowingGross = 0.0

        for (i in 0 until maxRows) {
            val rowIndex = startRow + i
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)

            if (i < groupedManifest.size) {
                val item = groupedManifest[i]
                val pcs = item.pcsQty.toDoubleOrNull() ?: 0.0
                val subtotal = item.subTotal.toDoubleOrNull() ?: 0.0
                totalManifestPcs += pcs
                totalManifestWeight += subtotal

                setStyledNumericCell(row, 0, (i + 1).toDouble(), sampleRow?.getCell(0))
                setStyledTextCell(row, 1, item.pti, sampleRow?.getCell(1))
                setStyledNumericCell(row, 2, pcs, sampleRow?.getCell(2))
                setStyledNumericCell(row, 3, item.weight.toDoubleOrNull() ?: 0.0, sampleRow?.getCell(3))
                setStyledNumericCell(row, 4, subtotal, sampleRow?.getCell(4))
                setStyledTextCell(row, 5, item.description, sampleRow?.getCell(5))
                setStyledTextCell(row, 6, item.customer, sampleRow?.getCell(6))
            }

            if (i < groupedStowing.size) {
                val item = groupedStowing[i]
                val net = item.subTotal.toDoubleOrNull() ?: 0.0
                val gross = net + 125.0
                totalStowingNet += net
                totalStowingGross += gross

                setStyledNumericCell(row, 7, (i + 1).toDouble(), sampleRow?.getCell(7))
                setStyledTextCell(row, 8, item.noPag, sampleRow?.getCell(8))
                setStyledTextCell(row, 9, item.description, sampleRow?.getCell(9))
                setStyledNumericCell(row, 10, net, sampleRow?.getCell(10))
                setStyledNumericCell(row, 11, gross, sampleRow?.getCell(11))
                setStyledTextCell(row, 12, item.customer, sampleRow?.getCell(12))
            }
        }

        val totalRowIndex = startRow + maxRows
        val totalRow = sheet.getRow(totalRowIndex) ?: sheet.createRow(totalRowIndex)
        setNumericCell(totalRow, 2, totalManifestPcs)
        setNumericCell(totalRow, 4, totalManifestWeight)
        setNumericCell(totalRow, 10, totalStowingNet)
        setNumericCell(totalRow, 11, totalStowingGross)
    }

    private fun fillStowingPagSheet(sheet: XSSFSheet, cargoList: List<CargoItem>) {
        if (cargoList.isEmpty()) return
        val groupedByPag = cargoList.groupBy { it.noPag.trim() }.filterKeys { it.isNotBlank() }
        var pagBlockIndex = 0

        for ((noPag, itemsInPag) in groupedByPag) {
            if (pagBlockIndex >= PAG_ROW_INDEXES.size) break
            val startPagRowIndex = PAG_ROW_INDEXES[pagBlockIndex]
            val pagRow = sheet.getRow(startPagRowIndex) ?: sheet.createRow(startPagRowIndex)
            val pagCell = pagRow.getCell(1) ?: pagRow.createCell(1)
            pagCell.setCellValue(noPag)

            val totalPagKg = itemsInPag.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
            val totalCell = pagRow.getCell(4) ?: pagRow.createCell(4)
            totalCell.setCellValue(totalPagKg)

            val customerStartRow = startPagRowIndex + 2
            var currentStartCol = 0
            for (item in itemsInPag) {
                val custRow = sheet.getRow(customerStartRow) ?: sheet.createRow(customerStartRow)
                val custCell = custRow.getCell(currentStartCol) ?: custRow.createCell(currentStartCol)
                custCell.setCellValue(item.customer)

                val kgValues = item.weight.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                var currentRow = customerStartRow + 1
                var colOffset = 0
                var rowCountInCol = 0
                for (kg in kgValues) {
                    val r = sheet.getRow(currentRow) ?: sheet.createRow(currentRow)
                    val targetCol = currentStartCol + colOffset
                    val c = r.getCell(targetCol) ?: r.createCell(targetCol)
                    c.setCellValue(kg)
                    currentRow++
                    rowCountInCol++
                    if (rowCountInCol >= 5) {
                        rowCountInCol = 0
                        colOffset++
                        currentRow = customerStartRow + 1
                    }
                }
                currentStartCol += 6
            }
            pagBlockIndex++
        }
    }

    private fun copySheet(source: XSSFSheet, target: XSSFSheet, targetWorkbook: XSSFWorkbook) {
        val maxColumns = (0..source.lastRowNum).maxOfOrNull { source.getRow(it)?.lastCellNum?.toInt() ?: 0 } ?: 0
        for (c in 0 until maxColumns) {
            val width = source.getColumnWidth(c)
            if (width > 0) target.setColumnWidth(c, width)
            target.setColumnHidden(c, source.isColumnHidden(c))
        }
        target.defaultRowHeight = source.defaultRowHeight
        target.defaultColumnWidth = source.defaultColumnWidth

        val styleCache = mutableMapOf<Short, XSSFCellStyle>()
        for (rIndex in 0..source.lastRowNum) {
            val srcRow = source.getRow(rIndex) ?: continue
            val dstRow = target.createRow(rIndex)
            dstRow.height = srcRow.height
            dstRow.hidden = srcRow.hidden

            for (cIndex in 0 until maxColumns) {
                val srcCell = srcRow.getCell(cIndex) ?: continue
                val dstCell = dstRow.createCell(cIndex)
                copyCellValue(srcCell, dstCell)
                if (srcCell.cellStyle != null) {
                    val styleIndex = srcCell.cellStyle.index
                    val copiedStyle = styleCache.getOrPut(styleIndex) {
                        targetWorkbook.createCellStyle().also { it.cloneStyleFrom(srcCell.cellStyle) }
                    }
                    dstCell.cellStyle = copiedStyle
                }
            }
        }

        for (merged in source.mergedRegions) {
            target.addMergedRegion(merged.copy())
        }
        target.sheetFormatPr.defaultRowHeight = source.sheetFormatPr.defaultRowHeight
    }

    private fun copyCellValue(src: Cell, dst: Cell) {
        when (src.cellType) {
            org.apache.poi.ss.usermodel.CellType.STRING -> dst.setCellValue(src.stringCellValue)
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> dst.setCellValue(src.numericCellValue)
            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> dst.setCellValue(src.booleanCellValue)
            org.apache.poi.ss.usermodel.CellType.FORMULA -> dst.cellFormula = src.cellFormula
            org.apache.poi.ss.usermodel.CellType.ERROR -> dst.setCellErrorValue(src.errorCellValue)
            org.apache.poi.ss.usermodel.CellType.BLANK -> dst.setBlank()
            else -> dst.setBlank()
        }
    }

    private fun setStyledTextCell(row: Row, col: Int, value: String, sample: Cell?) {
        val cell = row.getCell(col) ?: row.createCell(col)
        if (sample != null) cell.cellStyle = sample.cellStyle
        cell.setCellValue(value)
    }

    private fun setStyledNumericCell(row: Row, col: Int, value: Double, sample: Cell?) {
        val cell = row.getCell(col) ?: row.createCell(col)
        if (sample != null) cell.cellStyle = sample.cellStyle
        cell.setCellValue(value)
    }

    private fun setNumericCell(row: Row, col: Int, value: Double) {
        val cell = row.getCell(col) ?: row.createCell(col)
        cell.setCellValue(value)
    }

    private fun normalize(value: String): String = value.trim().replace("\\s+".toRegex(), " ").uppercase()

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun saveWorkbook(context: Context, uri: Uri, workbook: Workbook) {
        val outputStream: OutputStream = context.contentResolver.openOutputStream(uri)
            ?: throw java.io.IOException("Tidak bisa membuka output stream untuk URI tujuan")
        outputStream.use { workbook.write(it) }
    }
}
