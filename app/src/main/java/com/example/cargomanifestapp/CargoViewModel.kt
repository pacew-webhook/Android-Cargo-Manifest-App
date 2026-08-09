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

                // 1. Cek apakah file template terdeteksi di dalam APK
                val assetList = context.assets.list("") ?: arrayOf()
                val isTemplateExist = assetList.contains("template_manifest.xlsx")

                if (!isTemplateExist) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "File 'template_manifest.xlsx' tidak ditemukan dalam APK!\nIsi assets: ${assetList.joinToString()}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                // 2. Baca template Excel secara langsung
                val inputStream = context.assets.open("template_manifest.xlsx")
                val workbook: Workbook = WorkbookFactory.create(inputStream)
                inputStream.close()

                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)

                // 3. Isi Header AWB No dan Flight No (Ubah indeks baris/kolom sesuai sel template)
                val rowAwb = sheet.getRow(4) ?: sheet.createRow(4)
                (rowAwb.getCell(2) ?: rowAwb.createCell(2)).setCellValue(awbNo)

                val rowFlight = sheet.getRow(5) ?: sheet.createRow(5)
                (rowFlight.getCell(2) ?: rowFlight.createCell(2)).setCellValue(flightNo)

                // 4. Masukkan data barang mulai dari baris ke-14 Excel (index 13)
                var rowIndex = 13
                for ((index, item) in currentList.withIndex()) {
                    val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)

                    (row.getCell(0) ?: row.createCell(0)).setCellValue((index + 1).toDouble())
                    (row.getCell(1) ?: row.createCell(1)).setCellValue(item.pti)
                    (row.getCell(2) ?: row.createCell(2)).setCellValue(item.pcsQty.toDoubleOrNull() ?: 0.0)
                    (row.getCell(3) ?: row.createCell(3)).setCellValue(item.weight.toDoubleOrNull() ?: 0.0)
                    (row.getCell(4) ?: row.createCell(4)).setCellValue(item.subTotal.toDoubleOrNull() ?: 0.0)
                    (row.getCell(5) ?: row.createCell(5)).setCellValue(item.description)

                    val customerText = if (item.noPag.isNotBlank()) "${item.customer} - ${item.noPag}" else item.customer
                    (row.getCell(6) ?: row.createCell(6)).setCellValue(customerText)

                    rowIndex++
                }

                // Save & Share File
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
                        "Error Template: ${e.javaClass.simpleName} - ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
