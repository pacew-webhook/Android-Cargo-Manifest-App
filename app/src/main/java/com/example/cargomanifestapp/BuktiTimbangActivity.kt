package com.example.cargomanifestapp

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import org.json.JSONArray
import org.json.JSONObject

class BuktiTimbangActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BuktiTimbangScreen(onBack = { finish() })
                }
            }
        }
    }
}

// --- PREFERENCES LOCAL STORAGE FOR BTB ---
private fun saveBtbListToPrefs(context: Context, list: List<BtbFormData>) {
    val prefs = context.getSharedPreferences("btb_prefs", Context.MODE_PRIVATE)
    val jsonArray = JSONArray()
    for (item in list) {
        val obj = JSONObject().apply {
            put("hariTanggal", item.hariTanggal)
            put("customerName", item.customerName)
            put("trademarks", item.trademarks)
            put("jenisBarang", item.jenisBarang)
            
            val kgArray = JSONArray()
            item.daftarTimbangan.forEach { kgArray.put(it) }
            put("daftarTimbangan", kgArray)
        }
        jsonArray.put(obj)
    }
    prefs.edit().putString("saved_btb_list", jsonArray.toString()).apply()
}

private fun loadBtbListFromPrefs(context: Context): List<BtbFormData> {
    val prefs = context.getSharedPreferences("btb_prefs", Context.MODE_PRIVATE)
    val jsonString = prefs.getString("saved_btb_list", null) ?: return emptyList()
    val list = mutableListOf<BtbFormData>()
    try {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val kgJsonArray = obj.optJSONArray("daftarTimbangan") ?: JSONArray()
            val kgList = mutableListOf<Double>()
            for (j in 0 until kgJsonArray.length()) {
                kgList.add(kgJsonArray.getDouble(j))
            }
            list.add(
                BtbFormData(
                    hariTanggal = obj.optString("hariTanggal", ""),
                    customerName = obj.optString("customerName", ""),
                    trademarks = obj.optString("trademarks", ""),
                    jenisBarang = obj.optString("jenisBarang", ""),
                    daftarTimbangan = kgList
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

private enum class BtbDeleteType {
    NONE, RESET_ALL, ITEM, KG_ENTRY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuktiTimbangScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // State Input Form Header
    var hariTanggal by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var trademarks by remember { mutableStateOf("") }
    var jenisBarang by remember { mutableStateOf("") }
    var inputKg by remember { mutableStateOf("") }

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    val btbList = remember { mutableStateListOf<BtbFormData>() }
    
    // Matriks Timbangan 5 Kolom (Max 70 sel = A10:E23)
    val currentKgEntries = remember { mutableStateListOf<Double?>() }
    val activeEntries = currentKgEntries.filterNotNull()
    val totalKg = activeEntries.sum()

    // Dialog State
    var deleteType by remember { mutableStateOf(BtbDeleteType.NONE) }
    var itemIndexToDelete by remember { mutableStateOf<Int?>(null) }
    var kgIndexToDelete by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        val savedData = loadBtbListFromPrefs(context)
        btbList.clear()
        btbList.addAll(savedData)
    }

    fun updateAndSave() {
        saveBtbListToPrefs(context, btbList.toList())
    }

    // Export Excel Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let {
            try {
                val dataToExport = if (activeEntries.isNotEmpty()) {
                    BtbFormData(hariTanggal, customerName, trademarks, jenisBarang, activeEntries)
                } else if (btbList.isNotEmpty()) {
                    btbList.first()
                } else {
                    null
                }

                if (dataToExport != null) {
                    BtbExcelWriter.fillBtbTemplate(context, it, dataToExport)
                    Toast.makeText(context, "Export BTB Berhasil!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Tidak ada data untuk di-export", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal Export: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addKgEntry() {
        val kgVal = inputKg.toDoubleOrNull()
        if (kgVal != null && kgVal > 0) {
            val emptyIndex = currentKgEntries.indexOfFirst { it == null }
            if (emptyIndex != -1) {
                currentKgEntries[emptyIndex] = kgVal
            } else if (currentKgEntries.size < 70) {
                currentKgEntries.add(kgVal)
            } else {
                Toast.makeText(context, "Kapasitas Maksimal (70 Timbangan) Tercapai", Toast.LENGTH_SHORT).show()
            }
            inputKg = ""
        } else {
            Toast.makeText(context, "Masukkan angka KG valid", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveForm() {
        if (customerName.isBlank()) {
            Toast.makeText(context, "Isi Nama Customer terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        if (activeEntries.isEmpty()) {
            Toast.makeText(context, "Masukkan minimal 1 inputan KG", Toast.LENGTH_SHORT).show()
            return
        }

        val newItem = BtbFormData(
            hariTanggal = hariTanggal.trim(),
            customerName = customerName.uppercase().trim(),
            trademarks = trademarks.uppercase().trim(),
            jenisBarang = jenisBarang.uppercase().trim(),
            daftarTimbangan = activeEntries
        )

        val idx = editingIndex
        if (idx != null && idx in btbList.indices) {
            btbList[idx] = newItem
            Toast.makeText(context, "Data BTB Diperbarui!", Toast.LENGTH_SHORT).show()
        } else {
            btbList.add(0, newItem)
            Toast.makeText(context, "Data BTB Disimpan!", Toast.LENGTH_SHORT).show()
        }

        updateAndSave()

        // Reset Form
        hariTanggal = ""
        customerName = ""
        trademarks = ""
        jenisBarang = ""
        inputKg = ""
        currentKgEntries.clear()
        editingIndex = null
    }

    // --- POPUP DIALOG HAPUS ---
    if (deleteType != BtbDeleteType.NONE) {
        AlertDialog(
            onDismissRequest = { deleteType = BtbDeleteType.NONE },
            title = { Text("Konfirmasi Hapus", fontWeight = FontWeight.Bold) },
            text = {
                val msg = when (deleteType) {
                    BtbDeleteType.RESET_ALL -> "Hapus SELURUH Riwayat BTB?"
                    BtbDeleteType.ITEM -> "Hapus item BTB ini?"
                    BtbDeleteType.KG_ENTRY -> "Hapus angka timbangan ini?"
                    else -> ""
                }
                Text(msg)
            },
            confirmButton = {
                TextButton(onClick = {
                    when (deleteType) {
                        BtbDeleteType.RESET_ALL -> {
                            btbList.clear()
                            updateAndSave()
                            currentKgEntries.clear()
                            editingIndex = null
                        }
                        BtbDeleteType.ITEM -> {
                            itemIndexToDelete?.let {
                                if (it in btbList.indices) {
                                    btbList.removeAt(it)
                                    updateAndSave()
                                }
                            }
                        }
                        BtbDeleteType.KG_ENTRY -> {
                            kgIndexToDelete?.let {
                                if (it in currentKgEntries.indices) {
                                    currentKgEntries[it] = null
                                }
                            }
                        }
                        else -> {}
                    }
                    deleteType = BtbDeleteType.NONE
                }) {
                    Text("Hapus", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteType = BtbDeleteType.NONE }) { Text("Batal") }
            }
        )
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color(0xFF381E72))
                }
                Text("Bukti Timbang Barang", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF381E72))
            }

            Row {
                if (btbList.isNotEmpty()) {
                    IconButton(onClick = { deleteType = BtbDeleteType.RESET_ALL }) {
                        Icon(Icons.Default.Delete, contentDescription = "Reset", tint = Color.Red)
                    }
                }
                IconButton(onClick = {
                    exportLauncher.launch("BTB_${System.currentTimeMillis()}.xlsx")
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Export Excel", tint = Color(0xFF2E7D32))
                }
            }
        }

        // --- FORM INPUT ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (editingIndex != null) Color(0xFFFFF8E1) else Color(0xFFF3EDF7)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (editingIndex != null) "Edit Form BTB" else "Input Data BTB",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF381E72)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hariTanggal,
                        onValueChange = { hariTanggal = it },
                        label = { Text("Hari / Tgl") },
                        placeholder = { Text("11/08/2026") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it.uppercase() },
                        label = { Text("Customer") },
                        placeholder = { Text("ULIN") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = trademarks,
                        onValueChange = { trademarks = it.uppercase() },
                        label = { Text("Trademarks") },
                        placeholder = { Text("LABEWA") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = jenisBarang,
                        onValueChange = { jenisBarang = it.uppercase() },
                        label = { Text("Jenis Barang") },
                        placeholder = { Text("57.57.20.57.57") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input KG & Tombol Tambah
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputKg,
                        onValueChange = { inputKg = it },
                        label = { Text("Input Berat (KG)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addKgEntry() }),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = { addKgEntry() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("+ KG")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- GRID TIMBANGAN (5 KOLOM A-E) ---
                if (currentKgEntries.isNotEmpty()) {
                    Text(
                        text = "Rincian Timbangan (${activeEntries.size} Koli) | Total: $totalKg KG",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(currentKgEntries) { index, itemVal ->
                            if (itemVal != null) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFE8DEF8), RoundedCornerShape(6.dp))
                                        .clickable {
                                            kgIndexToDelete = index
                                            deleteType = BtbDeleteType.KG_ENTRY
                                        }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (itemVal % 1.0 == 0.0) itemVal.toInt().toString() else itemVal.toString(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF381E72)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .background(Color.White, RoundedCornerShape(6.dp))
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("-", fontSize = 11.sp, color = Color.LightGray)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { saveForm() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72))
                ) {
                    Text(if (editingIndex != null) "Update BTB" else "Simpan BTB")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- RIWAYAT DATA BTB ---
        Text("Daftar BTB Tersimpan (${btbList.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(btbList) { index, item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${item.customerName} (${item.trademarks})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Tgl: ${item.hariTanggal} | Barang: ${item.jenisBarang}", fontSize = 11.sp, color = Color.Gray)
                            Text("Koli: ${item.daftarTimbangan.size} | Total: ${item.daftarTimbangan.sum()} KG", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                        }
                        Row {
                            IconButton(onClick = {
                                editingIndex = index
                                hariTanggal = item.hariTanggal
                                customerName = item.customerName
                                trademarks = item.trademarks
                                jenisBarang = item.jenisBarang
                                currentKgEntries.clear()
                                currentKgEntries.addAll(item.daftarTimbangan)
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF1976D2))
                            }
                            IconButton(onClick = {
                                itemIndexToDelete = index
                                deleteType = BtbDeleteType.ITEM
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}
