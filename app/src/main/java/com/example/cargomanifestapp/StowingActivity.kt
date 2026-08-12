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
import androidx.lifecycle.viewmodel.compose.viewModel

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
fun StowingInputScreen(
    onBack: () -> Unit,
    viewModel: StowingViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadCargoListFromPrefs(context)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let {
            try {
                ExcelUtils.writeCargoListToExcel(context, it, viewModel.cargoList)
                Toast.makeText(context, "Export Berhasil!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal Export: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val groupedCargo = remember(viewModel.cargoList.toList()) {
        viewModel.cargoList.mapIndexed { originalIndex, item ->
            Pair(originalIndex, item)
        }.groupBy { it.second.noPag }
    }

    // --- POP-UP DIALOG KONFIRMASI DELETE ---
    if (viewModel.deleteType != DeleteType.NONE) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("Konfirmasi Hapus", fontWeight = FontWeight.Bold) },
            text = {
                val message = when (viewModel.deleteType) {
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
                        viewModel.confirmDelete(context) { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Hapus", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
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
                if (viewModel.cargoList.isNotEmpty()) {
                    IconButton(onClick = { viewModel.showDeleteDialog(DeleteType.RESET_ALL) }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Reset Data", tint = Color.Red)
                    }
                }

                IconButton(onClick = {
                    if (viewModel.cargoList.isNotEmpty()) {
                        exportLauncher.launch("Stowing_Report_${System.currentTimeMillis()}.xlsx")
                    } else {
                        Toast.makeText(context, "Data Kosong", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Export Excel", tint = Color(0xFF2E7D32))
                }
            }
        }

        // --- CARD FORM INPUT ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (viewModel.editingIndex != null) Color(0xFFFFF8E1) else Color(0xFFF3EDF7)
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
                        text = if (viewModel.editingIndex != null) "Edit Data Stowing" else "Input PAG, Customer & KG",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (viewModel.editingIndex != null) Color(0xFFE65100) else Color(0xFF381E72)
                    )

                    if (viewModel.editingIndex != null) {
                        TextButton(onClick = { viewModel.cancelEdit() }) {
                            Text("Batal Edit", color = Color.Red, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (viewModel.existingPags.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = viewModel.expandedPag,
                            onExpandedChange = { viewModel.updateExpandedPag(!viewModel.expandedPag) },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = viewModel.noPag,
                                onValueChange = { viewModel.updateNoPag(it) },
                                label = { Text("NO PAG") },
                                placeholder = { Text("Pilih / Ketik PAG") },
                                singleLine = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewModel.expandedPag) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = viewModel.expandedPag,
                                onDismissRequest = { viewModel.updateExpandedPag(false) }
                            ) {
                                viewModel.existingPags.forEach { pag ->
                                    DropdownMenuItem(
                                        text = { Text(pag) },
                                        onClick = {
                                            viewModel.updateNoPag(pag)
                                            viewModel.updateExpandedPag(false)
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = viewModel.noPag,
                            onValueChange = { viewModel.updateNoPag(it) },
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
                        value = viewModel.customer,
                        onValueChange = { viewModel.updateCustomer(it) },
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
                        value = viewModel.inputKg,
                        onValueChange = { viewModel.updateInputKg(it) },
                        label = { Text("Input Berat (KG)") },
                        placeholder = { Text("10") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            viewModel.addKgEntry {
                                Toast.makeText(context, "Masukkan angka KG yang valid", Toast.LENGTH_SHORT).show()
                            }
                        }),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            viewModel.addKgEntry {
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
                if (viewModel.currentKgEntries.isNotEmpty()) {
                    Text(
                        text = "Rincian Input KG (${viewModel.currentActiveEntries.size} Koli):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(viewModel.currentKgEntries) { index, itemVal ->
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
                                            onClick = { viewModel.showDeleteDialog(DeleteType.KG_ENTRY, kgIdx = index) },
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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "TOTAL KG:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (viewModel.currentTotalKg % 1.0 == 0.0) "${viewModel.currentTotalKg.toInt()} KG" else "${viewModel.currentTotalKg} KG",
                            color = Color.Yellow,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                Button(
                    onClick = {
                        viewModel.saveCargoItem(
                            context = context,
                            onSuccess = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
                            onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewModel.editingIndex != null) Color(0xFFE65100) else Color(0xFF2E7D32)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (viewModel.editingIndex != null) "Update Data Stowing" else "Simpan ke Cargo Table",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- DAFTAR CARGO TERGROUPING ---
        // --- DAFTAR CARGO TERGROUPING ---
        val grandTotalKg = viewModel.cargoList.sumOf { item -> item.subTotal.toDoubleOrNull() ?: 0.0 }
        val grandTotalKoli = viewModel.cargoList.sumOf { item -> item.pcsQty.toIntOrNull() ?: 0 }

        val formattedGrandTotal = if (grandTotalKg % 1.0 == 0.0) {
            grandTotalKg.toLong().toString()
        } else {
            grandTotalKg.toString()
        }

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

            if (viewModel.cargoList.isNotEmpty()) {
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

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(groupedCargo.entries.toList()) { group ->
                val pagKey = group.key
                val itemsInGroup = group.value

                val groupTotalKg = itemsInGroup.sumOf { pair -> pair.second.subTotal.toDoubleOrNull() ?: 0.0 }
                val groupTotalKoli = itemsInGroup.sumOf { pair -> pair.second.pcsQty.toIntOrNull() ?: 0 }

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
                        Text(text = "NO PAG: $pagKey", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF381E72))

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray)

                        itemsInGroup.forEachIndexed { subIndex, pair ->
                            val originalIndex = pair.first
                            val item = pair.second

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (viewModel.editingIndex == originalIndex) Color(0xFFFFF3E0) else Color(0xFFF8F9FA),
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
                                    Text(text = "KG: ${item.weight}", fontSize = 11.sp, color = Color.DarkGray)
                                }

                                Row {
                                    IconButton(
                                        onClick = { viewModel.startEditCargoItem(originalIndex, item) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Data", tint = Color(0xFF0288D1), modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.showDeleteDialog(DeleteType.CARGO_ITEM, itemIdx = originalIndex) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Hapus Data", tint = Color(0xFFB3261E), modifier = Modifier.size(18.dp))
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
                            Text(text = "TOTAL PAG ($groupTotalKoli Koli):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1B5E20))
                            Text(text = "$formattedGroupKg KG", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF2E7D32))
                        }
                    }
                }
            }
        }
    }
}
