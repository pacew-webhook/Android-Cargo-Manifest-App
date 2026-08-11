package com.example.cargomanifestapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuktiTimbangScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Mendapatkan tanggal hari ini secara otomatis (format: dd/MM/yyyy)
    val todayDate = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    }

    // State Input Form
    var customer by remember { mutableStateOf("") }
    var trademarks by remember { mutableStateOf("") }
    var jenisBarang by remember { mutableStateOf("") }
    var inputBerat by remember { mutableStateOf("") }

    // Rincian Timbangan (Koli)
    val rincianBerat = remember { mutableStateListOf<Double>() }
    var selectedBtbId by remember { mutableStateOf<Long?>(null) }

    // List Simpan Data Local
    val btbList = remember { mutableStateListOf<BtbModel>() }

    // Focus Requesters untuk navigasi Enter
    val customerFocusRequester = remember { FocusRequester() }
    val trademarksFocusRequester = remember { FocusRequester() }
    val jenisBarangFocusRequester = remember { FocusRequester() }
    val beratFocusRequester = remember { FocusRequester() }

    val nextKeyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Characters,
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Next
    )

    val nextKeyboardActions = KeyboardActions(
        onNext = { focusManager.moveFocus(FocusDirection.Next) }
    )

    fun clearForm() {
        customer = ""
        trademarks = ""
        jenisBarang = ""
        inputBerat = ""
        rincianBerat.clear()
        selectedBtbId = null
    }

    fun tambahBerat() {
        val berat = inputBerat.toDoubleOrNull()
        if (berat != null && berat > 0) {
            rincianBerat.add(berat)
            inputBerat = ""
            beratFocusRequester.requestFocus()
        } else {
            Toast.makeText(context, "Masukkan berat yang valid!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Bar Header
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
                        tint = Color(0xFF4A148C)
                    )
                }
                Text(
                    text = "Bukti Timbang Barang",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A148C)
                )
            }
            Row {
                IconButton(onClick = { clearForm() }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Reset Form",
                        tint = Color.Red
                    )
                }
                // Tombol Share Data BTB
                IconButton(onClick = {
                    if (btbList.isNotEmpty()) {
                        val shareText = StringBuilder().apply {
                            append("=== BUKTI TIMBANG BARANG ===\n")
                            append("Tanggal: $todayDate\n\n")
                            btbList.forEachIndexed { index, btb ->
                                append("${index + 1}. Customer: ${btb.customer}\n")
                                if (btb.trademarks.isNotBlank()) append("   Trademarks: ${btb.trademarks}\n")
                                if (btb.jenisBarang.isNotBlank()) append("   Barang: ${btb.jenisBarang}\n")
                                append("   Rincian: ${btb.rincianBerat.joinToString(", ")} KG\n")
                                append("   Total: ${btb.rincianBerat.size} Koli | ${btb.totalBerat} KG\n\n")
                            }
                        }.toString()

                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Bukti Timbang Barang")
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Bagikan BTB via"))
                    } else {
                        Toast.makeText(context, "Belum ada data BTB untuk dibagikan!", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color(0xFF2E7D32)
                    )
                }
            }
        }

        // Card Form Input
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedBtbId == null) "Input Form BTB" else "Edit Form BTB",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4A148C)
                    )
                    Text(
                        text = "Tgl: $todayDate",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Customer & Trademarks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customer,
                        onValueChange = { customer = it.uppercase() },
                        label = { Text("Customer") },
                        keyboardOptions = nextKeyboardOptions,
                        keyboardActions = nextKeyboardActions,
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(customerFocusRequester)
                    )
                    OutlinedTextField(
                        value = trademarks,
                        onValueChange = { trademarks = it.uppercase() },
                        label = { Text("Trademarks") },
                        keyboardOptions = nextKeyboardOptions,
                        keyboardActions = nextKeyboardActions,
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(trademarksFocusRequester)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Jenis Barang
                OutlinedTextField(
                    value = jenisBarang,
                    onValueChange = { jenisBarang = it.uppercase() },
                    label = { Text("Jenis Barang") },
                    keyboardOptions = nextKeyboardOptions,
                    keyboardActions = nextKeyboardActions,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(jenisBarangFocusRequester)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Input Berat (KG) + Tombol Tambah
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputBerat,
                        onValueChange = { inputBerat = it },
                        label = { Text("Input Berat (KG)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { tambahBerat() }
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(beratFocusRequester)
                    )

                    Button(
                        onClick = { tambahBerat() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF311B92)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("+ KG", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Rincian Timbangan Badge
                val totalBerat = rincianBerat.sum()
                Text(
                    text = "Rincian Timbangan (${rincianBerat.size} Koli) | Total: $totalBerat KG",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rincianBerat.forEachIndexed { index, weight ->
                        SuggestionChip(
                            onClick = { rincianBerat.removeAt(index) },
                            label = { Text("${if (weight % 1.0 == 0.0) weight.toInt() else weight}") },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFFEDE7F6)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tombol Simpan / Update
                Button(
                    onClick = {
                        if (customer.isNotBlank() && rincianBerat.isNotEmpty()) {
                            if (selectedBtbId == null) {
                                btbList.add(
                                    BtbModel(
                                        id = System.currentTimeMillis(),
                                        tanggal = todayDate,
                                        customer = customer,
                                        trademarks = trademarks,
                                        jenisBarang = jenisBarang,
                                        rincianBerat = ArrayList(rincianBerat),
                                        totalBerat = totalBerat
                                    )
                                )
                                Toast.makeText(context, "Data BTB Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
                            } else {
                                val index = btbList.indexOfFirst { it.id == selectedBtbId }
                                if (index != -1) {
                                    btbList[index] = BtbModel(
                                        id = selectedBtbId!!,
                                        tanggal = todayDate,
                                        customer = customer,
                                        trademarks = trademarks,
                                        jenisBarang = jenisBarang,
                                        rincianBerat = ArrayList(rincianBerat),
                                        totalBerat = totalBerat
                                    )
                                    Toast.makeText(context, "Data BTB Berhasil Diperbarui!", Toast.LENGTH_SHORT).show()
                                }
                            }
                            clearForm()
                            customerFocusRequester.requestFocus()
                        } else {
                            Toast.makeText(context, "Lengkapi Customer dan Rincian Berat!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedBtbId == null) Color(0xFF311B92) else Color(0xFF006A60)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = if (selectedBtbId == null) "Simpan BTB" else "Update BTB",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Daftar BTB Tersimpan
        Text(
            text = "Daftar BTB Tersimpan (${btbList.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(btbList) { btb ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${btb.customer} (${btb.trademarks.ifEmpty { "-" }})",
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = "Tgl: ${btb.tanggal} | Barang: ${btb.jenisBarang.ifEmpty { "-" }}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = "Koli: ${btb.rincianBerat.size} | Total: ${btb.totalBerat} KG",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }

                        Row {
                            IconButton(onClick = {
                                selectedBtbId = btb.id
                                customer = btb.customer
                                trademarks = btb.trademarks
                                jenisBarang = btb.jenisBarang
                                rincianBerat.clear()
                                rincianBerat.addAll(btb.rincianBerat)
                                customerFocusRequester.requestFocus()
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF1976D2))
                            }
                            IconButton(onClick = {
                                btbList.remove(btb)
                                if (selectedBtbId == btb.id) clearForm()
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

// Data Model
data class BtbModel(
    val id: Long,
    val tanggal: String,
    val customer: String,
    val trademarks: String,
    val jenisBarang: String,
    val rincianBerat: List<Double>,
    val totalBerat: Double
)
