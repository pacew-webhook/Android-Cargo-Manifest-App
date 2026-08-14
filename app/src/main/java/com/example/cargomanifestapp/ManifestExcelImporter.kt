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
import java.util.Locale

/**
 * Imports only the real Manifest sheet from every Excel file in the selected tree.
 *
 * Important design rules:
 * 1. File name is NOT trusted. AWB files may live beside Manifest files.
 * 2. A workbook is a Manifest source only when a valid Manifest sheet/header is found.
 * 3. Files already indexed with the same lastModified + size are skipped.
 * 4. Each file is committed atomically, so a bad file cannot corrupt previous data.
 * 5. Work is sequential on Dispatchers.IO to keep memory usage predictable.
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

        val excelFiles = collectExcelFiles(root)
        val filesTotal = excelFiles.size
        var filesDone = 0
        var filesImported = 0
        var filesSkipped = 0
        var filesNotManifest = 0
        var rowsImported = 0
        val errors = mutableListOf<String>()
        val seenManifestKeys = HashSet<String>()

        onProgress?.invoke(0, filesTotal, 0)

        for (file in excelFiles) {

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

        // Remove records for Manifest files that no longer exist in the selected tree.
        // Do this only when the tree walk itself completed without read errors.
        if (errors.isEmpty()) {
            val storedKeys = dao.getAllSourceKeys()
            for (key in storedKeys) {

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

    private suspend fun collectExcelFiles(root: DocumentFile): List<DocumentFile> {
        val result = ArrayList<DocumentFile>()
        val pending = ArrayDeque<DocumentFile>()
        pending.add(root)

        while (pending.isNotEmpty()) {

            val dir = pending.removeLast()
            val children = dir.listFiles()
            for (file in children) {

                when {
                    file.isDirectory -> pending.add(file)
                    file.isFile && isExcel(file.name) -> result.add(file)
                }
            }
        }
        return result.sortedBy { it.name?.lowercase(Locale.US).orEmpty() }
    }

    private suspend fun importFile(file: DocumentFile): FileResult {
        val sourceKey = file.uri.toString()
        val modified = file.lastModified()
        val fileSize = file.length()
        val old = dao.getFile(sourceKey)

        // A provider can report 0 for metadata. Never treat 0/0 as proof that a file
        // is unchanged; otherwise a changed cloud/document-provider file could remain stale.
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
            WorkbookFactory.create(stream).use { workbook ->
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

        if (parsed == null) {
            // An AWB or other Excel file can remain in the folder. It simply does not
            // become part of the Manifest database.
            if (old != null) {
                dao.deleteItemsForSource(sourceKey)
                dao.deleteFile(sourceKey)
            }
            return FileResult(skipped = false, rows = 0, notManifest = true)
        }

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
