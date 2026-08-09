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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileOutputStream

// Helper Model Manifest untuk Export Excel
data class GroupedManifestItem(
    val pti: String,
    val description: String,
    val customer: String,
    var pcsQty: Double,
    var weight: Double,
    var subTotal: Double
)

// Helper Model Stowing untuk Export Excel
data class GroupedStowingItem(
    val noPag: String,
    val description: String,
    val customer: String,
    var subTotal: Double
)

class CargoViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = CargoDatabase.getDatabase(application).cargoDao()

    val cargoList: StateFlow<List<CargoItem>> = dao.getAllCargo()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addCargo(
        awbNo: String,
        flightNo: String,
        pti: String,
        pcsQty: String,
        weight: String,
        subTotal: String,
        description: String,
        customer: String,
        noPag: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cargo = CargoItem(
                awbNo = awbNo.trim().uppercase(),
                flightNo = flightNo.trim().uppercase(),
                pti = pti.trim().uppercase(),
                pcsQty = pcsQty.trim(),
                weight = weight.trim(),
                subTotal = subTotal.trim(),
                description = description.trim().uppercase(),
                customer = customer.trim().uppercase(),
                noPag = noPag.trim().uppercase()
            )
            dao.insertCargo(cargo)
        }
    }

    fun updateCargo(cargo: CargoItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedCargo = cargo.copy(
                awbNo = cargo.awbNo.trim().uppercase(),
                flightNo = cargo.flightNo.trim().uppercase(),
                pti = cargo.pti.trim().uppercase(),
                pcsQty = cargo.pcsQty.trim(),
                weight = cargo.weight.trim(),
                subTotal = cargo.subTotal.trim(),
                description = cargo.description.trim().uppercase(),
                customer = cargo.customer.trim().uppercase(),
                noPag = cargo.noPag.trim().uppercase()
            )
            dao.updateCargo(updatedCargo)
        }
    }

    fun deleteCargo(cargo: CargoItem) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteCargo(cargo)
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAllCargo()
        }
    }

    // ================= FUNGSI IMPORT DATA EXCEL =================
    fun importFromExcel(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Gagal membaca file dari penyimpanan.")

                val workbook = WorkbookFactory.create(inputStream)
                inputStream.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                val formatter = DataFormatter()

                // Baca Header AWB & Flight No jika tersedia
                val awbNo = formatter.formatCellValue(sheet.getRow(2)?.getCell(6)).trim().uppercase()
                val flightRaw = formatter.formatCellValue(sheet.getRow(8)?.getCell(6)).trim().uppercase()
                val flightNo = flightRaw.removePrefix(":").trim()

                val importedItems = mutableListOf<CargoItem>()

                // Cek baris awal data (baris 13 untuk template, baris 1 untuk file tabel sederhana)
                val startRow = if (sheet.lastRowNum >= 13) 13 else 1

                for (rowIndex in startRow..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue

                    // Ambil isi kolom
                    val rawPti = formatter.formatCellValue(row.getCell(1)).trim().uppercase()
                    val pcsQty = formatter.formatCellValue(row.getCell(2)).trim()
                    val weight = formatter.formatCellValue(row.getCell(3)).trim()
                    val subTotal = formatter.formatCellValue(row.getCell(4)).trim()
                    val rawDesc = formatter.formatCellValue(row.getCell(5)).trim().uppercase()
                    val rawCust = formatter.formatCellValue(row.getCell(6)).trim().uppercase()
                    val rawPag = formatter.formatCellValue(row.getCell(8)).trim().uppercase()

                    // Pastikan baris berisi minimal 1 data valid
                    if (rawPti.isNotBlank() || pcsQty.isNotBlank() || rawDesc.isNotBlank() || rawCust.isNotBlank()) {
                        
                        // Format Otomatis Awalan KAL dan PAG
                        val finalPti = if (rawPti.isBlank()) "" else if (rawPti.startsWith("KAL")) rawPti else "KAL$rawPti"
                        val finalPag = if (rawPag.isBlank() || rawPag == "-") "" else if (rawPag.startsWith("PAG")) rawPag else "PAG$rawPag"

                        importedItems.add(
                            CargoItem(
                                awbNo = awbNo,
                                flightNo = flightNo,
                                pti = finalPti,
                                pcsQty = pcsQty,
                                weight = weight,
                                subTotal = subTotal,
                                description = if (rawDesc == "-") "" else rawDesc,
                                customer = if (rawCust == "-") "" else rawCust,
                                noPag = finalPag
                            )
                        )
                    }
                }

                if (importedItems.isNotEmpty()) {
                    importedItems.forEach { dao.insertCargo(it) }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Berhasil mengimpor ${importedItems.size} data!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Tidak ada data valid yang ditemukan dalam file!", Toast.LENGTH_SHORT).show()
                    }
                }

                workbook.close()
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal Import: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ================= FUNGSI EXPORT DATA EXCEL =================
    fun exportToExcel(context: Context, awbNo: String, flightNo: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentList = cargoList.value

                if (currentList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Tidak ada data untuk diexport!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val groupedManifest = currentList.groupBy {
                    Pair(it.description.trim().uppercase(), it.customer.trim().uppercase())
                }.map { (keyPair, items) ->
                    val descKey = keyPair.first
                    val custKey = keyPair.second

                    val uniquePti = items.map { it.pti }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                    
                    GroupedManifestItem(
                        pti = uniquePti,
                        description = if (descKey.isBlank()) "-" else descKey,
                        customer = if (custKey.isBlank()) "-" else custKey,
                        pcsQty = items.sumOf { it.pcsQty.toDoubleOrNull() ?: 0.0 },
                        weight = items.sumOf { it.weight.toDoubleOrNull() ?: 0.0 },
                        subTotal = items.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
                    )
                }

                val groupedStowing = currentList.groupBy {
                    it.noPag.trim().uppercase()
                }.map { (pagKey, items) ->
                    val uniqueDescs = items.map { it.description }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                    val uniqueCusts = items.map { it.customer }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                    GroupedStowingItem(
                        noPag = if (pagKey.isBlank()) "-" else pagKey,
                        description = uniqueDescs,
                        customer = uniqueCusts,
                        subTotal = items.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
                    )
                }

                val inputStream = context.assets.open("template_manifest.xlsx")
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)

                val row3 = sheet.getRow(2) ?: sheet.createRow(2)
                (row3.getCell(6) ?: row3.createCell(6)).setCellValue(awbNo.trim().uppercase())

                val row4 = sheet.getRow(3) ?: sheet.createRow(3)
                (row4.getCell(6) ?: row4.createCell(6)).setCellValue(awbNo.trim().uppercase())

                val row9 = sheet.getRow(8) ?: sheet.createRow(8)
                (row9.getCell(6) ?: row9.createCell(6)).setCellValue(": ${flightNo.trim().uppercase()}")

                var manifestRowIndex = 13
                for ((index, item) in groupedManifest.withIndex()) {
                    val row = sheet.getRow(manifestRowIndex) ?: sheet.createRow(manifestRowIndex)

                    (row.getCell(0) ?: row.createCell(0)).setCellValue((index + 1).toDouble())
                    (row.getCell(1) ?: row.createCell(1)).setCellValue(item.pti)
                    (row.getCell(2) ?: row.createCell(2)).setCellValue(item.pcsQty)
                    (row.getCell(3) ?: row.createCell(3)).setCellValue(item.weight)
                    (row.getCell(4) ?: row.createCell(4)).setCellValue(item.subTotal)
                    (row.getCell(5) ?: row.createCell(5)).setCellValue(item.description)
                    (row.getCell(6) ?: row.createCell(6)).setCellValue(item.customer)

                    manifestRowIndex++
                }

                var stowingRowIndex = 13
                for ((index, item) in groupedStowing.withIndex()) {
                    val row = sheet.getRow(stowingRowIndex) ?: sheet.createRow(stowingRowIndex)

                    (row.getCell(7) ?: row.createCell(7)).setCellValue((index + 1).toDouble())
                    (row.getCell(8) ?: row.createCell(8)).setCellValue(item.noPag)
                    (row.getCell(9) ?: row.createCell(9)).setCellValue(item.description)
                    (row.getCell(10) ?: row.createCell(10)).setCellValue(item.subTotal)
                    (row.getCell(12) ?: row.createCell(12)).setCellValue(item.customer)

                    stowingRowIndex++
                }

                val file = File(context.cacheDir, "Manifest_Cargo_Output.xlsx")
                val outputStream = FileOutputStream(file)
                workbook.write(outputStream)
                outputStream.close()
                workbook.close()

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )

                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }

                    context.startActivity(Intent.createChooser(intent, "Buka File Excel dengan"))
                    Toast.makeText(context, "Export Excel Berhasil!", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
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
}
