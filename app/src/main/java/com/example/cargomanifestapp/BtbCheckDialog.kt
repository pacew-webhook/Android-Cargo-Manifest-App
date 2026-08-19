package com.example.cargomanifestapp

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import kotlin.math.floor

/**
 * Sumber pengecekan BUKAN data/foto BTB.
 *
 * Data diambil dari hasil yang sudah disimpan oleh Form Stowing Cargo
 * pada SharedPreferences "stowing_prefs" -> "saved_cargo_list".
 *
 * Setiap CargoItem menyimpan daftar KG dalam field weight, misalnya:
 * "7, 24, 21, 19, 25"
 *
 * Semua CargoItem dengan Customer + Description yang sama digabung
 * mengikuti urutan data Stowing Cargo.
 */
private data class StowingCheckSource(
    val customer: String,
    val jenisBarang: String,
    val weights: List<Double>
)

private fun checkNormalize(text: String): String =
    text.trim().replace(Regex("\\s+"), " ").lowercase()

private fun checkRoundWeight(weight: Double): Int = floor(weight + 0.5).toInt()

private fun loadStowingCheckSources(context: Context): List<StowingCheckSource> {
    val raw = context.getSharedPreferences("stowing_prefs", Context.MODE_PRIVATE)
        .getString("saved_cargo_list", "[]") ?: "[]"

    val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    val grouped = linkedMapOf<String, MutableList<Double>>()
    val labels = linkedMapOf<String, Pair<String, String>>()

    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val customer = obj.optString("customer").trim()
        val jenis = obj.optString("description").trim()
        if (customer.isBlank() || jenis.isBlank()) continue

        val key = "${checkNormalize(customer)}|${checkNormalize(jenis)}"
        val target = grouped.getOrPut(key) { mutableListOf() }
        labels.putIfAbsent(key, customer to jenis)

        // Form Stowing menyimpan weight sebagai daftar KG: "7, 24, 21..."
        val weightText = obj.optString("weight")
        weightText.split(",", ";", "\n")
            .map { it.trim().replace(",", ".") }
            .forEach { token ->
                val value = token.toDoubleOrNull()
                if (value != null && value.isFinite() && value > 0.0) {
                    target.add(value)
                }
            }
    }

    return grouped.map { (key, weights) ->
        val label = labels[key] ?: ("" to "")
        StowingCheckSource(label.first, label.second, weights)
    }.filter { it.weights.isNotEmpty() }
}

@Composable
fun BtbCheckDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    // Setiap kali dialog dibuka, data dibaca ulang dari Form Stowing Cargo.
    val sources = remember { loadStowingCheckSources(context) }

    var selectedCustomer by remember { mutableStateOf("") }
    var selectedJenis by remember { mutableStateOf("") }
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

    LaunchedEffect(Unit) {
        if (selectedCustomer.isBlank()) {
            selectedCustomer = customerOptions.firstOrNull().orEmpty()
        }
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
        val value = input.trim().replace(",", ".").toDoubleOrNull()
        if (value == null || !value.isFinite() || value <= 0.0) {
            errorText = "Masukkan KG yang valid."
            successText = null
            return
        }

        if (expected.isEmpty()) {
            errorText = "Data KG dari Form Stowing Cargo belum ada untuk pilihan ini."
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
            successText = "✓ BENAR — Koli ${accepted.size}: $actual KG"
        } else {
            // KG salah tidak pernah ditambahkan ke hasil pengecekan.
            errorText = "✗ SALAH. Berikutnya harus $target KG. $actual KG ditolak."
            successText = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cek Data BTB", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 650.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Data KG diambil dari Form Stowing Cargo yang sudah disimpan. " +
                        "Foto BTB hanya menjadi acuan. Input harus mengikuti urutan KG Stowing.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(Modifier.height(10.dp))

                Box {
                    OutlinedButton(
                        onClick = { customerMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = customerOptions.isNotEmpty()
                    ) {
                        Text(
                            if (selectedCustomer.isBlank()) "Pilih Customer" else selectedCustomer,
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

                Box {
                    OutlinedButton(
                        onClick = { jenisMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = jenisOptions.isNotEmpty()
                    ) {
                        Text(
                            if (selectedJenis.isBlank()) "Pilih Jenis Barang" else selectedJenis,
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
                                text = { Text(jenis) },
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

                if (sources.isEmpty()) {
                    Surface(
                        color = Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Data Stowing Cargo belum ditemukan. Simpan data dari Form Stowing Cargo terlebih dahulu.",
                            modifier = Modifier.padding(10.dp),
                            color = Color(0xFFB00020),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (expected.isEmpty()) {
                    Surface(
                        color = Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Data KG tidak ditemukan untuk Customer + Jenis Barang ini.",
                            modifier = Modifier.padding(10.dp),
                            color = Color(0xFFB00020),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        "Urutan KG Stowing (${expected.size} Koli):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        expected.joinToString(" → "),
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Input KG") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { checkInput() }
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { checkInput() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = accepted.size < expected.size
                    ) {
                        Text("✓ Cek KG")
                    }

                    successText?.let {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                it,
                                modifier = Modifier.padding(10.dp),
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    errorText?.let {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                it,
                                modifier = Modifier.padding(10.dp),
                                color = Color(0xFFB00020),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Sudah dicek: ${accepted.size}/${expected.size} Koli",
                        fontWeight = FontWeight.Bold
                    )

                    if (accepted.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            accepted.joinToString(" → "),
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (accepted.size == expected.size) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "✓ SEMUA DATA BTB SUDAH COCOK",
                                modifier = Modifier.padding(10.dp),
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}
