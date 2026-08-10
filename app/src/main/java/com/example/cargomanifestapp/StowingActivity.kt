package com.example.cargomanifestapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class StowingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StowingInputScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StowingInputScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // State Form Input
    var noPag by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var inputKg by remember { mutableStateOf("") }

    val cargoList = remember { mutableStateListOf<CargoItem>() }
    val currentKgEntries = remember { mutableStateListOf<Double>() }
    val currentTotalKg = currentKgEntries.sum()

    // Launcher untuk export ke template Excel
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let {
            try {
                ExcelUtils.writeCargoListToExcel(context, it, cargoList)
                Toast.makeText(context, "Export Berhasil!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal Export: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addKgEntry() {
        val kgVal = inputKg.toDoubleOrNull()
        if (kgVal != null && kgVal > 0) {
            currentKgEntries.add(kgVal)
            inputKg = ""
        } else {
            Toast.makeText(context, "Masukkan angka KG yang valid", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveCargoItem() {
        if (noPag.isBlank() || customer.isBlank()) {
            Toast.makeText(context, "Mohon isi NO PAG dan Customer", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentKgEntries.isEmpty()) {
            Toast.makeText(context, "Masukkan minimal 1 nilai KG", Toast.LENGTH_SHORT).show()
            return
        }

        val formattedWeightList = currentKgEntries.joinToString(", ") {
            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
        }

        val formattedTotalKg = if (currentTotalKg % 1.0 == 0.0) {
            currentTotalKg.toInt().toString()
        } else {
            currentTotalKg.toString()
        }

        val newCargoItem = CargoItem(
            noPag = if (noPag.startsWith("PAG")) noPag else "PAG $noPag",
            customer = customer.uppercase(),
            pcsQty = currentKgEntries.size.toString(),
            weight = formattedWeightList,
            subTotal = formattedTotalKg
        )

        cargoList.add(0, newCargoItem)
        inputKg = ""
        currentKgEntries.clear()
        Toast.makeText(context, "Data berhasil disimpan!", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // --- HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color(0xFF381E72)
                    )
                }
                Text(
                    text = "Form Stowing Cargo",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF381E72)
                )
            }

            IconButton(onClick = {
                if (cargoList.isNotEmpty()) {
                    exportLauncher.launch("Stowing_Report_${System.currentTimeMillis()}.xlsx")
                } else {
                    Toast.makeText(context, "Data Kosong", Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Export Excel",
                    tint = Color(0xFF2E7D32)
                )
            }
        }

        // --- CARD FORM INPUT ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Input PAG, Customer & KG",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF381E72)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = noPag,
                        onValueChange = { noPag = it.uppercase() },
                        label = { Text("NO PAG") },
                        placeholder = { Text("283 MYI") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customer,
                        onValueChange = { customer = it.uppercase() },
                        label = { Text("Customer") },
                        placeholder = { Text("ULIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputKg,
                        onValueChange = { inputKg = it },
                        label = { Text("Input Berat (KG)") },
                        placeholder = { Text("10") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { addKgEntry() }),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = { addKgEntry() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ KG")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- RINCIAN INPUT KG (5 BARIS/KOLOM PER ROW) ---
                if (currentKgEntries.isNotEmpty()) {
                    Text(
                        text = "Rincian Input KG (${currentKgEntries.size} Koli):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5), // 5 kolom
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(currentKgEntries.indices.toList()) { index ->
                            val itemVal = currentKgEntries[index]
                            Box(
                                modifier = Modifier
                                    .background(
                                        Color(0xFFE8DEF8),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (itemVal % 1.0 == 0.0) "${itemVal.toInt()}" else "$itemVal",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { currentKgEntries.removeAt(index) },
                                        modifier = Modifier.size(14.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Hapus",
                                            tint = Color.Red
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF381E72), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL KG:",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (currentTotalKg % 1.0 == 0.0) "${currentTotalKg.toInt()} KG" else "$currentTotalKg KG",
                            color = Color.Yellow,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                Button(
                    onClick = { saveCargoItem() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Simpan ke Cargo Table", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- DAFTAR CARGO ITEM TERSEMPAN ---
        Text(
            text = "Daftar Stowing Tersimpan (${cargoList.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color(0xFF381E72)
        )
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cargoList) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.noPag} | Customer: ${item.customer}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF381E72)
                            )
                            IconButton(onClick = { cargoList.remove(item) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Hapus",
                                    tint = Color(0xFFB3261E)
                                )
                            }
                        }

                        Text(
                            text = "Jumlah Koli: ${item.pcsQty}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Rincian KG: ${item.weight}",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "TOTAL: ${item.subTotal} KG",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
        }
    }
}
