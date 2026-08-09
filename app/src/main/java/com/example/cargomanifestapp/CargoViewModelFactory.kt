package com.example.cargomanifestapp

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CargoViewModelFactory(
    private val application: Application,
    private val cargoDao: CargoDao // Tambahkan ini
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CargoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CargoViewModel(application, cargoDao) as T // Tambahkan cargoDao di sini
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
