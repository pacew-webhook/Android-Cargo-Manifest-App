package com.example.cargomanifestapp

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: CargoViewModel) {
    val context = LocalContext.current
    val cargoList: List<CargoItem> by viewModel.cargoList.collectAsState(initial = emptyList())

    // State untuk input field
    var pti by remember { mutableStateOf("") }
    var pcsQty by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var subTotal by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var noPag by remember { mutableStateOf("") }

    var isEditing by remember { mutableStateOf(false) }
    var selectedItemId by remember { mutableStateOf<Long?>(null) }
    
    // UI Layout
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cargo Manifest App") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            // [Form input di sini disederhanakan...]
            // ... Pastikan semua Field ada sesuai kebutuhan Anda ...

            Spacer(modifier = Modifier.height(8.dp))
            // FIX: Menggunakan Divider standar agar kompatibel
            Divider(modifier = Modifier.fillMaxWidth()) 
            Spacer(modifier = Modifier.height(8.dp))

            // LazyColumn untuk list data
            LazyColumn {
                itemsIndexed(cargoList) { index, item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(text = "PTI: ${item.pti} | Pcs: ${item.pcsQty}")
                    }
                }
            }
        }
    }
}
