package com.example.cargomanifestapp

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CargoAppScreen(
    viewModel: CargoViewModel,
    onBackToMenu: () -> Unit = {}
) {
    BackHandler { onBackToMenu() }

    val context = LocalContext.current
    val groups by viewModel.manifestGroups.collectAsState()
    var isSendingToN8n by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<ManifestGroup?>(null) }
    var selectedDetail by remember { mutableStateOf<ManifestDetailItem?>(null) }
    var validationErrors by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshFromStowingPrefs(context) }

    val totalPcs = groups.sumOf { it.summary.pcsQty.toDoubleOrNull()?.toInt() ?: 0 }
    val totalWeight = groups.sumOf { it.summary.subTotal.toDoubleOrNull() ?: 0.0 }
    val totalWeightText = if (totalWeight % 1.0 == 0.0) totalWeight.toInt().toString() else totalWeight.toString()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackToMenu) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color(0xFF6200EE))
            }
            Text("Manifest Cargo App", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6200EE))
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Data Manifest", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3F207A))
                Spacer(Modifier.height(4.dp))
                Text("Data digabung berdasarkan PTI + Customer + Description. Tekan kartu untuk melihat data asal dan mengedit salah satunya.", fontSize = 13.sp, color = Color(0xFF49454F))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Data: ${groups.size}", fontWeight = FontWeight.SemiBold)
                    Text("Total Pcs: $totalPcs", fontWeight = FontWeight.SemiBold)
                    Text("Total: $totalWeightText KG", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
                Spacer(Modifier.height(8.dp))
                val status = viewModel.validateManifestData()
                Surface(
                    color = if (status.valid) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (status.valid) "✓ DATA SIAP EXPORT" else "⚠ PERLU DIPERIKSA (${status.errors.size})",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        color = if (status.valid) Color(0xFF2E7D32) else Color(0xFFB00020),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val result = viewModel.validateManifestData()
                    if (result.valid) viewModel.exportToExcel(context, "", "") else validationErrors = result.errors
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(20.dp)
            ) { Text("Export Excel", fontWeight = FontWeight.Bold) }

            Button(
                onClick = {
                    val validation = viewModel.validateManifestData()
                    if (!validation.valid) {
                        validationErrors = validation.errors
                    } else if (!isSendingToN8n) {
                        isSendingToN8n = true
                        viewModel.sendManifestToN8n { result ->
                            isSendingToN8n = false
                            result.onSuccess { Toast.makeText(context, "Data Manifest berhasil dikirim ke n8n", Toast.LENGTH_LONG).show() }
                                .onFailure { Toast.makeText(context, "Gagal kirim ke n8n: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
                        }
                    }
                },
                enabled = groups.isNotEmpty() && !isSendingToN8n,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(20.dp)
            ) { Text(if (isSendingToN8n) "Mengirim..." else "Kirim ke Laptop (n8n)", fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(12.dp))
        Text("Data dari Form Stowing Cargo (${groups.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF3F207A))
        Spacer(Modifier.height(6.dp))

        if (groups.isEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)), shape = RoundedCornerShape(14.dp)) {
                Text("Belum ada data. Silakan input data melalui Form Stowing Cargo.", Modifier.padding(16.dp), color = Color.Gray)
            }
        } else {
            LazyRow(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(groups, key = { it.groupKey }) { group ->
                    ManifestSummaryCard(group) { selectedGroup = group }
                }
            }
        }
    }

    validationErrors?.let { errors ->
        AlertDialog(
            onDismissRequest = { validationErrors = null },
            title = { Text("⚠ Periksa Data Sebelum Export", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    errors.take(30).forEach { Text("• $it", fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                    if (errors.size > 30) Text("... dan ${errors.size - 30} masalah lainnya", fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { validationErrors = null }) { Text("Mengerti") } }
        )
    }

    selectedGroup?.let { group ->
        ManifestGroupDialog(
            group = group,
            onDismiss = { selectedGroup = null },
            onEdit = { detail ->
                selectedGroup = null
                selectedDetail = detail
            }
        )
    }

    selectedDetail?.let { detail ->
        ManifestEditDialog(
            detail = detail,
            onDismiss = { selectedDetail = null },
            onSave = { edited ->
                viewModel.updateManifestDetail(context, detail, edited)
                selectedDetail = null
            }
        )
    }
}

@Composable
private fun ManifestSummaryCard(group: ManifestGroup, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(340.dp).height(280.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("PTI: ${group.summary.pti}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF3F207A))
                Text("${group.summary.subTotal} KG", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            }
            Spacer(Modifier.height(6.dp))
            Text("Customer: ${group.summary.customer.ifBlank { "-" }}")
            Text("Description: ${group.summary.description.ifBlank { "-" }}")
            Text("Pcs / Qty: ${group.summary.pcsQty.ifBlank { "0" }}")
            Text("NO PAG: ${group.summary.noPag.ifBlank { "-" }}")
            Text("Data asal: ${group.details.size} baris", fontWeight = FontWeight.SemiBold, color = Color(0xFF6750A4))
            Spacer(Modifier.height(8.dp))
            Text("Tekan kartu untuk edit", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun ManifestGroupDialog(
    group: ManifestGroup,
    onDismiss: () -> Unit,
    onEdit: (ManifestDetailItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detail Manifest") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("${group.summary.pti} • ${group.summary.customer}", fontWeight = FontWeight.Bold)
                Text(group.summary.description, color = Color.Gray)
                Spacer(Modifier.height(10.dp))
                group.details.forEach { detail ->
                    val item = detail.item
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${item.noPag.ifBlank { "Tanpa NO PAG" }}", fontWeight = FontWeight.Bold)
                            Text("PCS: ${item.pcsQty} • KG: ${item.subTotal}")
                            Text("${item.description} • ${item.customer}", fontSize = 12.sp, color = Color.Gray)
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(onClick = { onEdit(detail) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Edit data ini")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun ManifestEditDialog(
    detail: ManifestDetailItem,
    onDismiss: () -> Unit,
    onSave: (CargoItem) -> Unit
) {
    val original = detail.item
    var pti by remember { mutableStateOf(original.pti) }
    var noPag by remember { mutableStateOf(original.noPag) }
    var customer by remember { mutableStateOf(original.customer) }
    var description by remember { mutableStateOf(original.description) }

    // Model input KG dibuat sama dengan Form Stowing Cargo:
    // 1 field "Input Berat (KG)" + tombol "+ KG" + rincian KG dalam grid.
    val initialWeights = remember(original.weight) {
        original.weight
            .split(",", ";", "\n")
            .mapNotNull { it.trim().replace(',', '.').toDoubleOrNull() }
            .filter { it.isFinite() && it > 0.0 }
            .map { it }
    }
    val kgEntries = remember {
        initialWeights.map { it }.toMutableStateList<Double?>().also {
            if (it.isEmpty()) it.add(null)
        }
    }
    var inputKg by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun formatKg(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    fun addKgFromInput(): Boolean {
        val kg = inputKg.replace(',', '.').toDoubleOrNull()
        if (kg == null || !kg.isFinite() || kg <= 0.0) {
            errorText = "Masukkan angka KG yang valid."
            return false
        }
        val emptyIndex = kgEntries.indexOfFirst { it == null }
        if (emptyIndex >= 0) kgEntries[emptyIndex] = kg
        else kgEntries.add(kg)
        inputKg = ""
        errorText = null
        return true
    }

    val activeWeights = kgEntries.filterNotNull().filter { it.isFinite() && it > 0.0 }
    val totalKg = activeWeights.sum()
    val totalKgText = formatKg(totalKg)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Data Manifest") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                EditField("PTI", pti) { pti = it }
                EditField("NO PAG", noPag) { noPag = it }
                EditField("Customer", customer) { customer = it }
                EditField("Description", description) { description = it }

                Spacer(Modifier.height(10.dp))

                // === SAMA SEPERTI FORM STOWING CARGO ===
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputKg,
                        onValueChange = {
                            inputKg = it
                            errorText = null
                        },
                        label = { Text("Input Berat (KG)") },
                        placeholder = { Text("10") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            addKgFromInput()
                        }),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = { addKgFromInput() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Tambah KG")
                        Spacer(Modifier.width(4.dp))
                        Text("+ KG")
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Rincian Input KG (${activeWeights.size} Koli):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Masukkan berat satu per satu. Tekan Enter atau + KG untuk menambah.",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(4.dp))

                // Grid dibuat 5 kolom seperti Form Stowing Cargo.
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(kgEntries.indices.toList()) { index ->
                        val itemVal = kgEntries[index]
                        if (itemVal != null) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                        text = formatKg(itemVal),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = {
                                            // Sama dengan Form Stowing: slot dikosongkan,
                                            // tidak menggeser posisi KG lainnya.
                                            kgEntries[index] = null
                                        },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Hapus KG",
                                            tint = Color.Red,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.height(28.dp).fillMaxWidth())
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF381E72), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TOTAL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "$totalKgText KG",
                            color = Color.Yellow,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("JUMLAH KOLI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            "${activeWeights.size} KOLI",
                            color = Color.Yellow,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                }

                errorText?.let {
                    Spacer(Modifier.height(5.dp))
                    Text(it, color = Color(0xFFB00020), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val validWeights = kgEntries.filterNotNull().filter { it.isFinite() && it > 0.0 }
                if (validWeights.isEmpty()) {
                    errorText = "Masukkan minimal 1 nilai KG."
                    return@Button
                }

                val formattedWeights = validWeights.joinToString(", ") { formatKg(it) }
                val sum = validWeights.sum()

                onSave(
                    original.copy(
                        pti = pti.trim(),
                        noPag = noPag.trim(),
                        customer = customer.trim(),
                        description = description.trim(),
                        pcsQty = validWeights.size.toString(),
                        weight = formattedWeights,
                        subTotal = formatKg(sum)
                    )
                )
            }) {
                Text("Simpan")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun EditField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        singleLine = true
    )
}
