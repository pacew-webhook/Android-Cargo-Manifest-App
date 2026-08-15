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

                    copySheet(
                        sourceSheet,
                        targetSheet,
                        workbook
                    )

                    if (index == 0) {
                        fillStowingPagSheet(
                            targetSheet,
                            cargoList
                        )
                    }
                }
            } finally {
                pagWorkbook.close()
            }

            val stowingDataSheet =
                getOrCreateStowingDataSheet(workbook)

            fillStowingDataSheet(
                stowingDataSheet,
                cargoList
            )

            workbook.setSheetVisibility(
                workbook.getSheetIndex(stowingDataSheet),
                SheetVisibility.VERY_HIDDEN
            )

            saveWorkbook(context, uri, workbook)
        } finally {
            workbook.close()
        }
    }

    private fun getOrCreateStowingDataSheet(
        workbook: XSSFWorkbook
    ): XSSFSheet {
        return workbook.getSheet("STOWING_DATA")
            ?: workbook.createSheet("STOWING_DATA")
    }

    private fun fillStowingDataSheet(
        sheet: XSSFSheet,
        cargoList: List<CargoItem>
    ) {
        /*
         * Bersihkan isi lama sepenuhnya agar data hasil import lama tidak
         * tertinggal ketika jumlah data berubah.
         */
        if (sheet.lastRowNum >= 0) {
            for (r in 0..sheet.lastRowNum) {
                val row = sheet.getRow(r) ?: continue

                for (c in 0 until maxOf(
                    8,
                    row.lastCellNum.toInt()
                )) {
                    row.getCell(c)?.setBlank()
                }
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

        val header =
            sheet.getRow(0) ?: sheet.createRow(0)

        headers.forEachIndexed { index, value ->
            val cell =
                header.getCell(index)
                    ?: header.createCell(index)

            cell.setCellValue(value)
        }

        cargoList.forEachIndexed { index, item ->
            val row =
                sheet.getRow(index + 1)
                    ?: sheet.createRow(index + 1)

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
                val cell =
                    row.getCell(col)
                        ?: row.createCell(col)

                cell.setCellValue(value)
            }
        }

        val widths = intArrayOf(
            8, 18, 14, 20, 24, 12, 28, 16
        )

        widths.forEachIndexed { col, width ->
            sheet.setColumnWidth(
                col,
                width * 256
            )
        }

        sheet.createFreezePane(0, 1)
    }

    /**
     * Export Manifest + Stowing.
     *
     * V11 FIX:
     *
     * 1. Sumber TOTAL selalu cargoList TERBARU.
     * 2. Manifest ditulis terlebih dahulu sampai seluruh cargoList selesai.
     * 3. Total Manifest dihitung dari item yang benar-benar ditulis.
     * 4. Stowing ditulis secara independen.
     * 5. Total Stowing dihitung dari group yang benar-benar ditulis.
     * 6. Tidak menggunakan nilai/formula Total lama dari template.
     * 7. Data baru setelah Import tetap masuk ke perhitungan Total.
     * 8. Mekanisme penambahan baris yang sudah fix tetap dipertahankan.
     */
    private fun fillManifestSheet(
        workbook: XSSFWorkbook,
        sheet: XSSFSheet,
        cargoList: List<CargoItem>
    ) {
        val startRow = 13

        /*
         * Index baris template sebelum ada insertRowsBefore().
         * Ini sengaja dipertahankan dari versi sebelumnya agar layout UI/Excel
         * tidak berubah.
         */
        val baseStowingTotalRow = 36
        val baseManifestTotalRow = 44

        /*
         * Snapshot baru.
         *
         * Sangat penting: Export hanya bekerja dari daftar yang diterima
         * fungsi ini. Jadi data hasil Import + data baru yang sudah masuk
         * cargoList akan ikut seluruhnya.
         */
        val manifestRows = cargoList.toList()

        /*
         * Group Stowing berdasarkan NO PAG.
         */
        val groupedStowing = cargoList
            .filter { it.noPag.isNotBlank() }
            .groupBy { normalize(it.noPag) }
            .map { (_, items) ->
                val totalNet =
                    items.sumOf {
                        parseWeight(it.subTotal)
                    }

                val customers =
                    items
                        .map { it.customer.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(4)

                val descriptions =
                    items
                        .map { it.description.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(4)

                items.first().copy(
                    customer =
                        customers.joinToString(" / "),
                    description =
                        descriptions.joinToString(" / "),
                    subTotal =
                        formatNumber(totalNet)
                )
            }

        /*
         * -------------------------------------------------------------
         * 1. Hitung kebutuhan baris Stowing.
         * -------------------------------------------------------------
         */
        val stowingCapacity =
            baseStowingTotalRow - startRow + 1

        val stowingExtra =
            maxOf(
                0,
                groupedStowing.size - stowingCapacity
            )

        if (stowingExtra > 0) {
            insertRowsBefore(
                sheet = sheet,
                rowIndex = baseStowingTotalRow,
                count = stowingExtra,
                styleSourceRowIndex = baseStowingTotalRow - 1
            )
        }

        /*
         * Posisi Total Manifest ikut bergeser jika baris Stowing ditambah.
         */
        val manifestTotalRowAfterStowing =
            baseManifestTotalRow + stowingExtra

        val manifestCapacityAfterStowing =
            manifestTotalRowAfterStowing - startRow

        val manifestExtra =
            maxOf(
                0,
                manifestRows.size - manifestCapacityAfterStowing
            )

        if (manifestExtra > 0) {
            insertRowsBefore(
                sheet = sheet,
                rowIndex = manifestTotalRowAfterStowing,
                count = manifestExtra,
                styleSourceRowIndex =
                    manifestTotalRowAfterStowing - 1
            )
        }

        /*
         * Posisi final Total setelah semua insertRowsBefore selesai.
         */
        val finalStowingTotalRow =
            baseStowingTotalRow + stowingExtra

        val finalManifestTotalRow =
            manifestTotalRowAfterStowing + manifestExtra

        /*
         * -------------------------------------------------------------
         * 2. Bersihkan AREA DATA LAMA.
         * -------------------------------------------------------------
         *
         * Hanya kolom masing-masing tabel yang dibersihkan.
         * Dengan demikian Manifest A:G dan Stowing H:M tidak saling
         * menghapus.
         */
        clearDataArea(
            sheet = sheet,
            startRow = startRow,
            endRow = finalManifestTotalRow - 1,
            startCol = 0,
            endCol = 6
        )

        clearDataArea(
            sheet = sheet,
            startRow = startRow,
            endRow = finalStowingTotalRow - 1,
            startCol = 7,
            endCol = 12
        )

        val sampleRow =
            sheet.getRow(startRow)

        /*
         * -------------------------------------------------------------
         * 3. TOTAL DARI CARGO LIST TERBARU.
         * -------------------------------------------------------------
         *
         * Jangan mengambil total dari formula/template lama.
         */
        var totalManifestPcs = 0.0
        var totalManifestWeight = 0.0

        /*
         * -------------------------------------------------------------
         * 4. TULIS MANIFEST SAJA.
         * -------------------------------------------------------------
         *
         * Loop terpisah dari Stowing supaya jumlah data Manifest
         * tidak pernah bergantung pada jumlah group Stowing.
         */
        manifestRows.forEachIndexed { index, item ->
            val rowIndex =
                startRow + index

            val row =
                sheet.getRow(rowIndex)
                    ?: sheet.createRow(rowIndex)

            val pcs =
                parseWeight(item.pcsQty)

            val subtotal =
                parseWeight(item.subTotal)

            /*
             * TOTAL DIAMBIL DARI DATA YANG BENAR-BENAR DITULIS.
             */
            totalManifestPcs += pcs
            totalManifestWeight += subtotal

            setStyledNumericCell(
                row = row,
                col = 0,
                value = (index + 1).toDouble(),
                sample = sampleRow?.getCell(0)
            )

            setStyledTextCell(
                row = row,
                col = 1,
                value = item.pti,
                sample = sampleRow?.getCell(1)
            )

            setStyledNumericCell(
                row = row,
                col = 2,
                value = pcs,
                sample = sampleRow?.getCell(2)
            )

            val weightPerClyCell =
                row.getCell(3)
                    ?: row.createCell(3)

            weightPerClyCell.setBlank()

            sampleRow
                ?.getCell(3)
                ?.let {
                    weightPerClyCell.cellStyle =
                        it.cellStyle
                }

            setStyledNumericCell(
                row = row,
                col = 4,
                value = subtotal,
                sample = sampleRow?.getCell(4)
            )

            setStyledTextCell(
                row = row,
                col = 5,
                value = item.description,
                sample = sampleRow?.getCell(5)
            )

            setStyledTextCell(
                row = row,
                col = 6,
                value = item.customer,
                sample = sampleRow?.getCell(6)
            )
        }

        /*
         * -------------------------------------------------------------
         * 5. TULIS STOWING SAJA.
         * -------------------------------------------------------------
         */
        var totalStowingNet = 0.0
        var totalStowingGross = 0.0

        groupedStowing.forEachIndexed { index, item ->
            val rowIndex =
                startRow + index

            val row =
                sheet.getRow(rowIndex)
                    ?: sheet.createRow(rowIndex)

            val net =
                parseWeight(item.subTotal)

            val gross =
                net + 125.0

            totalStowingNet += net
            totalStowingGross += gross

            setStyledNumericCell(
                row = row,
                col = 7,
                value = (index + 1).toDouble(),
                sample = sampleRow?.getCell(7)
            )

            setStyledTextCell(
                row = row,
                col = 8,
                value = item.noPag,
                sample = sampleRow?.getCell(8)
            )

            setChecklistTextCell(
                workbook = workbook,
                row = row,
                col = 9,
                value = item.description,
                sample = sampleRow?.getCell(9)
            )

            setStyledNumericCell(
                row = row,
                col = 10,
                value = net,
                sample = sampleRow?.getCell(10)
            )

            setStyledNumericCell(
                row = row,
                col = 11,
                value = gross,
                sample = sampleRow?.getCell(11)
            )

            setChecklistTextCell(
                workbook = workbook,
                row = row,
                col = 12,
                value = item.customer,
                sample = sampleRow?.getCell(12)
            )
        }

        /*
         * -------------------------------------------------------------
         * 6. TULIS ULANG TOTAL MANIFEST.
         * -------------------------------------------------------------
         *
         * Ini sengaja bukan formula Excel.
         *
         * Dengan angka final, Excel/Google Sheets tidak akan menggunakan
         * cached formula lama dari template.
         */
        val manifestTotalRow =
            sheet.getRow(finalManifestTotalRow)
                ?: sheet.createRow(finalManifestTotalRow)

        setNumericCell(
            row = manifestTotalRow,
            col = 2,
            value = totalManifestPcs
        )

        setNumericCell(
            row = manifestTotalRow,
            col = 4,
            value = totalManifestWeight
        )

        /*
         * Hapus formula lama pada kolom total lainnya.
         */
        manifestTotalRow.getCell(0)?.setBlank()
        manifestTotalRow.getCell(1)?.setBlank()
        manifestTotalRow.getCell(3)?.setBlank()
        manifestTotalRow.getCell(5)?.setBlank()
        manifestTotalRow.getCell(6)?.setBlank()

        /*
         * -------------------------------------------------------------
         * 7. TULIS ULANG TOTAL STOWING.
         * -------------------------------------------------------------
         */
        val stowingTotalRow =
            sheet.getRow(finalStowingTotalRow)
                ?: sheet.createRow(finalStowingTotalRow)

        /*
         * Pastikan kolom H:M tidak menyimpan formula lama.
         */
        for (col in 7..12) {
            stowingTotalRow.getCell(col)?.setBlank()
        }

        setNumericCell(
            row = stowingTotalRow,
            col = 10,
            value = totalStowingNet
        )

        setNumericCell(
            row = stowingTotalRow,
            col = 11,
            value = totalStowingGross
        )

        /*
         * Excel tidak perlu menghitung formula Total karena nilai final
         * sudah ditulis sebagai numeric cell.
         *
         * Force recalculation tetap diaktifkan untuk formula lain yang
         * mungkin masih ada di template.
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

        val lastRow =
            sheet.lastRowNum

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
            val newRowIndex =
                rowIndex + i

            val newRow =
                sheet.getRow(newRowIndex)
                    ?: sheet.createRow(newRowIndex)

            if (sourceRow != null) {
                newRow.height =
                    sourceRow.height

                newRow.zeroHeight =
                    sourceRow.zeroHeight
            }

            for (c in 0 until columnsToCopy) {
                val sourceCell =
                    sourceRow?.getCell(c)

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
            val row =
                sheet.getRow(r) ?: continue

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

        val groupedByPag =
            cargoList
                .groupBy { it.noPag.trim() }
                .filterKeys { it.isNotBlank() }

        var pagBlockIndex = 0

        for ((noPag, itemsInPag) in groupedByPag) {
            if (pagBlockIndex >= PAG_ROW_INDEXES.size) {
                break
            }

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

                custCell.setCellValue(
                    item.customer
                )

                val kgValues =
                    item.weight
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
            (0..source.lastRowNum)
                .maxOfOrNull {
                    source.getRow(it)
                        ?.lastCellNum
                        ?.toInt()
                        ?: 0
                }
                ?: 0

        for (c in 0 until maxColumns) {
            val width =
                source.getColumnWidth(c)

            if (width > 0) {
                target.setColumnWidth(
                    c,
                    width
                )
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
                dst.setCellValue(
                    src.stringCellValue
                )

            org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                dst.setCellValue(
                    src.numericCellValue
                )

            org.apache.poi.ss.usermodel.CellType.BOOLEAN ->
                dst.setCellValue(
                    src.booleanCellValue
                )

            org.apache.poi.ss.usermodel.CellType.FORMULA ->
                dst.cellFormula =
                    src.cellFormula

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

            cell.cellStyle =
                style
        }

        /*
         * Jika nilai panjang, jangan sampai hilang karena tinggi baris
         * terlalu kecil.
         */
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

        /*
         * setCellValue(Double) otomatis mengganti formula lama menjadi
         * numeric cell.
         */
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

        /*
         * Jika cell sebelumnya berisi formula, setCellValue(Double)
         * menggantinya dengan nilai numeric baru.
         */
        cell.setCellValue(value)
    }

    private fun normalize(
        value: String
    ): String {
        return value
            .trim()
            .replace(
                "\\s+".toRegex(),
                " "
            )
            .uppercase()
    }

    private fun parseWeight(
        value: String
    ): Double {
        val raw =
            value
                .trim()
                .replace(" ", "")

        if (raw.isBlank()) {
            return 0.0
        }

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
                    raw
                        .toDoubleOrNull()
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
            context.contentResolver.openOutputStream(uri)
                ?: throw java.io.IOException(
                    "Tidak bisa membuka output stream untuk URI tujuan"
                )

        outputStream.use {
            workbook.write(it)
        }
    }
}
