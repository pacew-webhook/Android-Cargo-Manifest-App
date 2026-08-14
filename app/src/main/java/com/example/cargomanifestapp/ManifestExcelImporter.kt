package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.util.Locale

class ManifestExcelImporter(private val context: Context) {
    private val dao = ManifestDatabase.getDatabase(context).manifestDao()
    private val formatter = DataFormatter(Locale.US)

    suspend fun scanFolderTree(
        treeUri: Uri,
        onProgress: (suspend (filesDone: Int, filesFound: Int, rowsImported: Int) -> Unit)? = null
    ): ScanResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Folder Manifest tidak dapat dibuka")

        var filesFound = 0
        var filesImported = 0
        var filesSkipped = 0
        var rowsImported = 0
        val errors = mutableListOf<String>()

        suspend fun walk(dir: DocumentFile) {
            coroutineContext.ensureActive()

            // Snapshot the directory once so we do not repeatedly ask SAF for children.
            val children = runCatching { dir.listFiles().toList() }.getOrElse {
                errors += "${dir.name ?: dir.uri}: ${it.message ?: "folder tidak dapat dibaca"}"
                return
            }

            for (file in children) {
                coroutineContext.ensureActive()

                if (file.isDirectory) {
                    walk(file)
                    continue
                }
                if (!file.isFile || !isExcel(file.name)) continue

                filesFound++
                try {
                    val result = importFile(file)
                    if (result.skipped) {
                        filesSkipped++
                    } else {
                        filesImported++
                        rowsImported += result.rows
                    }
                } catch (e: Exception) {
                    errors += "${file.name ?: file.uri}: ${e.message ?: "gagal dibaca"}"
                }

                onProgress?.invoke(filesFound, filesFound, rowsImported)
            }
        }

        walk(root)
        ScanResult(filesFound, filesImported, filesSkipped, rowsImported, errors)
    }

    private suspend fun importFile(file: DocumentFile): FileResult {
        val sourceKey = file.uri.toString()
        val modified = file.lastModified()
        val old = dao.getFile(sourceKey)

        // Reuse the already imported file when the provider reports the same
        // modification timestamp. Some Android document providers report 0,
        // which is still useful as a stable value for the same URI.
        if (old != null && old.lastModified == modified && old.rowCount >= 0) {
            return FileResult(skipped = true, rows = old.rowCount)
        }

        val items = context.contentResolver.openInputStream(file.uri)?.use { stream ->
            WorkbookFactory.create(stream).use { workbook ->
                val sheet = workbook.getSheet("Manifest")
                    ?: findManifestSheet(workbook)
                    ?: error("Sheet Manifest tidak ditemukan")
                parseSheet(sheet, file.name.orEmpty(), sourceKey, modified)
            }
        } ?: error("Tidak dapat membuka file")

        // Commit the complete file replacement atomically. If parsing fails, the old
        // data remains untouched instead of leaving a half-imported file.
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

        return FileResult(skipped = false, rows = items.size)
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
        modified: Long
    ): List<ManifestEntity> {
        val header = findHeader(sheet)
            ?: error("Header Sheet Manifest tidak dikenali")

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

            // Only accept rows belonging to the left Manifest Cargo table.
            // The workbook often contains a second table (stowing checklist) on the
            // right side; requiring a numeric No + PTI prevents those cells from being
            // imported as cargo records.
            if (!isCargoDataRow(row, header)) continue

            result += ManifestEntity(
                sourceKey = sourceKey,
                sourceName = fileName,
                sourceLastModified = modified,
                sheetName = sheet.sheetName,
                rowNumber = r + 1,
                no = cell(row, header.noCol),
                pti = cell(row, header.ptiCol),
                pcs = cell(row, header.pcsCol),
                weightPerPiece = cell(row, header.weightCol),
                subTotal = cell(row, header.subtotalCol),
                description = cell(row, header.descriptionCol),
                customer = cell(row, header.customerCol),
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

            // Only inspect the first 12 columns. The left table in the sample Manifest
            // workbook occupies A:G; H onward belongs to the stowing/checklist table.
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

            // The canonical template has A:G as the cargo table. Reject a candidate
            // that crosses into the second table.
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

        // No must be a row number such as 1, 2, 3 or 1.0.
        val noIsNumeric = no.replace(",", ".").toDoubleOrNull() != null
        if (!noIsNumeric) return false

        // PTI is the primary cargo identifier in this manifest format.
        // Rows without PTI are not imported to avoid picking up unrelated checklist rows.
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

    private fun cell(row: Row?, col: Int): String =
        if (row == null || col < 0) "" else formatter.formatCellValue(row.getCell(col)).trim()

    private fun normalize(value: String): String =
        value.trim()
            .lowercase(Locale.US)
            .replace("\\s+".toRegex(), " ")
            .replace("\\s*/\\s*".toRegex(), "/")

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

    private data class FileResult(val skipped: Boolean, val rows: Int)

    data class ScanResult(
        val filesFound: Int,
        val filesImported: Int,
        val filesSkipped: Int,
        val rowsImported: Int,
        val errors: List<String>
    )

    companion object {
        private val PCS_HEADERS = setOf("pcs/cly", "pcs/qty", "pcs qty", "pcs/qty", "pcs/cly")
        private val WEIGHT_HEADERS = setOf("weight (kg)", "weight kg", "weight", "berat")
        private val SUBTOTAL_HEADERS = setOf("sub total", "subtotal", "sub total (kg)")
        private val DESCRIPTION_HEADERS = setOf("description", "descriptions")
        private val CUSTOMER_HEADERS = setOf("costumers", "customers", "customer")
    }
}
