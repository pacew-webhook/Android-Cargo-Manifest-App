package com.example.cargomanifestapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val viewModel: CargoViewModel by viewModels {
        CargoViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                CargoAppScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CargoAppScreen(viewModel: CargoViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val cargoList by viewModel.cargoList.collectAsState()

    // Focus Requester Khusus untuk Kolom PTI
    val ptiFocusRequester = remember { FocusRequester() }

    // State Header Penerbangan
    var awbNo by remember { mutableStateOf("") }
    var flightNo by remember { mutableStateOf("") }

    // State Input Data Barang
    var pti by remember { mutableStateOf("") }
    var pcsQty by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var subTotal by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var noPag by remember { mutableStateOf("") }

    // Opsi Keyboard Teks Kapital
    val textNextKeyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Characters,
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Next
    )

    // Opsi Keyboard Angka
    val numberNextKeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Next
    )

    val nextKeyboardActions = KeyboardActions(
        onNext = { focusManager.moveFocus(FocusDirection.Next) }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Title App Bar
        Text(
            text = "Manifest Cargo App",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6200EE),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ================= 1. BAGIAN INPUT (DIAM / TIDAK DI-SCROLL) =================
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header Penerbangan
            Text(
                text = "Header Penerbangan",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = awbNo,
                    onValueChange = { awbNo = it.uppercase() },
                    label = { Text("AWB No") },
                    keyboardOptions = textNextKeyboardOptions,
                    keyboardActions = nextKeyboardActions,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = flightNo,
                    onValueChange = { flightNo = it.uppercase() },
                    label = { Text("Flight No") },
                    keyboardOptions = textNextKeyboardOptions,
                    keyboardActions = nextKeyboardActions,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input Data Barang
            Text(
                text = "Input Data Barang",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Baris 1: PTI (Teks) & Pcs/Qty (Angka)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = pti,
                    onValueChange = { pti = it.uppercase() },
                    label = { Text("PTI") },
                    keyboardOptions = textNextKeyboardOptions,
                    keyboardActions = nextKeyboardActions,
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(ptiFocusRequester)
                )
                OutlinedTextField(
                    value = pcsQty,
                    onValueChange = { pcsQty = it },
                    label = { Text("Pcs / Qty") },
                    keyboardOptions = numberNextKeyboardOptions, // Keyboard Angka
                    keyboardActions = nextKeyboardActions,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Baris 2: Weight (Angka) & Sub Total (Angka)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Pcs/Qty Wt") },
                    keyboardOptions = numberNextKeyboardOptions, // Keyboard Angka
                    keyboardActions = nextKeyboardActions,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = subTotal,
                    onValueChange = { subTotal = it },
                    label = { Text("Sub Total (Kg)") },
                    keyboardOptions = numberNextKeyboardOptions, // Keyboard Angka
                    keyboardActions = nextKeyboardActions,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Baris 3: Description (Teks)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.uppercase() },
                label = { Text("Description") },
                keyboardOptions = textNextKeyboardOptions,
                keyboardActions = nextKeyboardActions,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Baris 4: Customer (Teks) & NO PAG (Teks)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customer,
                    onValueChange = { customer = it.uppercase() },
                    label = { Text("Customer") },
                    keyboardOptions = textNextKeyboardOptions,
                    keyboardActions = nextKeyboardActions,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = noPag,
                    onValueChange = { noPag = it.uppercase() },
                    label = { Text("NO PAG") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            ptiFocusRequester.requestFocus()
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Tombol Simpan Ke Database
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
                            customer = customer,
                            noPag = noPag
                        )
                        // Reset form input barang
                        pti = ""
                        pcsQty = ""
                        weight = ""
                        subTotal = ""
                        description = ""
                        customer = ""
                        noPag = ""

                        // Kembalikan Fokus Kursor ke Kolom PTI
                        ptiFocusRequester.requestFocus()

                        Toast.makeText(context, "Data disimpan!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Mohon isi PTI dan Pcs/Qty!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Simpan Ke Database", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ================= 2. TABEL DATA HEADER =================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tabel Data (${cargoList.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.exportToExcel(context, awbNo, flightNo) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
                ) {
                    Text("Export Excel", fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        viewModel.clearAll()
                        Toast.makeText(context, "Semua data dihapus!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
                ) {
                    Text("Hapus Semua", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ================= 3. DAFTAR KARTU DATA (KHUSUS INI YANG BISA DI-SCROLL) =================
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f), // Mengisi sisa area paling bawah dan hanya bagian ini yang scroll
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cargoList) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PTI: ${item.pti}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF49454F)
                            )
                            Text(
                                text = "Pcs: ${item.pcsQty} | SubTotal: ${item.subTotal} Kg",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )

                            val pagInfo = if (item.noPag.isNotBlank()) " | PAG: ${item.noPag}" else ""
                            Text(
                                text = "Desc: ${item.description} | Cust: ${item.customer}$pagInfo",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }

                        Button(
                            onClick = { viewModel.deleteCargo(item) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Hapus", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
