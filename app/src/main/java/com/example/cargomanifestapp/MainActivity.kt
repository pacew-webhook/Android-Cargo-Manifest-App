package com.example.cargomanifestapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
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

    val ptiFocusRequester = remember { FocusRequester() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.importFromExcel(context, it)
        }
    }

    // Menggunakan Long? agar sinkron dengan id database CargoItem
    var selectedCargoId by remember { mutableStateOf<Long?>(null) }

    var awbNo by remember { mutableStateOf("") }
    var flightNo by remember { mutableStateOf("") }

    var pti by remember { mutableStateOf("") }
    var pcsQty by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var noPag by remember { mutableStateOf("") }

    var subTotalInput by remember { mutableStateOf("") }

    val calculatedSubTotal = remember(pcsQty, weight) {
        val pcs = pcsQty.toDoubleOrNull() ?: 0.0
        val wt = weight.toDoubleOrNull() ?: 0.0
        val result = pcs * wt
        if (result > 0.0) {
            if (result % 1.0 == 0.0) result.toInt().toString() else result.toString()
        } else {
            ""
        }
    }

    val subTotal = if (subTotalInput.isNotBlank()) subTotalInput else calculatedSubTotal

    var itemToDelete by remember { mutableStateOf<CargoItem?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val textNextKeyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Characters,
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Next
    )

    val numberNextKeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Next
    )

    val nextKeyboardActions = KeyboardActions(
        onNext = { focusManager.moveFocus(FocusDirection.Next) }
    )

    fun clearInputForm() {
        pti = ""
        pcsQty = ""
        weight = ""
        description = ""
        customer = ""
        noPag = ""
        subTotalInput = ""
        selectedCargoId = null
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Konfirmasi Hapus") },
            text = { Text("Apakah Anda yakin ingin menghapus data PTI ${item.pti}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedCargoId == item.id) {
                            clearInputForm()
                        }
                        viewModel.deleteCargo(item)
                        itemToDelete = null
                        Toast.makeText(context, "Data berhasil dihapus!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Hapus", color = Color(0xFFB3261E))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Hapus Semua Data") },
            text = { Text("Apakah Anda yakin ingin menghapus seluruh data pada tabel?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        clearInputForm()
                        showDeleteAllDialog = false
                        Toast.makeText(context, "Semua data berhasil dihapus!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Hapus Semua", color = Color(0xFFB3261E))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Manifest Cargo App",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6200EE),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
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

            Text(
                text = if (selectedCargoId == null) "Input Data Barang" else "Edit Data Barang",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (selectedCargoId == null) Color.Unspecified else Color(0xFF006A60)
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = pti,
                    onValueChange = { pti = it.uppercase() },
                    label = { Text("PTI") },
                    placeholder = { Text("Contoh: 001") },
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
                    keyboardOptions = numberNextKeyboardOptions,
                    keyboardActions = nextKeyboardActions,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Pcs/Qty Wt") },
                    keyboardOptions = numberNextKeyboardOptions,
                    keyboardActions = nextKeyboardActions,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = subTotalInput,
                    onValueChange = { subTotalInput = it },
                    readOnly = false,
                    label = { Text("Sub Total (Kg)") },
                    placeholder = { Text(calculatedSubTotal.ifEmpty { "Manual/Auto" }) },
                    keyboardOptions = numberNextKeyboardOptions,
                    keyboardActions = nextKeyboardActions,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customer,
                    onValueChange = { inputCustomer ->
                        val uppercaseInput = inputCustomer.uppercase()
                        customer = uppercaseInput

                        if (selectedCargoId == null) {
                            val existingItem = cargoList.find { it.customer.trim().equals(uppercaseInput.trim(), ignoreCase = true) }
                            if (existingItem != null) {
                                pti = existingItem.pti.removePrefix("KAL").trim()
                            }
                        }
                    },
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
                    placeholder = { Text("Contoh: 002") },
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

            Button(
                onClick = {
                    if (pti.isNotBlank() && pcsQty.isNotBlank()) {
                        val finalPti = if (pti.startsWith("KAL")) pti else "KAL$pti"
                        val finalPag = if (noPag.isBlank() || noPag.startsWith("PAG")) noPag else "PAG$noPag"

                        if (selectedCargoId == null) {
                            viewModel.addCargo(
                                awbNo = awbNo,
                                flightNo = flightNo,
                                pti = finalPti,
                                pcsQty = pcsQty,
                                weight = weight,
                                subTotal = subTotal,
                                description = description,
                                customer = customer,
                                noPag = finalPag
                            )
                            Toast.makeText(context, "Data berhasil disimpan!", Toast.LENGTH_SHORT).show()
                        } else {
                            val updatedItem = CargoItem(
                                id = selectedCargoId!!,
                                awbNo = awbNo,
                                flightNo = flightNo,
                                pti = finalPti,
                                pcsQty = pcsQty,
                                weight = weight,
                                subTotal = subTotal,
                                description = description,
                                customer = customer,
                                noPag = finalPag
                            )
                            viewModel.updateCargo(updatedItem)
                            Toast.makeText(context, "Data berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                        }

                        clearInputForm()
                        ptiFocusRequester.requestFocus()
                    } else {
                        Toast.makeText(context, "Mohon isi PTI dan Pcs/Qty!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedCargoId == null) Color(0xFF6750A4) else Color(0xFF006A60)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = if (selectedCargoId == null) "Simpan Ke Database" else "Update Data",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tabel Data (${cargoList.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = {
                        filePickerLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Import Excel", fontSize = 11.sp)
                }
                Button(
                    onClick = { viewModel.exportToExcel(context, awbNo, flightNo) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Export Excel", fontSize = 11.sp)
                }
                Button(
                    onClick = {
                        if (cargoList.isNotEmpty()) {
                            showDeleteAllDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Hapus Semua", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cargoList) { item ->
                val isSelected = selectedCargoId == item.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedCargoId = item.id
                            pti = item.pti.removePrefix("KAL").trim()
                            pcsQty = item.pcsQty
                            weight = item.weight
                            subTotalInput = item.subTotal
                            description = item.description
                            customer = item.customer
                            noPag = item.noPag.removePrefix("PAG").trim()
                            ptiFocusRequester.requestFocus()
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFFE8DEF8) else Color(0xFFF3EDF7)
                    ),
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
                        }

                        Button(
                            onClick = {
                                itemToDelete = item
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Hapus", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
