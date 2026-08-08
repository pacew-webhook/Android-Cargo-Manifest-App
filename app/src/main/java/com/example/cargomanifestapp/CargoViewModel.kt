package com.example.cargomanifestapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class CargoViewModel(private val cargoDao: CargoDao) : ViewModel() {

    // 1. GABUNGKAN DATA KHUSUS UNTUK TAMPILAN DI APLIKASI (UI)
    // Jika Customer & Description sama, otomatis dijumlahkan Pcs & SubTotal-nya di layar HP
    val cargoList: StateFlow<List<CargoItem>> = cargoDao.getAllCargo()
        .map { list ->
            list.groupBy { Pair(it.customer.uppercase(), it.description.uppercase()) }
                .map { (_, groupItems) ->
                    val first = groupItems.first()
                    val totalPcs = groupItems.sumOf { it.pcsQty.toIntOrNull() ?: 0 }
                    val totalSub = groupItems.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
                    
                    // Ambil gabungan No PAG untuk info di UI jika diperlukan
                    val combinedPag = groupItems.map { it.noPag }.distinct().joinToString(", ")

                    first.copy(
                        pcsQty = totalPcs.toString(),
                        subTotal = if (totalSub % 1.0 == 0.0) totalSub.toLong().toString() else totalSub.toString(),
                        noPag = combinedPag
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Untuk keperluan export Excel, kita butuh data mentah aslinya dari database agar berat per PAG akurat
    private suspend fun getRawCargoList(): List<CargoItem> {
        // Mengambil langsung dari DAO tanpa grouping UI
        // Jika ViewModel Anda belum punya fungsi getRaw, Anda bisa buat atau sesuaikan dengan DAO Anda
        return cargoDao.getAllCargoSync() // Pastikan DAO memiliki fungsi non-Flow atau ambil dari value mentah
    }

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
        viewModelScope.launch {
            val cleanCustomer = customer.trim().uppercase()
            val cleanDescription = description.trim().uppercase()
            val cleanNoPag = noPag.trim().uppercase()

            // Simpan murni apa adanya ke database agar berat per No PAG tetap akurat
            cargoDao.insert(
                CargoItem(
                    awbNo = awbNo.trim().uppercase(),
                    flightNo = flightNo.trim().uppercase(),
                    pti = pti.trim().uppercase(),
                    pcsQty = pcsQty.trim(),
                    weight = weight.trim(),
                    subTotal = subTotal.trim(),
                    description = cleanDescription,
                    customer = cleanCustomer,
                    noPag = cleanNoPag
                )
            )
        }
    }

    fun updateCargo(cargoItem: CargoItem) {
        viewModelScope.launch { cargoDao.update(cargoItem) }
    }

    fun deleteCargo(cargoItem: CargoItem) {
        viewModelScope.launch { cargoDao.delete(cargoItem) }
    }

    fun clearAll() {
        viewModelScope.launch { cargoDao.deleteAll() }
    }

    fun exportToExcel(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            // Gunakan data asli (mentah) dari database untuk Excel
            val rawList = cargoDao.getAllCargo().let { 
                // Mengambil snapshot data list database saat ini secara aman
                kotlinx.coroutines.flow.first(cargoDao.getAllCargo()) 
            }

            if (rawList.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Data masih kosong!", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            try {
                val inputStream: InputStream = context.assets.open("template_manifest.xlsx")
                val workbook = XSSFWorkbook(inputStream)
                val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
                val firstItem = rawList.first()

                // Header Flight / AWB
                sheet.getRow(2)?.getCell(6)?.setCellValue(firstItem.awbNo.uppercase())
                sheet.getRow(8)?.getCell(6)?.setCellValue(": ${firstItem.flightNo.uppercase()}")

                val startRowIndex = 13

                // --- 1. ISI TABEL MANIFEST (SEBELAH KIRI) ---
                // Digabung berdasarkan Customer & Description
                val manifestGrouped = rawList.groupBy { Pair(it.customer.uppercase(), it.description.uppercase()) }
                var manifestIdx = 0

                for ((_, groupItems) in manifestGrouped) {
                    val sampleItem = groupItems.first()
                    val totalPcs = groupItems.sumOf { it.pcsQty.toDoubleOrNull() ?: 0.0 }
                    val totalWeight = groupItems.sumOf { it.weight.toDoubleOrNull() ?: 0.0 }
                    val totalSub = groupItems.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }

                    val row = sheet.getRow(startRowIndex + manifestIdx) ?: sheet.createRow(startRowIndex + manifestIdx)
                    
                    (row.getCell(0) ?: row.createCell(0)).setCellValue((manifestIdx + 1).toDouble())
                    (row.getCell(1) ?: row.createCell(1)).setCellValue(sampleItem.pti.uppercase())
                    (row.getCell(2) ?: row.createCell(2)).setCellValue(totalPcs)
                    (row.getCell(3) ?: row.createCell(3)).setCellValue(totalWeight)
                    (row.getCell(4) ?: row.createCell(4)).setCellValue(totalSub)
                    (row.getCell(5) ?: row.createCell(5)).setCellValue(sampleItem.description.uppercase())
                    (row.getCell(6) ?: row.createCell(6)).setCellValue(sampleItem.customer.uppercase())

                    manifestIdx++
                }

                // --- 2. ISI TABEL STOWING CHECKLIST (SEBELAH KANAN) ---
                // Dikelompokkan murni berdasarkan No PAG
                val groupedByPag = rawList.groupBy { it.noPag.uppercase() }
                var stowingRowIdx = startRowIndex
                var totalNet = 0.0
                var totalGross = 0.0

                for ((noPag, items) in groupedByPag) {
                    val row = sheet.getRow(stowingRowIdx) ?: sheet.createRow(stowingRowIdx)
                    
                    val combinedDesc = items.joinToString(" + ") { it.description }
                    val combinedCust = items.map { it.customer }.distinct().joinToString(" + ")
                    val totalWeightPerPag = items.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
                    
                    totalNet += totalWeightPerPag
                    totalGross += totalWeightPerPag

                    (row.getCell(7) ?: row.createCell(7)).setCellValue((stowingRowIdx - startRowIndex + 1).toDouble())
                    (row.getCell(8) ?: row.createCell(8)).setCellValue(noPag)
                    (row.getCell(9) ?: row.createCell(9)).setCellValue(combinedDesc.uppercase())
                    (row.getCell(10) ?: row.createCell(10)).setCellValue(totalWeightPerPag) // Net
                    (row.getCell(11) ?: row.createCell(11)).setCellValue(totalWeightPerPag) // Gross
                    (row.getCell(12) ?: row.createCell(12)).setCellValue(combinedCust.uppercase()) // Customer

                    stowingRowIdx++
                }

                // --- 3. ISI TOTAL WEIGHT ---
                val totalRow = sheet.getRow(36) ?: sheet.createRow(36)
                (totalRow.getCell(10) ?: totalRow.createCell(10)).setCellValue(totalNet)
                (totalRow.getCell(11) ?: totalRow.createCell(11)).setCellValue(totalGross)

                val file = File(context.cacheDir, "MANIFEST_CARGO.xlsx")
                workbook.write(FileOutputStream(file))
                workbook.close()

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) { context.startActivity(intent) }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }
}
