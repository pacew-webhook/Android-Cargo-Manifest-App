package com.example.cargomanifestapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Import FlowRow dari foundation.layout
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

// Extension Function: Menghilangkan .0 pada angka bulat (contoh: 50.0 -> "50", 50.5 -> "50.5")
fun Double.toCleanString(): String {
    return if (this % 1.0 == 0.0) {
        this.toLong().toString()
    } else {
        DecimalFormat("#.##").format(this)
    }
}

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BtbScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val currentDateStr = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }

    var customerName by remember { mutableStateOf("") }
    var trademarks by remember { mutableStateOf("") }
    var jenisBarang by remember { mutableStateOf("") }
    var inputBeratText by remember { mutableStateOf("") }
    var daftarTimbangan by remember { mutableStateOf<List<Double>>(emptyList()) }
    // Disiapkan untuk foto BTB/galeri. Tetap kosong jika fitur foto belum digunakan.
    val photoUris = remember { mutableStateListOf<Uri>() }

    val savedBtbList = remember { mutableStateListOf<BtbFormData>() }
    var editingId by remember { mutableStateOf<String?>(null) }

    // Deklarasikan FocusRequester sebelum launcher karena callback launcher dapat
    // merujuknya saat hasil OCR dikembalikan.
    val customerFocus = remember { FocusRequester() }
    val trademarkFocus = remember { FocusRequester() }
    val barangFocus = remember { FocusRequester() }
    val beratFocus = remember { FocusRequester() }

    val scaleOcrLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val weight = result.data?.getDoubleExtra(ScaleOcrActivity.EXTRA_WEIGHT, Double.NaN) ?: Double.NaN
            if (!weight.isNaN() && weight > 0.0) {
                inputBeratText = weight.toCleanString()
                beratFocus.requestFocus()
            }
        }
    }

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

    fun tambahBerat() {
        val weight = inputBeratText.toDoubleOrNull()
        if (weight != null && weight > 0) {
            daftarTimbangan = daftarTimbangan + weight
            inputBeratText = ""
            beratFocus.requestFocus()
        } else {
            Toast.makeText(context, "Masukkan berat angka yang valid", Toast.LENGTH_SHORT).show()
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
                    IconButton(onClick = { resetForm() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Reset Form", tint = Color.Red)
                    }
                    IconButton(onClick = {
                        val activeData = BtbFormData(
                            hariTanggal = currentDateStr,
                            customerName = customerName,
                            trademarks = trademarks,
                            jenisBarang = jenisBarang,
                            daftarTimbangan = daftarTimbangan
                        )

                        if (daftarTimbangan.isNotEmpty()) {
                            exportAndShare(activeData)
                        } else if (savedBtbList.isNotEmpty()) {
                            exportAndShare(savedBtbList.last())
                        } else {
                            Toast.makeText(context, "Isi form dan tambahkan berat timbangan dulu!", Toast.LENGTH_SHORT).show()
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
                                color = Color(0xFF4A148C)
                            )
                            Text("Tgl: $currentDateStr", fontSize = 12.sp, color = Color.Gray)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { customerName = it.uppercase() },
                                label = { Text("Customer") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                modifier = Modifier.weight(1f).focusRequester(customerFocus)
                            )
                            OutlinedTextField(
                                value = trademarks,
                                onValueChange = { trademarks = it.uppercase() },
                                label = { Text("Trademarks") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                modifier = Modifier.weight(1f).focusRequester(trademarkFocus)
                            )
                        }

                        OutlinedTextField(
                            value = jenisBarang,
                            onValueChange = { jenisBarang = it.uppercase() },
                            label = { Text("Jenis Barang") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                            modifier = Modifier.fillMaxWidth().focusRequester(barangFocus)
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
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { tambahBerat() }),
                                modifier = Modifier.weight(1f).focusRequester(beratFocus)
                            )
                            Button(
                                onClick = { tambahBerat() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF311B92)),
                                modifier = Modifier.height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("+ KG", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    scaleOcrLauncher.launch(Intent(context, ScaleOcrActivity::class.java))
                                },
                                modifier = Modifier.height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("📷", fontSize = 20.sp)
                            }
                        }

                        if (daftarTimbangan.isNotEmpty()) {
                            Text(
                                "Rincian Timbangan (${daftarTimbangan.size} Koli) | Total: ${daftarTimbangan.sum().toCleanString()} KG",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )

                            // Tampilan Chip Timbangan Otomatis Pindah Baris
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                daftarTimbangan.forEachIndexed { index, weight ->
                                    SuggestionChip(
                                        onClick = {
                                            daftarTimbangan = daftarTimbangan.toMutableList().apply { removeAt(index) }
                                        },
                                        label = { Text(weight.toCleanString()) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFFEDE7F6))
                                    )
                                }
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
                                customerFocus.requestFocus()
                                Toast.makeText(context, "Data BTB Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (editingId == null) Color(0xFF311B92) else Color(0xFF00695C)
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(if (editingId == null) "Simpan BTB" else "Update BTB", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Text("Daftar BTB Tersimpan (${savedBtbList.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            items(savedBtbList) { btb ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${btb.customerName} (${btb.trademarks.ifEmpty { "-" }})", fontWeight = FontWeight.Bold)
                            Text("Tgl: ${btb.hariTanggal} | Barang: ${btb.jenisBarang.ifEmpty { "-" }}", fontSize = 12.sp, color = Color.Gray)
                            Text("Koli: ${btb.jumlahKoli} | Total: ${btb.totalBerat.toCleanString()} KG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                        Row {
                            IconButton(onClick = {
                                customerName = btb.customerName
                                trademarks = btb.trademarks
                                jenisBarang = btb.jenisBarang
                                daftarTimbangan = btb.daftarTimbangan
                                editingId = btb.id
                                customerFocus.requestFocus()
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF1976D2))
                            }
                            IconButton(onClick = {
                                savedBtbList.remove(btb)
                                if (editingId == btb.id) resetForm()
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
