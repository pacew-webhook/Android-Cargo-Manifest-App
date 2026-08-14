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

    private enum class MainScreen { MAIN_MENU, MANIFEST_CARGO, STOWING_PALLET, BUKTI_TIMBANG, MANIFEST_SEARCH }

    private val viewModel: CargoViewModel by viewModels {
        CargoViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var currentScreen by remember { mutableStateOf(MainScreen.MAIN_MENU) }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        MainScreen.MAIN_MENU -> {
                            MainMenuScreen(
                                onNavigateToManifest = {
                                    currentScreen = MainScreen.MANIFEST_CARGO
                                },
                                onNavigateToStowing = {
                                    val intent = Intent(context, StowingActivity::class.java)
                                    context.startActivity(intent)
                                },
                                onNavigateToBuktiTimbang = {
                                    val intent = Intent(context, BuktiTimbangActivity::class.java)
                                    context.startActivity(intent)
                                },
                                onNavigateToManifestSearch = {
                                    currentScreen = MainScreen.MANIFEST_SEARCH
                                }
                            )
                        }
                        MainScreen.MANIFEST_CARGO -> {
                            CargoAppScreen(
                                viewModel = viewModel,
                                onBackToMenu = { currentScreen = MainScreen.MAIN_MENU }
                            )
                        }
                        MainScreen.MANIFEST_SEARCH -> {
                            ManifestSearchScreen(onBack = { currentScreen = MainScreen.MAIN_MENU })
                        }
                        // STOWING_PALLET dan BUKTI_TIMBANG tidak pernah di-set sebagai currentScreen
                        // (navigasinya lewat Intent/Activity terpisah, bukan state ini), jadi
                        // cabang ini murni fallback pengaman dan tidak seharusnya pernah terpakai.
                        MainScreen.STOWING_PALLET, MainScreen.BUKTI_TIMBANG -> {
                            MainMenuScreen(
                                onNavigateToManifest = { currentScreen = MainScreen.MANIFEST_CARGO },
                                onNavigateToStowing = {
                                    val intent = Intent(context, StowingActivity::class.java)
                                    context.startActivity(intent)
                                },
                                onNavigateToBuktiTimbang = {
                                    val intent = Intent(context, BuktiTimbangActivity::class.java)
                                    context.startActivity(intent)
                                },
                                onNavigateToManifestSearch = {
                                    currentScreen = MainScreen.MANIFEST_SEARCH
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
