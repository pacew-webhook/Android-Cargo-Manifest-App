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
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val database = remember { CargoDatabase.getDatabase(context.applicationContext) }
                    
                    // Menggunakan Factory terpisah agar aman dari error reference
                    val cargoViewModel: CargoViewModel = viewModel(
                        factory = CargoViewModelFactory(database.cargoDao())
                    )

                    CargoScreen(viewModel = cargoViewModel)
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

    var awbNo by remember { mutableStateOf("") }
    var flightNo by remember { mutableStateOf("") }
    var pti by remember { mutableStateOf("") }
    var pcsQty by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var subTotal by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var noPag by remember { mutableStateOf("") }

    var editingItem by remember { mutableStateOf<CargoItem?>(null) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<CargoItem?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

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

                item {
                    Text(
                        text = if (editingItem == null) "Input Data Barang" else "Edit Data Barang",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (editingItem == null) Color.Unspecified else Color(0xFF2196F3)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

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

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.uppercase() },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customer,
                            onValueChange = { customer = it.uppercase() },
                            label = { Text("Customer") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = noPag,
                            onValueChange = { noPag = it.uppercase() },
                            label = { Text("NO PAG") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (pti.isNotBlank() && pcsQty.isNotBlank()) {
                                if (editingItem == null) {
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
                                    Toast.makeText(context, "Data berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val current = editingItem!!
                                    viewModel.updateCargo(
                                        current.copy(
                                            awbNo = awbNo.uppercase(),
                                            flightNo = flightNo.uppercase(),
                                            pti = pti.uppercase(),
                                            pcsQty = pcsQty,
                                            weight = weight,
                                            subTotal = subTotal,
                                            description = description.uppercase(),
                                            customer = customer.uppercase(),
                                            noPag = noPag.uppercase()
                                        )
                                    )
                                    editingItem = null
                                    Toast.makeText(context, "Data berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                                }

                                pcsQty = ""
                                weight = ""
                                subTotal = ""
                                description = ""
                                customer = ""
                                noPag = ""

                            } else {
                                Toast.makeText(context, "PTI dan Pcs/Qty wajib diisi!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (editingItem == null) Color(0xFF6750A4) else Color(0xFF2196F3)
                        )
                    ) {
                        Text(if (editingItem == null) "Simpan Ke Database" else "Update Data", color = Color.White)
                    }
                }

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
                            TextButton(onClick = { showClearAllDialog = true }) {
                                Text("Hapus Semua", color = Color.Red)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF6750A4))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("No", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.4f))
                        Text("PTI", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.0f))
                        Text("Pcs", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                        Text("SubTotal", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                        Text("Aksi", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.0f))
                    }
                }

                items(cargoList.indices.toList()) { index ->
                    val item = cargoList[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (index % 2 == 0) Color(0xFFF2F0F4) else Color.White)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", modifier = Modifier.weight(0.4f))
                        Text(item.pti, modifier = Modifier.weight(1.0f))
                        Text(item.pcsQty, modifier = Modifier.weight(0.5f))
                        Text(item.subTotal, modifier = Modifier.weight(0.8f))

                        Row(modifier = Modifier.weight(1.0f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(
                                onClick = {
                                    editingItem = item
                                    awbNo = item.awbNo
                                    flightNo = item.flightNo
                                    pti = item.pti
                                    pcsQty = item.pcsQty
                                    weight = item.weight
                                    subTotal = item.subTotal
                                    description = item.description
                                    customer = item.customer
                                    noPag = item.noPag
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Edit", color = Color(0xFF2196F3), fontSize = 12.sp)
                            }

                            TextButton(
                                onClick = {
                                    itemToDelete = item
                                    showDeleteDialog = true
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Hapus", color = Color.Red, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog && itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Konfirmasi Hapus") },
            text = { Text("Apakah Anda yakin ingin menghapus data PTI ${itemToDelete?.pti}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToDelete?.let { viewModel.deleteCargo(it) }
                        showDeleteDialog = false
                        itemToDelete = null
                        Toast.makeText(context, "Data berhasil dihapus", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Ya", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        itemToDelete = null
                    }
                ) {
                    Text("Tidak")
                }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Peringatan") },
            text = { Text("Apakah Anda yakin ingin menghapus SELURUH data yang ada di tabel?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearAllDialog = false
                        Toast.makeText(context, "Semua data berhasil dihapus", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Ya, Hapus Semua", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearAllDialog = false }
                ) {
                    Text("Tidak")
                }
            }
        )
    }
}
