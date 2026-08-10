package com.example.cargomanifestapp

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

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

    // Field Form Input
    var noPag by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var inputKg by remember { mutableStateOf("") }

    // List simpanan CargoItem utama
    val cargoList = remember { mutableStateListOf<CargoItem>() }

    // List rincian KG sementara untuk customer/PAG yang sedang diisi
    val currentKgEntries = remember { mutableStateListOf<Double>() }

    // Hitung Total KG Real-time
    val currentTotalKg = currentKgEntries.sum()

    // Fungsi Menambah Angka KG ke Daftar Sementara
    fun addKgEntry() {
        val kgVal = inputKg.toDoubleOrNull()
        if (kgVal != null && kgVal > 0) {
            currentKgEntries.add(kgVal)
            inputKg = ""
        } else {
            Toast.makeText(context, "Masukkan angka KG yang valid", Toast.LENGTH_SHORT).show()
        }
    }

    // Fungsi Menyimpan Input Form ke List CargoItem
    fun saveCargoItem() {
        if (noPag.isBlank() || customer.isBlank()) {
            Toast.makeText(context, "Mohon isi NO PAG dan Customer", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentKgEntries.isEmpty()) {
            Toast.makeText(context, "Masukkan minimal 1 nilai KG", Toast.LENGTH_SHORT).show()
            return
        }

        // Format Rincian KG menjadi String (Contoh: "10, 10, 48, 64")
        val formattedWeightList = currentKgEntries.joinToString(", ") {
            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
        }

        // Format Total KG
        val formattedTotalKg = if (currentTotalKg % 1.0 == 0.0) {
            currentTotalKg.toInt().toString()
        } else {
            currentTotalKg.toString()
        }

        // Mapping Data ke Entity CargoItem Anda
        val newCargoItem = CargoItem(
            noPag = if (noPag.startsWith("PAG")) noPag else "PAG $noPag",
            customer = customer.uppercase(),
            pcsQty = currentKgEntries.size.toString(), // Jumlah koli
            weight = formattedWeightList,              // Rincian KG
            subTotal = formattedTotalKg                // Total KG
        )

        cargoList.add(0, newCargoItem)

        // Reset Input Form
        inputKg = ""
        currentKgEntries.clear()
        Toast.makeText(context, "Data berhasil disimpan!", Toast.LENGTH_SHORT).show()
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

            // Tombol Export Excel Template
            IconButton(onClick = { exportToExcelTemplate(context, cargoList) }) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Export Excel",
                    tint = Color(0xFF2E7D32)
                )
            }
        }

        // --- CARD FORM INPUT ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Input PAG, Customer & KG",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF381E72)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Row NO PAG & Customer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = noPag,
                        onValueChange = { noPag = it.uppercase() },
                        label = { Text("NO PAG") },
                        placeholder = { Text("0288") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customer,
                        onValueChange = { customer = it.uppercase() },
                        label = { Text("Customer") },
                        placeholder = { Text("AULIA") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input Berat (KG) & Tombol Tambah
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

                // Grid Rincian Nilai KG
                if (currentKgEntries.isNotEmpty()) {
                    Text(
                        text = "Rincian Input KG (${currentKgEntries.size} Koli):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(currentKgEntries.indices.toList()) { index ->
                            val itemVal = currentKgEntries[index]
                            Box(
                                modifier = Modifier
                                    .background(
                                        Color(0xFFE8DEF8),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (itemVal % 1.0 == 0.0) "${itemVal.toInt()}" else "$itemVal",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { currentKgEntries.removeAt(index) },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Hapus",
                                            tint = Color.Red
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card Tampilan Total KG Real-time
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

                // Tombol Simpan
                Button(
                    onClick = { saveCargoItem() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Simpan ke Cargo Table", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- DAFTAR CARGOITEM TERSEMPAN ---
        Text(
            text = "Daftar Stowing Tersimpan (${cargoList.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color(0xFF381E72)
        )
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cargoList) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.noPag} | Customer: ${item.customer}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF381E72)
                            )
                            IconButton(onClick = { cargoList.remove(item) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Hapus",
                                    tint = Color(0xFFB3261E)
                                )
                            }
                        }

                        Text(
                            text = "Jumlah Koli: ${item.pcsQty}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Rincian KG: ${item.weight}",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "TOTAL: ${item.subTotal} KG",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
        }
    }
}

// --- FUNGSI EXPORT DATA KE TEMPLATE EXCEL ---
fun exportToExcelTemplate(context: Context, cargoList: List<CargoItem>) {
    if (cargoList.isEmpty()) {
        Toast.makeText(context, "Tidak ada data untuk di-export", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        // 1. Ambil template Excel dari folder assets
        val inputStream: InputStream = context.assets.open("STOWINGAN_PAG_TEMPLATE.xlsx")
        val workbook = XSSFWorkbook(inputStream)
        val sheet = workbook.getSheetAt(0) // Menggunakan Sheet Pertama

        var startRow = 4 // Sesuaikan dengan baris awal data di Excel Template Anda

        // 2. Tulis Data CargoItem ke Cell Excel
        for ((index, item) in cargoList.withIndex()) {
            val row = sheet.getRow(startRow) ?: sheet.createRow(startRow)

            // Mengisi sel berdasarkan kolom Excel
            row.createCell(0).setCellValue((index + 1).toDouble()) // Kolom No
            row.createCell(1).setCellValue(item.noPag)            // Kolom NO PAG
            row.createCell(2).setCellValue(item.customer)         // Kolom Customer
            row.createCell(3).setCellValue(item.weight)           // Kolom Rincian KG
            row.createCell(4).setCellValue(item.subTotal.toDoubleOrNull() ?: 0.0) // Kolom Total KG

            startRow++
        }

        inputStream.close()

        // 3. Simpan File Baru Hasil Output
        val outFile = File(
            context.getExternalFilesDir(null),
            "Stowing_Report_${System.currentTimeMillis()}.xlsx"
        )
        val outputStream = FileOutputStream(outFile)
        workbook.write(outputStream)
        outputStream.close()
        workbook.close()

        Toast.makeText(context, "Export Berhasil: ${outFile.name}", Toast.LENGTH_LONG).show()

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Gagal Export: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
