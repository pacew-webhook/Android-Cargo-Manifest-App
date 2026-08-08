package com.example.cargomanifestapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CargoManifestScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CargoManifestScreen(viewModel: CargoViewModel = viewModel()) {
    val cargoList by viewModel.cargoList.collectAsState()

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Header Penerbangan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    }
                }
            }

            item {
                Text("Input Data Barang", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = weight,
                            onValueChange = { weight = it },
                            label = { Text("Pcs/Qty Wt") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = subTotal,
                            onValueChange = { subTotal = it },
                            label = { Text("Sub Total (Kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (mis: SAYURAN)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = customer,
                        onValueChange = { customer = it },
                        label = { Text("Customer") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (description.isNotEmpty()) {
                                viewModel.addCargo(
                                    awbNo, flightNo, pti, pcsQty, weight, subTotal, description, customer
                                )
                                pti = ""; pcsQty = ""; weight = ""; subTotal = ""; description = ""; customer = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text("Simpan Ke Database")
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tabel Data Tersimpan (${cargoList.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (cargoList.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearAll() }) {
                            Text("Hapus Semua", color = Color.Red)
                        }
                    }
                }
            }

            item {
                val horizontalScrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScrollState)
                        .background(Color.White, shape = RoundedCornerShape(4.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(8.dp)
                    ) {
                        TableCell("No", width = 40.dp, isHeader = true)
                        TableCell("PTI", width = 60.dp, isHeader = true)
                        TableCell("Pcs/Qty", width = 70.dp, isHeader = true)
                        TableCell("Weight", width = 70.dp, isHeader = true)
                        TableCell("Sub Total", width = 80.dp, isHeader = true)
                        TableCell("Description", width = 140.dp, isHeader = true)
                        TableCell("Customer", width = 120.dp, isHeader = true)
                        TableCell("Aksi", width = 60.dp, isHeader = true)
                    }

                    if (cargoList.isEmpty()) {
                        Text("Database kosong.", modifier = Modifier.padding(16.dp), color = Color.Gray)
                    } else {
                        cargoList.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .background(if (index % 2 == 0) Color(0xFFF2F2F2) else Color.White)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TableCell("${index + 1}", width = 40.dp)
                                TableCell(item.pti, width = 60.dp)
                                TableCell(item.pcsQty, width = 70.dp)
                                TableCell(item.weight, width = 70.dp)
                                TableCell(item.subTotal, width = 80.dp)
                                TableCell(item.description, width = 140.dp)
                                TableCell(item.customer, width = 120.dp)
                                TextButton(
                                    onClick = { viewModel.deleteCargo(item) },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.width(60.dp)
                                ) {
                                    Text("Hapus", color = Color.Red, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TableCell(text: String, width: androidx.compose.ui.unit.Dp, isHeader: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        color = if (isHeader) Color.White else Color.Black,
        fontSize = 12.sp
    )
}
