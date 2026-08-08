package com.example.cargomanifestapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CargoViewModelFactory(private val dao: CargoDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CargoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CargoViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
