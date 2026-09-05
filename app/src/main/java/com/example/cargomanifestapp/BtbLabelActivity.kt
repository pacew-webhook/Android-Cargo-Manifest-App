package com.example.cargomanifestapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale

class BtbLabelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val json = intent.getStringExtra(EXTRA_BTB_JSON)
        val data = json?.let { BtbLabelUtils.decode(it) }

        if (data == null || data.daftarTimbangan.isEmpty()) {
            Toast.makeText(this, "Data BTB untuk label tidak ditemukan.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            CargoRetroTheme {
                BtbLabelScreen(
                    data = data,
                    onBack = { finish() },
                    onIssue = {
                        try {
                            val file = BtbLabelPdfWriter.createPdf(this, data)
                            BtbLabelPdfWriter.sharePdf(this, file)
                        } catch (e: Exception) {
                            Toast.makeText(
                                this,
                                "Gagal menerbitkan label: ${e.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_BTB_JSON = "extra_btb_json"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BtbLabelScreen(
    data: BtbFormData,
    onBack: () -> Unit,
    onIssue: () -> Unit
) {
    val labels = remember(data.id, data.daftarTimbangan) {
        BtbLabelUtils.createLabels(data)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terbitkan Label", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = onIssue,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF311B92))
            ) {
                Text("TERBITKAN & BAGIKAN LABEL")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("BTB: ${data.id}", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Customer: ${data.customerName}")
                Text("Total label: ${labels.size}")
                Spacer(Modifier.height(8.dp))
            }

            items(labels) { label ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(label.labelId, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("Jenis: ${label.jenisBarang.ifBlank { "-" }}")
                        Text(
                            String.format(
                                Locale.US,
                                "Total: %.0f KG",
                                label.beratPembulatan
                            ),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
