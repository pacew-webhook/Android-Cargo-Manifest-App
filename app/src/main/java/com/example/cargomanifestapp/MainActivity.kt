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

    // State untuk Header Penerbangan
    var awbNo by remember { mutableStateOf("") }
    var flightNo by remember { mutableStateOf("") }

    // State untuk Form Input Barang
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
                title = { Text("Manifest Cargo App") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- BAGIAN 1: HEADER PENERBANGAN ---
            item {
                Text("Header Penerbangan", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = awbNo,
                        onValueChange = { awbNo = it },
                        label = { Text("AWB No") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = flightNo,
                        onValueChange = { flightNo = it },
                        label = { Text("Flight No") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Divider()
            }

            // --- BAGIAN 2: INPUT DATA BARANG ---
            item {
                Text("Input Data Barang", style = MaterialTheme.typography.titleMedium)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = pti,
                        onValueChange = { pti = it },
                        label = { Text("PTI") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pcsQty,
                        onValueChange = { pcsQty = it },
                        label = { Text("Pcs / Qty") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Pcs/Qty Wt") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = subTotal,
                        onValueChange = { subTotal = it },
                        label = { Text("Sub Total (Kg)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customer,
                        onValueChange = { customer = it },
                        label = { Text("Customer") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = noPag,
                        onValueChange = { noPag = it },
                        label = { Text("NO PAG") },
                        modifier = Modifier.weight(1f)
                    )
                }

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
                            // Reset form input barang setelah disimpan
                            pti = ""
                            pcsQty = ""
                            weight = ""
                            subTotal = ""
                            description = ""
                            customer = ""
                            noPag = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Simpan Ke Database")
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                // Baris Tombol Tabel (Export & Hapus Semua)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tabel Data (${cargoList.size})", style = MaterialTheme.typography.titleMedium)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.exportToExcel(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Export Excel")
                        }
                        Button(
                            onClick = { viewModel.clearAll() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Hapus Semua")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Divider()
                Spacer(modifier = Modifier.height(4.dp))
            }

            // --- BAGIAN 3: DAFTAR TABEL LIST DATA ---
            items(cargoList) { cargo ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PTI: ${cargo.pti}", style = MaterialTheme.typography.bodyLarge)
                            Text("Pcs: ${cargo.pcsQty} | SubTotal: ${cargo.subTotal} Kg", style = MaterialTheme.typography.bodyMedium)
                            Text("Desc: ${cargo.description} | Cust: ${cargo.customer}", style = MaterialTheme.typography.bodySmall)
                        }
                        
                        Button(
                            onClick = { viewModel.deleteCargo(cargo) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Hapus", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CargoManifestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content
    )
}
