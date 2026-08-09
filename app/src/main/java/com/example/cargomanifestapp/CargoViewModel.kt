package com.example.cargomanifestapp

import android.app.Application
import android.content.Context
import android.content.Intent
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
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileOutputStream

// Helper Model Manifest
data class GroupedManifestItem(
    val pti: String,
    val description: String,
    val customer: String,
    var pcsQty: Double,
    var weight: Double,
    var subTotal: Double
)

// Helper Model Stowing
data class GroupedStowingItem(
    val noPag: String,
    val description: String,
    val customer: String,
    var subTotal: Double
)

class CargoViewModel(application: Application) : AndroidViewModel(application) {

    private val cargoDao: CargoDao = CargoDatabase.getDatabase(application).cargoDao()

    val cargoList: StateFlow<List<CargoItem>> = cargoDao.getAllCargo()
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
            val item = CargoItem(
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
            cargoDao.insert(item)
        }
    }

    fun updateCargo(item: CargoItem) {
        viewModelScope.launch(Dispatchers.IO) {
            cargoDao.update(item)
        }
    }

    fun deleteCargo(item: CargoItem) {
        viewModelScope.launch(Dispatchers.IO) {
            cargoDao.delete(item)
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            cargoDao.deleteAll()
        }
    }

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

                // -------------------------------------------------------------
                // 1. PENGGABUNGAN DATA (AGGREGATION)
                // -------------------------------------------------------------
                
                // Grouping Manifest CARGO MURNI berdasarkan DESCRIPTION SAMA
                val groupedManifest = currentList.groupBy {
                    it.description.trim().uppercase()
                }.map { (descKey, items) ->
                    val uniquePti = items.map { it.pti }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                    val uniqueCust = items.map { it.customer }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                    GroupedManifestItem(
                        pti = uniquePti,
                        description = if (descKey.isBlank()) "-" else descKey,
                        customer = uniqueCust,
                        pcsQty = items.sumOf { it.pcsQty.toDoubleOrNull() ?: 0.0 },
                        weight = items.sumOf { it.weight.toDoubleOrNull() ?: 0.0 },
                        subTotal = items.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
                    )
                }

                // Grouping Stowing Checklist berdasarkan NO PAG SAMA
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

                // -------------------------------------------------------------
                // 2. OLAH TEMPLATE EXCEL
                // -------------------------------------------------------------
                val inputStream = context.assets.open("template_manifest.xlsx")
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)

                // AWB NO ke Kolom G3:G4
                val row3 = sheet.getRow(2) ?: sheet.createRow(2)
                (row3.getCell(6) ?: row3.createCell(6)).setCellValue(awbNo.trim().uppercase())

                val row4 = sheet.getRow(3) ?: sheet.createRow(3)
                (row4.getCell(6) ?: row4.createCell(6)).setCellValue(awbNo.trim().uppercase())

                // FLIGHT NO ke Kolom G9 dengan format ": 2Y704"
                val row9 = sheet.getRow(8) ?: sheet.createRow(8)
                (row9.getCell(6) ?: row9.createCell(6)).setCellValue(": ${flightNo.trim().uppercase()}")

                // -------------------------------------------------------------
                // 3. MASUKKAN DATA MANIFEST CARGO (Kolom A - G)
                // -------------------------------------------------------------
                var manifestRowIndex = 13 // Mulai Baris 14 (Index 13)
                for ((index, item) in groupedManifest.withIndex()) {
                    val row = sheet.getRow(manifestRowIndex) ?: sheet.createRow(manifestRowIndex)

                    (row.getCell(0) ?: row.createCell(0)).setCellValue((index + 1).toDouble()) // A: No
                    (row.getCell(1) ?: row.createCell(1)).setCellValue(item.pti)              // B: PTI
                    (row.getCell(2) ?: row.createCell(2)).setCellValue(item.pcsQty)            // C: Pcs/Qty
                    (row.getCell(3) ?: row.createCell(3)).setCellValue(item.weight)            // D: Weight
                    (row.getCell(4) ?: row.createCell(4)).setCellValue(item.subTotal)          // E: SubTotal
                    (row.getCell(5) ?: row.createCell(5)).setCellValue(item.description)     // F: Description
                    (row.getCell(6) ?: row.createCell(6)).setCellValue(item.customer)        // G: Customer

                    manifestRowIndex++
                }

                // -------------------------------------------------------------
                // 4. MASUKKAN DATA STOWING CHEKLIST (Kolom H - M)
                // -------------------------------------------------------------
                var stowingRowIndex = 13 // Mulai Baris 14 (Index 13)
                for ((index, item) in groupedStowing.withIndex()) {
                    val row = sheet.getRow(stowingRowIndex) ?: sheet.createRow(stowingRowIndex)

                    (row.getCell(7) ?: row.createCell(7)).setCellValue((index + 1).toDouble()) // H: No
                    (row.getCell(8) ?: row.createCell(8)).setCellValue(item.noPag)            // I: NO PAG
                    (row.getCell(9) ?: row.createCell(9)).setCellValue(item.description)      // J: Description
                    (row.getCell(10) ?: row.createCell(10)).setCellValue(item.subTotal)        // K: Net Weight
                    (row.getCell(12) ?: row.createCell(12)).setCellValue(item.customer)       // M: Customer

                    stowingRowIndex++
                }

                // -------------------------------------------------------------
                // 5. SIMPAN DAN BUKA FILE OUTPUT EXCEL
                // -------------------------------------------------------------
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
