package com.example.cargomanifestapp

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightTrackingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var flightNumber by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flight Tracking") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Masukkan nomor penerbangan untuk membuka tracking Flightradar24.")
            OutlinedTextField(
                value = flightNumber,
                onValueChange = {
                    flightNumber = it.uppercase(Locale.ROOT).filter { c -> c.isLetterOrDigit() }
                    error = ""
                },
                label = { Text("Flight Number") },
                placeholder = { Text("Contoh: GA102") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxSize().weight(0f)
            )
            if (error.isNotEmpty()) Text(error)
            Button(
                onClick = {
                    val flight = flightNumber.trim()
                    if (flight.length < 3) {
                        error = "Masukkan nomor penerbangan yang valid."
                    } else {
                        val uri = Uri.parse("https://www.flightradar24.com/data/flights/$flight")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                },
                modifier = Modifier.fillMaxSize().weight(0f)
            ) { Text("Buka Flightradar24") }
            Text("Catatan: versi ini tidak menyimpan atau menanamkan API token Flightradar24 di APK.")
        }
    }
}
