package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.SheetVisibility
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFDrawing
import org.apache.poi.xssf.usermodel.XSSFPicture
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

/**
 * ExcelUtils_V17_DYNAMIC_FOOTER
 *
 * Perbaikan utama:
 * 1. Data Manifest dan Stowing Checklist tetap mengikuti cargoList terbaru.
 * 2. Total dihitung dari data yang benar-benar ditulis.
 * 3. STOWING_DATA selalu ditulis ulang dari cargoList terbaru.
 * 4. Baris kosong yang muncul di sekitar row 37 tidak dibuat secara paksa.
 * 5. Penyisipan baris hanya dilakukan jika kapasitas benar-benar kurang.
 * 6. Row total diposisikan berdasarkan jumlah data aktual.
 * 7. Tidak membuat "baris data kosong" hanya karena sisi Manifest dan Stowing
 *    memiliki jumlah baris yang berbeda.
 * 8. Formula TOTAL Manifest/Stowing mengikuti baris data aktual.
 * 9. Footer Prepared/Approved dan foto tanda tangan mengikuti posisi TOTAL baru.
 * 10. Import file hasil export menggunakan STOWING_DATA untuk menjaga detail cargo.
 */
object ExcelUtils {

    private val PAG_ROW_INDEXES = listOf(0, 10, 22, 33, 43, 56, 66, 77)

    // Posisi 0-based dari template Manifest asli.
    private const val BASE_STOWING_TOTAL_ROW = 36
    private const val STOWING_FOOTER_BASE_ROW = 39
    private const val BASE_MANIFEST_TOTAL_ROW = 44
    private const val MANIFEST_FOOTER_BASE_ROW = 47

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

    private fun fillStowingDataSheet(
        sheet: XSSFSheet,
        cargoList: List<CargoItem>
    ) {
        // Hapus isi lama dan row sisa agar hasil export tidak membawa
        // data dari export sebelumnya.
        val oldLast = sheet.lastRowNum
        if (oldLast >= 0) {
            for (r in oldLast downTo 1) {
                sheet.removeRow(sheet.getRow(r))
            }
        }

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

        val header = sheet.getRow(0) ?: sheet.createRow(0)

        headers.forEachIndexed { index, value ->
            val cell = header.getCell(index) ?: header.createCell(index)
            cell.setCellValue(value)
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

        // Baris total asli template (0-based).
        // Data Stowing tersedia pada row 14..37 Excel.
        val baseStowingTotalRow = BASE_STOWING_TOTAL_ROW

        // Baris total Manifest asli template (0-based).
        val baseManifestTotalRow = BASE_MANIFEST_TOTAL_ROW

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

        /*
         * FIX ROW 37:
         *
         * Jangan lagi memakai maxRows untuk menulis Manifest dan Stowing
         * secara bersamaan. Jika jumlah Manifest lebih banyak daripada
         * Stowing, loop gabungan sebelumnya dapat meninggalkan row kosong
         * pada area Stowing. Sebaliknya, kedua area sekarang ditulis
         * independen.
         *
         * Dengan demikian row 37 hanya berisi total Stowing jika memang
         * posisi totalnya di sana, bukan baris data kosong.
         */

        val stowingCapacity =
            baseStowingTotalRow - startRow

        val stowingExtra =
            maxOf(0, groupedStowing.size - stowingCapacity)

        val stowingTotalRow =
            baseStowingTotalRow + stowingExtra

        /*
         * Posisi total Manifest ikut bergeser ketika row Stowing disisipkan.
         */
        val manifestBaseAfterStowing =
            baseManifestTotalRow + stowingExtra

        val manifestCapacity =
            manifestBaseAfterStowing - startRow

        val manifestExtra =
            maxOf(0, manifestRows.size - manifestCapacity)

        val manifestTotalRow =
            manifestBaseAfterStowing + manifestExtra

        /*
         * Jangan menggunakan XSSFSheet.shiftRows() di sini.
         * Template Manifest memiliki drawing/image anchor (tanda tangan).
         * Apache POI 5.2.3 dapat melempar NullPointerException pada
         * CTTwoCellAnchor.setFrom() ketika shiftRows mencoba memindahkan
         * drawing tertentu dari template.
         *
         * Sebagai gantinya footer dipindahkan secara manual: cell, style,
         * tinggi row, merged region, lalu anchor gambar diposisikan ulang.
         * Dengan cara ini data boleh melewati kapasitas template tanpa
         * menyentuh mekanisme shift drawing POI.
         */
        if (manifestExtra > 0) {
            moveFooterBlock(
                sheet = sheet,
                sourceStartRow = BASE_MANIFEST_TOTAL_ROW,
                sourceEndRow = MANIFEST_FOOTER_BASE_ROW + 8,
                targetStartRow = manifestTotalRow
            )
        }

        if (stowingExtra > 0) {
            moveFooterBlock(
                sheet = sheet,
                sourceStartRow = BASE_STOWING_TOTAL_ROW,
                sourceEndRow = STOWING_FOOTER_BASE_ROW + 4,
                targetStartRow = stowingTotalRow
            )
        }

        /*
         * Bersihkan hanya area DATA.
         *
         * Penting: jangan membersihkan sampai row total secara membabi buta
         * pada sisi Stowing setelah Manifest mempunyai jumlah baris berbeda.
         */
        clearDataArea(
            sheet = sheet,
            startRow = startRow,
            endRow = stowingTotalRow - 1,
            startCol = 7,
            endCol = 12
        )

        clearDataArea(
            sheet = sheet,
            startRow = startRow,
            endRow = manifestTotalRow - 1,
            startCol = 0,
            endCol = 6
        )

        val sampleRow = sheet.getRow(startRow)

        /*
         * Tulis Manifest SECARA TERPISAH.
         * Tidak ada lagi row kosong yang dibuat karena maxOf().
         */
        var totalManifestPcs = 0.0
        var totalManifestWeight = 0.0

        manifestRows.forEachIndexed { index, item ->
            val rowIndex = startRow + index
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)

            val pcs = parseWeight(item.pcsQty)
            val subtotal = parseWeight(item.subTotal)

            totalManifestPcs += pcs
            totalManifestWeight += subtotal

            setStyledNumericCell(
                row, 0, (index + 1).toDouble(), sampleRow?.getCell(0)
            )
            setStyledTextCell(
                row, 1, item.pti, sampleRow?.getCell(1)
            )
            setStyledNumericCell(
                row, 2, pcs, sampleRow?.getCell(2)
            )

            val weightPerClyCell =
                row.getCell(3) ?: row.createCell(3)

            weightPerClyCell.setBlank()

            if (sampleRow != null) {
                weightPerClyCell.cellStyle =
                    sampleRow.getCell(3).cellStyle
            }

            setStyledNumericCell(
                row, 4, subtotal, sampleRow?.getCell(4)
            )
            setStyledTextCell(
                row, 5, item.description, sampleRow?.getCell(5)
            )
            setStyledTextCell(
                row, 6, item.customer, sampleRow?.getCell(6)
            )
        }

        /*
         * Tulis Stowing SECARA TERPISAH.
         * Ini inti perbaikan row kosong.
         */
        var totalStowingNet = 0.0
        var totalStowingGross = 0.0

        groupedStowing.forEachIndexed { index, item ->
            val rowIndex = startRow + index
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)

            val net = parseWeight(item.subTotal)
            val gross = net + 125.0

            totalStowingNet += net
            totalStowingGross += gross

            setStyledNumericCell(
                row, 7, (index + 1).toDouble(), sampleRow?.getCell(7)
            )
            setStyledTextCell(
                row, 8, item.noPag, sampleRow?.getCell(8)
            )
            setChecklistTextCell(
                workbook,
                row,
                9,
                item.description,
                sampleRow?.getCell(9)
            )
            setStyledNumericCell(
                row, 10, net, sampleRow?.getCell(10)
            )
            setStyledNumericCell(
                row, 11, gross, sampleRow?.getCell(11)
            )
            setChecklistTextCell(
                workbook,
                row,
                12,
                item.customer,
                sampleRow?.getCell(12)
            )
        }

        /*
         * TOTAL MANIFEST
         */
        val manifestTotalRowObj =
            sheet.getRow(manifestTotalRow)
                ?: sheet.createRow(manifestTotalRow)

        // TOTAL MANIFEST dibuat sebagai formula dinamis agar selalu mengikuti
        // baris data aktual. Formula template lama seperti E14:E44 tidak lagi
        // dipertahankan ketika jumlah data melewati kapasitas template.
        setFormulaCell(
            manifestTotalRowObj,
            2,
            "SUM(C${startRow + 1}:C${manifestTotalRow})"
        )

        manifestTotalRowObj
            .getCell(3)
            ?.setBlank()

        setFormulaCell(
            manifestTotalRowObj,
            4,
            "SUM(E${startRow + 1}:E${manifestTotalRow})"
        )

        /*
         * TOTAL STOWING juga dibuat dinamis.
         */
        val stowingTotalRowObj =
            sheet.getRow(stowingTotalRow)
                ?: sheet.createRow(stowingTotalRow)

        setFormulaCell(
            stowingTotalRowObj,
            10,
            "SUM(K${startRow + 1}:K${stowingTotalRow})"
        )

        setFormulaCell(
            stowingTotalRowObj,
            11,
            "SUM(L${startRow + 1}:L${stowingTotalRow})"
        )

        /*
         * Footer template ikut bergerak bersama data. Row teks biasanya sudah
         * ikut dipindahkan oleh shiftRows(), tetapi gambar Excel mempunyai
         * anchor drawing sendiri, sehingga harus diposisikan ulang secara
         * eksplisit.
         *
         * Ada dua footer di template Manifest:
         * 1) footer Stowing Checklist: total + 3 baris
         * 2) footer Manifest: total Manifest + 3 baris
         */
        val stowingFooterRow = stowingTotalRow + (STOWING_FOOTER_BASE_ROW - BASE_STOWING_TOTAL_ROW)
        val manifestFooterRow = manifestTotalRow + (MANIFEST_FOOTER_BASE_ROW - BASE_MANIFEST_TOTAL_ROW)

        repositionManifestFooterPictures(
            sheet = sheet,
            stowingFooterRow = stowingFooterRow,
            manifestFooterRow = manifestFooterRow
        )

        workbook.setForceFormulaRecalculation(true)
    }

    private data class FooterCellSnapshot(
        val sourceRowOffset: Int,
        val column: Int,
        val cellType: org.apache.poi.ss.usermodel.CellType,
        val stringValue: String?,
        val numericValue: Double?,
        val booleanValue: Boolean?,
        val errorValue: Byte?,
        val formula: String?,
        val style: XSSFCellStyle
    )

    private data class FooterRowSnapshot(
        val rowOffset: Int,
        val height: Short,
        val zeroHeight: Boolean
    )

    private data class FooterMergeSnapshot(
        val firstRowOffset: Int,
        val lastRowOffset: Int,
        val firstColumn: Int,
        val lastColumn: Int
    )

    private fun moveFooterBlock(
        sheet: XSSFSheet,
        sourceStartRow: Int,
        sourceEndRow: Int,
        targetStartRow: Int
    ) {
        if (sourceStartRow == targetStartRow) return

        val rowSnapshots = mutableListOf<FooterRowSnapshot>()
        val cellSnapshots = mutableListOf<FooterCellSnapshot>()

        for (rowIndex in sourceStartRow..sourceEndRow) {
            val row = sheet.getRow(rowIndex)
            rowSnapshots += FooterRowSnapshot(
                rowOffset = rowIndex - sourceStartRow,
                height = row?.height ?: sheet.defaultRowHeight,
                zeroHeight = row?.zeroHeight ?: false
            )

            if (row != null) {
                for (cell in row) {
                    val type = cell.cellType
                    cellSnapshots += FooterCellSnapshot(
                        sourceRowOffset = rowIndex - sourceStartRow,
                        column = cell.columnIndex,
                        cellType = type,
                        stringValue = if (type == org.apache.poi.ss.usermodel.CellType.STRING) cell.stringCellValue else null,
                        numericValue = if (type == org.apache.poi.ss.usermodel.CellType.NUMERIC) cell.numericCellValue else null,
                        booleanValue = if (type == org.apache.poi.ss.usermodel.CellType.BOOLEAN) cell.booleanCellValue else null,
                        errorValue = if (type == org.apache.poi.ss.usermodel.CellType.ERROR) cell.errorCellValue else null,
                        formula = if (type == org.apache.poi.ss.usermodel.CellType.FORMULA) cell.cellFormula else null,
                        style = cell.cellStyle as XSSFCellStyle
                    )
                }
            }
        }

        val mergedSnapshots = sheet.mergedRegions
            .filter { region ->
                region.firstRow >= sourceStartRow &&
                    region.lastRow <= sourceEndRow
            }
            .map { region ->
                FooterMergeSnapshot(
                    firstRowOffset = region.firstRow - sourceStartRow,
                    lastRowOffset = region.lastRow - sourceStartRow,
                    firstColumn = region.firstColumn,
                    lastColumn = region.lastColumn
                )
            }

        // Hapus merge source terlebih dahulu agar target dapat menggunakan
        // cell-cell yang mungkin sebelumnya merupakan MergedCell.
        for (i in sheet.mergedRegions.indices.reversed()) {
            val region = sheet.mergedRegions[i]
            if (region.firstRow >= sourceStartRow && region.lastRow <= sourceEndRow) {
                sheet.removeMergedRegion(i)
            }
        }

        // Hapus merge yang berada tepat di area target.
        for (i in sheet.mergedRegions.indices.reversed()) {
            val region = sheet.mergedRegions[i]
            val overlapsTarget =
                region.firstRow <= targetStartRow + (sourceEndRow - sourceStartRow) &&
                    region.lastRow >= targetStartRow
            if (overlapsTarget) {
                sheet.removeMergedRegion(i)
            }
        }

        // Bersihkan cell source dan target sebelum menulis snapshot.
        for (rowIndex in sourceStartRow..sourceEndRow) {
            val row = sheet.getRow(rowIndex) ?: continue
            for (cell in row) {
                cell.setBlank()
            }
        }

        val targetEndRow = targetStartRow + (sourceEndRow - sourceStartRow)
        for (rowIndex in targetStartRow..targetEndRow) {
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            for (cell in row) {
                cell.setBlank()
            }
        }

        rowSnapshots.forEach { snapshot ->
            val rowIndex = targetStartRow + snapshot.rowOffset
            val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            row.height = snapshot.height
            row.zeroHeight = snapshot.zeroHeight
        }

        cellSnapshots.forEach { snapshot ->
            val row = sheet.getRow(targetStartRow + snapshot.sourceRowOffset)
                ?: sheet.createRow(targetStartRow + snapshot.sourceRowOffset)
            val cell = row.getCell(snapshot.column) ?: row.createCell(snapshot.column)
            cell.cellStyle = snapshot.style

            when (snapshot.cellType) {
                org.apache.poi.ss.usermodel.CellType.STRING -> cell.setCellValue(snapshot.stringValue ?: "")
                org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.setCellValue(snapshot.numericValue ?: 0.0)
                org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.setCellValue(snapshot.booleanValue ?: false)
                org.apache.poi.ss.usermodel.CellType.ERROR -> cell.setCellErrorValue(snapshot.errorValue ?: 0)
                org.apache.poi.ss.usermodel.CellType.FORMULA -> cell.cellFormula = snapshot.formula ?: ""
                org.apache.poi.ss.usermodel.CellType.BLANK -> cell.setBlank()
                else -> cell.setBlank()
            }
        }

        mergedSnapshots.forEach { snapshot ->
            sheet.addMergedRegion(
                org.apache.poi.ss.util.CellRangeAddress(
                    targetStartRow + snapshot.firstRowOffset,
                    targetStartRow + snapshot.lastRowOffset,
                    snapshot.firstColumn,
                    snapshot.lastColumn
                )
            )
        }
    }

    private fun clearDataArea(
        sheet: Sheet,
        startRow: Int,
        endRow: Int,
        startCol: Int,
        endCol: Int
    ) {
        if (endRow < startRow) return

        for (r in startRow..endRow) {
            val row = sheet.getRow(r) ?: continue

            for (c in startCol..endCol) {
                row.getCell(c)?.setBlank()
            }
        }
    }

    private fun fillStowingPagSheet(
        sheet: XSSFSheet,
        cargoList: List<CargoItem>
    ) {
        if (cargoList.isEmpty()) return

        val groupedByPag = cargoList
            .groupBy { it.noPag.trim() }
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
            val width = source.getColumnWidth(c)

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
                dst.setCellErrorValue(
                    src.errorCellValue
                )

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

    private fun setFormulaCell(
        row: Row,
        col: Int,
        formula: String
    ) {
        val cell =
            row.getCell(col)
                ?: row.createCell(col)

        cell.cellFormula = formula
    }

    /**
     * Menempatkan ulang foto tanda tangan yang berasal dari template.
     *
     * POI dapat memindahkan row/cell dengan shiftRows(), tetapi gambar adalah
     * drawing object dengan anchor sendiri. Karena itu gambar diarahkan ke
     * footer yang baru berdasarkan row footer aktual, tanpa mengubah ukuran
     * atau kolom tempat gambar berada.
     */
    private fun repositionManifestFooterPictures(
        sheet: XSSFSheet,
        stowingFooterRow: Int,
        manifestFooterRow: Int
    ) {
        val drawing: XSSFDrawing = sheet.getDrawingPatriarch() ?: return

        for (shape in drawing.shapes) {
            val picture = shape as? XSSFPicture ?: continue
            val anchor = picture.clientAnchor ?: continue
            val currentRow = anchor.row1
            if (currentRow < 0) continue

            // Kolom anchor membedakan dua foto tanda tangan yang memang ada
            // di template: checklist berada di kolom J (index 9), sedangkan
            // footer Manifest berada di kolom A (index 0). Logo di bagian atas
            // tidak disentuh.
            val targetRow: Short = when (anchor.col1.toInt()) {
                9 -> stowingFooterRow.toShort()
                0 -> manifestFooterRow.toShort()
                else -> continue
            }

            val delta = targetRow.toInt() - currentRow
            if (delta == 0) continue

            anchor.row1 = anchor.row1 + delta
            if (anchor.row2 >= 0) {
                anchor.row2 = anchor.row2 + delta
            }
        }
    }

    private fun normalize(
        value: String
    ): String {
        return value
            .trim()
            .replace(Regex("\\s+"), " ")
            .uppercase()
    }

    private fun parseWeight(
        value: String
    ): Double {
        val raw =
            value
                .trim()
                .replace(" ", "")

        if (raw.isBlank()) return 0.0

        return when {
            raw.contains(".") &&
                raw.contains(",") -> {
                raw
                    .replace(".", "")
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
                    raw
                        .replace(',', '.')
                        .toDoubleOrNull()
                        ?: 0.0
                } else {
                    raw
                        .replace(",", "")
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
                    raw
                        .replace(".", "")
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
