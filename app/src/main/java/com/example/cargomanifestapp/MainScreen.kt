package com.example.cargomanifestapp

import android.widget.Toast
import androidx.compose.foundation.background
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
    val cargoList by viewModel.cargoList.collectAsState(initial = emptyList())

    // State untuk Form Input
    var pti by remember { mutableStateOf("") }
    var pcsQty by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var subTotal by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var noPag by remember { mutableStateOf("") }

    // State untuk mode edit dan dialog
    var isEditing by remember { mutableStateOf(false) }
    var selectedItemId by remember { mutableStateOf<Long?>(null) }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<CargoEntity?>(null) }
    
    var showClearAllDialog by remember { mutableStateOf(false) }

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Form Input Section
            Text(
                text = if (isEditing) "Edit Data Kargo" else "Tambah Data Kargo",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            OutlinedTextField(
                value = pti,
                onValueChange = { pti = it },
                label = { Text("PTI") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pcsQty,
                    onValueChange = { pcsQty = it },
                    label = { Text("Pcs / Qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight (Kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = subTotal,
                    onValueChange = { subTotal = it },
                    label = { Text("Sub Total") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = noPag,
                    onValueChange = { noPag = it },
                    label = { Text("No PAG") },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = customer,
                onValueChange = { customer = it },
                label = { Text("Customer") },
                modifier = Modifier.fillMaxWidth()
            )

            // Tombol Aksi Form (Simpan/Update & Ekspor/Hapus Semua)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (pti.isBlank() || pcsQty.isBlank()) {
                            Toast.makeText(context, "PTI dan Pcs/Qty harus diisi!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        if (isEditing && selectedItemId != null) {
                            val updatedItem = CargoEntity(
                                id = selectedItemId!!,
                                pti = pti,
                                pcsQty = pcsQty,
                                weight = weight,
                                subTotal = subTotal,
                                description = description,
                                customer = customer,
                                noPag = noPag
                            )
                            viewModel.updateCargo(updatedItem)
                            Toast.makeText(context, "Data berhasil diperbarui", Toast.LENGTH_SHORT).show()
                            isEditing = false
                            selectedItemId = null
                        } else {
                            val newItem = CargoEntity(
                                pti = pti,
                                pcsQty = pcsQty,
                                weight = weight,
                                subTotal = subTotal,
                                description = description,
                                customer = customer,
                                noPag = noPag
                            )
                            viewModel.insertCargo(newItem)
                            Toast.makeText(context, "Data berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                        }

                        // Reset form
                        pti = ""
                        pcsQty = ""
                        weight = ""
                        subTotal = ""
                        description = ""
                        customer = ""
                        noPag = ""
                    }
                ) {
                    Text(if (isEditing) "Update Data" else "Simpan Data")
                }

                Row {
                    TextButton(onClick = {
                        Toast.makeText(context, "Fitur Ekspor Excel dipanggil", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Export Excel")
                    }

                    TextButton(onClick = { showClearAllDialog = true }) {
                        Text("Hapus Semua", color = Color.Red)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(4.dp))

            // Daftar Tabel Data
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(cargoList) { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${index + 1}", modifier = Modifier.weight(0.5f))
                            Text(item.pti, modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                            Text(item.pcsQty, modifier = Modifier.weight(1f))
                            Text(item.subTotal, modifier = Modifier.weight(1f))

                            Row(
                                modifier = Modifier.weight(1.5f),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    isEditing = true
                                    selectedItemId = item.id
                                    pti = item.pti
                                    pcsQty = item.pcsQty
                                    weight = item.weight
                                    subTotal = item.subTotal
                                    description = item.description
                                    customer = item.customer
                                    noPag = item.noPag
                                }) {
                                    Text("Edit")
                                }

                                TextButton(onClick = {
                                    itemToDelete = item
                                    showDeleteDialog = true
                                }) {
                                    Text("Hapus", color = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog Konfirmasi Hapus Item Satuan
    if (showDeleteDialog && itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Konfirmasi Hapus") },
            text = { Text("Apakah Anda yakin ingin menghapus data kargo ${itemToDelete?.pti}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToDelete?.let { viewModel.deleteCargo(it) }
                        showDeleteDialog = false
                        itemToDelete = null
                        Toast.makeText(context, "Data berhasil dihapus", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Hapus", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog Konfirmasi Hapus Semua Data
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Konfirmasi Hapus Semua") },
            text = { Text("Apakah Anda yakin ingin menghapus SELURUH data manifest?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllCargo()
                        showClearAllDialog = false
                        Toast.makeText(context, "Semua data berhasil dibersihkan", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Hapus Semua", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
