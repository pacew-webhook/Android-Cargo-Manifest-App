package com.example.cargomanifestapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class CargoViewModel(private val repository: CargoRepository) : ViewModel() {

    val cargoList = repository.allCargo

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
            repository.insert(
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
            repository.delete(cargoItem)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    fun exportToExcel(context: Context) {
        viewModelScope.launch {
            val list = cargoList.value
            if (list.isEmpty()) return@launch

            try {
                // Membaca template dari folder assets
                val inputStream: InputStream = context.assets.open("template.xlsx")
                val workbook = XSSFWorkbook(inputStream)
                val sheet = workbook.getSheetAt(0)

                val firstItem = list.first()

                // Styles untuk data tabel
                val dataStyle = workbook.createCellStyle().apply {
                    alignment = HorizontalAlignment.CENTER
                    verticalAlignment = VerticalAlignment.CENTER
                    val font = workbook.createFont().apply {
                        fontName = "Arial"
                        fontHeightInPoints = 10.toShort() // DIPERBAIKI: fontHeightInPoints
                    }
                    setFont(font)
                }

                val leftStyle = workbook.createCellStyle().apply {
                    alignment = HorizontalAlignment.LEFT
                    verticalAlignment = VerticalAlignment.CENTER
                    val font = workbook.createFont().apply {
                        fontName = "Arial"
                        fontHeightInPoints = 10.toShort() // DIPERBAIKI: fontHeightInPoints
                    }
                    setFont(font)
                }

                // 1. ISI FLIGHT NO & AWB NO SESUAI TEMPLATE
                // FLIGHT NO -> Row 9 (Index 8), Column G (Index 6)
                val flightRow = sheet.getRow(8) ?: sheet.createRow(8)
                val flightCell = flightRow.getCell(6) ?: flightRow.createCell(6)
                flightCell.setCellValue(": ${firstItem.flightNo}")

                // AWB NO -> Row 4 (Index 3), Column G (Index 6)
                val awbRow = sheet.getRow(3) ?: sheet.createRow(3)
                val awbCell = awbRow.getCell(6) ?: awbRow.createCell(6)
                awbCell.setCellValue(firstItem.awbNo)

                // 2. ISI TABEL DATA BARANG (Mulai Baris 14 / Index 13)
                val startRow = 13
                list.forEachIndexed { index, item ->
                    val row = sheet.getRow(startRow + index) ?: sheet.createRow(startRow + index)

                    // No
                    val cellNo = row.getCell(0) ?: row.createCell(0)
                    cellNo.setCellValue((index + 1).toDouble())
                    cellNo.cellStyle = dataStyle

                    // PTI
                    val cellPti = row.getCell(1) ?: row.createCell(1)
                    cellPti.setCellValue(item.pti)
                    cellPti.cellStyle = dataStyle

                    // Pcs/Cly
                    val cellPcs = row.getCell(2) ?: row.createCell(2)
                    cellPcs.setCellValue(item.pcsQty.toDoubleOrNull() ?: 0.0)
                    cellPcs.cellStyle = dataStyle

                    // Weight Pcs/Cly
                    val cellWt = row.getCell(3) ?: row.createCell(3)
                    cellWt.setCellValue(item.weight.toDoubleOrNull() ?: 0.0)
                    cellWt.cellStyle = dataStyle

                    // Weight Sub Total
                    val cellSub = row.getCell(4) ?: row.createCell(4)
                    cellSub.setCellValue(item.subTotal.toDoubleOrNull() ?: 0.0)
                    cellSub.cellStyle = dataStyle

                    // Description
                    val cellDesc = row.getCell(5) ?: row.createCell(5)
                    cellDesc.setCellValue(item.description)
                    cellDesc.cellStyle = leftStyle

                    // Customer
                    val cellCust = row.getCell(6) ?: row.createCell(6)
                    cellCust.setCellValue(item.customer)
                    cellCust.cellStyle = leftStyle
                }

                inputStream.close()

                // Simpan & Buka File Excel
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
