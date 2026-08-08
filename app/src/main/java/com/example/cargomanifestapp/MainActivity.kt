package com.example.cargomanifestapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Inisialisasi Database
        val database = CargoDatabase.getDatabase(applicationContext)
        
        // 2. Inisialisasi Factory untuk ViewModel
        val factory = CargoViewModelFactory(database.cargoDao())
        
        // 3. Inisialisasi ViewModel
        val cargoViewModel = ViewModelProvider(this, factory)[CargoViewModel::class.java]

        // 4. Set Content ke UI (MainScreen)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Memanggil fungsi MainScreen dari file MainScreen.kt
                    MainScreen(viewModel = cargoViewModel)
                }
            }
        }
    }
}
