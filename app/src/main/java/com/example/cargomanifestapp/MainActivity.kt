package com.example.cargomanifestapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inisialisasi ViewModel menggunakan CargoViewModelFactory yang baru
        val factory = CargoViewModelFactory(application)
        val viewModel = ViewModelProvider(this, factory)[CargoViewModel::class.java]

        setContent {
            CargoManifestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CargoMainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CargoMainScreen(viewModel: CargoViewModel) {
    val context = LocalContext.current
    val cargoList by viewModel.cargoList.collectAsState()

    // State untuk form input
    var awbNo by remember { mutableStateOf("") }
    var flightNo by remember { mutableStateOf("") }
    var pti by remember { mutableStateOf("") }
    var pcsQty by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var subTotal by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var noPag by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cargo Manifest App") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Tombol Export Excel
            Button(
                onClick = { viewModel.exportToExcel(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export ke Template Excel")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Form Input Sederhana
            OutlinedTextField(
                value = pti,
                onValueChange = { pti = it },
                label = { Text("PTI") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = pcsQty,
                onValueChange = { pcsQty = it },
                label = { Text("Pcs / Qty") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text("Weight / Sub Total") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = subTotal,
                onValueChange = { subTotal = it },
                label = { Text("Sub Total Kg") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = customer,
                onValueChange = { customer = it },
                label = { Text("Customer / PAG") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (pti.isNotBlank()) {
                        viewModel.addCargo(
                            awbNo = awbNo,
                            flightNo = flightNo,
                            pti = pti,
                            pcsQty = pcsQty,
                            weight = weight,
                            subTotal = subTotal,
                            description = description,
                            customer = customer,
                            noPag = noPag
                        )
                        // Reset form setelah simpan
                        pti = ""
                        pcsQty = ""
                        weight = ""
                        subTotal = ""
                        description = ""
                        customer = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tambah Kargo")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // Daftar Data Kargo
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(cargoList) { cargo ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("PTI: ${cargo.pti} | Pcs: ${cargo.pcsQty}", style = MaterialTheme.typography.bodyLarge)
                            Text("Weight: ${cargo.weight} | Desc: ${cargo.description}", style = MaterialTheme.typography.bodyMedium)
                            Text("Customer: ${cargo.customer}", style = MaterialTheme.typography.bodySmall)
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Button(
                                onClick = { viewModel.deleteCargo(cargo) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Hapus")
                            }
                        }
                    }
                }
            }
        }
    }
}

// Tema dasar cadangan jika belum ada file theme tersendiri
@Composable
fun CargoManifestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content
    )
}
