package com.example.cargomanifestapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class CargoViewModel(private val cargoDao: CargoDao) : ViewModel() {

    // Mengambil data dari DAO secara asinkron
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
        customer: String
    ) {
        viewModelScope.launch {
            cargoDao.insert(
                CargoItem(
                    awbNo = awbNo,
                    flightNo = flightNo,
                    pti = pti,
                    pcsQty = pcsQty,
                    weight = weight,
                    subTotal = subTotal,
                    description = description,
                    customer = customer
                )
            )
        }
    }

    fun deleteCargo(cargoItem: CargoItem) {
        viewModelScope.launch {
            cargoDao.delete(cargoItem)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            cargoDao.deleteAll()
        }
    }

    fun exportToExcel(context: Context) {
        viewModelScope.launch {
            val list = cargoList.value
            if (list.isEmpty()) return@launch

            try {
                val inputStream: InputStream = context.assets.open("template.xlsx")
                val workbook = XSSFWorkbook(inputStream)
                val sheet = workbook.getSheetAt(0)

                val firstItem = list.first()

                // 1. ISI HEADER (FLIGHT NO & AWB NO)
                val flightRow = sheet.getRow(8) ?: sheet.createRow(8)
                val flightCell = flightRow.getCell(6) ?: flightRow.createCell(6)
                flightCell.setCellValue(": ${firstItem.flightNo}")

                val awbRow = sheet.getRow(3) ?: sheet.createRow(3)
                val awbCell = awbRow.getCell(6) ?: awbRow.createCell(6)
                awbCell.setCellValue(firstItem.awbNo)

                // 2. ISI TABEL DATA BARANG (Mulai Baris 14 / Index 13)
                val startRow = 13
                val size = list.size

                for (i in 0 until size) {
                    val item = list[i]
                    val rowIndex = startRow + i
                    val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)

                    // No
                    val cellNo = row.getCell(0) ?: row.createCell(0)
                    cellNo.setCellValue((i + 1).toDouble())

                    // PTI
                    val cellPti = row.getCell(1) ?: row.createCell(1)
                    cellPti.setCellValue(item.pti)

                    // Pcs/Cly
                    val cellPcs = row.getCell(2) ?: row.createCell(2)
                    cellPcs.setCellValue(item.pcsQty.toDoubleOrNull() ?: 0.0)

                    // Weight Pcs/Cly
                    val cellWt = row.getCell(3) ?: row.createCell(3)
                    cellWt.setCellValue(item.weight.toDoubleOrNull() ?: 0.0)

                    // Weight Sub Total
                    val cellSub = row.getCell(4) ?: row.createCell(4)
                    cellSub.setCellValue(item.subTotal.toDoubleOrNull() ?: 0.0)

                    // Description
                    val cellDesc = row.getCell(5) ?: row.createCell(5)
                    cellDesc.setCellValue(item.description)

                    // Customer
                    val cellCust = row.getCell(6) ?: row.createCell(6)
                    cellCust.setCellValue(item.customer)
                }

                workbook.setForceFormulaRecalculation(true)
                inputStream.close()

                val file = File(context.cacheDir, "MANIFEST_CARGO.xlsx")
                val outputStream = FileOutputStream(file)
                workbook.write(outputStream)
                outputStream.close()
                workbook.close()

                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        uri,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
