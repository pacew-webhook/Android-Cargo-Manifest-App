package com.example.cargomanifestapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: CargoViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(applicationContext)
        val factory = CargoViewModelFactory(database.cargoDao())
        viewModel = ViewModelProvider(this, factory)[CargoViewModel::class.java]

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CargoScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CargoScreen(viewModel: CargoViewModel) {
    val context = LocalContext.current
    val cargoList by viewModel.cargoList.collectAsState()

    // State Input
    var awbNo by remember { mutableStateOf("") }
    var flightNo by remember { mutableStateOf("") }
    var pti by remember { mutableStateOf("") }
    var pcsQty by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var subTotal by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manifest Cargo App", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color(0xFFE8DEF8))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // SECTION 1: HEADER PENERBANGAN
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Header Penerbangan", fontWeight = FontWeight.Bold, color = Color(0xFF49454F))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = awbNo,
                                    onValueChange = { awbNo = it.uppercase() },
                                    label = { Text("AWB No") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = flightNo,
                                    onValueChange = { flightNo = it.uppercase() },
                                    label = { Text("Flight No") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // SECTION 2: INPUT DATA BARANG
                item {
                    Text("Input Data Barang", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 1: PTI & Pcs/Qty
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pti,
                            onValueChange = { pti = it.uppercase() },
                            label = { Text("PTI") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = pcsQty,
                            onValueChange = { pcsQty = it },
                            label = { Text("Pcs / Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 2: Weight & Sub Total
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = weight,
                            onValueChange = { weight = it },
                            label = { Text("Pcs/Qty Wt") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = subTotal,
                            onValueChange = { subTotal = it },
                            label = { Text("Sub Total (Kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.uppercase() },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Customer
                    OutlinedTextField(
                        value = customer,
                        onValueChange = { customer = it.uppercase() },
                        label = { Text("Customer") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tombol Simpan
                    Button(
                        onClick = {
                            if (pti.isNotBlank() && pcsQty.isNotBlank()) {
                                viewModel.addCargo(
                                    awbNo = awbNo,
                                    flightNo = flightNo,
                                    pti = pti,
                                    pcsQty = pcsQty,
                                    weight = weight,
                                    subTotal = subTotal,
                                    description = description,
                                    customer = customer
                                )

                                // AWB No, Flight No, dan PTI TETAP TERISI (tidak dikosongkan)
                                // Hanya rincian barang berikut ini yang dikosongkan:
                                pcsQty = ""
                                weight = ""
                                subTotal = ""
                                description = ""
                                customer = ""

                                Toast.makeText(context, "Data berhasil disimpan!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "PTI dan Pcs/Qty wajib diisi!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
                    ) {
                        Text("Simpan Ke Database", color = Color.White)
                    }
                }

                // SECTION 3: TABEL DAFTAR DATA & ACTION BUTTONS
                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tabel Data (${cargoList.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.exportToExcel(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                            ) {
                                Text("Export Excel", color = Color.White)
                            }
                            TextButton(onClick = { viewModel.clearAll() }) {
                                Text("Hapus Semua", color = Color.Red)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Header Tabel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF6750A4))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("No", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                        Text("PTI", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                        Text("Pcs/Qty", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Weight", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Sub Total", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Description", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                    }
                }

                // Baris Isi Tabel
                items(cargoList.indices.toList()) { index ->
                    val item = cargoList[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (index % 2 == 0) Color(0xFFF2F0F4) else Color.White)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${index + 1}", modifier = Modifier.weight(0.5f))
                        Text(item.pti, modifier = Modifier.weight(1.5f))
                        Text(item.pcsQty, modifier = Modifier.weight(1f))
                        Text(item.weight, modifier = Modifier.weight(1f))
                        Text(item.subTotal, modifier = Modifier.weight(1f))
                        Text(item.description, modifier = Modifier.weight(1.5f))
                    }
                }
            }
        }
    }
}
