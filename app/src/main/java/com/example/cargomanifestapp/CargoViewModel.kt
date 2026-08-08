package com.example.cargomanifestapp

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter

class CargoViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = CargoDatabase.getDatabase(application).cargoDao()

    val cargoList: StateFlow<List<CargoItem>> = dao.getAllCargo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCargo(
        awbNo: String, flightNo: String, pti: String, pcsQty: String, 
        weight: String, subTotal: String, description: String, customer: String
    ) {
        viewModelScope.launch {
            dao.insertCargo(
                CargoItem(
                    awbNo = awbNo, flightNo = flightNo, pti = pti,
                    pcsQty = pcsQty, weight = weight, subTotal = subTotal,
                    description = description, customer = customer
                )
            )
        }
    }

    fun deleteCargo(cargo: CargoItem) {
        viewModelScope.launch {
            dao.deleteCargo(cargo)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            dao.deleteAll()
        }
    }

    // --- FUNGSI EKSPOR KE EXCEL (CSV) ---
    fun exportToExcel(context: Context) {
        val currentList = cargoList.value
        if (currentList.isEmpty()) return

        val fileName = "Cargo_Manifest_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)

        try {
            val writer = FileWriter(file)
            // Header Kolom Excel
            writer.append("No,AWB No,Flight No,PTI,Pcs/Qty,Weight,Sub Total,Description,Customer\n")

            // Isi Data
            currentList.forEachIndexed { index, item ->
                writer.append("${index + 1},${item.awbNo},${item.flightNo},${item.pti},${item.pcsQty},${item.weight},${item.subTotal},\"${item.description}\",\"${item.customer}\"\n")
            }

            writer.flush()
            writer.close()

            // Bagikan File Ke WhatsApp/Email/File Manager
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Bagikan / Buka File Excel")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
