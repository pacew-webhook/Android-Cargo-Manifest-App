package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Synchronizes Manifest sheets from a user-selected folder into the local Room DB.
 *
 * Important design rules:
 * - The folder may contain hundreds of unrelated Excel files (for example AWB files).
 * - Unrelated Excel files must be skipped before loading a full POI workbook whenever possible.
 * - One bad Excel file must never stop the complete synchronization.
 * - A workbook is opened, parsed, committed, and closed one file at a time.
 * - Already imported, unchanged files are skipped.
 */
class ManifestExcelImporter(private val context: Context) {
    private val dao = ManifestDatabase.getDatabase(context).manifestDao()
    private val formatter = DataFormatter(Locale.US)

    suspend fun scanFolderTree(
        treeUri: Uri,
        onProgress: (suspend (filesDone: Int, filesTotal: Int, rowsImported: Int) -> Unit)? = null
    ): ScanResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Folder Manifest tidak dapat dibuka")

        val excelFiles = ArrayList<DocumentFile>()
        val errors = mutableListOf<String>()
        collectExcelFiles(root, excelFiles, errors)

        val filesTotal = excelFiles.size
        var filesImported = 0
        var filesSkipped = 0
        var filesIgnored = 0
        var rowsImported = 0
        var filesDone = 0

        onProgress?.invoke(0, filesTotal, 0)

        for (file in excelFiles) {
            coroutineContext.ensureActive()

            try {
                val result = importFile(file)
                when {
                    result.skipped -> filesSkipped++
                    result.ignored -> filesIgnored++
                    else -> {
                        filesImported++
                        rowsImported += result.rows
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A single malformed/protected/unreadable workbook must not crash the app.
                errors += "${file.name ?: file.uri}: ${e.message ?: "gagal dibaca"}"
            }

            filesDone++
            onProgress?.invoke(filesDone, filesTotal, rowsImported)
        }

        ScanResult(
            filesFound = filesTotal,
            filesImported = filesImported,
            filesSkipped = filesSkipped,
            filesIgnored = filesIgnored,
            rowsImported = rowsImported,
            errors = errors
        )
    }

    private suspend fun collectExcelFiles(
        dir: DocumentFile,
        output: MutableList<DocumentFile>,
        errors: MutableList<String>
    ) {
        coroutineContext.ensureActive()

        val children = try {
            dir.listFiles().toList()
        } catch (e: Exception) {
            errors += "${dir.name ?: dir.uri}: ${e.message ?: "folder tidak dapat dibaca"}"
            return
        }

        for (file in children) {
            coroutineContext.ensureActive()
            if (file.isDirectory) {
                collectExcelFiles(file, output, errors)
            } else if (file.isFile && isExcel(file.name)) {
                output += file
            }
        }
    }

    private suspend fun importFile(file: DocumentFile): FileResult {
        val sourceKey = file.uri.toString()
        val modified = file.lastModified()
        val old = dao.getFile(sourceKey)

        if (old != null && old.lastModified == modified && old.rowCount >= 0 && !dao.hasFormulaSubTotal(sourceKey)) {
            return FileResult(skipped = true, ignored = false, rows = old.rowCount)
        }

        // Most unrelated files in the user's DATA MANIFEST folder are AWB workbooks.
        // For OOXML files we can inspect xl/workbook.xml without creating a full POI
        // workbook. This avoids a large memory allocation for files that will be skipped.
        if (isOoxml(file.name) && !hasManifestSheetLightweight(file.uri)) {
            return FileResult(skipped = false, ignored = true, rows = 0)
        }

        val items = context.contentResolver.openInputStream(file.uri)?.use { stream ->
            WorkbookFactory.create(stream).use { workbook ->
                val sheet = workbook.getSheet("Manifest")
                    ?: findManifestSheet(workbook)
                    ?: return@use emptyList()

                parseSheet(
                    sheet = sheet,
                    fileName = file.name.orEmpty(),
                    sourceKey = sourceKey,
                    modified = modified,
                    evaluator = workbook.creationHelper.createFormulaEvaluator()
                )
            }
        } ?: error("Tidak dapat membuka file")

        // A workbook can have the wrong format/headers even when a sheet happens to be
        // named Manifest. Treat it as unrelated instead of storing an empty database entry.
        if (items.isEmpty()) {
            return FileResult(skipped = false, ignored = true, rows = 0)
        }

        dao.replaceFileData(
            sourceKey = sourceKey,
            file = ManifestFileEntity(
                sourceKey = sourceKey,
                sourceName = file.name.orEmpty(),
                lastModified = modified,
                rowCount = items.size,
                importedAt = System.currentTimeMillis()
            ),
            items = items
        )

        return FileResult(skipped = false, ignored = false, rows = items.size)
    }

    /**
     * Reads only xl/workbook.xml from an XLSX/XLSM ZIP container.
     * Sheet names are stored directly in workbook.xml, so there is no need to load POI.
     */
    private suspend fun hasManifestSheetLightweight(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                val buffer = ByteArray(8192)
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "xl/workbook.xml") {
                        val text = buildString {
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = zip.read(buffer)
                                if (read <= 0) break
                                append(String(buffer, 0, read, Charsets.UTF_8))
                            }
                        }
                        val names = Regex("<sheet\\b[^>]*\\bname=[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE)
                            .findAll(text)
                            .map { normalize(it.groupValues[1]) }
                            .toList()
                        return@withContext names.any { it == "manifest" || it.contains("manifest cargo") }
                    }
                    zip.closeEntry()
                }
            }
        } ?: false
    }

    private fun findManifestSheet(workbook: org.apache.poi.ss.usermodel.Workbook): Sheet? {
        for (i in 0 until workbook.numberOfSheets) {
            val sheet = workbook.getSheetAt(i)
            val title = normalize(sheet.sheetName)
            if (title == "manifest" || title.contains("manifest cargo")) return sheet
        }
        return null
    }

    private fun parseSheet(
        sheet: Sheet,
        fileName: String,
        sourceKey: String,
        modified: Long,
        evaluator: FormulaEvaluator
    ): List<ManifestEntity> {
        val header = findHeader(sheet) ?: return emptyList()

        val date = findMetadata(sheet, "date")
        val flight = findMetadata(sheet, "flight no")
            .ifBlank { findMetadata(sheet, "flight") }
        val from = findMetadata(sheet, "from")
        val destination = findMetadata(sheet, "to")
        val year = Regex("(20\\d{2})").find(date)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("(20\\d{2})").find(fileName)?.groupValues?.get(1)?.toIntOrNull()
            ?: 0

        val result = ArrayList<ManifestEntity>()
        for (r in header.rowIndex + 1..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            if (!isCargoDataRow(row, header)) continue

            result += ManifestEntity(
                sourceKey = sourceKey,
                sourceName = fileName,
                sourceLastModified = modified,
                sheetName = sheet.sheetName,
                rowNumber = r + 1,
                no = cell(row, header.noCol, evaluator),
                pti = cell(row, header.ptiCol, evaluator),
                pcs = cell(row, header.pcsCol, evaluator),
                weightPerPiece = cell(row, header.weightCol, evaluator),
                subTotal = cell(row, header.subtotalCol, evaluator),
                description = cell(row, header.descriptionCol, evaluator),
                customer = cell(row, header.customerCol, evaluator),
                manifestDate = date,
                flightNo = flight,
                fromStation = from,
                destination = destination,
                year = year
            )
        }
        return result
    }

    private fun findHeader(sheet: Sheet): HeaderInfo? {
        val maxHeaderRows = minOf(50, sheet.lastRowNum + 1)
        for (r in 0 until maxHeaderRows) {
            val row = sheet.getRow(r) ?: continue
            val cells = mutableMapOf<String, Int>()
            val last = row.lastCellNum.toInt().coerceAtLeast(0)

            for (c in 0 until minOf(12, last)) {
                val v = normalize(formatter.formatCellValue(row.getCell(c)))
                if (v.isNotBlank()) cells.putIfAbsent(v, c)
            }

            val noCol = cells["no"] ?: continue
            val ptiCol = cells["pti"] ?: continue
            val pcsCol = cells.entries.firstOrNull { it.key in PCS_HEADERS }?.value ?: continue
            val weightCol = cells.entries.firstOrNull { it.key in WEIGHT_HEADERS }?.value ?: continue
            val descriptionCol = cells.entries.firstOrNull { it.key in DESCRIPTION_HEADERS }?.value ?: continue
            val customerCol = cells.entries.firstOrNull { it.key in CUSTOMER_HEADERS }?.value ?: continue
            val subtotalCol = cells.entries.firstOrNull { it.key in SUBTOTAL_HEADERS }?.value
                ?: (weightCol + 1).takeIf { it < 12 }

            if (noCol > 1 || ptiCol > 2 || descriptionCol > 6 || customerCol > 7) continue

            return HeaderInfo(
                rowIndex = r,
                noCol = noCol,
                ptiCol = ptiCol,
                pcsCol = pcsCol,
                weightCol = weightCol,
                subtotalCol = subtotalCol ?: -1,
                descriptionCol = descriptionCol,
                customerCol = customerCol
            )
        }
        return null
    }

    private fun isCargoDataRow(row: Row, header: HeaderInfo): Boolean {
        val no = cell(row, header.noCol)
        val pti = cell(row, header.ptiCol)

        val noIsNumeric = no.replace(",", ".").toDoubleOrNull() != null
        if (!noIsNumeric) return false
        if (pti.isBlank()) return false
        if (normalize(pti) == "pti") return false

        val pcs = cell(row, header.pcsCol)
        val weight = cell(row, header.weightCol)
        val subtotal = cell(row, header.subtotalCol)
        val description = cell(row, header.descriptionCol)
        val customer = cell(row, header.customerCol)

        return pcs.isNotBlank() || weight.isNotBlank() || subtotal.isNotBlank() ||
            description.isNotBlank() || customer.isNotBlank()
    }

    private fun findMetadata(sheet: Sheet, label: String): String {
        val target = normalize(label)
        val maxRows = minOf(20, sheet.lastRowNum + 1)
        for (r in 0 until maxRows) {
            val row = sheet.getRow(r) ?: continue
            val last = row.lastCellNum.toInt().coerceAtLeast(0)
            for (c in 0 until minOf(20, last)) {
                if (normalize(formatter.formatCellValue(row.getCell(c))) == target) {
                    for (n in c + 1 until minOf(c + 5, last)) {
                        val value = formatter.formatCellValue(row.getCell(n))
                            .trim()
                            .removePrefix(":")
                            .trim()
                        if (value.isNotBlank()) return value
                    }
                }
            }
        }
        return ""
    }

    private fun cell(row: Row?, col: Int, evaluator: FormulaEvaluator? = null): String {
        if (row == null || col < 0) return ""
        val value = row.getCell(col) ?: return ""

        return try {
            if (value.cellType == CellType.FORMULA && evaluator != null) {
                formatter.formatCellValue(value, evaluator).trim()
            } else {
                formatter.formatCellValue(value).trim()
            }
        } catch (_: Exception) {
            runCatching { formatter.formatCellValue(value).trim() }.getOrDefault("")
        }
    }

    private fun normalize(value: String): String =
        value.trim()
            .lowercase(Locale.US)
            .replace("\\s+".toRegex(), " ")
            .replace("\\s*/\\s*".toRegex(), "/")

    private fun isExcel(name: String?): Boolean {
        val n = name?.lowercase(Locale.US) ?: return false
        return n.endsWith(".xlsx") || n.endsWith(".xls") || n.endsWith(".xlsm")
    }

    private fun isOoxml(name: String?): Boolean {
        val n = name?.lowercase(Locale.US) ?: return false
        return n.endsWith(".xlsx") || n.endsWith(".xlsm")
    }

    private data class HeaderInfo(
        val rowIndex: Int,
        val noCol: Int,
        val ptiCol: Int,
        val pcsCol: Int,
        val weightCol: Int,
        val subtotalCol: Int,
        val descriptionCol: Int,
        val customerCol: Int
    )

    private data class FileResult(
        val skipped: Boolean,
        val ignored: Boolean,
        val rows: Int
    )

    data class ScanResult(
        val filesFound: Int,
        val filesImported: Int,
        val filesSkipped: Int,
        val filesIgnored: Int,
        val rowsImported: Int,
        val errors: List<String>
    )

    companion object {
        private val PCS_HEADERS = setOf("pcs/cly", "pcs/qty", "pcs qty")
        private val WEIGHT_HEADERS = setOf("weight (kg)", "weight kg", "weight", "berat")
        private val SUBTOTAL_HEADERS = setOf("sub total", "subtotal", "sub total (kg)")
        private val DESCRIPTION_HEADERS = setOf("description", "descriptions")
        private val CUSTOMER_HEADERS = setOf("costumers", "customers", "customer")
    }
}
