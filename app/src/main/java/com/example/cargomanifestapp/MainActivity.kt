package com.example.cargomanifestapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {

    private val viewModel: CargoViewModel by viewModels {
        CargoViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var currentScreen by remember { mutableStateOf<Screen>(Screen.MAIN_MENU) }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        Screen.MAIN_MENU -> {
                            MainMenuScreen(
                                onNavigateToManifest = {
                                    currentScreen = Screen.MANIFEST_CARGO
                                },
                                onNavigateToStowing = {
                                    val intent = Intent(context, StowingActivity::class.java)
                                    context.startActivity(intent)
                                },
                                onNavigateToBuktiTimbang = {
                                    val intent = Intent(context, BuktiTimbangActivity::class.java)
                                    context.startActivity(intent)
                                }
                            )
                        }
                        Screen.MANIFEST_CARGO -> {
                            CargoAppScreen(
                                viewModel = viewModel,
                                onBackToMenu = { currentScreen = Screen.MAIN_MENU }
                            )
                        }
                        else -> {
                            MainMenuScreen(
                                onNavigateToManifest = { currentScreen = Screen.MANIFEST_CARGO },
                                onNavigateToStowing = {
                                    val intent = Intent(context, StowingActivity::class.java)
                                    context.startActivity(intent)
                                },
                                onNavigateToBuktiTimbang = {
                                    val intent = Intent(context, BuktiTimbangActivity::class.java)
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
