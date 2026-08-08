package com.example.cargomanifestapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.example.cargomanifestapp.ui.theme.CargoManifestAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val viewModel = ViewModelProvider(
            this,
            CargoViewModelFactory(application)
        )[CargoViewModel::class.java]

        setContent {
            CargoManifestAppTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
