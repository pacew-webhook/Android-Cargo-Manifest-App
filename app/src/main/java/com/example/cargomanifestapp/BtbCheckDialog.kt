package com.example.cargomanifestapp

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import kotlin.math.floor

private data class BtbCheckSource(
    val customer: String,
    val jenisBarang: String,
    val weights: List<Double>
)

private fun checkNormalize(text: String): String =
    text.trim().replace(Regex("\\s+"), " ").lowercase()

private fun checkRoundWeight(weight: Double): Int = floor(weight + 0.5).toInt()

private fun checkFormatKg(weight: Double): String =
    if (weight % 1.0 == 0.0) weight.toInt().toString() else weight.toString()

private fun loadBtbCheckSources(context: Context): List<BtbCheckSource> {
    // SUMBER DATA CEK = Stowing Cargo yang sudah tersimpan.
    // Foto BTB tidak dibaca oleh fitur ini.
    val raw = context.getSharedPreferences("stowing_prefs", Context.MODE_PRIVATE)
        .getString("saved_cargo_list", "[]") ?: "[]"

    val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }

    // Semua Stowing dengan Customer + Jenis Barang yang sama digabung.
    // Urutan KG mengikuti urutan data Stowing Cargo yang tersimpan.
    val grouped = linkedMapOf<String, MutableList<Double>>()
    val labels = linkedMapOf<String, Pair<String, String>>()

    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val customer = obj.optString("customer").trim()
        val jenis = obj.optString("description").trim()
        if (customer.isBlank()) continue

        val key = "${checkNormalize(customer)}|${checkNormalize(jenis)}"
        val target = grouped.getOrPut(key) { mutableListOf() }
        labels.putIfAbsent(key, customer to jenis)

        // Weight Stowing disimpan seperti: "7, 24, 21, 19, 25"
        val weightText = obj.optString("weight").trim()
        weightText.split(",", ";", "\n")
            .mapNotNull { it.trim().replace(",", ".").toDoubleOrNull() }
            .filter { it.isFinite() && it > 0.0 }
            .forEach { target.add(it) }
    }

    return grouped.map { (key, weights) ->
        val label = labels[key] ?: ("" to "")
        BtbCheckSource(label.first, label.second, weights)
    }.filter { it.weights.isNotEmpty() }
}

@Composable
fun BtbCheckDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val sources = remember { loadBtbCheckSources(context) }

    var selectedCustomer by remember { mutableStateOf(sources.firstOrNull()?.customer.orEmpty()) }
    var selectedJenis by remember { mutableStateOf(sources.firstOrNull()?.jenisBarang.orEmpty()) }
    var customerMenu by remember { mutableStateOf(false) }
    var jenisMenu by remember { mutableStateOf(false) }

    val customerOptions = remember(sources) {
        sources.map { it.customer }.distinctBy { checkNormalize(it) }
    }

    val jenisOptions = remember(sources, selectedCustomer) {
        sources.filter {
            checkNormalize(it.customer) == checkNormalize(selectedCustomer)
        }.map { it.jenisBarang }.distinctBy { checkNormalize(it) }
    }

    LaunchedEffect(selectedCustomer) {
        if (jenisOptions.none { checkNormalize(it) == checkNormalize(selectedJenis) }) {
            selectedJenis = jenisOptions.firstOrNull().orEmpty()
        }
    }

    val source = sources.firstOrNull {
        checkNormalize(it.customer) == checkNormalize(selectedCustomer) &&
            checkNormalize(it.jenisBarang) == checkNormalize(selectedJenis)
    }

    val expected = remember(source) {
        source?.weights?.map(::checkRoundWeight).orEmpty()
    }

    var accepted by remember(source) { mutableStateOf<List<Int>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var successText by remember { mutableStateOf<String?>(null) }

    fun checkInput() {
        val value = input.replace(",", ".").toDoubleOrNull()
        if (value == null || !value.isFinite() || value <= 0.0) {
            errorText = "Masukkan KG yang valid."
            successText = null
            return
        }

        if (expected.isEmpty()) {
            errorText = "Data BTB untuk Customer/Jenis Barang ini belum ada."
            successText = null
            return
        }

        val position = accepted.size
        if (position >= expected.size) {
            errorText = "Semua ${expected.size} Koli sudah selesai dicek."
            successText = null
            input = ""
            return
        }

        val actual = checkRoundWeight(value)
        val target = expected[position]

        if (actual == target) {
            accepted = accepted + actual
            input = ""
            errorText = null
            successText = "✓ BENAR — $actual KG"
        } else {
            // Nilai SALAH tidak pernah dimasukkan ke daftar accepted.
            errorText = "✗ SALAH. Berikutnya harus $target KG. $actual KG tidak dimasukkan."
            successText = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Cek Data BTB", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 650.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Data KG diambil dari Stowing Cargo yang sudah disimpan. Foto BTB hanya menjadi acuan. Input harus mengikuti urutan KG Stowing.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(Modifier.height(10.dp))

                // Customer
                Box {
                    OutlinedButton(
                        onClick = { customerMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (selectedCustomer.isBlank()) "Pilih Customer"
                            else selectedCustomer,
                            modifier = Modifier.weight(1f)
                        )
                        Text("▼")
                    }
                    DropdownMenu(
                        expanded = customerMenu,
                        onDismissRequest = { customerMenu = false }
                    ) {
                        customerOptions.forEach { customer ->
                            DropdownMenuItem(
                                text = { Text(customer) },
                                onClick = {
                                    selectedCustomer = customer
                                    customerMenu = false
                                    accepted = emptyList()
                                    input = ""
                                    errorText = null
                                    successText = null
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Jenis Barang
                Box {
                    OutlinedButton(
                        onClick = { jenisMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = jenisOptions.isNotEmpty()
                    ) {
                        Text(
                            if (selectedJenis.isBlank()) "Pilih Jenis Barang"
                            else selectedJenis,
                            modifier = Modifier.weight(1f)
                        )
                        Text("▼")
                    }
                    DropdownMenu(
                        expanded = jenisMenu,
                        onDismissRequest = { jenisMenu = false }
                    ) {
                        jenisOptions.forEach { jenis ->
                            DropdownMenuItem(
                                text = { Text(jenis.ifBlank { "Tanpa Jenis Barang" }) },
                                onClick = {
                                    selectedJenis = jenis
                                    jenisMenu = false
                                    accepted = emptyList()
                                    input = ""
                                    errorText = null
                                    successText = null
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (expected.isEmpty()) {
                    Surface(
                        color = Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Data Stowing tidak ditemukan untuk pilihan ini.",
                            modifier = Modifier.padding(10.dp),
                            color = Color(0xFFB00020),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        "KG STOWING: ${accepted.size}/${expected.size} KOLI",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    // Semua posisi urutan ditampilkan:
                    // hijau = sudah benar, kuning = posisi berikutnya,
                    // abu-abu = belum dicek.
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(expected) { index, kg ->
                            val isAccepted = index < accepted.size
                            val isNext = index == accepted.size

                            val bg = when {
                                isAccepted -> Color(0xFFC8E6C9)
                                isNext -> Color(0xFFFFF3CD)
                                else -> Color(0xFFEDE7F6)
                            }
                            val fg = when {
                                isAccepted -> Color(0xFF1B5E20)
                                isNext -> Color(0xFF8A5A00)
                                else -> Color(0xFF49454F)
                            }

                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(bg, RoundedCornerShape(6.dp))
                                    .padding(vertical = 7.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isAccepted) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Benar",
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(2.dp))
                                    }
                                    Text(
                                        "$kg KG",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = fg
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            errorText = null
                            successText = null
                        },
                        label = { Text("Input Berat (KG)") },
                        placeholder = {
                            Text(
                                if (accepted.size < expected.size)
                                    "Berikutnya: ${expected[accepted.size]} KG"
                                else
                                    "Selesai"
                            )
                        },
                        singleLine = true,
                        isError = errorText != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { checkInput() }),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(6.dp))

                    Button(
                        onClick = { checkInput() },
                        enabled = expected.isNotEmpty() && accepted.size < expected.size,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF381E72)
                        )
                    ) {
                        Text("CEK + INPUT", fontWeight = FontWeight.Bold)
                    }

                    successText?.let {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    it,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    errorText?.let {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color(0xFFB00020),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    it,
                                    color = Color(0xFFB00020),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (accepted.isNotEmpty()) {
                        Text(
                            "KG YANG SUDAH DIINPUT (${accepted.size} KOLI)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 120.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(accepted) { _, kg ->
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Color(0xFFC8E6C9),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        checkFormatKg(kg.toDouble()),
                                        color = Color(0xFF1B5E20),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Color(0xFF381E72),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("TOTAL INPUT", color = Color.White, fontSize = 11.sp)
                            Text(
                                "${checkFormatKg(accepted.sum().toDouble())} KG",
                                color = Color.Yellow,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                "${accepted.size} KOLI",
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("TOTAL STOWING", color = Color.White, fontSize = 11.sp)
                            Text(
                                "${checkFormatKg(expected.sum().toDouble())} KG",
                                color = Color.Yellow,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Sisa ${(expected.size - accepted.size).coerceAtLeast(0)} Koli",
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (accepted.size == expected.size) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFFC8E6C9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "✓ SEMUA DATA BTB SUDAH COCOK",
                                modifier = Modifier.padding(10.dp),
                                color = Color(0xFF1B5E20),
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}
