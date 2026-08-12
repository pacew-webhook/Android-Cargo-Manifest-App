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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StowingInputScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // State Form Input
    var noPag by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var inputKg by remember { mutableStateOf("") }

    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val cargoList = remember { mutableStateListOf<CargoItem>() }
    
    // State untuk Dropdown Menu (List Pemintal) NO PAG
    var expandedPag by remember { mutableStateOf(false) }
    val existingPags = remember(cargoList.toList()) {
        cargoList.map { it.noPag }.distinct()
    }
    
    // Menggunakan Double? agar item yang dihapus bernilai null (posisi tetap ada, tetapi kosong)
    val currentKgEntries = remember { mutableStateListOf<Double?>() }
    
    // Total KG hanya menghitung item yang aktif (tidak null)
    val currentActiveEntries = currentKgEntries.filterNotNull()
    val currentTotalKg = currentActiveEntries.sum()

    // State Dialog Hapus
    var deleteType by remember { mutableStateOf(DeleteType.NONE) }
    var itemIndexToDelete by remember { mutableStateOf<Int?>(null) }
    var kgIndexToDelete by remember { mutableStateOf<Int?>(null) }

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

    // --- LOGIKA MENAMBAH ITEM (Mengisi Kotak Kosong Terlebih Dahulu) ---
    fun addKgEntry() {
        val kgVal = inputKg.toDoubleOrNull()
        if (kgVal != null && kgVal > 0) {
            val emptyIndex = currentKgEntries.indexOfFirst { it == null }
            if (emptyIndex != -1) {
                currentKgEntries[emptyIndex] = kgVal
            } else {
                currentKgEntries.add(kgVal)
            }
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
        if (currentActiveEntries.isEmpty()) {
            Toast.makeText(context, "Masukkan minimal 1 nilai KG", Toast.LENGTH_SHORT).show()
            return
        }

        val formattedWeightList = currentActiveEntries.joinToString(", ") {
            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
        }

        val formattedTotalKg = if (currentTotalKg % 1.0 == 0.0) {
            currentTotalKg.toInt().toString()
        } else {
            currentTotalKg.toString()
        }

        val newItem = CargoItem(
            noPag = noPag.uppercase().trim(),
            customer = customer.uppercase().trim(),
            pcsQty = currentActiveEntries.size.toString(),
            weight = formattedWeightList,
            subTotal = formattedTotalKg
        )

        val index = editingIndex
        if (index != null && index in cargoList.indices) {
            cargoList[index] = newItem
            Toast.makeText(context, "Data berhasil diperbarui!", Toast.LENGTH_SHORT).show()
        } else {
            cargoList.add(0, newItem)
            Toast.makeText(context, "Data berhasil disimpan!", Toast.LENGTH_SHORT).show()
        }

        updateAndSaveCargoList()

        // Reset Form
        noPag = ""
        customer = ""
        inputKg = ""
        currentKgEntries.clear()
        editingIndex = null
    }

    fun startEditCargoItem(indexInOriginalList: Int, item: CargoItem) {
        editingIndex = indexInOriginalList
        noPag = item.noPag
        customer = item.customer
        inputKg = ""
        currentKgEntries.clear()

        val parsedKgList = item.weight.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        currentKgEntries.addAll(parsedKgList)
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
                kgIndexToDelete = null
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
                                editingIndex = null
                                noPag = ""
                                customer = ""
                                inputKg = ""
                                currentKgEntries.clear()
                                Toast.makeText(context, "Semua data berhasil dihapus", Toast.LENGTH_SHORT).show()
                            }
                            DeleteType.CARGO_ITEM -> {
                                itemIndexToDelete?.let { idx ->
                                    if (idx in cargoList.indices) {
                                        if (editingIndex == idx) {
                                            editingIndex = null
                                            noPag = ""
                                            customer = ""
                                            inputKg = ""
                                            currentKgEntries.clear()
                                        }
                                        cargoList.removeAt(idx)
                                        updateAndSaveCargoList()
                                        Toast.makeText(context, "Data berhasil dihapus", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            DeleteType.KG_ENTRY -> {
                                kgIndexToDelete?.let { idx ->
                                    if (idx in currentKgEntries.indices) {
                                        currentKgEntries[idx] = null
                                    }
                                }
                            }
                            DeleteType.NONE -> {}
                        }
                        deleteType = DeleteType.NONE
                        itemIndexToDelete = null
                        kgIndexToDelete = null
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
                        kgIndexToDelete = null
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
                    IconButton(onClick = {
                        deleteType = DeleteType.RESET_ALL
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Reset Data",
                            tint = Color.Red
                        )
                    }
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
        }

        // --- CARD FORM INPUT ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (editingIndex != null) Color(0xFFFFF8E1) else Color(0xFFF3EDF7)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editingIndex != null) "Edit Data Stowing" else "Input PAG, Customer & KG",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (editingIndex != null) Color(0xFFE65100) else Color(0xFF381E72)
                    )

                    if (editingIndex != null) {
                        TextButton(onClick = {
                            editingIndex = null
                            noPag = ""
                            customer = ""
                            inputKg = ""
                            currentKgEntries.clear()
                        }) {
                            Text("Batal Edit", color = Color.Red, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // --- KONDISI NO PAG: MENJADI LIST PEMINTAL (DROPDOWN) JIKA SUDAH PERNAH DIISI ---
                    if (existingPags.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = expandedPag,
                            onExpandedChange = { expandedPag = !expandedPag },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = noPag,
                                onValueChange = { noPag = it.uppercase() },
                                label = { Text("NO PAG") },
                                placeholder = { Text("Pilih / Ketik PAG") },
                                singleLine = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPag)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedPag,
                                onDismissRequest = { expandedPag = false }
                            ) {
                                existingPags.forEach { pag ->
                                    DropdownMenuItem(
                                        text = { Text(pag) },
                                        onClick = {
                                            noPag = pag
                                            expandedPag = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = noPag,
                            onValueChange = { noPag = it.uppercase() },
                            label = { Text("NO PAG") },
                            placeholder = { Text("001 MYI") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

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

                // --- RINCIAN INPUT KG ---
                if (currentKgEntries.isNotEmpty()) {
                    Text(
                        text = "Rincian Input KG (${currentActiveEntries.size} Koli):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

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
                                            onClick = {
                                                kgIndexToDelete = index
                                                deleteType = DeleteType.KG_ENTRY
                                            },
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
                            } else {
                                Box(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .fillMaxWidth()
                                )
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (editingIndex != null) Color(0xFFE65100) else Color(0xFF2E7D32)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (editingIndex != null) "Update Data Stowing" else "Simpan ke Cargo Table",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- DAFTAR CARGO TERGROUPING ---
        val grandTotalKg = cargoList.sumOf { item -> item.subTotal.toDoubleOrNull() ?: 0.0 }
        val grandTotalKoli = cargoList.sumOf { item -> item.pcsQty.toIntOrNull() ?: 0 }

        val formattedGrandTotal = if (grandTotalKg % 1.0 == 0.0) {
            grandTotalKg.toLong().toString()
        } else {
            grandTotalKg.toString()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
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
                Surface(
                    color = Color(0xFF2E7D32),
                    shape = RoundedCornerShape(16.dp)
                ) {
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

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(groupedCargo.entries.toList()) { group ->
                val pagKey = group.key
                val itemsInGroup = group.value

                val groupTotalKg = itemsInGroup.sumOf { it.second.subTotal.toDoubleOrNull() ?: 0.0 }
                val groupTotalKoli = itemsInGroup.sumOf { it.second.pcsQty.toIntOrNull() ?: 0 }

                val formattedGroupKg = if (groupTotalKg % 1.0 == 0.0) {
                    groupTotalKg.toLong().toString()
                } else {
                    groupTotalKg.toString()
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "NO PAG: $pagKey",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF381E72)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = Color.LightGray
                        )

                        itemsInGroup.forEachIndexed { subIndex, pair ->
                            val originalIndex = pair.first
                            val item = pair.second

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (editingIndex == originalIndex) Color(0xFFFFF3E0) else Color(0xFFF8F9FA),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(8.dp),
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
                                    Text(
                                        text = "KG: ${item.weight}",
                                        fontSize = 11.sp,
                                        color = Color.DarkGray
                                    )
                                }

                                Row {
                                    IconButton(
                                        onClick = { startEditCargoItem(originalIndex, item) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Data",
                                            tint = Color(0xFF0288D1),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            itemIndexToDelete = originalIndex
                                            deleteType = DeleteType.CARGO_ITEM
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Hapus Data",
                                            tint = Color(0xFFB3261E),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (subIndex < itemsInGroup.size - 1) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL PAG ($groupTotalKoli Koli):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF1B5E20)
                            )
                            Text(
                                text = "$formattedGroupKg KG",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }
        }
    }
} 
