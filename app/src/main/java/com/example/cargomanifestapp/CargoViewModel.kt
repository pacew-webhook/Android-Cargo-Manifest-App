package com.example.cargomanifestapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CargoViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).cargoDao()

    // Mengambil data secara real-time dari database
    val cargoList: StateFlow<List<CargoEntity>> = dao.getAllCargoItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Fungsi Tambah Data
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
            dao.insertCargo(
                CargoEntity(
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

    // Fungsi Hapus Item
    fun deleteCargo(cargo: CargoEntity) {
        viewModelScope.launch {
            dao.deleteCargo(cargo)
        }
    }

    // Fungsi Hapus Semua Data
    fun clearAll() {
        viewModelScope.launch {
            dao.deleteAll()
        }
    }
}
