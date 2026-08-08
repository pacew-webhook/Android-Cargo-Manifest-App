
package com.example.cargomanifestapp

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainScreen(viewModel: CargoViewModel) {
    val context = LocalContext.current
    val cargoList by viewModel.cargoList.collectAsState()

    // State untuk Form Input / Edit
    var awbNo by remember { mutableStateOf("") }
    var flightNo by remember { mutableStateOf("") }
    var pti by remember { mutableStateOf("") }
    var pcsQty by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var subTotal by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var noPag by remember { mutableStateOf("") }

    // State untuk Mode Edit & ID Item yang sedang diedit
    var isEditing by remember { mutableStateOf(false) }
    var selectedItemId by remember { mutableStateOf<Int?>(null) }

    // State untuk Dialog Konfirmasi Hapus Per Baris
    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<CargoItem?>(null) }

    // State untuk Dialog Konfirmasi Hapus Semua
    var showClearAllDialog by remember { mutableStateOf(false) }

    // Fungsi untuk mengosongkan form
    fun clearForm() {
        pti = ""
        pcsQty = ""
        weight = ""
        subTotal = ""
        description = ""
        customer = ""
        noPag = ""
        isEditing = false
        selectedItemId = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3EDF7))
            .padding(16.dp)
    ) {
        Text(
            text = "Manifest Cargo App",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Header Penerbangan
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Header Penerbangan", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        // Form Input / Edit Data Barang
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isEditing) "Edit Data Barang" else "Input Data Barang",
                    fontWeight = FontWeight.Bold,
                    color = if (isEditing) Color.Blue else Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                Spacer(modifier = Modifier.height(12.dp))

                // Tombol Simpan / Update Data
                Button(
                    onClick = {
                        if (pti.isBlank() || customer.isBlank()) {
                            Toast.makeText(context, "PTI dan Customer wajib diisi!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        if (isEditing && selectedItemId != null) {
                            // Proses Update Data (Murni tanpa menjumlahkan)
                            val updatedItem = CargoItem(
                                id = selectedItemId!!,
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
                            viewModel.updateCargo(updatedItem)
                            Toast.makeText(context, "Data berhasil diperbarui", Toast.LENGTH_SHORT).show()
                        } else {
                            // Proses Tambah Data Baru
                            viewModel.addCargo(awbNo, flightNo, pti, pcsQty, weight, subTotal, description, customer, noPag)
                            Toast.makeText(context, "Data berhasil disimpan", Toast.LENGTH_SHORT).show()
                        }
                        clearForm()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isEditing) "Update Data" else "Simpan Ke Database")
                }
            }
        }

        // Tombol Aksi Tabel & Tabel Data
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Tabel Data (${cargoList.size})", fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.exportToExcel(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Text("Export Excel")
                }

                TextButton(onClick = { showClearAllDialog = true }) {
                    Text("Hapus Semua", color = Color.Red)
                }
            }
        }

        // Daftar Tabel Data
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", modifier = Modifier.weight(0.5f))
                        Text(item.pti, modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                        Text(item.pcsQty, modifier = Modifier.weight(1f))
                        Text(item.subTotal, modifier = Modifier.weight(1f))

                        Row(modifier = Modifier.weight(1.5f), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                // Masukkan data ke form untuk diedit
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
                                Text("Edit", color = Color.Blue)
                            }

                            TextButton(onClick = {
                                // Panggil dialog konfirmasi hapus per baris
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

    // --- POP-UP DIALOG KONFIRMASI HAPUS PER BARIS ---
    if (showDeleteDialog && itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Konfirmasi Hapus") },
            text = { Text("Apakah Anda yakin ingin menghapus data PTI ${itemToDelete?.pti} (${itemToDelete?.customer})?") },
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

    // --- POP-UP DIALOG KONFIRMASI HAPUS SEMUA ---
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
