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

                // 1. PENGGABUNGAN DATA (AGGREGATION) BERDASARKAN DESCRIPTION DAN CUSTOMER
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

                // Grouping Stowing Checklist berdasarkan NO PAG
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

                // 2. OLAH TEMPLATE EXCEL
                val inputStream = context.assets.open("template_manifest.xlsx")
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)

                // AWB NO ke Kolom G3:G4
                val row3 = sheet.getRow(2) ?: sheet.createRow(2)
                (row3.getCell(6) ?: row3.createCell(6)).setCellValue(awbNo.trim().uppercase())

                val row4 = sheet.getRow(3) ?: sheet.createRow(3)
                (row4.getCell(6) ?: row4.createCell(6)).setCellValue(awbNo.trim().uppercase())

                // FLIGHT NO ke Kolom G9
                val row9 = sheet.getRow(8) ?: sheet.createRow(8)
                (row9.getCell(6) ?: row9.createCell(6)).setCellValue(": ${flightNo.trim().uppercase()}")

                // 3. MASUKKAN DATA MANIFEST CARGO
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

                // 4. MASUKKAN DATA STOWING CHECKLIST
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

                // 5. SIMPAN DAN BUKA FILE
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
