package com.example.cargomanifestapp

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManifestSearchScreen(
    onBack: () -> Unit,
    viewModel: ManifestSearchViewModel = viewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val totalRows by viewModel.totalRows.collectAsState()
    val fileCount by viewModel.fileCount.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val message by viewModel.message.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            viewModel.scanFolder(uri)
        }
    }

    LaunchedEffect(Unit) {
        // Load the existing Room index only. Do not rescan hundreds of Excel files
        // every time this screen is opened; the user starts synchronization explicitly
        // with the folder button. This prevents repeated scans and force-closes.
        viewModel.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pencarian Manifest", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { folderLauncher.launch(null) },
                        enabled = !busy
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Pilih Folder")
                    }
                    IconButton(
                        onClick = { viewModel.clearDatabase() },
                        enabled = !busy
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus Database")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            OutlinedButton(
                onClick = { folderLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Sedang membaca... ($progress file)" else "Pilih Folder Manifest")
            }

            Spacer(Modifier.height(8.dp))

            if (totalRows > 0 || fileCount > 0) {
                Text(
                    "Database: $totalRows data dari $fileCount file Excel",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Cari PTI / Customer / Barang / No") },
                placeholder = { Text("Contoh: KAL004392 atau IKAN SEGAR") }
            )

            if (busy) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    "Data yang sudah masuk database tetap bisa dicari selama sinkronisasi.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(10.dp))

            if (query.isBlank()) {
                Text("Masukkan PTI, customer, barang, atau nomor untuk mencari.", modifier = Modifier.padding(8.dp))
            } else if (results.isEmpty()) {
                Text("Tidak ada data ditemukan.", modifier = Modifier.padding(8.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.id }) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(item.pti.ifBlank { "PTI -" }, fontWeight = FontWeight.Bold)
                                Text("Customer: ${item.customer.ifBlank { "-" }}")
                                Text("Barang: ${item.description.ifBlank { "-" }}")
                                Text("Pcs: ${item.pcs.ifBlank { "-" }} | Berat: ${item.subTotal.ifBlank { "-" }} KG")
                                if (item.weightPerPiece.isNotBlank()) {
                                    Text("Berat/koli: ${item.weightPerPiece} KG")
                                }
                                Text("Tanggal: ${item.manifestDate.ifBlank { "-" }} | Flight: ${item.flightNo.ifBlank { "-" }}")
                                Text("File: ${item.sourceName}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
