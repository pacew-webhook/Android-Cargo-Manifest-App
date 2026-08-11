package com.example.cargomanifestapp

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import java.io.File
import java.io.FileOutputStream

class CargoViewModel(application: Application) : AndroidViewModel(application) {

    // Data cargo sekarang bersumber dari Room Database (persisten),
    // bukan lagi dari MutableStateFlow di memori yang hilang saat app ditutup.
    private val dao = CargoDatabase.getDatabase(application).cargoDao()

    val cargoList: StateFlow<List<CargoItem>> = dao.getAllCargo()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _importedAwbNo = MutableStateFlow("")
    val importedAwbNo: StateFlow<String> = _importedAwbNo.asStateFlow()

    private val _importedFlightNo = MutableStateFlow("")
    val importedFlightNo: StateFlow<String> = _importedFlightNo.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredCargoList: StateFlow<List<CargoItem>> = combine(cargoList, searchQuery) { list, query ->
        if (query.isBlank()) list
        else list.filter {
            it.pti.contains(query, ignoreCase = true) ||
            it.customer.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true) ||
            it.noPag.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalPcs: StateFlow<Int> = cargoList.map { list ->
        list.sumOf { it.pcsQty.toIntOrNull() ?: 0 }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val totalWeight: StateFlow<Double> = cargoList.map { list ->
        list.sumOf { parseDoubleOrZero(it.subTotal) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // ==========================================
    // 1. KELOLA DATA LOCAL (INPUT FLEKSIBEL DI APP)
    // ==========================================
    fun addCargo(
        awbNo: String = "", flightNo: String = "", pti: String = "",
        pcsQty: String = "", weight: String = "", subTotal: String = "",
        description: String = "", customer: String = "", noPag: String = ""
    ) {
        val currentList = cargoList.value

        val isExactDuplicate = currentList.any {
            it.pti.equals(pti, ignoreCase = true) &&
            it.description.equals(description, ignoreCase = true) &&
            it.pcsQty.equals(pcsQty, ignoreCase = true) &&
            it.customer.equals(customer, ignoreCase = true) &&
            it.subTotal.equals(subTotal, ignoreCase = true) &&
            it.noPag.equals(noPag, ignoreCase = true)
        }

        if (isExactDuplicate) {
            Toast.makeText(getApplication(), "Data persis sama sudah ada di list!", Toast.LENGTH_SHORT).show()
            return
        }

        val newItem = CargoItem(
            // id = 0L agar Room yang men-generate primary key secara otomatis (autoGenerate = true)
            awbNo = awbNo, flightNo = flightNo, pti = pti,
            pcsQty = pcsQty, weight = weight, subTotal = subTotal,
            description = description, customer = customer, noPag = noPag
        )
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertCargo(newItem)
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Data Berhasil Ditambahkan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateCargo(item: CargoItem) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateCargo(item)
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Data Berhasil Diperbarui", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteCargo(item: CargoItem) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteCargo(item)
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAllCargo()
            withContext(Dispatchers.Main) {
                _importedAwbNo.value = ""
                _importedFlightNo.value = ""
            }
        }
    }

    // ==========================================
    // 2. IMPORT DATA FROM EXCEL
    // ==========================================
    fun importFromExcel(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream?.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                val importedList = mutableListOf<CargoItem>()
                val evaluator = workbook.creationHelper.createFormulaEvaluator()

                val awbCell = sheet.getRow(2)?.getCell(6)
                val flightCell = sheet.getRow(8)?.getCell(6)

                val extractedAwb = getCellStringFromCell(awbCell, evaluator)
                val extractedFlight = getCellStringFromCell(flightCell, evaluator)

                // --- Bangun peta NO PAG dari blok STOWING CHECKLIST (kolom I/J/K) ---
                // PENTING: baris blok Stowing TIDAK sejajar (row-aligned) dengan baris blok
                // Manifest -- blok Stowing hanya berisi ringkasan per-kontainer (biasanya
                // jauh lebih sedikit barisnya daripada daftar barang di Manifest). Sebelumnya
                // kode membaca kolom NO PAG (index 8) pada BARIS YANG SAMA dengan item
                // manifest, padahal itu tabel yang berbeda -> hasilnya NO PAG tertukar/hilang
                // untuk hampir semua baris saat data di-import ulang.
                // Di sini kita kumpulkan dulu semua pasangan (DESCRIPTION+CUSTOMER -> NO PAG)
                // dari blok Stowing, lalu cocokkan ke tiap baris Manifest berdasarkan isinya
                // (bukan posisi barisnya). Jika satu kombinasi Deskripsi+Customer muncul di
                // lebih dari satu NO PAG (ambigu), kita SENGAJA tidak menebak -> dibiarkan
                // kosong, supaya tidak salah assign (lebih baik kosong daripada salah).
                val stowingPagMap = mutableMapOf<String, MutableSet<String>>()
                for (i in 13..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue
                    val stowNoPag = getCellString(row, 8, evaluator)
                    val stowDescription = getCellString(row, 9, evaluator)
                    val stowCustomer = getCellString(row, 12, evaluator)
                    if (stowNoPag.isBlank() || stowNoPag.contains("TOTAL", true)) continue

                    val key = "${stowDescription.trim().uppercase()}_${stowCustomer.trim().uppercase()}"
                    stowingPagMap.getOrPut(key) { mutableSetOf() }.add(stowNoPag)
                }

                for (i in 13..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue

                    val noCol = getCellString(row, 0, evaluator)
                    val pti = getCellString(row, 1, evaluator)
                    val pcsQty = getCellString(row, 2, evaluator)
                    val pcsWeight = getCellString(row, 3, evaluator)
                    val subTotal = getCellString(row, 4, evaluator)
                    val description = getCellString(row, 5, evaluator)
                    val customer = getCellString(row, 6, evaluator)

                    // Kolom N (index 13) = kolom tersembunyi khusus yang ditulis oleh
                    // fungsi export di bawah untuk menyimpan NO PAG per baris manifest
                    // secara langsung (lossless round-trip untuk file hasil export app ini).
                    val directNoPag = getCellString(row, 13, evaluator)

                    val isTotalRow = noCol.contains("TOTAL", true) ||
                            pti.contains("TOTAL", true) ||
                            description.contains("TOTAL", true) ||
                            description.contains("Prepared", true) ||
                            description.contains("Approved", true) ||
                            customer.contains("Approved", true)

                    if (isTotalRow) continue
                    if (pti.isBlank() && description.isBlank() && pcsQty.isBlank()) continue

                    // Prioritas: 1) kolom tersembunyi (paling akurat), 2) pencocokan
                    // deskripsi+customer ke blok Stowing (hanya jika tidak ambigu).
                    val matchKey = "${description.trim().uppercase()}_${customer.trim().uppercase()}"
                    val matchedSet = stowingPagMap[matchKey]
                    val resolvedNoPag = when {
                        directNoPag.isNotBlank() -> directNoPag
                        matchedSet != null && matchedSet.size == 1 -> matchedSet.first()
                        else -> ""
                    }

                    val newItem = CargoItem(
                        id = System.currentTimeMillis() + i + (0..1000).random(),
                        awbNo = extractedAwb,
                        flightNo = extractedFlight,
                        pti = pti,
                        pcsQty = pcsQty,
                        weight = pcsWeight,
                        subTotal = if (subTotal.isNotBlank()) subTotal else pcsWeight,
                        description = description,
                        customer = customer,
                        noPag = resolvedNoPag
                    )

                    importedList.add(newItem)
                }
                workbook.close()

                // Ganti seluruh isi tabel di database dengan data hasil import,
                // dengan id = 0L agar Room men-generate primary key baru untuk tiap baris.
                dao.deleteAllCargo()
                dao.insertAll(importedList.map { it.copy(id = 0L) })

                withContext(Dispatchers.Main) {
                    _importedAwbNo.value = extractedAwb
                    _importedFlightNo.value = extractedFlight
                    Toast.makeText(context, "Berhasil Import ${importedList.size} Data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal Import: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ==========================================
    // 3. EXPORT EXCEL (DENGAN GROUPING SAMA DI MANIFEST & NO PAG STOWING)
    // ==========================================
    fun exportToExcel(context: Context, awbNo: String, flightNo: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rawList = cargoList.value
                if (rawList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Data Kosong!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // A. GROUPING MANIFEST: Gabungkan item dengan PTI + Description + Customer + NO PAG yang sama.
                // NO PAG ikut disertakan di key supaya item dengan PTI/Deskripsi/Customer sama
                // tapi beda kontainer (NO PAG) TIDAK ikut tergabung dan kehilangan info PAG-nya.
                val groupedManifest = rawList.groupBy { 
                    "${it.pti.trim().uppercase()}_${it.description.trim().uppercase()}_${it.customer.trim().uppercase()}_${it.noPag.trim().uppercase()}" 
                }.map { (_, items) ->
                    val totalPcs = items.sumOf { parseDoubleOrZero(it.pcsQty) }
                    val totalWeight = items.sumOf { parseDoubleOrZero(it.subTotal) }
                    val firstItem = items.first()
                    
                    firstItem.copy(
                        pcsQty = formatNumber(totalPcs),
                        subTotal = formatNumber(totalWeight),
                        weight = if (totalPcs > 0) formatNumber(totalWeight / totalPcs) else firstItem.weight
                    )
                }

                // B. GROUPING STOWING: Gabungkan item dengan NO PAG yang sama
                val groupedStowing = rawList.filter { it.noPag.isNotBlank() }
                    .groupBy { "${it.noPag.trim().uppercase()}_${it.description.trim().uppercase()}_${it.customer.trim().uppercase()}" }
                    .map { (_, items) ->
                        val totalNet = items.sumOf { parseDoubleOrZero(it.subTotal) }
                        val firstItem = items.first()
                        firstItem.copy(subTotal = formatNumber(totalNet))
                    }

                val inputStream = context.assets.open("template_manifest.xlsx")
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                inputStream.close()

                val startRow = 13 // Baris 14 di Excel (0-indexed)
                val templateDataCapacity = 24 // Kapasitas default baris 14 s/d 37

                val finalAwb = if (awbNo.isNotBlank()) awbNo else _importedAwbNo.value
                val finalFlight = if (flightNo.isNotBlank()) flightNo else _importedFlightNo.value

                sheet.getRow(2)?.getCell(6)?.setCellValue(finalAwb)
                sheet.getRow(8)?.getCell(6)?.setCellValue(finalFlight)

                val maxRows = maxOf(groupedManifest.size, groupedStowing.size)
                val sampleRow = sheet.getRow(startRow)

                // 1. CLEANSING TOTAL: Bersihkan seluruh isi sel dari baris 14 sampai 38
                // (Kolom 0 s/d 13; kolom 13/N ikut dibersihkan karena dipakai untuk
                // menyimpan NO PAG per baris manifest secara tersembunyi)
                for (r in startRow until (startRow + templateDataCapacity + 1)) {
                    val targetRow = sheet.getRow(r)
                    if (targetRow != null) {
                        for (c in 0..13) {
                            val cell = targetRow.getCell(c)
                            if (cell != null) {
                                cell.setCellValue("")
                            }
                        }
                    }
                }

                // 2. SHIFT ROWS JIKA DATA LEBIH DARI KAPASITAS TEMPLATE
                if (maxRows > templateDataCapacity) {
                    val extraRowsNeeded = maxRows - templateDataCapacity
                    sheet.shiftRows(37, sheet.lastRowNum, extraRowsNeeded, true, true)
                }

                var totalManifestPcs = 0.0
                var totalManifestWeight = 0.0
                var totalStowingNet = 0.0
                var totalStowingGross = 0.0

                // 3. ISI DATA TERAGREGASI KE EXCEL
                for (i in 0 until maxRows) {
                    val rowIdx = startRow + i
                    var row = sheet.getRow(rowIdx)
                    if (row == null) {
                        row = sheet.createRow(rowIdx)
                        sampleRow?.let { row.height = it.height }
                    }

                    // A. ISI SISI MANIFEST
                    if (i < groupedManifest.size) {
                        val item = groupedManifest[i]
                        val pcs = parseDoubleOrZero(item.pcsQty)
                        val subTotal = parseDoubleOrZero(item.subTotal)

                        totalManifestPcs += pcs
                        totalManifestWeight += subTotal

                        setStyledNumericCell(row, 0, (i + 1).toDouble(), sampleRow?.getCell(0))
                        setStyledTextCell(row, 1, item.pti, sampleRow?.getCell(1))
                        setStyledNumericCell(row, 2, pcs, sampleRow?.getCell(2))
                        setStyledNumericCell(row, 3, parseDoubleOrZero(item.weight), sampleRow?.getCell(3))
                        setStyledNumericCell(row, 4, subTotal, sampleRow?.getCell(4))
                        setStyledTextCell(row, 5, item.description, sampleRow?.getCell(5))
                        setStyledTextCell(row, 6, item.customer, sampleRow?.getCell(6))
                        // Kolom N (index 13): simpan NO PAG asli per baris manifest ini,
                        // supaya kalau file ini di-import lagi ke app, NO PAG tiap item
                        // terbaca persis (tidak lagi menebak lewat posisi baris blok Stowing).
                        setStyledTextCell(row, 13, item.noPag, sampleRow?.getCell(1))
                    }

                    // B. ISI SISI STOWING CHECKLIST
                    if (i < groupedStowing.size) {
                        val stowing = groupedStowing[i]
                        val net = parseDoubleOrZero(stowing.subTotal)
                        val gross = net + 125.0 // Tare kontainer

                        totalStowingNet += net
                        totalStowingGross += gross

                        setStyledNumericCell(row, 7, (i + 1).toDouble(), sampleRow?.getCell(7))
                        setStyledTextCell(row, 8, stowing.noPag, sampleRow?.getCell(8))
                        setStyledTextCell(row, 9, stowing.description, sampleRow?.getCell(9))
                        setStyledNumericCell(row, 10, net, sampleRow?.getCell(10))
                        setStyledNumericCell(row, 11, gross, sampleRow?.getCell(11))
                        setStyledTextCell(row, 12, stowing.customer, sampleRow?.getCell(12))
                    }
                }

                // 4. SET BARIS TOTAL AKTUAL
                val totalRowIdx = if (maxRows <= templateDataCapacity) 37 else (startRow + maxRows)
                val totalRow = sheet.getRow(totalRowIdx) ?: sheet.createRow(totalRowIdx)

                setNumericCell(totalRow, 2, totalManifestPcs)
                setNumericCell(totalRow, 4, totalManifestWeight)

                if (groupedStowing.isNotEmpty()) {
                    setNumericCell(totalRow, 10, totalStowingNet)
                    setNumericCell(totalRow, 11, totalStowingGross)
                }

                val file = File(context.cacheDir, "Manifest_Cargo_Output.xlsx")
                val outputStream = FileOutputStream(file)
                workbook.write(outputStream)
                outputStream.close()
                workbook.close()

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal Export: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ==========================================
    // HELPER FUNCTIONS
    // ==========================================
    private fun getCellString(row: Row, colIdx: Int, evaluator: FormulaEvaluator): String {
        val cell = row.getCell(colIdx) ?: return ""
        return getCellStringFromCell(cell, evaluator)
    }

    private fun getCellStringFromCell(cell: Cell?, evaluator: FormulaEvaluator): String {
        if (cell == null) return ""
        val evaluated = evaluator.evaluate(cell)
        return when (evaluated?.cellType) {
            CellType.NUMERIC -> formatNumber(evaluated.numberValue)
            CellType.STRING -> evaluated.stringValue.trim()
            else -> cell.toString().trim()
        }
    }

    private fun parseDoubleOrZero(value: String): Double {
        if (value.isBlank()) return 0.0
        val cleanValue = value.replace(",", ".").replace("[^0-9.]".toRegex(), "")
        return cleanValue.toDoubleOrNull() ?: 0.0
    }

    private fun formatNumber(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }

    private fun setNumericCell(row: Row, col: Int, value: Double) {
        val cell = row.getCell(col) ?: row.createCell(col)
        cell.setCellValue(value)
    }

    private fun setStyledNumericCell(row: Row, col: Int, value: Double, sampleCell: Cell?) {
        val cell = row.getCell(col) ?: row.createCell(col)
        cell.setCellValue(value)
        sampleCell?.cellStyle?.let { cell.cellStyle = it }
    }

    private fun setStyledTextCell(row: Row, col: Int, value: String, sampleCell: Cell?) {
        val cell = row.getCell(col) ?: row.createCell(col)
        cell.setCellValue(value)
        sampleCell?.cellStyle?.let { cell.cellStyle = it }
    }
}
