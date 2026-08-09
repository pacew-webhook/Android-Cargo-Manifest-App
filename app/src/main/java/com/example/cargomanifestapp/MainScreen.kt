package com.example.cargomanifestapp

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Entity
import androidx.room.PrimaryKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    
    // State Header Penerbangan
    var awbNo by remember { mutableStateOf("") }
    var flightNo by remember { mutableStateOf("") }

    // State Input Data Barang sesuai kolom Excel
    var pti by remember { mutableStateOf("") }
    var pcsCly by remember { mutableStateOf("") }
    var pcsClyWt by remember { mutableStateOf("") }
    var subTotal by remember { mutableStateOf("") }
    var noPag by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }

    // List data tabel sementara
    val cargoList = remember { mutableStateListOf<CargoItem>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manifest Cargo App", fontSize = 16.sp) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Penerbangan
            Text("Header Penerbangan", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = awbNo,
                    onValueChange = { awbNo = it },
                    label = { Text("AWB No", fontSize = 16.sp) },
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                )
                OutlinedTextField(
                    value = flightNo,
                    onValueChange = { flightNo = it },
                    label = { Text("Flight No", fontSize = 16.sp) },
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Input Data Barang", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = pti,
                onValueChange = { pti = it },
                label = { Text("PTI", fontSize = 16.sp) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pcsCly,
                    onValueChange = { pcsCly = it },
                    label = { Text("Pcs / Qty", fontSize = 16.sp) },
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                )
                OutlinedTextField(
                    value = pcsClyWt,
                    onValueChange = { pcsClyWt = it },
                    label = { Text("Pcs/Qty Wt", fontSize = 16.sp) },
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = subTotal,
                    onValueChange = { subTotal = it },
                    label = { Text("Sub Total (Kg)", fontSize = 16.sp) },
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                )
                OutlinedTextField(
                    value = noPag,
                    onValueChange = { noPag = it },
                    label = { Text("NO PAG", fontSize = 16.sp) },
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description", fontSize = 16.sp) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
            )

            OutlinedTextField(
                value = customer,
                onValueChange = { customer = it },
                label = { Text("Customer", fontSize = 16.sp) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (pti.isNotBlank()) {
                        cargoList.add(
                            CargoItem(
                                pti = pti,
                                pcsCly = pcsCly,
                                pcsClyWt = pcsClyWt,
                                subTotal = subTotal,
                                noPag = noPag,
                                description = description,
                                customer = customer
                            )
                        )
                        // Reset input setelah disimpan
                        pti = ""
                        pcsCly = ""
                        pcsClyWt = ""
                        subTotal = ""
                        noPag = ""
                        description = ""
                        customer = ""
                        Toast.makeText(context, "Data berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Kolom PTI tidak boleh kosong", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Simpan Ke Database", fontSize = 16.sp)
            }
        }
    }
}

// Data class yang dikonfigurasi sebagai Room Entity
@Entity(tableName = "cargo_table")
data class CargoItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val pti: String,
    val pcsCly: String,
    val pcsClyWt: String,
    val subTotal: String,
    val noPag: String,
    val description: String,
    val customer: String
)
