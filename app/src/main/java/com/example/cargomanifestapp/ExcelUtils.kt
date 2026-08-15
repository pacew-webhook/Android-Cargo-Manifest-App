package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.SheetVisibility
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

/**
 * V11 FIXED REV2
 *
 * Perbaikan utama:
 * 1. TOTAL Manifest dan TOTAL Stowing selalu dihitung ulang dari cargoList
 *    yang benar-benar diekspor.
 * 2. Nilai/formula total lama dari template dibersihkan sebelum ditulis.
 * 3. STOWING_DATA dibangun ulang dari cargoList sebagai sumber data persis.
 * 4. Penambahan baris otomatis tetap dipertahankan.
 * 5. Regex normalize menggunakan "\\\\s+" agar aman di Kotlin.
 */
object ExcelUtils {

    private val PAG_ROW_INDEXES = listOf(0, 10, 22, 33, 43, 56, 66, 77)

    fun writeCargoListToExcel(
        context: Context,
        uri: Uri,
        cargoList: List<CargoItem>
    ) {
        require(cargoList.isNotEmpty()) { "Data Stowing kosong" }

        val inputStream: InputStream =
            context.assets.open("STOWINGAN_PAG_TEMPLATE.xlsx")
        val workbook = inputStream.use { XSSFWorkbook(it) }

        try {
            fillStowingPagSheet(workbook.getSheetAt(0), cargoList)

            val dataSheet = getOrCreateStowingDataSheet(workbook)
            fillStowingDataSheet(dataSheet, cargoList)

            workbook.setSheetVisibility(
                workbook.getSheetIndex(dataSheet),
                SheetVisibility.VERY_HIDDEN
            )

            workbook.setForceFormulaRecalculation(true)
            saveWorkbook(context, uri, workbook)
        } finally {
            workbook.close()
        }
    }

    fun writeCombinedCargoWorkbook(
        context: Context,
        uri: Uri,
        cargoList: List<CargoItem>
    ) {
        require(cargoList.isNotEmpty()) { "Data Stowing kosong" }

        val manifestInput = context.assets.open("template_manifest.xlsx")
        val workbook = manifestInput.use { XSSFWorkbook(it) }

        try {
            val manifestSheet =
                workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)

            fillManifestSheet(workbook, manifestSheet, cargoList)

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
                    val targetName =
                        names.getOrElse(index) { "PAG TEMPLATE ${index + 1}" }

                    val targetSheet = workbook.createSheet(targetName)
                    copySheet(sourceSheet, targetSheet, workbook)

                    if (index == 0) {
                        fillStowingPagSheet(targetSheet, cargoList)
                    }
                }
            } finally {
                pagWorkbook.close()
            }

            val stowingDataSheet = getOrCreateStowingDataSheet(workbook)
            fillStowingDataSheet(stowingDataSheet, cargoList)

            workbook.setSheetVisibility(
                workbook.getSheetIndex(stowingDataSheet),
                SheetVisibility.VERY_HIDDEN
            )

            workbook.setForceFormulaRecalculation(true)
            saveWorkbook(context, uri, workbook)
        } finally {
            workbook.close()
        }
    }

    private fun getOrCreateStowingDataSheet(workbook: XSSFWorkbook): XSSFSheet {
        return workbook.getSheet("STOWING_DATA")
            ?: workbook.createSheet("STOWING_DATA")
    }

    /**
     * STOWING_DATA adalah sumber data internal yang harus identik dengan
     * cargoList saat export.
     *
     * Seluruh isi lama dibersihkan terlebih dahulu agar baris lama yang
     * jumlahnya lebih banyak tidak tertinggal dan kemudian terbaca lagi
     * ketika file di-import.
     */
    private fun fillStowingDataSheet(
        sheet: XSSFSheet,
        cargoList: List<CargoItem>
    ) {
        // Hapus SEMUA row lama dengan menghapus dari indeks terakhir
        // menuju indeks pertama. Menghapus dari belakang mencegah row
        // yang tersisa bergeser/tertinggal dan terbaca kembali saat import.
        for (r in sheet.lastRowNum downTo 0) {
            val row = sheet.getRow(r)
            if (row != null) {
                sheet.removeRow(row)
            }
        }

        // Setelah removeRow(), pastikan sheet benar-benar tidak menyimpan
        // baris lama yang masih terindeks.
        val headers = listOf(
            "No",
            "NO PAG",
            "PTI",
            "Customer",
            "Description",
            "Pcs/Cly",
            "Weight Detail",
            "Sub Total KG"
        )

        val header = sheet.createRow(0)
        headers.forEachIndexed { index, value ->
            header.createCell(index).setCellValue(value)
        }

        cargoList.forEachIndexed { index, item ->
            val row = sheet.createRow(index + 1)

            val values = listOf(
                (index + 1).toString(),
                item.noPag.trim(),
                item.pti.trim(),
                item.customer.trim(),
                item.description.trim(),
                item.pcsQty.trim(),
                item.weight.trim(),
                item.subTotal.trim()
            )

            values.forEachIndexed { col, value ->
                row.createCell(col).setCellValue(value)
            }
        }

        // Jangan biarkan row setelah data terakhir tetap berada di sheet.
        // Ini adalah pengaman tambahan terhadap data export lama.
        val expectedLastRow = cargoList.size
        for (r in sheet.lastRowNum downTo expectedLastRow + 1) {
            sheet.getRow(r)?.let { sheet.removeRow(it) }
        }

        val widths = intArrayOf(8, 18, 14, 20, 24, 12, 28, 16)
        widths.forEachIndexed { col, width ->
            sheet.setColumnWidth(col, width * 256)
        }

        sheet.createFreezePane(0, 1)
    }

    private fun fillManifestSheet(
        workbook: XSSFWorkbook,
        sheet: XSSFSheet,
        cargoList: List<CargoItem>
    ) {
        val startRow = 13
        val baseStowingTotalRow = 36
        val baseManifestTotalRow = 44

        // Snapshot supaya export konsisten walaupun list adalah SnapshotStateList.
        val manifestRows = cargoList.toList()

        val groupedStowing = cargoList
            .filter { it.noPag.isNotBlank() }
            .groupBy { normalize(it.noPag) }
            .map { (_, items) ->
                val totalNet = items.sumOf { parseWeight(it.subTotal) }

                val customers = items
                    .map { it.customer.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(4)

                val descriptions = items
                    .map { it.description.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(4)

                items.first().copy(
                    customer = customers.joinToString(" / "),
                    description = descriptions.joinToString(" / "),
                    subTotal = formatNumber(totalNet)
                )
            }

        val stowingCapacity = baseStowingTotalRow - startRow + 1
        val stowingExtra = maxOf(
            0,
            groupedStowing.size - stowingCapacity
        )

        if (stowingExtra > 0) {
            insertRowsBefore(
                sheet,
                baseStowingTotalRow,
                stowingExtra,
                baseStowingTotalRow - 1
            )
        }

        val manifestTotalRowAfterStowing =
            baseManifestTotalRow + stowingExtra

        val manifestCapacityAfterStowing =
            manifestTotalRowAfterStowing - startRow

        val manifestExtra = maxOf(
            0,
            manifestRows.size - manifestCapacityAfterStowing
        )

        if (manifestExtra > 0) {
            insertRowsBefore(
                sheet,
                manifestTotalRowAfterStowing,
                manifestExtra,
                manifestTotalRowAfterStowing - 1
            )
        }

        val finalStowingTotalRow =
            baseStowingTotalRow + stowingExtra

        val finalManifestTotalRow =
            manifestTotalRowAfterStowing + manifestExtra

        /*
         * Bersihkan area data DAN baris total sebelum menulis.
         *
         * Ini penting karena template dapat berisi formula/nilai lama.
         * Jika tidak dibersihkan, Excel dapat tetap menampilkan angka lama
         * walaupun cargoList sudah berubah.
         */
        clearDataArea(
            sheet,
            startRow,
            finalManifestTotalRow - 1,
            0,
            6
        )

        clearDataArea(
            sheet,
            startRow,
            finalStowingTotalRow - 1,
            7,
            12
        )

        clearCells(
            sheet.getRow(finalManifestTotalRow),
            0,
            6
        )

        clearCells(
            sheet.getRow(finalStowingTotalRow),
            7,
            12
        )

        val sampleRow = sheet.getRow(startRow)

        /*
         * TOTAL DIHITUNG DARI DATA YANG BENAR-BENAR DITULIS.
         *
         * Tidak menggunakan formula lama dari template.
         */
        var totalManifestPcs = 0.0
        var totalManifestWeight = 0.0
        var totalStowingNet = 0.0
        var totalStowingGross = 0.0

        val maxRows = maxOf(
            manifestRows.size,
            groupedStowing.size
        )

        for (i in 0 until maxRows) {
            val rowIndex = startRow + i
            val row =
                sheet.getRow(rowIndex)
                    ?: sheet.createRow(rowIndex)

            if (i < manifestRows.size) {
                val item = manifestRows[i]

                val pcs = parseWeight(item.pcsQty)
                val subtotal = parseWeight(item.subTotal)

                // Hanya dari item Manifest yang benar-benar ditulis.
                totalManifestPcs += pcs
                totalManifestWeight += subtotal

                setStyledNumericCell(
                    row,
                    0,
                    (i + 1).toDouble(),
                    sampleRow?.getCell(0)
                )

                setStyledTextCell(
                    row,
                    1,
                    item.pti,
                    sampleRow?.getCell(1)
                )

                setStyledNumericCell(
                    row,
                    2,
                    pcs,
                    sampleRow?.getCell(2)
                )

                val weightPerClyCell =
                    row.getCell(3)
                        ?: row.createCell(3)

                weightPerClyCell.setBlank()

                if (sampleRow != null) {
                    weightPerClyCell.cellStyle =
                        sampleRow.getCell(3).cellStyle
                }

                setStyledNumericCell(
                    row,
                    4,
                    subtotal,
                    sampleRow?.getCell(4)
                )

                setStyledTextCell(
                    row,
                    5,
                    item.description,
                    sampleRow?.getCell(5)
                )

                setStyledTextCell(
                    row,
                    6,
                    item.customer,
                    sampleRow?.getCell(6)
                )
            }

            if (i < groupedStowing.size) {
                val item = groupedStowing[i]

                val net = parseWeight(item.subTotal)
                val gross = net + 125.0

                // Hanya dari group Stowing yang benar-benar ditulis.
                totalStowingNet += net
                totalStowingGross += gross

                setStyledNumericCell(
                    row,
                    7,
                    (i + 1).toDouble(),
                    sampleRow?.getCell(7)
                )

                setStyledTextCell(
                    row,
                    8,
                    item.noPag,
                    sampleRow?.getCell(8)
                )

                setChecklistTextCell(
                    workbook,
                    row,
                    9,
                    item.description,
                    sampleRow?.getCell(9)
                )

                setStyledNumericCell(
                    row,
                    10,
                    net,
                    sampleRow?.getCell(10)
                )

                setStyledNumericCell(
                    row,
                    11,
                    gross,
                    sampleRow?.getCell(11)
                )

                setChecklistTextCell(
                    workbook,
                    row,
                    12,
                    item.customer,
                    sampleRow?.getCell(12)
                )
            }
        }

        /*
         * Tulis TOTAL FINAL sebagai numeric value.
         *
         * Kita sengaja menghapus formula lama sehingga tidak ada formula
         * template yang masih menunjuk range lama.
         */
        val manifestTotalRowObj =
            sheet.getRow(finalManifestTotalRow)
                ?: sheet.createRow(finalManifestTotalRow)

        clearCells(manifestTotalRowObj, 0, 12)

        setNumericCell(
            manifestTotalRowObj,
            2,
            totalManifestPcs
        )

        setNumericCell(
            manifestTotalRowObj,
            4,
            totalManifestWeight
        )

        val stowingTotalRowObj =
            sheet.getRow(finalStowingTotalRow)
                ?: sheet.createRow(finalStowingTotalRow)

        clearCells(stowingTotalRowObj, 0, 12)

        setNumericCell(
            stowingTotalRowObj,
            10,
            totalStowingNet
        )

        setNumericCell(
            stowingTotalRowObj,
            11,
            totalStowingGross
        )

        /*
         * Excel/Google Sheets tetap diberi tahu bahwa workbook boleh
         * melakukan recalculation jika ada formula lain di template.
         * TOTAL yang kita perbaiki sendiri tetap berupa angka final.
         */
        workbook.setForceFormulaRecalculation(true)
    }

    private fun insertRowsBefore(
        sheet: XSSFSheet,
        rowIndex: Int,
        count: Int,
        styleSourceRowIndex: Int
    ) {
        if (count <= 0) return

        val lastRow = sheet.lastRowNum

        if (rowIndex <= lastRow) {
            sheet.shiftRows(
                rowIndex,
                lastRow,
                count,
                true,
                false
            )
        }

        val sourceRow =
            sheet.getRow(styleSourceRowIndex)

        val columnsToCopy =
            maxOf(
                13,
                sourceRow?.lastCellNum?.toInt() ?: 0
            )

        for (i in 0 until count) {
            val newRowIndex = rowIndex + i

            val newRow =
                sheet.getRow(newRowIndex)
                    ?: sheet.createRow(newRowIndex)

            if (sourceRow != null) {
                newRow.height = sourceRow.height
                newRow.zeroHeight = sourceRow.zeroHeight
            }

            for (c in 0 until columnsToCopy) {
                val sourceCell = sourceRow?.getCell(c)

                val newCell =
                    newRow.getCell(c)
                        ?: newRow.createCell(c)

                if (sourceCell != null) {
                    newCell.cellStyle =
                        sourceCell.cellStyle
                }

                newCell.setBlank()
            }
        }
    }

    private fun clearDataArea(
        sheet: XSSFSheet,
        startRow: Int,
        endRow: Int,
        startCol: Int,
        endCol: Int
    ) {
        if (endRow < startRow) return

        for (r in startRow..endRow) {
            val row = sheet.getRow(r) ?: continue
            clearCells(row, startCol, endCol)
        }
    }

    private fun clearCells(
        row: Row?,
        startCol: Int,
        endCol: Int
    ) {
        if (row == null) return

        for (c in startCol..endCol) {
            val cell = row.getCell(c) ?: continue

            // Hapus formula/nilai lama tanpa menghapus style.
            cell.setBlank()
        }
    }

    private fun fillStowingPagSheet(
        sheet: XSSFSheet,
        cargoList: List<CargoItem>
    ) {
        if (cargoList.isEmpty()) return

        val groupedByPag = cargoList
            .groupBy { normalize(it.noPag) }
            .filterKeys { it.isNotBlank() }

        var pagBlockIndex = 0

        for ((noPag, itemsInPag) in groupedByPag) {
            if (pagBlockIndex >= PAG_ROW_INDEXES.size) break

            val startPagRowIndex =
                PAG_ROW_INDEXES[pagBlockIndex]

            val pagRow =
                sheet.getRow(startPagRowIndex)
                    ?: sheet.createRow(startPagRowIndex)

            val pagCell =
                pagRow.getCell(1)
                    ?: pagRow.createCell(1)

            pagCell.setCellValue(noPag)

            val totalPagKg =
                itemsInPag.sumOf {
                    parseWeight(it.subTotal)
                }

            val totalCell =
                pagRow.getCell(4)
                    ?: pagRow.createCell(4)

            totalCell.setCellValue(totalPagKg)

            val customerStartRow =
                startPagRowIndex + 2

            var currentStartCol = 0

            for (item in itemsInPag) {
                val custRow =
                    sheet.getRow(customerStartRow)
                        ?: sheet.createRow(customerStartRow)

                val custCell =
                    custRow.getCell(currentStartCol)
                        ?: custRow.createCell(currentStartCol)

                custCell.setCellValue(item.customer)

                val kgValues = item.weight
                    .split(",")
                    .mapNotNull {
                        parseWeight(it)
                            .takeIf { kg -> kg > 0.0 }
                    }

                var currentRow =
                    customerStartRow + 1

                var colOffset = 0
                var rowCountInCol = 0

                for (kg in kgValues) {
                    val r =
                        sheet.getRow(currentRow)
                            ?: sheet.createRow(currentRow)

                    val targetCol =
                        currentStartCol + colOffset

                    val c =
                        r.getCell(targetCol)
                            ?: r.createCell(targetCol)

                    c.setCellValue(kg)

                    currentRow++
                    rowCountInCol++

                    if (rowCountInCol >= 5) {
                        rowCountInCol = 0
                        colOffset++
                        currentRow =
                            customerStartRow + 1
                    }
                }

                currentStartCol += 6
            }

            pagBlockIndex++
        }
    }

    private fun copySheet(
        source: Sheet,
        target: XSSFSheet,
        targetWorkbook: XSSFWorkbook
    ) {
        val maxColumns =
            (0..source.lastRowNum).maxOfOrNull {
                source.getRow(it)
                    ?.lastCellNum
                    ?.toInt()
                    ?: 0
            } ?: 0

        for (c in 0 until maxColumns) {
            val width =
                source.getColumnWidth(c)

            if (width > 0) {
                target.setColumnWidth(c, width)
            }

            target.setColumnHidden(
                c,
                source.isColumnHidden(c)
            )
        }

        target.defaultRowHeight =
            source.defaultRowHeight

        target.defaultColumnWidth =
            source.defaultColumnWidth

        val styleCache =
            mutableMapOf<Short, XSSFCellStyle>()

        for (rIndex in 0..source.lastRowNum) {
            val srcRow =
                source.getRow(rIndex)
                    ?: continue

            val dstRow =
                target.createRow(rIndex)

            dstRow.height =
                srcRow.height

            dstRow.zeroHeight =
                srcRow.zeroHeight

            for (cIndex in 0 until maxColumns) {
                val srcCell =
                    srcRow.getCell(cIndex)
                        ?: continue

                val dstCell =
                    dstRow.createCell(cIndex)

                copyCellValue(
                    srcCell,
                    dstCell
                )

                val styleIndex =
                    srcCell.cellStyle.index

                val copiedStyle =
                    styleCache.getOrPut(styleIndex) {
                        targetWorkbook
                            .createCellStyle()
                            .also {
                                it.cloneStyleFrom(
                                    srcCell.cellStyle
                                )
                            }
                    }

                dstCell.cellStyle =
                    copiedStyle
            }
        }

        for (mergedRegion in source.mergedRegions) {
            target.addMergedRegion(
                mergedRegion.copy()
            )
        }
    }

    private fun copyCellValue(
        src: Cell,
        dst: Cell
    ) {
        when (src.cellType) {
            org.apache.poi.ss.usermodel.CellType.STRING ->
                dst.setCellValue(src.stringCellValue)

            org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                dst.setCellValue(src.numericCellValue)

            org.apache.poi.ss.usermodel.CellType.BOOLEAN ->
                dst.setCellValue(src.booleanCellValue)

            org.apache.poi.ss.usermodel.CellType.FORMULA ->
                dst.cellFormula = src.cellFormula

            org.apache.poi.ss.usermodel.CellType.ERROR ->
                dst.setCellErrorValue(src.errorCellValue)

            org.apache.poi.ss.usermodel.CellType.BLANK ->
                dst.setBlank()

            else ->
                dst.setBlank()
        }
    }

    private fun setChecklistTextCell(
        workbook: XSSFWorkbook,
        row: Row,
        col: Int,
        value: String,
        sample: Cell?
    ) {
        val cell =
            row.getCell(col)
                ?: row.createCell(col)

        if (sample != null) {
            val style =
                workbook.createCellStyle()

            style.cloneStyleFrom(
                sample.cellStyle
            )

            style.setWrapText(true)
            style.setShrinkToFit(true)

            cell.cellStyle = style
        }

        cell.setCellValue(value)

        if (row.height.toInt() < 420) {
            row.height = 420
        }
    }

    private fun setStyledTextCell(
        row: Row,
        col: Int,
        value: String,
        sample: Cell?
    ) {
        val cell =
            row.getCell(col)
                ?: row.createCell(col)

        if (sample != null) {
            cell.cellStyle =
                sample.cellStyle
        }

        cell.setCellValue(value)
    }

    private fun setStyledNumericCell(
        row: Row,
        col: Int,
        value: Double,
        sample: Cell?
    ) {
        val cell =
            row.getCell(col)
                ?: row.createCell(col)

        if (sample != null) {
            cell.cellStyle =
                sample.cellStyle
        }

        cell.setCellValue(value)
    }

    private fun setNumericCell(
        row: Row,
        col: Int,
        value: Double
    ) {
        val cell =
            row.getCell(col)
                ?: row.createCell(col)

        cell.setCellValue(value)
    }

    private fun normalize(value: String): String {
        return value
            .trim()
            .replace("\\s+".toRegex(), " ")
            .uppercase()
    }

    private fun parseWeight(value: String): Double {
        val raw =
            value.trim().replace(" ", "")

        if (raw.isBlank()) return 0.0

        return when {
            raw.contains(".") &&
                raw.contains(",") -> {
                raw.replace(".", "")
                    .replace(",", ".")
                    .toDoubleOrNull()
                    ?: 0.0
            }

            raw.count { it == ',' } == 1 -> {
                val commaIndex =
                    raw.indexOf(',')

                val digitsAfter =
                    raw.length -
                        commaIndex -
                        1

                if (digitsAfter in 1..2) {
                    raw.replace(',', '.')
                        .toDoubleOrNull()
                        ?: 0.0
                } else {
                    raw.replace(",", "")
                        .toDoubleOrNull()
                        ?: 0.0
                }
            }

            raw.count { it == '.' } == 1 -> {
                val dotIndex =
                    raw.indexOf('.')

                val digitsAfter =
                    raw.length -
                        dotIndex -
                        1

                if (digitsAfter in 1..2) {
                    raw.toDoubleOrNull()
                        ?: 0.0
                } else {
                    raw.replace(".", "")
                        .toDoubleOrNull()
                        ?: 0.0
                }
            }

            else ->
                raw.toDoubleOrNull()
                    ?: 0.0
        }
    }

    private fun formatNumber(
        value: Double
    ): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    private fun saveWorkbook(
        context: Context,
        uri: Uri,
        workbook: Workbook
    ) {
        val outputStream: OutputStream =
            context.contentResolver
                .openOutputStream(uri)
                ?: throw java.io.IOException(
                    "Tidak bisa membuka output stream untuk URI tujuan"
                )

        outputStream.use {
            workbook.write(it)
        }
    }
}
