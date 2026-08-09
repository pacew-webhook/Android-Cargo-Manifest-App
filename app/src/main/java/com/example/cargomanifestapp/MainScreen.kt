package com.example.cargomanifestapp

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: CargoViewModel) {
    val context = LocalContext.current
    val cargoList: List<CargoItem> by viewModel.cargoList.collectAsState(initial = emptyList())

    // State Header Penerbangan (Otomatis Kapital)
    var awbNo by remember { mutableStateOf("") }
    var flightNo by remember { mutableStateOf("") }

    // State Input Barang
    var pti by remember { mutableStateOf("") }
    var pcsQty by remember { mutableStateOf("") }
    var pcsQtyWt by remember { mutableStateOf("") }
    var subTotalKg by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var noPag by remember { mutableStateOf("") }

    var isEditing by remember { mutableStateOf(false) }
    var selectedItemId by remember { mutableStateOf<Long?>(null) }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<CargoItem?>(null) }
    
    var showClearAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manifest Cargo App") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFE6DEEC),
                    titleContentColor = Color.Black
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header Penerbangan
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2EFE9)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Header Penerbangan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = awbNo,
                            onValueChange = { awbNo = it.uppercase() },
                            label = { Text("AWB No") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = flightNo,
                            onValueChange = { flightNo = it.uppercase() },
                            label = { Text("Flight No") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Text(
                text = "Input Data Barang",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            OutlinedTextField(
                value = pti,
                onValueChange = { pti = it.uppercase() },
                label = { Text("PTI") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pcsQty,
                    onValueChange = { pcsQty = it },
                    label = { Text("Pcs / Qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = pcsQtyWt,
                    onValueChange = { pcsQtyWt = it },
                    label = { Text("Pcs/Qty Wt") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = subTotalKg,
                    onValueChange = { subTotalKg = it },
                    label = { Text("Sub Total (Kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = noPag,
                    onValueChange = { noPag = it.uppercase() },
                    label = { Text("NO PAG") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it.uppercase() },
                label = { Text("Description") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customer,
                    onValueChange = { customer = it.uppercase() },
                    label = { Text("Customer") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    if (pti.isBlank()) {
                        Toast.makeText(context, "PTI harus diisi!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (isEditing && selectedItemId != null) {
                        val updatedItem = CargoItem(
                            id = selectedItemId!!,
                            pti = pti,
                            pcsQty = pcsQty,
                            weight = pcsQtyWt,
                            subTotal = subTotalKg,
                            description = description,
                            customer = customer,
                            noPag = noPag
                        )
                        viewModel.updateCargo(updatedItem)
                        Toast.makeText(context, "Data berhasil diperbarui", Toast.LENGTH_SHORT).show()
                        isEditing = false
                        selectedItemId = null
                    } else {
                        val newItem = CargoItem(
                            pti = pti,
                            pcsQty = pcsQty,
                            weight = pcsQtyWt,
                            subTotal = subTotalKg,
                            description = description,
                            customer = customer,
                            noPag = noPag
                        )
                        viewModel.insertCargo(newItem)
                        Toast.makeText(context, "Simpan ke Database Berhasil", Toast.LENGTH_SHORT).show()
                    }

                    pti = ""
                    pcsQty = ""
                    pcsQtyWt = ""
                    subTotalKg = ""
                    description = ""
                    customer = ""
                    noPag = ""
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
            ) {
                Text(if (isEditing) "Update Data Kargo" else "Simpan Ke Database", color = Color.White)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Baris Judul Tabel & Tombol Aksi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tabel Data (${cargoList.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            exportToExcel(context, cargoList, awbNo, flightNo)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Export Excel", color = Color.White, fontSize = 12.sp)
                    }

                    TextButton(onClick = { showClearAllDialog = true }) {
                        Text("Hapus Semua", color = Color.Red, fontSize = 12.sp)
                    }
                }
            }

            // Tabel Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF6750A4))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("No", color = Color.White, modifier = Modifier.weight(0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("PTI", color = Color.White, modifier = Modifier.weight(1.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Pcs", color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("SubTotal", color = Color.White, modifier = Modifier.weight(1.2f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Aksi", color = Color.White, modifier = Modifier.weight(1.3f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // LazyColumn Data List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(cargoList) { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F5FA))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${index + 1}", modifier = Modifier.weight(0.5f), fontSize = 12.sp)
                            Text(item.pti, modifier = Modifier.weight(1.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(item.pcsQty, modifier = Modifier.weight(1f), fontSize = 12.sp)
                            Text(item.subTotal, modifier = Modifier.weight(1.2f), fontSize = 12.sp)

                            Row(
                                modifier = Modifier.weight(1.3f),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        isEditing = true
                                        selectedItemId = item.id
                                        pti = item.pti
                                        pcsQty = item.pcsQty
                                        pcsQtyWt = item.weight
                                        subTotalKg = item.subTotal
                                        description = item.description
                                        customer = item.customer
                                        noPag = item.noPag
                                    },
                                    contentPadding = PaddingValues(2.dp)
                                ) {
                                    Text("Edit", color = Color(0xFF6750A4), fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                TextButton(
                                    onClick = {
                                        itemToDelete = item
                                        showDeleteDialog = true
                                    },
                                    contentPadding = PaddingValues(2.dp)
                                ) {
                                    Text("Hapus", color = Color.Red, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog && itemToDelete != null) {
        val targetItem = itemToDelete!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; itemToDelete = null },
            title = { Text("Konfirmasi Hapus") },
            text = { Text("Apakah Anda yakin ingin menghapus data ${targetItem.pti}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCargo(targetItem)
                    showDeleteDialog = false
                    itemToDelete = null
                    Toast.makeText(context, "Data berhasil dihapus", Toast.LENGTH_SHORT).show()
                }) { Text("Hapus", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; itemToDelete = null }) { Text("Batal") }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Konfirmasi Hapus Semua") },
            text = { Text("Yakin ingin menghapus seluruh data manifest?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllCargo()
                    showClearAllDialog = false
                    Toast.makeText(context, "Semua data dibersihkan", Toast.LENGTH_SHORT).show()
                }) { Text("Hapus Semua", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Batal") }
            }
        )
    }
}

// Fungsi Export Excel versi stabil awal yang berfungsi normal
fun exportToExcel(context: Context, list: List<CargoItem>, awbNo: String, flightNo: String) {
    if (list.isEmpty()) {
        Toast.makeText(context, "Tidak ada data untuk diexport!", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Cargo Manifest")

        var rowIndex = 0
        sheet.createRow(rowIndex++).createCell(0).setCellValue("AWB No: $awbNo")
        sheet.createRow(rowIndex++).createCell(0).setCellValue("Flight No: $flightNo")
        rowIndex++ // Baris kosong

        // Header Kolom Tabel
        val headerRow = sheet.createRow(rowIndex++)
        headerRow.createCell(0).setCellValue("No")
        headerRow.createCell(1).setCellValue("PTI")
        headerRow.createCell(2).setCellValue("Pcs/Qty")
        headerRow.createCell(3).setCellValue("Weight")
        headerRow.createCell(4).setCellValue("Sub Total")
        headerRow.createCell(5).setCellValue("Description")
        headerRow.createCell(6).setCellValue("Customer")
        headerRow.createCell(7).setCellValue("No PAG")

        // Masukkan Data List Kargo
        list.forEachIndexed { index, item ->
            val row = sheet.createRow(rowIndex++)
            row.createCell(0).setCellValue((index + 1).toDouble())
            row.createCell(1).setCellValue(item.pti)
            row.createCell(2).setCellValue(item.pcsQty)
            row.createCell(3).setCellValue(item.weight)
            row.createCell(4).setCellValue(item.subTotal)
            row.createCell(5).setCellValue(item.description)
            row.createCell(6).setCellValue(item.customer)
            row.createCell(7).setCellValue(item.noPag)
        }

        val fileName = "CargoManifest_${System.currentTimeMillis()}.xlsx"
        var fos: OutputStream? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            if (uri != null) {
                fos = context.contentResolver.openOutputStream(uri)
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(downloadsDir, fileName)
            fos = java.io.FileOutputStream(file)
        }

        fos?.use {
            workbook.write(it)
            workbook.close()
            Toast.makeText(context, "Berhasil Export ke folder Download!", Toast.LENGTH_LONG).show()
        } ?: run {
            Toast.makeText(context, "Gagal menyimpan file Excel", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

