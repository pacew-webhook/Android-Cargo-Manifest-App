package com.example.cargomanifestapp

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==========================================
// HALAMAN MANIFEST CARGO
// ==========================================
// Manifest sekarang bersifat READ-ONLY dan menjadi tampilan data yang
// berasal dari Form Stowing Cargo. Tidak ada lagi form input Manifest.
@Composable
fun CargoAppScreen(
    viewModel: CargoViewModel,
    onBackToMenu: () -> Unit = {}
) {
    BackHandler { onBackToMenu() }

    val context = LocalContext.current
    val cargoList by viewModel.cargoList.collectAsState()
    var isSendingToN8n by remember { mutableStateOf(false) }

    val totalWeight = cargoList.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
    val totalPcs = cargoList.sumOf { it.pcsQty.toIntOrNull() ?: 0 }
    val totalWeightText = if (totalWeight % 1.0 == 0.0) {
        totalWeight.toInt().toString()
    } else {
        totalWeight.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackToMenu) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali ke Menu",
                    tint = Color(0xFF6200EE)
                )
            }
            Text(
                text = "Manifest Cargo App",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6200EE)
            )
        }

        // Penjelasan bahwa Manifest hanya menampilkan data dari Stowing.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Data Manifest",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3F207A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Data di bawah otomatis mengikuti input dari Form Stowing Cargo.",
                    fontSize = 13.sp,
                    color = Color(0xFF49454F)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Total Data: ${cargoList.size}",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF49454F)
                    )
                    Text(
                        text = "Total Pcs: $totalPcs",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF49454F)
                    )
                    Text(
                        text = "Total: $totalWeightText KG",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tombol yang tetap relevan untuk data Manifest.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.exportToExcel(context, "", "") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Export Excel", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    if (!isSendingToN8n) {
                        isSendingToN8n = true
                        viewModel.sendManifestToN8n { result ->
                            isSendingToN8n = false
                            result.onSuccess {
                                Toast.makeText(
                                    context,
                                    "Data Manifest berhasil dikirim ke n8n",
                                    Toast.LENGTH_LONG
                                ).show()
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    "Gagal kirim ke n8n: ${error.localizedMessage}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                },
                enabled = cargoList.isNotEmpty() && !isSendingToN8n,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    if (isSendingToN8n) "Mengirim..." else "Kirim ke Laptop (n8n)",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Data dari Form Stowing Cargo (${cargoList.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color(0xFF3F207A)
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (cargoList.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Belum ada data. Silakan input data melalui Form Stowing Cargo.",
                    modifier = Modifier.padding(16.dp),
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cargoList, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PTI: ${item.pti}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF3F207A)
                                )
                                Text(
                                    text = "${item.subTotal} KG",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "NO PAG: ${item.noPag.ifBlank { "-" }}",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF49454F)
                            )
                            Text(
                                text = "Customer: ${item.customer.ifBlank { "-" }}",
                                color = Color(0xFF49454F)
                            )
                            Text(
                                text = "Description: ${item.description.ifBlank { "-" }}",
                                color = Color(0xFF49454F)
                            )
                            Text(
                                text = "Pcs / Qty: ${item.pcsQty.ifBlank { "0" }}",
                                color = Color(0xFF49454F)
                            )
                            Text(
                                text = "Rincian KG: ${formatWeightForDisplay(item.weight)}",
                                color = Color(0xFF49454F)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatWeightForDisplay(weight: String): String {
    if (weight.isBlank()) return "-"

    return weight
        .split(",", limit = Int.MAX_VALUE)
        .map { token -> token.trim().ifBlank { "-" } }
        .joinToString("  |  ")
}
