package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.util.Locale

class ManifestExcelImporter(private val context: Context) {
    private val dao = ManifestDatabase.getDatabase(context).manifestDao()
    private val formatter = DataFormatter(Locale.US)

    suspend fun scanFolderTree(treeUri: Uri): ScanResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Folder Manifest tidak dapat dibuka")

        var files = 0
        var imported = 0
        var skipped = 0
        var rows = 0
        val errors = mutableListOf<String>()

        suspend fun walk(dir: DocumentFile) {
            dir.listFiles().forEach { file ->
                if (file.isDirectory) {
                    walk(file)
                } else if (file.isFile && isExcel(file.name)) {
                    files++
                    try {
                        val result = importFile(file)
                        if (result.skipped) skipped++ else {
                            imported++
                            rows += result.rows
                        }
                    } catch (e: Exception) {
                        errors += "${file.name}: ${e.message ?: "gagal dibaca"}"
                    }
                }
            }
        }

        walk(root)
        ScanResult(files, imported, skipped, rows, errors)
    }

    private suspend fun importFile(file: DocumentFile): FileResult {
        val sourceKey = file.uri.toString()
        val modified = file.lastModified()
        val old = dao.getFile(sourceKey)
        if (old != null && old.lastModified == modified && old.rowCount >= 0) {
            return FileResult(true, old.rowCount)
        }

        val input = context.contentResolver.openInputStream(file.uri)
            ?: error("Tidak dapat membuka file")

        val items = input.use { stream ->
            WorkbookFactory.create(stream).use { workbook ->
                val sheet = workbook.getSheet("Manifest")
                    ?: error("Sheet Manifest tidak ditemukan")
                parseSheet(sheet, file.name.orEmpty(), sourceKey, modified)
            }
        }

        dao.deleteItemsForSource(sourceKey)
        if (items.isNotEmpty()) dao.insertAll(items)
        dao.upsertFile(
            ManifestFileEntity(
                sourceKey = sourceKey,
                sourceName = file.name.orEmpty(),
                lastModified = modified,
                rowCount = items.size,
                importedAt = System.currentTimeMillis()
            )
        )
        return FileResult(false, items.size)
    }

    private fun parseSheet(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        fileName: String,
        sourceKey: String,
        modified: Long
    ): List<ManifestEntity> {
        var headerRow = -1
        var noCol = -1
        var ptiCol = -1
        var pcsCol = -1
        var weightCol = -1
        var subtotalCol = -1
        var descriptionCol = -1
        var customerCol = -1

        val maxHeaderRows = minOf(40, sheet.lastRowNum + 1)
        for (r in 0 until maxHeaderRows) {
            val row = sheet.getRow(r) ?: continue
            for (c in 0 until minOf(25, row.lastCellNum.toInt().coerceAtLeast(0))) {
                val v = normalize(formatter.formatCellValue(row.getCell(c)))
                when (v) {
                    "no" -> noCol = c
                    "pti" -> ptiCol = c
                    "pcs/cly", "pcs cly", "pcs/qty", "pcs qty" -> pcsCol = c
                    "weight (kg)", "weight kg", "weight", "berat" -> {
                        if (weightCol == -1) weightCol = c
                    }
                    "sub total", "subtotal" -> subtotalCol = c
                    "description", "descriptions" -> descriptionCol = c
                    "costumers", "customers", "customer" -> customerCol = c
                }
            }
            if (ptiCol >= 0 && pcsCol >= 0 && descriptionCol >= 0 && customerCol >= 0) {
                headerRow = r
                break
            }
        }

        if (headerRow < 0) error("Header Sheet Manifest tidak dikenali")

        val date = findMetadata(sheet, "date")
        val flight = findMetadata(sheet, "flight no")
            .ifBlank { findMetadata(sheet, "flight") }
        val from = findMetadata(sheet, "from")
        val destination = findMetadata(sheet, "to")
        val year = Regex("(20\\d{2})").find(date)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("(20\\d{2})").find(fileName)?.groupValues?.get(1)?.toIntOrNull()
            ?: 0

        val result = mutableListOf<ManifestEntity>()
        for (r in headerRow + 1..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            val pti = cell(row, ptiCol)
            val description = cell(row, descriptionCol)
            val customer = cell(row, customerCol)
            if (pti.isBlank() && description.isBlank() && customer.isBlank()) continue
            if (pti.equals("pti", true)) continue

            result += ManifestEntity(
                sourceKey = sourceKey,
                sourceName = fileName,
                sourceLastModified = modified,
                sheetName = sheet.sheetName,
                rowNumber = r + 1,
                no = cell(row, noCol),
                pti = pti,
                pcs = cell(row, pcsCol),
                weightPerPiece = cell(row, weightCol),
                subTotal = cell(row, subtotalCol),
                description = description,
                customer = customer,
                manifestDate = date,
                flightNo = flight,
                fromStation = from,
                destination = destination,
                year = year
            )
        }
        return result
    }

    private fun findMetadata(sheet: org.apache.poi.ss.usermodel.Sheet, label: String): String {
        val target = normalize(label)
        val maxRows = minOf(20, sheet.lastRowNum + 1)
        for (r in 0 until maxRows) {
            val row = sheet.getRow(r) ?: continue
            val last = row.lastCellNum.toInt().coerceAtLeast(0)
            for (c in 0 until minOf(20, last)) {
                if (normalize(formatter.formatCellValue(row.getCell(c))) == target) {
                    for (n in c + 1 until minOf(c + 5, last)) {
                        val value = formatter.formatCellValue(row.getCell(n)).trim()
                        if (value.isNotBlank()) return value
                    }
                }
            }
        }
        return ""
    }

    private fun cell(row: org.apache.poi.ss.usermodel.Row?, col: Int): String =
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


    data class ScanResult(
        val filesFound: Int,
        val filesImported: Int,
        val filesSkipped: Int,
        val rowsImported: Int,
        val errors: List<String>
    )

    private data class FileResult(val skipped: Boolean, val rows: Int)
}
