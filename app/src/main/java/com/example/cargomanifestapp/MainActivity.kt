package com.example.cargomanifestapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val viewModel = ViewModelProvider(
            this,
            CargoViewModelFactory(application)
        )[CargoViewModel::class.java]

        setContent {
            // Menggunakan MaterialTheme standar agar tidak terjadi unresolved reference pada theme custom
            MaterialTheme {
                Surface {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
