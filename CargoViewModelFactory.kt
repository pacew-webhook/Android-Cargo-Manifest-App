package com.example.cargomanifestapp

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CargoViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CargoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CargoViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
