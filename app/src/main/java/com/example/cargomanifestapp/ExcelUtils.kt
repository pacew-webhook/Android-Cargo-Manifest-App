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
     * Mengisi Sheet Manifest + Stowing Checklist.
     *
     * FIX3:
     * - Baris data tidak lagi dibatasi angka 24.
     * - Jika data mencapai/menabrak baris TOTAL, baris baru disisipkan
     *   tepat sebelum TOTAL.
     * - TOTAL selalu berada setelah seluruh data pada area masing-masing.
     * - Struktur template, merge cell dan style template dipertahankan.
     * - Kolom D Manifest (Pcs/Cly) sengaja tetap kosong.
     */
    private fun fillManifestSheet(
        sheet: XSSFSheet,
        cargoList: List<CargoItem>
    ) {
        val startRow = 13 // Excel row 14

        // Posisi TOTAL pada template asli.
        val baseStowingTotalRow = 36 // Excel row 37
        val baseManifestTotalRow = 44 // Excel row 45

        /*
         * Manifest: satu baris untuk setiap kombinasi
         * PAG + Customer + Description + PTI.
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
                val pcs = items.sumOf {
                    it.pcsQty.toDoubleOrNull() ?: 0.0
                }

                val totalWeight = items.sumOf {
                    it.subTotal.toDoubleOrNull() ?: 0.0
                }

                val ptis = items
                    .map { it.pti.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()

                items.first().copy(
                    pti = ptis.firstOrNull() ?: "",
                    pcsQty = formatNumber(pcs),
                    subTotal = formatNumber(totalWeight),
                    // FIX3: jangan pernah memasukkan berat per koli
                    // ke kolom D. C = jumlah koli, E = total KG.
                    weight = ""
                )
            }

        /*
         * Stowing Checklist:
         * SATU BARIS untuk setiap NO PAG.
         *
         * CUSTOMER dan DESCRIPTION BUKAN lagi kunci grouping. Jadi semua
         * input yang memiliki NO PAG sama akan digabung ke satu baris,
         * walaupun customer dan/atau description berbeda.
         *
         * Contoh:
         * PAG 003 MYI | ULIN | PINANG   | 750 KG
         * PAG 003 MYI | YYN  | SAYURAN  | 250 KG
         * PAG 003 MYI | ULIN | PINANG   | 450 KG
         *
         * menjadi SATU baris:
         * PAG 003 MYI | PINANG / SAYURAN | 1450 KG | ULIN / YYN
         *
         * PTI juga tidak ikut menjadi kunci grouping. Semua berat untuk
         * NO PAG tersebut dijumlahkan menjadi NET satu baris.
         */
        val groupedStowing = cargoList
            .filter { it.noPag.isNotBlank() }
            .groupBy { normalize(it.noPag) }
            .map { (_, items) ->
                val totalNet = items.sumOf {
                    it.subTotal.toDoubleOrNull() ?: 0.0
                }

                val customers = items
                    .map { it.customer.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()

                val descriptions = items
                    .map { it.description.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()

                items.first().copy(
                    customer = customers.joinToString(" / "),
                    description = descriptions.joinToString(" / "),
                    subTotal = formatNumber(totalNet)
                )
            }

        /*
         * =============================================================
         * FIX3 - AUTO ROW EXPANSION
         * =============================================================
         *
         * Stowing TOTAL berada di row 37 pada template dan menyediakan
         * 23 baris data (row 14..36).
         * Manifest TOTAL berada di row 45 dan menyediakan 31 baris data
         * (row 14..44).
         *
         * Karena keduanya berada dalam satu worksheet, insert dilakukan
         * dari bagian Stowing terlebih dahulu. Jika Stowing membutuhkan
         * baris tambahan, semua isi di bawah row 37 ikut turun dan posisi
         * Manifest TOTAL otomatis ikut turun. Setelah itu baru dihitung
         * kebutuhan tambahan Manifest.
         */
        val stowingCapacity = baseStowingTotalRow - startRow + 1
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

        // Setelah insert Stowing, Manifest TOTAL ikut bergeser.
        val manifestTotalRowAfterStowing =
            baseManifestTotalRow + stowingExtra

        val manifestCapacityAfterStowing =
            manifestTotalRowAfterStowing - startRow

        val manifestExtra =
            maxOf(
                0,
                groupedManifest.size - manifestCapacityAfterStowing
            )

        if (manifestExtra > 0) {
            insertRowsBefore(
                sheet = sheet,
                rowIndex = manifestTotalRowAfterStowing,
                count = manifestExtra,
                styleSourceRowIndex = manifestTotalRowAfterStowing - 1
            )
        }

        val finalStowingTotalRow =
            baseStowingTotalRow + stowingExtra

        val finalManifestTotalRow =
            manifestTotalRowAfterStowing + manifestExtra

        /*
         * Bersihkan HANYA area data.
         * Jangan lagi membersihkan sampai lastRowNum karena itu dapat
         * menghapus isi/template bagian signature dan area bawah sheet.
         */
        // Manifest hanya membersihkan A:G sampai tepat sebelum TOTAL Manifest.
        clearDataArea(
            sheet = sheet,
            startRow = startRow,
            endRow = finalManifestTotalRow - 1,
            startCol = 0,
            endCol = 6
        )

        // Stowing hanya membersihkan H:M sampai tepat sebelum TOTAL Stowing.
        // Dengan begitu label TOTAL WEIGHT dan area signature template tetap utuh.
        clearDataArea(
            sheet = sheet,
            startRow = startRow,
            endRow = finalStowingTotalRow - 1,
            startCol = 7,
            endCol = 12
        )

        val sampleRow =
            sheet.getRow(startRow)

        var totalManifestPcs = 0.0
        var totalManifestWeight = 0.0
        var totalStowingNet = 0.0
        var totalStowingGross = 0.0

        /*
         * Isi data. Jumlah baris mengikuti jumlah hasil grouping.
         * Tidak ada lagi angka 24 sebagai pembatas.
         */
        val maxRows =
            maxOf(
                groupedManifest.size,
                groupedStowing.size
            )

        for (i in 0 until maxRows) {
            val rowIndex = startRow + i
            val row =
                sheet.getRow(rowIndex)
                    ?: sheet.createRow(rowIndex)

            /* =========================
             * MANIFEST A:G
             * ========================= */
            if (i < groupedManifest.size) {
                val item = groupedManifest[i]

                val pcs =
                    item.pcsQty.toDoubleOrNull() ?: 0.0

                val subtotal =
                    item.subTotal.toDoubleOrNull() ?: 0.0

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

                // C = PCS/Qty.
                setStyledNumericCell(
                    row,
                    2,
                    pcs,
                    sampleRow?.getCell(2)
                )

                // D = Pcs/Cly. FIX3: selalu kosong.
                val weightPerClyCell =
                    row.getCell(3)
                        ?: row.createCell(3)
                weightPerClyCell.setBlank()
                if (sampleRow != null) {
                    weightPerClyCell.cellStyle =
                        sampleRow.getCell(3).cellStyle
                }

                // E = Sub Total KG.
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

            /* =========================
             * STOWING CHECKLIST H:M
             * ========================= */
            if (i < groupedStowing.size) {
                val item = groupedStowing[i]

                val net =
                    item.subTotal.toDoubleOrNull() ?: 0.0

                // Gross tetap mengikuti logika project sebelumnya:
                // setiap group PAG mendapatkan tambahan 125 KG.
                val gross = net + 125.0

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
         * =============================================================
         * TOTAL MANIFEST
         * =============================================================
         * Template asli:
         * C45:C46 = total PCS/Qty
         * E45:E46 = total Sub Total KG
         * Jika overflow, merge tersebut otomatis ikut turun bersama row.
         */
        val manifestTotalRowObj =
            sheet.getRow(finalManifestTotalRow)
                ?: sheet.createRow(finalManifestTotalRow)

        setNumericCell(
            manifestTotalRowObj,
            2,
            totalManifestPcs
        )

        // D total sengaja kosong; tidak ada perkalian/angka nyasar.
        manifestTotalRowObj.getCell(3)?.setBlank()

        setNumericCell(
            manifestTotalRowObj,
            4,
            totalManifestWeight
        )

        /*
         * =============================================================
         * TOTAL STOWING CHECKLIST
         * =============================================================
         * Template asli:
         * K37:K38 = Net
         * M37:M38 = Gross
         * Jika data > kapasitas, total ikut turun.
         */
        val stowingTotalRowObj =
            sheet.getRow(finalStowingTotalRow)
                ?: sheet.createRow(finalStowingTotalRow)

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
    }

    /**
     * Sisipkan sejumlah baris sebelum rowIndex tanpa mengubah layout
     * template secara manual. shiftRows menangani isi/merge di bawahnya.
     * Baris baru diberi style dari baris contoh agar format Excel tetap
     * mengikuti template.
     */
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

        val sourceRow = sheet.getRow(styleSourceRowIndex)

        for (i in 0 until count) {
            val newRowIndex = rowIndex + i
            val newRow =
                sheet.getRow(newRowIndex)
                    ?: sheet.createRow(newRowIndex)

            if (sourceRow != null) {
                newRow.height = sourceRow.height
                newRow.zeroHeight = sourceRow.zeroHeight

                for (c in 0 until sourceRow.lastCellNum.coerceAtLeast(0)) {
                    val sourceCell = sourceRow.getCell(c) ?: continue
                    val newCell =
                        newRow.getCell(c)
                            ?: newRow.createCell(c)

                    newCell.cellStyle = sourceCell.cellStyle
                    newCell.setBlank()
                }
            }
        }
    }

    /**
     * Bersihkan hanya area data, bukan TOTAL/signature/template bawah.
     */
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

            for (c in startCol..endCol) {
                row.getCell(c)?.setBlank()
            }
        }
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
