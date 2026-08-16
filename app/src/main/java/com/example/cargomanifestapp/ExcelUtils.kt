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
import java.io.File
import java.io.FileOutputStream

/**
 * ExcelUtils_V15_DYNAMIC_PAG
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
 * 8. PAG dan koli pada sheet STOWINGAN PAG diperluas dinamis melewati template.
 * 9. Ringkasan NO PAG ikut diperluas dan tidak membatasi jumlah PAG.
 * 10. Koli > kapasitas kolom template diteruskan ke kolom berikutnya.
 */
object ExcelUtils {

    // The template contains 8 visual PAG blocks. These are only the seed blocks;
    // export now creates additional blocks dynamically when the data exceeds them.
    private val TEMPLATE_PAG_ROW_INDEXES = listOf(0, 10, 22, 33, 43, 56, 66, 77)
    private const val PAG_BLOCK_HEIGHT = 10
    private const val PAG_SUMMARY_START_ROW = 88
    private const val PAG_TEMPLATE_LAST_COL = 45

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

    /**
     * Membuat workbook Stowing yang sama dengan Export Excel Android,
     * tetapi langsung ditulis ke File lokal (dipakai sebelum upload ke n8n).
     */
    fun writeCombinedCargoWorkbookToFile(
        context: Context,
        file: File,
        cargoList: List<CargoItem>
    ) {
        require(cargoList.isNotEmpty()) { "Data Stowing kosong" }

        file.parentFile?.mkdirs()

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

            FileOutputStream(file).use { output ->
                workbook.write(output)
            }
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
        val baseStowingTotalRow = 36

        // Baris total Manifest asli template (0-based).
        val baseManifestTotalRow = 44

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

        if (stowingExtra > 0) {
            insertRowsBefore(
                sheet = sheet,
                rowIndex = baseStowingTotalRow,
                count = stowingExtra,
                styleSourceRowIndex = baseStowingTotalRow - 1
            )
        }

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

        if (manifestExtra > 0) {
            insertRowsBefore(
                sheet = sheet,
                rowIndex = manifestBaseAfterStowing,
                count = manifestExtra,
                styleSourceRowIndex = manifestBaseAfterStowing - 1
            )
        }

        val manifestTotalRow =
            manifestBaseAfterStowing + manifestExtra

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
         *
         * Jangan mempertahankan formula bawaan template seperti
         * =SUM(E14:E44). Jika data melewati baris template (misalnya sampai
         * row 45, 60, 100, dst.), formula harus mengikuti baris data aktual.
         *
         * Data dimulai pada row Excel 14 (startRow = 13, zero-based).
         */
        val manifestTotalRowObj =
            sheet.getRow(manifestTotalRow)
                ?: sheet.createRow(manifestTotalRow)

        if (manifestRows.isNotEmpty()) {
            val firstExcelRow = startRow + 1
            val lastExcelRow = startRow + manifestRows.size

            setFormulaCell(
                manifestTotalRowObj,
                2,
                "SUM(C$firstExcelRow:C$lastExcelRow)"
            )

            manifestTotalRowObj
                .getCell(3)
                ?.setBlank()

            setFormulaCell(
                manifestTotalRowObj,
                4,
                "SUM(E$firstExcelRow:E$lastExcelRow)"
            )
        } else {
            setNumericCell(manifestTotalRowObj, 2, totalManifestPcs)
            manifestTotalRowObj.getCell(3)?.setBlank()
            setNumericCell(manifestTotalRowObj, 4, totalManifestWeight)
        }

        /*
         * TOTAL STOWING
         *
         * Area Stowing juga dibuat dinamis. Formula selalu berhenti tepat pada
         * baris data Stowing terakhir, bukan pada batas template lama.
         */
        val stowingTotalRowObj =
            sheet.getRow(stowingTotalRow)
                ?: sheet.createRow(stowingTotalRow)

        if (groupedStowing.isNotEmpty()) {
            val firstExcelRow = startRow + 1
            val lastExcelRow = startRow + groupedStowing.size

            setFormulaCell(
                stowingTotalRowObj,
                10,
                "SUM(K$firstExcelRow:K$lastExcelRow)"
            )

            setFormulaCell(
                stowingTotalRowObj,
                11,
                "SUM(L$firstExcelRow:L$lastExcelRow)"
            )
        } else {
            setNumericCell(stowingTotalRowObj, 10, totalStowingNet)
            setNumericCell(stowingTotalRowObj, 11, totalStowingGross)
        }

        // Paksa Excel/POI untuk menghitung ulang formula saat file dibuka.
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
            .toList()

        if (groupedByPag.isEmpty()) return

        /*
         * The original XLSX contains only 8 PAG blocks.  Never use that as a
         * data limit.  Keep the original 8 blocks for visual compatibility and
         * append more blocks immediately before the PAG summary when necessary.
         */
        val extraBlockCount =
            (groupedByPag.size - TEMPLATE_PAG_ROW_INDEXES.size).coerceAtLeast(0)

        if (extraBlockCount > 0) {
            sheet.shiftRows(
                PAG_SUMMARY_START_ROW,
                sheet.lastRowNum,
                extraBlockCount * PAG_BLOCK_HEIGHT,
                true,
                false
            )

            val firstExtraStart = TEMPLATE_PAG_ROW_INDEXES.last() + PAG_BLOCK_HEIGHT
            for (i in 0 until extraBlockCount) {
                copyPagBlock(
                    sourceSheet = sheet,
                    sourceStartRow = TEMPLATE_PAG_ROW_INDEXES.last(),
                    targetStartRow = firstExtraStart + (i * PAG_BLOCK_HEIGHT)
                )
            }
        }

        val pagRowIndexes = buildList {
            addAll(TEMPLATE_PAG_ROW_INDEXES)
            val firstExtraStart =
                TEMPLATE_PAG_ROW_INDEXES.last() + PAG_BLOCK_HEIGHT
            repeat(extraBlockCount) { index ->
                add(firstExtraStart + (index * PAG_BLOCK_HEIGHT))
            }
        }

        /*
         * The summary at the bottom of the template also had an 8-PAG limit.
         * Add rows for every additional PAG before the footer area and rewrite
         * all summary formulas so they point to the actual dynamic blocks.
         */
        val summaryStartRow =
            PAG_SUMMARY_START_ROW + (extraBlockCount * PAG_BLOCK_HEIGHT)

        val extraSummaryRows =
            (groupedByPag.size - TEMPLATE_PAG_ROW_INDEXES.size).coerceAtLeast(0)

        if (extraSummaryRows > 0) {
            val summaryInsertRow = summaryStartRow + 1 + TEMPLATE_PAG_ROW_INDEXES.size
            val summaryLastRow = sheet.lastRowNum
            if (summaryInsertRow <= summaryLastRow) {
                sheet.shiftRows(
                    summaryInsertRow,
                    summaryLastRow,
                    extraSummaryRows,
                    true,
                    false
                )
            }

            for (i in 0 until extraSummaryRows) {
                copySummaryRow(
                    sheet = sheet,
                    sourceRowIndex = summaryStartRow + TEMPLATE_PAG_ROW_INDEXES.size,
                    targetRowIndex = summaryInsertRow + i
                )
            }
        }

        // Clear the visible PAG/total fields first.
        pagRowIndexes.forEach { startRow ->
            val row = sheet.getRow(startRow) ?: sheet.createRow(startRow)
            row.getCell(1)?.setBlank()
            row.getCell(4)?.setBlank()
        }

        groupedByPag.forEachIndexed { pagIndex, (noPag, itemsInPag) ->
            val startPagRowIndex = pagRowIndexes[pagIndex]
            val pagRow = sheet.getRow(startPagRowIndex)
                ?: sheet.createRow(startPagRowIndex)

            val pagCell = pagRow.getCell(1)
                ?: pagRow.createCell(1)
            pagCell.setCellValue(noPag)

            val totalPagKg = itemsInPag.sumOf {
                parseWeight(it.subTotal)
            }

            val totalCell = pagRow.getCell(4)
                ?: pagRow.createCell(4)
            totalCell.setCellValue(totalPagKg)

            val customerStartRow = startPagRowIndex + 2
            var currentStartCol = 0

            for (item in itemsInPag) {
                val custRow = sheet.getRow(customerStartRow)
                    ?: sheet.createRow(customerStartRow)

                ensureStowingPagColumn(
                    sheet = sheet,
                    columnIndex = currentStartCol,
                    blockStartRow = startPagRowIndex
                )

                val custCell = custRow.getCell(currentStartCol)
                    ?: custRow.createCell(currentStartCol)
                custCell.setCellValue(item.customer)

                val kgValues = item.weight
                    .split(",")
                    .mapNotNull {
                        parseWeight(it).takeIf { kg -> kg > 0.0 }
                    }

                var currentRow = customerStartRow + 1
                var colOffset = 0
                var rowCountInCol = 0

                for (kg in kgValues) {
                    val targetCol = currentStartCol + colOffset
                    ensureStowingPagColumn(
                        sheet = sheet,
                        columnIndex = targetCol,
                        blockStartRow = startPagRowIndex
                    )

                    val r = sheet.getRow(currentRow)
                        ?: sheet.createRow(currentRow)
                    val c = r.getCell(targetCol)
                        ?: r.createCell(targetCol)
                    c.setCellValue(kg)

                    currentRow++
                    rowCountInCol++

                    // Five weights fit vertically in one column.  Additional
                    // koli automatically continue into the next column.
                    if (rowCountInCol >= 5) {
                        rowCountInCol = 0
                        colOffset++
                        currentRow = customerStartRow + 1
                    }
                }

                // Keep each cargo input separated. If one input contains more
                // than five koli, it consumes additional columns; the next input
                // must start after those columns instead of jumping a fixed 6.
                val columnsUsedByItem = maxOf(1, (kgValues.size + 4) / 5)
                currentStartCol += maxOf(6, columnsUsedByItem + 1)
            }
        }

        // Rebuild the dynamic PAG summary.
        val summaryHeader = sheet.getRow(summaryStartRow)
            ?: sheet.createRow(summaryStartRow)
        val headerCell = summaryHeader.getCell(3)
            ?: summaryHeader.createCell(3)
        headerCell.setCellValue("NO PAG")

        groupedByPag.forEachIndexed { index, _ ->
            val rowIndex = summaryStartRow + 1 + index
            val row = sheet.getRow(rowIndex)
                ?: sheet.createRow(rowIndex)
            val cell = row.getCell(3)
                ?: row.createCell(3)
            val blockExcelRow = pagRowIndexes[index] + 1
            cell.cellFormula = "B$blockExcelRow"
        }

        // Remove any stale summary formulas after the current PAG count.
        val firstStaleRow = summaryStartRow + 1 + groupedByPag.size
        val lastPossibleSummaryRow =
            firstStaleRow + TEMPLATE_PAG_ROW_INDEXES.size
        for (r in firstStaleRow..lastPossibleSummaryRow) {
            val row = sheet.getRow(r) ?: continue
            row.getCell(3)?.setBlank()
            row.getCell(4)?.setBlank()
        }
    }

    /** Copy one of the existing visual PAG blocks for an additional PAG. */
    private fun copyPagBlock(
        sourceSheet: XSSFSheet,
        sourceStartRow: Int,
        targetStartRow: Int
    ) {
        val maxColumns = maxOf(
            PAG_TEMPLATE_LAST_COL + 1,
            (0..PAG_BLOCK_HEIGHT - 1).maxOfOrNull { offset ->
                sourceSheet.getRow(sourceStartRow + offset)?.lastCellNum?.toInt() ?: 0
            } ?: 0
        )

        for (offset in 0 until PAG_BLOCK_HEIGHT) {
            val sourceRow = sourceSheet.getRow(sourceStartRow + offset)
            val targetRow = sourceSheet.getRow(targetStartRow + offset)
                ?: sourceSheet.createRow(targetStartRow + offset)

            if (sourceRow != null) {
                targetRow.height = sourceRow.height
                targetRow.zeroHeight = sourceRow.zeroHeight
            }

            for (col in 0 until maxColumns) {
                val sourceCell = sourceRow?.getCell(col)
                val targetCell = targetRow.getCell(col)
                    ?: targetRow.createCell(col)

                if (sourceCell != null) {
                    targetCell.cellStyle = sourceCell.cellStyle
                    copyCellValue(sourceCell, targetCell)
                } else {
                    targetCell.setBlank()
                }
            }
        }
    }

    /** Copy the formatting of the 8th summary row to a newly inserted row. */
    private fun copySummaryRow(
        sheet: XSSFSheet,
        sourceRowIndex: Int,
        targetRowIndex: Int
    ) {
        val sourceRow = sheet.getRow(sourceRowIndex)
        val targetRow = sheet.getRow(targetRowIndex)
            ?: sheet.createRow(targetRowIndex)

        if (sourceRow != null) {
            targetRow.height = sourceRow.height
            for (col in 0 until maxOf(6, sourceRow.lastCellNum.toInt())) {
                val sourceCell = sourceRow.getCell(col)
                val targetCell = targetRow.getCell(col)
                    ?: targetRow.createCell(col)
                if (sourceCell != null) {
                    targetCell.cellStyle = sourceCell.cellStyle
                    targetCell.setBlank()
                }
            }
        }
    }

    /**
     * Extend the horizontal template when a PAG contains more koli than the
     * original workbook's columns. New columns inherit the last template
     * column's width/style, so data is not silently dropped at column 46.
     */
    private fun ensureStowingPagColumn(
        sheet: XSSFSheet,
        columnIndex: Int,
        blockStartRow: Int
    ) {
        if (columnIndex < 0) return

        val templateColumn = PAG_TEMPLATE_LAST_COL
        if (columnIndex > sheet.lastRowNum) {
            // no-op; this condition is intentionally not used for rows. POI
            // creates cells lazily below.
        }

        if (columnIndex > templateColumn) {
            val width = sheet.getColumnWidth(templateColumn)
            if (width > 0) sheet.setColumnWidth(columnIndex, width)
            sheet.setColumnHidden(columnIndex, false)

            for (offset in 0 until PAG_BLOCK_HEIGHT) {
                val row = sheet.getRow(blockStartRow + offset)
                    ?: sheet.createRow(blockStartRow + offset)
                val sourceCell = row.getCell(templateColumn)
                val targetCell = row.getCell(columnIndex)
                    ?: row.createCell(columnIndex)
                if (sourceCell != null) {
                    targetCell.cellStyle = sourceCell.cellStyle
                }
            }
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
