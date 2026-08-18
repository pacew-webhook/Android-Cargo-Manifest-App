package com.example.cargomanifestapp

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.exportToExcel(context, "", "") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(20.dp)
            ) { Text("Export Excel", fontWeight = FontWeight.Bold) }

            Button(
                onClick = {
                    if (!isSendingToN8n) {
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

    // Sama seperti Form Stowing Cargo: KG disimpan sebagai daftar nilai per koli.
    // Enter pada field KG mempunyai fungsi yang sama dengan "+ KG": menambah baris KG baru.
    val initialWeights = remember(original.weight) {
        original.weight
            .split(",", ";", "\n")
            .mapNotNull { it.trim().replace(',', '.').toDoubleOrNull() }
            .filter { it.isFinite() && it > 0.0 }
            .map { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
            .toMutableStateList()
    }
    val kgEntries = remember {
        initialWeights.ifEmpty { mutableListOf("") }.toMutableStateList()
    }

    val totalKg = kgEntries.sumOf { it.replace(',', '.').toDoubleOrNull() ?: 0.0 }
    val totalKgText = if (totalKg % 1.0 == 0.0) {
        totalKg.toInt().toString()
    } else {
        totalKg.toString()
    }

    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Data Manifest") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                EditField("PTI", pti) { pti = it }
                EditField("NO PAG", noPag) { noPag = it }
                EditField("Customer", customer) { customer = it }
                EditField("Description", description) { description = it }

                Spacer(Modifier.height(10.dp))
                Text(
                    "Rincian Input KG",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3F207A)
                )
                Text(
                    "Masukkan KG satu per satu. Tekan Enter pada KG terakhir untuk menambah baris.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(6.dp))

                kgEntries.forEachIndexed { index, value ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { kgEntries[index] = it },
                            label = { Text("Koli ${index + 1}") },
                            suffix = { Text("KG") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    // Enter/Next selalu berfungsi sebagai "+ KG".
                                    // Tambahkan satu baris baru setelah nilai valid.
                                    val kg = kgEntries[index]
                                        .replace(',', '.')
                                        .toDoubleOrNull()
                                    if (kg != null && kg > 0.0) {
                                        kgEntries.add("")
                                    } else {
                                        errorText = "Isi KG Koli ${index + 1} dengan angka lebih dari 0."
                                    }
                                }
                            )
                        )

                        if (kgEntries.size > 1) {
                            TextButton(
                                onClick = { kgEntries.removeAt(index) },
                                modifier = Modifier.width(54.dp)
                            ) {
                                Text("Hapus", fontSize = 10.sp, color = Color.Red)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    "Total Koli: ${kgEntries.count { it.replace(',', '.').toDoubleOrNull()?.let { v -> v > 0.0 } == true }}",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Total KG: $totalKgText KG",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )

                errorText?.let {
                    Spacer(Modifier.height(5.dp))
                    Text(it, color = Color(0xFFB00020), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val validWeights = kgEntries.mapNotNull {
                    it.replace(',', '.').toDoubleOrNull()?.takeIf { value -> value > 0.0 }
                }

                if (validWeights.isEmpty()) {
                    errorText = "Masukkan minimal 1 nilai KG."
                    return@Button
                }

                val formattedWeights = validWeights.joinToString(", ") {
                    if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                }

                onSave(
                    original.copy(
                        pti = pti,
                        noPag = noPag,
                        customer = customer,
                        description = description,
                        pcsQty = validWeights.size.toString(),
                        weight = formattedWeights,
                        subTotal = if (validWeights.sum() % 1.0 == 0.0) {
                            validWeights.sum().toInt().toString()
                        } else {
                            validWeights.sum().toString()
                        }
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
