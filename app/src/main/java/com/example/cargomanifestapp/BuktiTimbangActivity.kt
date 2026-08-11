package com.example.cargomanifestapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BuktiTimbangActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BtbScreen(onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BtbScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val currentDateStr = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }

    var customerName by remember { mutableStateOf("") }
    var trademarks by remember { mutableStateOf("") }
    var jenisBarang by remember { mutableStateOf("") }
    var inputBeratText by remember { mutableStateOf("") }
    var daftarTimbangan by remember { mutableStateOf<List<Double>>(emptyList()) }

    val savedBtbList = remember { mutableStateListOf<BtbFormData>() }
    var editingId by remember { mutableStateOf<String?>(null) }

    fun resetForm() {
        customerName = ""
        trademarks = ""
        jenisBarang = ""
        inputBeratText = ""
        daftarTimbangan = emptyList()
        editingId = null
    }

    fun exportAndShare(btbData: BtbFormData) {
        try {
            val cacheFile = File(context.cacheDir, "BTB_${btbData.customerName.ifEmpty { "Export" }}.xlsx")
            val templateInputStream = context.assets.open("Bukti_Timbang_Barang_BTB.xlsx")
            
            FileOutputStream(cacheFile).use { fos ->
                val tempUri = Uri.fromFile(cacheFile)
                BtbExcelWriter.fillBtbTemplate(context, templateInputStream, tempUri, btbData)
            }

            val fileUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                cacheFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan BTB via..."))
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal mengekspor: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bukti Timbang Barang", color = Color(0xFF4A148C), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color(0xFF4A148C))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val activeData = BtbFormData(
                            hariTanggal = currentDateStr,
                            customerName = customerName,
                            trademarks = trademarks,
                            jenisBarang = jenisBarang,
                            daftarTimbangan = daftarTimbangan
                        )

                        if (savedBtbList.isNotEmpty()) {
                            exportAndShare(savedBtbList.last())
                        } else if (daftarTimbangan.isNotEmpty()) {
                            exportAndShare(activeData)
                        } else {
                            Toast.makeText(context, "Isi form atau tambahkan data timbangan terlebih dahulu!", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF2E7D32))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (editingId == null) "Input Form BTB" else "Edit Form BTB",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4A148C),
                                fontSize = 16.sp
                            )
                            Text("Tgl: $currentDateStr", fontSize = 12.sp, color = Color.Gray)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { customerName = it },
                                label = { Text("Customer") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = trademarks,
                                onValueChange = { trademarks = it },
                                label = { Text("Trademarks") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = jenisBarang,
                            onValueChange = { jenisBarang = it },
                            label = { Text("Jenis Barang") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputBeratText,
                                onValueChange = { inputBeratText = it },
                                label = { Text("Input Berat (KG)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val weight = inputBeratText.toDoubleOrNull()
                                    if (weight != null && weight > 0) {
                                        daftarTimbangan = daftarTimbangan + weight
                                        inputBeratText = ""
                                    } else {
                                        Toast.makeText(context, "Masukkan berat angka valid", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF311B92))
                            ) {
                                Text("+ KG")
                            }
                        }

                        if (daftarTimbangan.isNotEmpty()) {
                            Text(
                                "Rincian Timbangan (${daftarTimbangan.size} Koli) | Total: ${daftarTimbangan.sum()} KG",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )

                            FlowRowLayout(items = daftarTimbangan) { index, weight ->
                                InputChip(
                                    selected = false,
                                    onClick = {
                                        daftarTimbangan = daftarTimbangan.toMutableList().apply { removeAt(index) }
                                    },
                                    label = { Text("$weight") },
                                    trailingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (customerName.isEmpty() || daftarTimbangan.isEmpty()) {
                                    Toast.makeText(context, "Customer dan Timbangan wajib diisi!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                val formData = BtbFormData(
                                    id = editingId ?: System.currentTimeMillis().toString(),
                                    hariTanggal = currentDateStr,
                                    customerName = customerName,
                                    trademarks = trademarks,
                                    jenisBarang = jenisBarang,
                                    daftarTimbangan = daftarTimbangan
                                )

                                if (editingId != null) {
                                    val idx = savedBtbList.indexOfFirst { it.id == editingId }
                                    if (idx != -1) savedBtbList[idx] = formData
                                } else {
                                    savedBtbList.add(formData)
                                }

                                resetForm()
                                Toast.makeText(context, "BTB Berhasil Disimpan", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (editingId == null) Color(0xFF311B92) else Color(0xFF00695C)
                            )
                        ) {
                            Text(if (editingId == null) "Simpan BTB" else "Update BTB")
                        }
                    }
                }
            }

            item {
                Text(
                    "Daftar BTB Tersimpan (${savedBtbList.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            items(savedBtbList) { btb ->
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
                            Text("${btb.customerName} (${btb.trademarks})", fontWeight = FontWeight.Bold)
                            Text("Tgl: ${btb.hariTanggal} | Barang: ${btb.jenisBarang}", fontSize = 12.sp, color = Color.Gray)
                            Text("Koli: ${btb.jumlahKoli} | Total: ${btb.totalBerat} KG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                        Row {
                            IconButton(onClick = {
                                customerName = btb.customerName
                                trademarks = btb.trademarks
                                jenisBarang = btb.jenisBarang
                                daftarTimbangan = btb.daftarTimbangan
                                editingId = btb.id
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF1976D2))
                            }
                            IconButton(onClick = { savedBtbList.remove(btb) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFD32F2F))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlowRowLayout(items: List<Double>, onItemClick: (Int, Double) -> Unit) {
    Column {
        items.chunked(4).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEachIndexed { colIndex, weight ->
                    val globalIndex = items.indexOf(weight)
                    onItemClick(globalIndex, weight)
                }
            }
        }
    }
}
