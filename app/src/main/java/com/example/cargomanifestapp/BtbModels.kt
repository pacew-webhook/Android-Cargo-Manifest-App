package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import org.apache.poi.xssf.usermodel.XSSFWorkbook

// Data class untuk Bukti Timbang Barang
data class BtbFormData(
    val hariTanggal: String = "",
    val customerName: String = "",
    val trademarks: String = "",
    val jenisBarang: String = "",
    val daftarTimbangan: List<Double> = emptyList()
)

// Helper untuk export Excel Bukti Timbang Barang
object BtbExcelWriter {
    fun fillBtbTemplate(context: Context, uri: Uri, data: BtbFormData) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("BTB")
        
        // Header
        val row0 = sheet.createRow(0)
        row0.createCell(0).setCellValue("Hari/Tanggal")
        row0.createCell(1).setCellValue(data.hariTanggal)
        
        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("Customer")
        row1.createCell(1).setCellValue(data.customerName)
        
        val row2 = sheet.createRow(2)
        row2.createCell(0).setCellValue("Trademarks")
        row2.createCell(1).setCellValue(data.trademarks)
        
        val row3 = sheet.createRow(3)
        row3.createCell(0).setCellValue("Jenis Barang")
        row3.createCell(1).setCellValue(data.jenisBarang)
        
        // Data Timbangan (5 Kolom per baris)
        var currentRow = 5
        data.daftarTimbangan.chunked(5).forEach { chunk ->
            val row = sheet.createRow(currentRow++)
            chunk.forEachIndexed { colIndex, weight ->
                row.createCell(colIndex).setCellValue(weight)
            }
        }
        
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            workbook.write(outputStream)
        }
        workbook.close()
    }
}
