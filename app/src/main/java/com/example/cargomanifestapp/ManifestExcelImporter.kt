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
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Safe, sequential Manifest indexer.
 *
 * Design goals:
 * - AWB/unrelated Excel files can stay in the selected folder.
 * - For XLSX/XLSM, workbook.xml is inspected first so Apache POI is NOT opened
 *   for an unrelated workbook.
 * - A real Manifest workbook is opened only once, parsed, committed, then closed.
 * - One bad file does not stop the whole synchronization.
 * - Existing Room data stays searchable while synchronization is running.
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

        val excelFiles = collectManifestCandidates(root)
        val filesTotal = excelFiles.size

        var filesDone = 0
        var filesImported = 0
        var filesSkipped = 0
        var filesNotManifest = 0
        var rowsImported = 0
        val errors = mutableListOf<String>()
        val seenManifestKeys = HashSet<String>(filesTotal)

        onProgress?.invoke(0, filesTotal, 0)

        for (file in excelFiles) {
            coroutineContext.ensureActive()

            try {
                val result = importFile(file)
                when {
                    result.notManifest -> filesNotManifest++
                    result.skipped -> {
                        filesSkipped++
                        seenManifestKeys += file.uri.toString()
                    }
                    else -> {
                        filesImported++
                        rowsImported += result.rows
                        seenManifestKeys += file.uri.toString()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += "${file.name ?: file.uri}: ${e.message ?: "gagal dibaca"}"
            }

            filesDone++
            onProgress?.invoke(filesDone, filesTotal, rowsImported)
        }

        // Only clean removed Manifest sources after a completely successful scan.
        // If one or more files failed, keep the old database entries intact.
        if (errors.isEmpty()) {
            val storedKeys = dao.getAllSourceKeys()
            for (key in storedKeys) {
                coroutineContext.ensureActive()
                if (!seenManifestKeys.contains(key)) {
                    dao.deleteItemsForSource(key)
                    dao.deleteFile(key)
                }
            }
        }

        ScanResult(
            filesFound = filesTotal,
            filesImported = filesImported,
            filesSkipped = filesSkipped,
            filesNotManifest = filesNotManifest,
            rowsImported = rowsImported,
            errors = errors
        )
    }

    /**
     * Returns only likely Manifest workbooks.
     *
     * For xlsx/xlsm we can inspect xl/workbook.xml without creating an Apache POI
     * Workbook. This is much cheaper than opening every AWB workbook.
     * For old .xls we cannot do the lightweight ZIP check, so the filename is used
     * as a conservative candidate filter.
     */
    private suspend fun collectManifestCandidates(root: DocumentFile): List<DocumentFile> {
        val result = ArrayList<DocumentFile>()
        val pending = ArrayDeque<DocumentFile>()
        pending.add(root)

        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()

            val dir = pending.removeLast()
            val children = try {
                dir.listFiles()
            } catch (_: Exception) {
                emptyArray()
            }

            for (file in children) {
                coroutineContext.ensureActive()

                when {
                    file.isDirectory -> pending.add(file)
                    file.isFile && isExcel(file.name) -> {
                        if (isManifestCandidate(file)) result += file
                    }
                }
            }
        }

        return result.sortedBy { it.name?.lowercase(Locale.US).orEmpty() }
    }

    private suspend fun isManifestCandidate(file: DocumentFile): Boolean {
        val name = file.name.orEmpty()
        val lower = name.lowercase(Locale.US)

        // Normal operational files already use MANIFEST/MANIFES in their names.
        // This also avoids opening common AWB files such as "AWB KAL.xlsx".
        if (lower.contains("manifest") || lower.contains("manifes")) return true

        // XLS cannot be inspected as a ZIP. Keep unknown XLS files as candidates.
        if (lower.endsWith(".xls")) return true

        // XLSX/XLSM: inspect workbook.xml only. No POI Workbook is created here.
        return try {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val entry = zip.nextEntry ?: break
                        if (entry.name == "xl/workbook.xml") {
                            val text = BufferedReader(
                                InputStreamReader(zip, Charsets.UTF_8)
                            ).use { it.readText() }
                            return text.lowercase(Locale.US).contains("manifest")
                        }
                    }
                    false
                }
            } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // If the lightweight inspection fails, do not risk opening an unknown
            // workbook with POI. The next sync can retry it if the file changes.
            false
        }
    }

    private suspend fun importFile(file: DocumentFile): FileResult {
        coroutineContext.ensureActive()

        val sourceKey = file.uri.toString()
        val modified = file.lastModified()
        val fileSize = file.length()
        val old = dao.getFile(sourceKey)

        // Some Storage Access Framework providers report unreliable metadata.
        // Never treat 0/0 as proof that the file is unchanged.
        val metadataReliable = modified > 0L || fileSize > 0L
        if (
            old != null &&
            metadataReliable &&
            old.lastModified == modified &&
            old.fileSize == fileSize &&
            old.rowCount >= 0 &&
            !dao.hasFormulaSubTotal(sourceKey)
        ) {
            return FileResult(skipped = true, rows = old.rowCount, notManifest = false)
        }

        val parsed = context.contentResolver.openInputStream(file.uri)?.use { stream ->
            coroutineContext.ensureActive()
            WorkbookFactory.create(stream).use { workbook ->
                coroutineContext.ensureActive()

                val sheet = workbook.getSheet("Manifest")
                    ?: findManifestSheet(workbook)
                    ?: return@use null

                val evaluator = workbook.creationHelper.createFormulaEvaluator()
                parseSheet(
                    sheet = sheet,
                    fileName = file.name.orEmpty(),
                    sourceKey = sourceKey,
                    modified = modified,
                    evaluator = evaluator
                )
            }
        } ?: error("Tidak dapat membuka file")

        coroutineContext.ensureActive()

        if (parsed == null) {
            if (old != null) {
                dao.deleteItemsForSource(sourceKey)
                dao.deleteFile(sourceKey)
            }
            return FileResult(skipped = false, rows = 0, notManifest = true)
        }

        // The transaction deletes old rows and inserts the new snapshot for this file.
        // No other workbook is kept in memory while this happens.
        dao.replaceFileData(
            sourceKey = sourceKey,
            file = ManifestFileEntity(
                sourceKey = sourceKey,
                sourceName = file.name.orEmpty(),
                lastModified = modified,
                fileSize = fileSize,
                rowCount = parsed.size,
                importedAt = System.currentTimeMillis()
            ),
            items = parsed
        )

        return FileResult(skipped = false, rows = parsed.size, notManifest = false)
    }

    private fun findManifestSheet(workbook: Workbook): Sheet? {
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
        val header = findHeader(sheet) ?: error("Header Sheet Manifest tidak dikenali")

        val date = normalizeDate(findMetadata(sheet, "date"))
        val flight = findMetadata(sheet, "flight no").ifBlank { findMetadata(sheet, "flight") }
        val from = findMetadata(sheet, "from")
        val destination = findMetadata(sheet, "to")
        val year = Regex("""(20\d{2})""").find(date)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(20\d{2})""").find(fileName)?.groupValues?.get(1)?.toIntOrNull()
            ?: 0

        val result = ArrayList<ManifestEntity>()
        if (header.rowIndex + 1 > sheet.lastRowNum) return result

        for (r in (header.rowIndex + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            if (!isCargoDataRow(row, header, evaluator)) continue

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

    private fun isCargoDataRow(row: Row, header: HeaderInfo, evaluator: FormulaEvaluator): Boolean {
        val no = cell(row, header.noCol, evaluator)
        val pti = cell(row, header.ptiCol, evaluator)
        val noIsNumeric = no.replace(",", ".").toDoubleOrNull() != null
        if (!noIsNumeric || pti.isBlank() || normalize(pti) == "pti") return false

        val pcs = cell(row, header.pcsCol, evaluator)
        val weight = cell(row, header.weightCol, evaluator)
        val subtotal = cell(row, header.subtotalCol, evaluator)
        val description = cell(row, header.descriptionCol, evaluator)
        val customer = cell(row, header.customerCol, evaluator)

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
                        val value = formatter.formatCellValue(row.getCell(n)).trim()
                            .removePrefix(":").trim()
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

    private fun normalizeDate(value: String): String {
        val v = value.trim()
        val match = Regex("""^(\d{1,2})[./-](\d{1,2})[./-](20\d{2})$""").find(v)
            ?: return v
        val day = match.groupValues[1].padStart(2, '0')
        val month = match.groupValues[2].padStart(2, '0')
        val year = match.groupValues[3]
        return "$day/$month/$year"
    }

    private fun normalize(value: String): String = value.trim()
        .lowercase(Locale.US)
        .replace(Regex("""\s+"""), " ")
        .replace(Regex("""\s*/\s*"""), "/")

    private fun isExcel(name: String?): Boolean {
        val n = name?.lowercase(Locale.US) ?: return false
        return n.endsWith(".xlsx") || n.endsWith(".xls") || n.endsWith(".xlsm")
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
        val rows: Int,
        val notManifest: Boolean
    )

    data class ScanResult(
        val filesFound: Int,
        val filesImported: Int,
        val filesSkipped: Int,
        val filesNotManifest: Int,
        val rowsImported: Int,
        val errors: List<String>
    )

    companion object {
        private val PCS_HEADERS = setOf("pcs/cly", "pcs/qty", "pcs qty", "pcs")
        private val WEIGHT_HEADERS = setOf("weight (kg)", "weight kg", "weight", "berat")
        private val SUBTOTAL_HEADERS = setOf("sub total", "subtotal", "sub total (kg)", "total")
        private val DESCRIPTION_HEADERS = setOf("description", "descriptions")
        private val CUSTOMER_HEADERS = setOf("costumers", "customers", "customer")
    }
}
