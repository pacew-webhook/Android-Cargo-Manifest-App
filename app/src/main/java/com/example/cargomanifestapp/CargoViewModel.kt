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
                awbNo = awbNo.uppercase(),
                flightNo = flightNo.uppercase(),
                pti = pti.uppercase(),
                pcsQty = pcsQty,
                weight = weight,
                subTotal = subTotal,
                description = description.uppercase(),
                customer = customer.uppercase(),
                noPag = noPag.uppercase()
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

                // 1. Buka Template Excel dari Folder Assets
                val inputStream = context.assets.open("template_manifest.xlsx")
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)

                // 2. Set AWB NO di Kolom G9 (Baris 9 = Index 8, Kolom G = Index 6)
                val rowAwb = sheet.getRow(8) ?: sheet.createRow(8)
                (rowAwb.getCell(6) ?: rowAwb.createCell(6)).setCellValue(awbNo)

                // Set FLIGHT NO di Kolom G10 (Baris 10 = Index 9, Kolom G = Index 6)
                val rowFlight = sheet.getRow(9) ?: sheet.createRow(9)
                (rowFlight.getCell(6) ?: rowFlight.createCell(6)).setCellValue(flightNo)

                // 3. Mengisi Data ke Tabel Manifest & Tabel Stowing Checklist (Mulai Baris 14 / Index 13)
                var rowIndex = 13
                for ((index, item) in currentList.withIndex()) {
                    val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)

                    // === TABEL MANIFEST CARGO (Kolom A - G) ===
                    (row.getCell(0) ?: row.createCell(0)).setCellValue((index + 1).toDouble()) // A: No
                    (row.getCell(1) ?: row.createCell(1)).setCellValue(item.pti)              // B: PTI
                    (row.getCell(2) ?: row.createCell(2)).setCellValue(item.pcsQty.toDoubleOrNull() ?: 0.0) // C: Pcs/Qty
                    (row.getCell(3) ?: row.createCell(3)).setCellValue(item.weight.toDoubleOrNull() ?: 0.0) // D: Weight
                    (row.getCell(4) ?: row.createCell(4)).setCellValue(item.subTotal.toDoubleOrNull() ?: 0.0) // E: SubTotal
                    (row.getCell(5) ?: row.createCell(5)).setCellValue(item.description)     // F: Description
                    (row.getCell(6) ?: row.createCell(6)).setCellValue(item.customer)        // G: Customers

                    // === TABEL STOWING CHEKLIST (Kolom H - L) ===
                    (row.getCell(7) ?: row.createCell(7)).setCellValue((index + 1).toDouble()) // H: No
                    (row.getCell(8) ?: row.createCell(8)).setCellValue(item.noPag)            // I: NO PAG (I14)
                    (row.getCell(9) ?: row.createCell(9)).setCellValue(item.description)      // J: Description
                    (row.getCell(10) ?: row.createCell(10)).setCellValue(item.subTotal.toDoubleOrNull() ?: 0.0) // K: Net
                    (row.getCell(11) ?: row.createCell(11)).setCellValue(item.customer)       // L: Customer

                    rowIndex++
                }

                // 4. Simpan & Buka File Excel Output
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
