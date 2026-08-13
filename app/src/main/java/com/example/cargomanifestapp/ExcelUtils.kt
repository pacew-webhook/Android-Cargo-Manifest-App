package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

object ExcelUtils {

    private val PAG_ROW_INDEXES = listOf(
        0, 10, 22, 33, 43, 56, 66, 77
    )

    /**
     * Export lama untuk kebutuhan Stowing saja.
     * Tetap dipertahankan agar kompatibel dengan fitur lama.
     */
    fun writeCargoListToExcel(
        context: Context,
        uri: Uri,
        cargoList: List<CargoItem>
    ) {
        val inputStream: InputStream =
            context.assets.open("STOWINGAN_PAG_TEMPLATE.xlsx")

        val workbook = inputStream.use {
            XSSFWorkbook(it)
        }

        try {
            fillStowingPagSheet(
                workbook.getSheetAt(0),
                cargoList
            )

            saveWorkbook(
                context,
                uri,
                workbook
            )
        } finally {
            workbook.close()
        }
    }

    /**
     * Export utama V4:
     *
     * - 1 file Excel
     * - Sheet Manifest memakai template aplikasi
     * - Manifest otomatis grouping Customer + Description
     * - Stowing Checklist di sisi kanan Manifest
     * - Template STOWINGAN PAG ikut dimasukkan
     * - Detail PAG tetap dipertahankan untuk pengecekan LOOT
     */
    fun writeCombinedCargoWorkbook(
        context: Context,
        uri: Uri,
        cargoList: List<CargoItem>
    ) {
        require(cargoList.isNotEmpty()) {
            "Data Stowing kosong"
        }

        val manifestInput =
            context.assets.open("template_manifest.xlsx")

        val workbook = manifestInput.use {
            XSSFWorkbook(it)
        }

        try {
            val manifestSheet =
                workbook.getSheet("Manifest")
                    ?: workbook.getSheetAt(0)

            fillManifestSheet(
                manifestSheet,
                cargoList
            )

            // Salin seluruh workbook template Stowingan PAG
            // ke workbook Manifest.
            val pagInput =
                context.assets.open("STOWINGAN_PAG_TEMPLATE.xlsx")

            val pagWorkbook = pagInput.use {
                XSSFWorkbook(it)
            }

            try {
                val names = listOf(
                    "STOWINGAN PAG",
                    "PAG LOOT",
                    "PAG DATA",
                    "STOWING CHECK",
                    "BARANG KOLIAN"
                )

                pagWorkbook
                    .sheetIterator()
                    .asSequence()
                    .forEachIndexed { index, sourceSheet ->

                        val targetName =
                            names.getOrElse(index) {
                                "PAG TEMPLATE ${index + 1}"
                            }

                        val targetSheet =
                            workbook.createSheet(targetName)

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

            saveWorkbook(
                context,
                uri,
                workbook
            )

        } finally {
            workbook.close()
        }
    }

    /**
     * Mengisi Sheet Manifest.
     */
    private fun fillManifestSheet(
        sheet: XSSFSheet,
        cargoList: List<CargoItem>
    ) {
        val startRow = 13
        val templateCapacity = 24

        /*
         * Grouping Manifest FIX V13.5.4:
         *
         * Manifest harus 1 baris untuk setiap kombinasi PAG + Customer +
         * Description (+ PTI jika memang berbeda). Jangan menggabungkan
         * PAG yang berbeda hanya karena Customer dan Description sama.
         *
         * Contoh:
         *
         * 002 MYI | ULIN | PINANG | 72  | 1732
         * 001 MYI | ULIN | PINANG | 144 | 3349
         *
         * tidak boleh menjadi satu baris:
         *
         * ULIN | PINANG | 216 | 5081
         *
         * Ini juga mencegah nilai rata-rata Weight (Net/Cly) dihitung
         * dari gabungan seluruh PAG.
         */
        val groupedManifest = cargoList
            .filter {
                it.noPag.isNotBlank() &&
                    it.customer.isNotBlank() &&
                    it.description.isNotBlank()
            }
            .groupBy {
                "${normalize(it.noPag)}|" +
                    "${normalize(it.customer)}|" +
                    "${normalize(it.description)}|" +
                    normalize(it.pti)
            }
            .map { (_, items) ->

                val pcs =
                    items.sumOf {
                        it.pcsQty.toDoubleOrNull() ?: 0.0
                    }

                val totalWeight =
                    items.sumOf {
                        it.subTotal.toDoubleOrNull() ?: 0.0
                    }

                val ptis =
                    items
                        .map { it.pti.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()

                items.first().copy(
                    pti = ptis.firstOrNull() ?: "",
                    pcsQty = formatNumber(pcs),
                    subTotal = formatNumber(totalWeight),
                    weight =
                        if (pcs > 0) {
                            formatNumber(totalWeight / pcs)
                        } else {
                            ""
                        }
                )
            }

        /*
         * Grouping Stowing:
         *
         * PAG + Customer + Description
         */
        val groupedStowing = cargoList
            .filter {
                it.noPag.isNotBlank()
            }
            .groupBy {
                "${normalize(it.noPag)}|" +
                    "${normalize(it.customer)}|" +
                    normalize(it.description)
            }
            .map { (_, items) ->

                val totalNet =
                    items.sumOf {
                        it.subTotal.toDoubleOrNull() ?: 0.0
                    }

                items.first().copy(
                    subTotal = formatNumber(totalNet)
                )
            }

        /*
         * Bersihkan area data lama.
         */
        val clearUntil =
            maxOf(
                sheet.lastRowNum,
                startRow + templateCapacity + 40
            )

        for (r in startRow..clearUntil) {
            val row = sheet.getRow(r) ?: continue

            for (c in 0..12) {
                row.getCell(c)?.setBlank()
            }
        }

        /*
         * Jika data melebihi kapasitas template,
         * tambahkan baris.
         */
        val maxRows =
            maxOf(
                groupedManifest.size,
                groupedStowing.size
            )

        if (maxRows > templateCapacity) {

            val extra =
                maxRows - templateCapacity

            sheet.shiftRows(
                startRow + templateCapacity,
                sheet.lastRowNum,
                extra,
                true,
                false
            )
        }

        val sampleRow =
            sheet.getRow(startRow)

        var totalManifestPcs = 0.0
        var totalManifestWeight = 0.0
        var totalStowingNet = 0.0
        var totalStowingGross = 0.0

        /*
         * Isi Manifest + Stowing Checklist.
         */
        for (i in 0 until maxRows) {

            val rowIndex =
                startRow + i

            val row =
                sheet.getRow(rowIndex)
                    ?: sheet.createRow(rowIndex)

            /*
             * MANIFEST
             */
            if (i < groupedManifest.size) {

                val item =
                    groupedManifest[i]

                val pcs =
                    item.pcsQty.toDoubleOrNull()
                        ?: 0.0

                val subtotal =
                    item.subTotal.toDoubleOrNull()
                        ?: 0.0

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

                setStyledNumericCell(
                    row,
                    3,
                    item.weight.toDoubleOrNull() ?: 0.0,
                    sampleRow?.getCell(3)
                )

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

            /*
             * STOWING CHECKLIST
             */
            if (i < groupedStowing.size) {

                val item =
                    groupedStowing[i]

                val net =
                    item.subTotal.toDoubleOrNull()
                        ?: 0.0

                /*
                 * Gross sementara mengikuti
                 * formula project V4.
                 */
                val gross =
                    net + 125.0

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

                setStyledTextCell(
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

                setStyledTextCell(
                    row,
                    12,
                    item.customer,
                    sampleRow?.getCell(12)
                )
            }
        }

        /*
         * TOTAL.
         */
        val totalRowIndex =
            startRow + maxRows

        val totalRow =
            sheet.getRow(totalRowIndex)
                ?: sheet.createRow(totalRowIndex)

        setNumericCell(
            totalRow,
            2,
            totalManifestPcs
        )

        setNumericCell(
            totalRow,
            4,
            totalManifestWeight
        )

        setNumericCell(
            totalRow,
            10,
            totalStowingNet
        )

        setNumericCell(
            totalRow,
            11,
            totalStowingGross
        )
    }

    /**
     * Mengisi template STOWINGAN PAG.
     */
    private fun fillStowingPagSheet(
        sheet: XSSFSheet,
        cargoList: List<CargoItem>
    ) {
        if (cargoList.isEmpty()) {
            return
        }

        val groupedByPag =
            cargoList
                .groupBy {
                    it.noPag.trim()
                }
                .filterKeys {
                    it.isNotBlank()
                }

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

            /*
             * Total KG PAG.
             */
            val totalPagKg =
                itemsInPag.sumOf {
                    it.subTotal.toDoubleOrNull()
                        ?: 0.0
                }

            val totalCell =
                pagRow.getCell(4)
                    ?: pagRow.createCell(4)

            totalCell.setCellValue(totalPagKg)

            /*
             * Customer.
             */
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

                /*
                 * Berat per item.
                 */
                val kgValues =
                    item.weight
                        .split(",")
                        .mapNotNull {
                            it.trim().toDoubleOrNull()
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

    /**
     * Copy satu Sheet dari workbook sumber
     * ke workbook tujuan.
     *
     * PERBAIKAN V4:
     * source sekarang menggunakan Sheet umum,
     * bukan XSSFSheet.
     */
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

        /*
         * Copy ukuran dan status kolom.
         */
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

        /*
         * Copy default row/column size.
         */
        target.defaultRowHeight =
            source.defaultRowHeight

        target.defaultColumnWidth =
            source.defaultColumnWidth

        /*
         * Cache style agar tidak membuat
         * style baru terlalu banyak.
         */
        val styleCache =
            mutableMapOf<Short, XSSFCellStyle>()

        /*
         * Copy row dan cell.
         */
        for (rIndex in 0..source.lastRowNum) {

            val srcRow =
                source.getRow(rIndex)
                    ?: continue

            val dstRow =
                target.createRow(rIndex)

            /*
             * Copy tinggi row.
             */
            dstRow.height =
                srcRow.height

            /*
             * PERBAIKAN:
             *
             * POI tidak menyediakan
             * srcRow.hidden.
             *
             * zeroHeight digunakan
             * untuk row tersembunyi.
             */
            dstRow.zeroHeight =
                srcRow.zeroHeight

            for (cIndex in 0 until maxColumns) {

                val srcCell =
                    srcRow.getCell(cIndex)
                        ?: continue

                val dstCell =
                    dstRow.createCell(cIndex)

                /*
                 * Copy nilai.
                 */
                copyCellValue(
                    srcCell,
                    dstCell
                )

                /*
                 * Copy style.
                 */
                val styleIndex =
                    srcCell.cellStyle.index

                val copiedStyle =
                    styleCache.getOrPut(
                        styleIndex
                    ) {
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

        /*
         * Copy merged cells.
         */
        for (mergedRegion in source.mergedRegions) {

            target.addMergedRegion(
                mergedRegion.copy()
            )
        }

        /*
         * PERBAIKAN:
         *
         * Tidak lagi menggunakan:
         *
         * target.sheetFormatPr
         *
         * karena API tersebut tidak tersedia
         * pada tipe Sheet yang digunakan.
         *
         * defaultRowHeight sudah dicopy
         * di atas.
         */
    }

    /**
     * Copy nilai Cell.
     */
    private fun copyCellValue(
        src: Cell,
        dst: Cell
    ) {

        when (src.cellType) {

            org.apache.poi.ss.usermodel.CellType.STRING -> {
                dst.setCellValue(
                    src.stringCellValue
                )
            }

            org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                dst.setCellValue(
                    src.numericCellValue
                )
            }

            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> {
                dst.setCellValue(
                    src.booleanCellValue
                )
            }

            org.apache.poi.ss.usermodel.CellType.FORMULA -> {
                dst.cellFormula =
                    src.cellFormula
            }

            org.apache.poi.ss.usermodel.CellType.ERROR -> {
                dst.setCellErrorValue(
                    src.errorCellValue
                )
            }

            org.apache.poi.ss.usermodel.CellType.BLANK -> {
                dst.setBlank()
            }

            else -> {
                dst.setBlank()
            }
        }
    }

    /**
     * Set Text + style.
     */
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

    /**
     * Set Number + style.
     */
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

    /**
     * Set Number tanpa style.
     */
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

    /**
     * Normalisasi teks untuk grouping.
     */
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

    /**
     * Format angka:
     *
     * 2.0 -> 2
     * 2.5 -> 2.5
     */
    private fun formatNumber(
        value: Double
    ): String {

        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    /**
     * Simpan workbook ke URI.
     */
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
