package com.example.cargomanifestapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class CargoViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = CargoDatabase.getDatabase(application).cargoDao()
    
    val cargoList: Flow<List<CargoItem>> = dao.getAllCargo()

    fun insertCargo(item: CargoItem) = viewModelScope.launch {
        dao.insert(item)
    }

    fun updateCargo(item: CargoItem) = viewModelScope.launch {
        dao.update(item)
    }

    fun deleteCargo(item: CargoItem) = viewModelScope.launch {
        dao.delete(item)
    }

    fun clearAllCargo() = viewModelScope.launch {
        dao.deleteAll()
    }
}
