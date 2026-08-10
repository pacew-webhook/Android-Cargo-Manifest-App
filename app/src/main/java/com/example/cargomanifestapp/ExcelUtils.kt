package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

object ExcelUtils {
    
    fun writeCargoListToExcel(context: Context, uri: Uri, cargoList: List<CargoItem>) {
        try {
            // Membuka template dari assets
            val inputStream: InputStream = context.assets.open("STOWINGAN_PAG_TEMPLATE.xlsx")
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)
            
            var startRow = 4 // Sesuaikan dengan baris data template Anda

            for ((index, item) in cargoList.withIndex()) {
                val row = sheet.getRow(startRow) ?: sheet.createRow(startRow)
                
                // Pastikan indeks kolom sesuai dengan template Excel Anda
                row.createCell(0).setCellValue((index + 1).toDouble())
                row.createCell(1).setCellValue(item.noPag)
                row.createCell(2).setCellValue(item.customer)
                row.createCell(3).setCellValue(item.weight)
                row.createCell(4).setCellValue(item.subTotal.toDoubleOrNull() ?: 0.0)
                
                startRow++
            }

            // Menulis ke file yang dipilih user
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                workbook.write(outputStream)
                outputStream.close()
                workbook.close()
                inputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e // Lempar error agar bisa ditangkap UI untuk Toast
        }
    }
}

