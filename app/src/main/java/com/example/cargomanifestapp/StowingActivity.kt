package com.example.cargomanifestapp

import android.content.Context
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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

// --- HELPER UNTUK AUTO-SAVE & LOAD LOCAL STORAGE ---
private fun saveCargoListToPrefs(context: Context, list: List<CargoItem>) {
    val prefs = context.getSharedPreferences("stowing_prefs", Context.MODE_PRIVATE)
    val jsonArray = JSONArray()
    for (item in list) {
        val obj = JSONObject().apply {
            put("noPag", item.noPag)
            put("customer", item.customer)
            put("pcsQty", item.pcsQty)
            put("weight", item.weight)
            put("subTotal", item.subTotal)
        }
        jsonArray.put(obj)
    }
    prefs.edit().putString("saved_cargo_list", jsonArray.toString()).apply()
}

private fun loadCargoListFromPrefs(context: Context): List<CargoItem> {
    val prefs = context.getSharedPreferences("stowing_prefs", Context.MODE_PRIVATE)
    val jsonString = prefs.getString("saved_cargo_list", null) ?: return emptyList()
    val list = mutableListOf<CargoItem>()
    try {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                CargoItem(
                    noPag = obj.getString("noPag"),
                    customer = obj.getString("customer"),
                    pcsQty = obj.getString("pcsQty"),
                    weight = obj.getString("weight"),
                    subTotal = obj.getString("subTotal")
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

private enum class DeleteType {
    NONE, RESET_ALL, CARGO_ITEM, KG_ENTRY
}

// Data class pendukung untuk list baris input PAG dinamis
data class PagInputField(
    var noPag: String = "",
    var customer: String = "",
    val kgEntries: MutableList<Double?> = mutableStateListOf()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StowingInputScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // State List Input PAG Dinamis (Minimal ada 1 baris awal)
    val pagInputList = remember { mutableStateListOf(PagInputField()) }

    val cargoList = remember { mutableStateListOf<CargoItem>() }

    // State Dialog Hapus
    var deleteType by remember { mutableStateOf(DeleteType.NONE) }
    var itemIndexToDelete by remember { mutableStateOf<Int?>(null) }
    var kgIndexToDeletePair by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(Unit) {
        val savedData = loadCargoListFromPrefs(context)
        cargoList.clear()
        cargoList.addAll(savedData)
    }

    fun updateAndSaveCargoList() {
        saveCargoListToPrefs(context, cargoList.toList())
    }

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

    fun saveAllCargoItems() {
        var successCount = 0
        pagInputList.forEach { field ->
            val activeKg = field.kgEntries.filterNotNull()
            if (field.noPag.isNotBlank() && field.customer.isNotBlank() && activeKg.isNotEmpty()) {
                val formattedWeightList = activeKg.joinToString(", ") {
                    if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                }
                val totalKgVal = activeKg.sum()
                val formattedTotalKg = if (totalKgVal % 1.0 == 0.0) {
                    totalKgVal.toInt().toString()
                } else {
                    totalKgVal.toString()
                }

                val newItem = CargoItem(
                    noPag = field.noPag.uppercase().trim(),
                    customer = field.customer.uppercase().trim(),
                    pcsQty = activeKg.size.toString(),
                    weight = formattedWeightList,
                    subTotal = formattedTotalKg
                )
                cargoList.add(0, newItem)
                successCount++
            }
        }

        if (successCount > 0) {
            updateAndSaveCargoList()
            Toast.makeText(context, "$successCount Data berhasil disimpan!", Toast.LENGTH_SHORT).show()
            pagInputList.clear()
            pagInputList.add(PagInputField())
        } else {
            Toast.makeText(context, "Mohon lengkapi minimal No PAG, Customer, dan 1 nilai KG", Toast.LENGTH_SHORT).show()
        }
    }

    val groupedCargo = remember(cargoList.toList()) {
        cargoList.mapIndexed { originalIndex, item ->
            Pair(originalIndex, item)
        }.groupBy { it.second.noPag }
    }

    // --- POP-UP DIALOG KONFIRMASI DELETE ---
    if (deleteType != DeleteType.NONE) {
        AlertDialog(
            onDismissRequest = {
                deleteType = DeleteType.NONE
                itemIndexToDelete = null
                kgIndexToDeletePair = null
            },
            title = { Text("Konfirmasi Hapus", fontWeight = FontWeight.Bold) },
            text = {
                val message = when (deleteType) {
                    DeleteType.RESET_ALL -> "Apakah Anda yakin ingin menghapus SELURUH data stowing?"
                    DeleteType.CARGO_ITEM -> "Apakah Anda yakin ingin menghapus data customer ini?"
                    DeleteType.KG_ENTRY -> "Apakah Anda yakin ingin menghapus pecahan KG ini?"
                    DeleteType.NONE -> ""
                }
                Text(message)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (deleteType) {
                            DeleteType.RESET_ALL -> {
                                cargoList.clear()
                                updateAndSaveCargoList()
                                pagInputList.clear()
                                pagInputList.add(PagInputField())
                                Toast.makeText(context, "Semua data berhasil dihapus", Toast.LENGTH_SHORT).show()
                            }
                            DeleteType.CARGO_ITEM -> {
                                itemIndexToDelete?.let { idx ->
                                    if (idx in cargoList.indices) {
                                        cargoList.removeAt(idx)
                                        updateAndSaveCargoList()
                                        Toast.makeText(context, "Data berhasil dihapus", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            DeleteType.KG_ENTRY -> {
                                kgIndexToDeletePair?.let { pair ->
                                    val pagIdx = pair.first
                                    val kgIdx = pair.second
                                    if (pagIdx in pagInputList.indices && kgIdx in pagInputList[pagIdx].kgEntries.indices) {
                                        pagInputList[pagIdx].kgEntries[kgIdx] = null
                                    }
                                }
                            }
                            DeleteType.NONE -> {}
                        }
                        deleteType = DeleteType.NONE
                        itemIndexToDelete = null
                        kgIndexToDeletePair = null
                    }
                ) {
                    Text("Hapus", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleteType = DeleteType.NONE
                        itemIndexToDelete = null
                        kgIndexToDeletePair = null
                    }
                ) {
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

            Row {
                if (cargoList.isNotEmpty()) {
                    IconButton(onClick = { deleteType = DeleteType.RESET_ALL }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Reset Data", tint = Color.Red)
                    }
                }

                IconButton(onClick = {
                    if (cargoList.isNotEmpty()) {
                        exportLauncher.launch("Stowing_Report_${System.currentTimeMillis()}.xlsx")
                    } else {
                        Toast.makeText(context, "Data Kosong", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Export Excel", tint = Color(0xFF2E7D32))
                }
            }
        }

        // --- DAFTAR KARTU FORM INPUT DINAMIS BERBENTUK LIST ---
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(pagInputList) { pagIndex, pagField ->
                val activeEntries = pagField.kgEntries.filterNotNull()
                val totalKg = activeEntries.sum()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Input PAG #${pagIndex + 1}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF381E72)
                            )

                            if (pagInputList.size > 1) {
                                IconButton(
                                    onClick = { pagInputList.removeAt(pagIndex) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus Baris",
                                        tint = Color.Red
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = pagField.noPag,
                                onValueChange = { 
                                    pagInputList[pagIndex] = pagField.copy(noPag = it.uppercase())
                                    if (pagIndex == pagInputList.size - 1 && it.isNotBlank()) {
                                        pagInputList.add(PagInputField())
                                    }
                                },
                                label = { Text("NO PAG") },
                                placeholder = { Text("001 MYI") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = pagField.customer,
                                onValueChange = { pagInputList[pagIndex] = pagField.copy(customer = it.uppercase()) },
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

                        var tempInputKg by remember { mutableStateOf("") }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = tempInputKg,
                                onValueChange = { tempInputKg = it },
                                label = { Text("Input Berat (KG)") },
                                placeholder = { Text("10") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    val kgVal = tempInputKg.toDoubleOrNull()
                                    if (kgVal != null && kgVal > 0) {
                                        val emptyIdx = pagField.kgEntries.indexOfFirst { it == null }
                                        if (emptyIdx != -1) {
                                            pagField.kgEntries[emptyIdx] = kgVal
                                        } else {
                                            pagField.kgEntries.add(kgVal)
                                        }
                                        tempInputKg = ""
                                    } else {
                                        Toast.makeText(context, "Masukkan angka KG yang valid", Toast.LENGTH_SHORT).show()
                                    }
                                }),
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = {
                                    val kgVal = tempInputKg.toDoubleOrNull()
                                    if (kgVal != null && kgVal > 0) {
                                        val emptyIdx = pagField.kgEntries.indexOfFirst { it == null }
                                        if (emptyIdx != -1) {
                                            pagField.kgEntries[emptyIdx] = kgVal
                                        } else {
                                            pagField.kgEntries.add(kgVal)
                                        }
                                        tempInputKg = ""
                                    } else {
                                        Toast.makeText(context, "Masukkan angka KG yang valid", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ KG")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // --- RINCIAN INPUT KG ---
                        if (pagField.kgEntries.isNotEmpty()) {
                            Text(
                                text = "Rincian Input KG (${activeEntries.size} Koli):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(5),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(pagField.kgEntries) { kgIndex, itemVal ->
                                    if (itemVal != null) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFE8DEF8), shape = RoundedCornerShape(6.dp))
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
                                                    onClick = {
                                                        kgIndexToDeletePair = Pair(pagIndex, kgIndex)
                                                        deleteType = DeleteType.KG_ENTRY
                                                    },
                                                    modifier = Modifier.size(14.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red)
                                                }
                                            }
                                        }
                                    } else {
                                        Box(modifier = Modifier.height(28.dp).fillMaxWidth())
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF381E72), shape = RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TOTAL KG:",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (totalKg % 1.0 == 0.0) "${totalKg.toInt()} KG" else "$totalKg KG",
                                    color = Color.Yellow,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // --- TOMBOL TAMBAH BARIS MANUAL & SIMPAN ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { pagInputList.add(PagInputField()) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Baris")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tambah Baris PAG")
                    }

                    Button(
                        onClick = { saveAllCargoItems() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Simpan Semua Data", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // --- DAFTAR CARGO TERGROUPING (DI BAWAH FORM) ---
            item {
                Spacer(modifier = Modifier.height(10.dp))
                val grandTotalKg = cargoList.sumOf { item -> item.subTotal.toDoubleOrNull() ?: 0.0 }
                val grandTotalKoli = cargoList.sumOf { item -> item.pcsQty.toIntOrNull() ?: 0 }
                val formattedGrandTotal = if (grandTotalKg % 1.0 == 0.0) grandTotalKg.toLong().toString() else grandTotalKg.toString()

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daftar Stowing Group (${groupedCargo.size} PAG)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF381E72)
                    )

                    if (cargoList.isNotEmpty()) {
                        Surface(color = Color(0xFF2E7D32), shape = RoundedCornerShape(16.dp)) {
                            Text(
                                text = "Total: $formattedGrandTotal KG ($grandTotalKoli Koli)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            items(groupedCargo.entries.toList()) { group ->
                val pagKey = group.key
                val itemsInGroup = group.value
                val groupTotalKg = itemsInGroup.sumOf { it.second.subTotal.toDoubleOrNull() ?: 0.0 }
                val groupTotalKoli = itemsInGroup.sumOf { it.second.pcsQty.toIntOrNull() ?: 0 }
                val formattedGroupKg = if (groupTotalKg % 1.0 == 0.0) groupTotalKg.toLong().toString() else groupTotalKg.toString()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "NO PAG: $pagKey", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF381E72))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray)

                        itemsInGroup.forEachIndexed { subIndex, pair ->
                            val originalIndex = pair.first
                            val item = pair.second

                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color(0xFFF8F9FA), shape = RoundedCornerShape(6.dp)).padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.customer} - ${item.pcsQty} Koli (${item.subTotal} KG)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF381E72)
                                    )
                                    Text(text = "KG: ${item.weight}", fontSize = 11.sp, color = Color.DarkGray)
                                }

                                IconButton(
                                    onClick = {
                                        itemIndexToDelete = originalIndex
                                        deleteType = DeleteType.CARGO_ITEM
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Hapus Data", tint = Color(0xFFB3261E), modifier = Modifier.size(18.dp))
                                }
                            }
                            if (subIndex < itemsInGroup.size - 1) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFE8F5E9), shape = RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "TOTAL PAG ($groupTotalKoli Koli):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1B5E20))
                            Text(text = "$formattedGroupKg KG", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF2E7D32))
                        }
                    }
                }
            }
        }
    }
}
