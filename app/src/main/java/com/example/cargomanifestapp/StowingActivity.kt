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
    
    // State Form
    var noPag by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var inputKg by remember { mutableStateOf("") }
    val cargoList = remember { mutableStateListOf<CargoItem>() }
    val currentKgEntries = remember { mutableStateListOf<Double>() }
    val currentTotalKg = currentKgEntries.sum()

    // --- LAUNCHER EXPORT (Manifest Style) ---
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let {
            try {
                ExcelUtils.writeCargoListToExcel(context, it, cargoList)
                Toast.makeText(context, "Export Berhasil ke File yang Dipilih!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal Export: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Fungsi Pembantu
    fun addKgEntry() {
        val kgVal = inputKg.toDoubleOrNull()
        if (kgVal != null && kgVal > 0) {
            currentKgEntries.add(kgVal)
            inputKg = ""
        } else {
            Toast.makeText(context, "Masukkan angka KG valid", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveCargoItem() {
        if (noPag.isBlank() || customer.isBlank() || currentKgEntries.isEmpty()) {
            Toast.makeText(context, "Lengkapi data PAG, Customer, dan KG", Toast.LENGTH_SHORT).show()
            return
        }

        val newCargoItem = CargoItem(
            noPag = if (noPag.startsWith("PAG")) noPag else "PAG $noPag",
            customer = customer.uppercase(),
            pcsQty = currentKgEntries.size.toString(),
            weight = currentKgEntries.joinToString(", "),
            subTotal = currentTotalKg.toString()
        )

        cargoList.add(0, newCargoItem)
        currentKgEntries.clear()
        Toast.makeText(context, "Data Tersimpan!", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // HEADER
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
                Text("Form Stowing", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            // Tombol Share (Export)
            IconButton(onClick = {
                if (cargoList.isNotEmpty()) {
                    exportLauncher.launch("Stowing_Report_${System.currentTimeMillis()}.xlsx")
                } else {
                    Toast.makeText(context, "Data Kosong", Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(Icons.Default.Share, "Export Excel", tint = Color(0xFF2E7D32))
            }
        }

        // FORM CARD
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7))) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = noPag, onValueChange = { noPag = it.uppercase() }, label = { Text("PAG") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = customer, onValueChange = { customer = it.uppercase() }, label = { Text("Customer") }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = inputKg, onValueChange = { inputKg = it }, label = { Text("Input KG") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    Button(onClick = { addKgEntry() }) { Icon(Icons.Default.Add, null) }
                }
                
                // Grid KG
                if (currentKgEntries.isNotEmpty()) {
                    LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.heightIn(max = 100.dp)) {
                        items(currentKgEntries.indices.toList()) { i ->
                            Text("${currentKgEntries[i]}", modifier = Modifier.padding(4.dp))
                        }
                    }
                    Text("Total: $currentTotalKg KG", fontWeight = FontWeight.Bold)
                }
                
                Button(onClick = { saveCargoItem() }, modifier = Modifier.fillMaxWidth()) { Text("Simpan") }
            }
        }

        // LIST
        LazyColumn {
            items(cargoList) { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("${item.noPag} - ${item.customer}", fontWeight = FontWeight.Bold)
                        Text("Total: ${item.subTotal} KG (${item.pcsQty} Koli)")
                    }
                }
            }
        }
    }
}
