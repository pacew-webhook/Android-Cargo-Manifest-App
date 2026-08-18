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

    companion object {
        // Kolom ke-20 (index 19, 0-based -> kolom "T"). Dipilih jauh di luar area
        // tabel yang dipakai template (kolom terpakai cuma sampai R/index17), dan
        // di-hide eksplisit saat export (lihat sheet.setColumnHidden), supaya NO PAG
        // per item bisa disimpan tanpa pernah terlihat/mengacaukan tampilan Excel.
        private const val HIDDEN_PAG_COLUMN = 19
    }

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

    // ==========================================
    // 1B. KIRIM SNAPSHOT MANIFEST KE n8n
    // ==========================================
    // Android mengirim data mentah dari Room. Pengelompokan resmi dilakukan
    // di n8n dengan kunci: PTI + NO PAG + Customer + Description.
    fun sendManifestToN8n(onResult: (Result<String>) -> Unit) {
        val snapshot = cargoList.value
        if (snapshot.isEmpty()) {
            onResult(Result.failure(IllegalStateException("Data Manifest kosong")))
            return
        }

        viewModelScope.launch {
            val result = N8nClient.sendManifest(snapshot)
            withContext(Dispatchers.Main) {
                onResult(result)
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

                    // Kolom tersembunyi (HIDDEN_PAG_COLUMN) = kolom khusus yang ditulis oleh
                    // fungsi export di bawah untuk menyimpan NO PAG per baris manifest
                    // secara langsung (lossless round-trip untuk file hasil export app ini).
                    val directNoPag = getCellString(row, HIDDEN_PAG_COLUMN, evaluator)

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
                    val missingPagCount = importedList.count { it.noPag.isBlank() }
                    val message = if (missingPagCount > 0) {
                        "Berhasil Import ${importedList.size} Data (${missingPagCount} item belum ada NO PAG, cek & lengkapi manual)"
                    } else {
                        "Berhasil Import ${importedList.size} Data"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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

                /*
                 * SATU-SATUNYA jalur export sekarang adalah ExcelUtils.
                 * Jangan lagi membangun sheet Manifest/Stowing Checklist di sini.
                 * Dengan demikian export dari Manifest Cargo memakai logika yang
                 * sama persis dengan export Stowing Cargo:
                 *
                 * - sumber data = cargoList Stowing
                 * - Manifest digabung berdasarkan PTI + NO PAG + Customer + Description
                 * - Pcs/Cly dan Sub Total dijumlahkan
                 * - Weight Pcs/Cly dikosongkan
                 * - STOWING CHECK digabung berdasarkan NO PAG + Customer + Description
                 */
                val file = File(context.cacheDir, "Manifest_Cargo_Output.xlsx")
                ExcelUtils.writeCombinedCargoWorkbookToFile(
                    context = context,
                    file = file,
                    cargoList = rawList
                )

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )

                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(
                            uri,
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Gagal Export: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
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
