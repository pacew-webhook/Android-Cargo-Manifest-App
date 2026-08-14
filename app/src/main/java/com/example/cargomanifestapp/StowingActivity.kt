package com.example.cargomanifestapp

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

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
fun StowingInputScreen(
    onBack: () -> Unit,
    viewModel: StowingViewModel = viewModel()
) {
    val context = LocalContext.current
    val scanScope = rememberCoroutineScope()
    val customerFocusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }
    val ptiFocusRequester = remember { FocusRequester() }
    val kgFocusRequester = remember { FocusRequester() }

    fun closeAllDropdowns() {
        viewModel.updateExpandedPag(false)
        viewModel.updateExpandedCustomer(false)
        viewModel.updateExpandedDescription(false)
        viewModel.updateExpandedPti(false)
    }

    var pendingScanUri by remember { mutableStateOf<Uri?>(null) }
    var showScanResultDialog by remember { mutableStateOf(false) }
    var scannedWeightsText by remember { mutableStateOf("") }
    var scannedNoPagText by remember { mutableStateOf("") }
    var scannedCustomerText by remember { mutableStateOf("") }
    var scannedDescriptionText by remember { mutableStateOf("") }
    var scanRawText by remember { mutableStateOf("") }
    var scanRowsText by remember { mutableStateOf("") }
    var scanBusy by remember { mutableStateOf(false) }
    var stowingSearchQuery by remember { mutableStateOf("") }

    suspend fun processBtbUri(uri: Uri, deleteTemp: Boolean) {
        try {
            val result = if (deleteTemp) {
                BtbOcrScanner.scanAndDeleteTemp(context, uri)
            } else {
                // URI dari Galeri adalah milik aplikasi Galeri/MediaStore.
                // Jangan dihapus setelah OCR selesai.
                BtbOcrScanner.scan(context, uri)
            }

            if (result.weights.isEmpty()) {
                Toast.makeText(
                    context,
                    result.verificationMessage.ifBlank { "Angka KG belum terbaca." },
                    Toast.LENGTH_LONG
                ).show()
            } else {
                scannedWeightsText = result.weights.joinToString(", ") {
                    if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                }
                scannedNoPagText = result.noPag
                scannedCustomerText = result.customer
                scannedDescriptionText = result.description
                scanRawText = result.rawText
                scanRowsText = result.rows.mapIndexed { index, row -> "Baris ${index + 1}: ${if (row.isBlank()) "(tidak terbaca)" else row}" }.joinToString("\n")
                showScanResultDialog = true
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Gagal membaca BTB: ${e.localizedMessage ?: "OCR error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val scanCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingScanUri
        pendingScanUri = null

        if (!success || uri == null) {
            if (uri != null) BtbPhotoStorage.deletePhoto(context, uri.toString())
            return@rememberLauncherForActivityResult
        }

        scanBusy = true
        scanScope.launch {
            try {
                processBtbUri(uri, deleteTemp = true)
            } finally {
                scanBusy = false
            }
        }
    }

    // Memilih foto BTB yang sudah ada di Galeri/Google Photos/File Picker.
    // Tidak membutuhkan izin READ_EXTERNAL_STORAGE karena Android memberikan
    // akses sementara langsung ke URI yang dipilih pengguna.
    val scanGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null || scanBusy) return@rememberLauncherForActivityResult

        scanBusy = true
        scanScope.launch {
            try {
                processBtbUri(uri, deleteTemp = false)
            } finally {
                scanBusy = false
            }
        }
    }

    fun scanBtbFromCamera() {
        if (scanBusy) return
        val uri = BtbPhotoStorage.createPhotoUri(context)
        pendingScanUri = uri
        scanCameraLauncher.launch(uri)
    }

    fun scanBtbFromGallery() {
        if (scanBusy) return
        scanGalleryLauncher.launch("image/*")
    }

    LaunchedEffect(Unit) {
        viewModel.loadCargoListFromPrefs(context)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let {
            try {
                ExcelUtils.writeCombinedCargoWorkbook(context, it, viewModel.cargoList)
                Toast.makeText(context, "Export Berhasil!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal Export: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val groupedCargo = remember(viewModel.cargoList.toList()) {
        viewModel.cargoList.mapIndexed { originalIndex, item ->
            Pair(originalIndex, item)
        }.groupBy { it.second.noPag }
    }

    // Pencarian khusus daftar Stowing Group. Hanya memfilter tampilan.
    val filteredGroupedCargo = remember(groupedCargo, stowingSearchQuery) {
        val query = stowingSearchQuery.trim()
        if (query.isBlank()) {
            groupedCargo
        } else {
            groupedCargo.mapNotNull { (pag, entries) ->
                val filteredEntries = entries.filter { (_, item) ->
                    item.noPag.contains(query, ignoreCase = true) ||
                        item.customer.contains(query, ignoreCase = true) ||
                        item.description.contains(query, ignoreCase = true) ||
                        item.pti.contains(query, ignoreCase = true) ||
                        item.weight.contains(query, ignoreCase = true)
                }
                when {
                    pag.contains(query, ignoreCase = true) -> pag to entries
                    filteredEntries.isNotEmpty() -> pag to filteredEntries
                    else -> null
                }
            }.toMap()
        }
    }

    val customerSuggestions = remember(viewModel.cargoList.toList(), viewModel.customer) {
        viewModel.existingCustomers.filter {
            viewModel.customer.isBlank() || it.contains(viewModel.customer.trim(), ignoreCase = true)
        }
    }
    val descriptionSuggestions = remember(viewModel.cargoList.toList(), viewModel.customer, viewModel.description) {
        viewModel.descriptionsForCustomer().filter {
            viewModel.description.isBlank() || it.contains(viewModel.description.trim(), ignoreCase = true)
        }
    }
    val ptiSuggestions = remember(viewModel.cargoList.toList(), viewModel.customer, viewModel.pti) {
        viewModel.availablePtisForCustomer().filter {
            viewModel.pti.isBlank() || it.contains(viewModel.pti.trim(), ignoreCase = true)
        }
    }

    // --- DIALOG HASIL SCAN BTB ---
    if (showScanResultDialog) {
        AlertDialog(
            onDismissRequest = { showScanResultDialog = false },
            title = {
                Text("Hasil Scan BTB", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Periksa angka di bawah. Jika ada yang salah, koreksi sebelum memasukkan ke Form Stowing.",
                        fontSize = 12.sp
                    )
                    Text(
                        "Data BTB yang terbaca (cocokkan sebelum digunakan):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = scannedNoPagText,
                            onValueChange = { scannedNoPagText = it },
                            label = { Text("NO PAG") },
                            placeholder = { Text("Jika terlihat") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = scannedCustomerText,
                            onValueChange = { scannedCustomerText = it },
                            label = { Text("Customer") },
                            placeholder = { Text("Jika terlihat") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = scannedDescriptionText,
                        onValueChange = { scannedDescriptionText = it },
                        label = { Text("Description / Jenis Barang") },
                        placeholder = { Text("Jika terlihat") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = scannedWeightsText,
                        onValueChange = { scannedWeightsText = it },
                        label = { Text("KG per koli") },
                        placeholder = { Text("51, 51, 20, 51, 51, ...") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val detectedCount = scannedWeightsText
                        .replace("\n", ",")
                        .split(",", ";", " ", "\n")
                        .count { it.trim().toDoubleOrNull()?.let { value -> value > 0.0 } == true }

                    Text(
                        if (detectedCount > 0) "Terdeteksi $detectedCount koli" else "⚠ Belum ada koli yang terbaca",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (detectedCount > 0) Color(0xFF2E7D32) else Color(0xFFB00020)
                    )

                    if (scanRawText.isNotBlank()) {
                        val verifiedTotal = scannedWeightsText
                            .replace("\n", ",")
                            .split(",", ";", " ", "\n")
                            .mapNotNull { it.trim().toDoubleOrNull() }
                            .filter { it > 0.0 }
                            .fold(0.0) { acc, value -> acc + value }

                        Text(
                            "Verifikasi matematis: ${if (detectedCount > 0) String.format("%.0f", verifiedTotal) else "0"} KG",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }

                    if (scanRowsText.isNotBlank()) {
                        Text(
                            "Hasil per baris (periksa baris yang kosong atau tidak lengkap):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            scanRowsText,
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                    Text(
                        "OCR tulisan tangan tetap dapat salah. Periksa NO PAG, Customer, Description dan semua angka sebelum menekan Gunakan Hasil.",
                        fontSize = 11.sp,
                        color = Color(0xFFB00020)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // V13.3 FIX: snapshot seluruh angka dari dialog sebelum dipindahkan.
                        val values = scannedWeightsText
                            .replace('\n', ',')
                            .split(',', ';', ' ', '\t', '\r')
                            .mapNotNull { token ->
                                token.trim().replace(',', '.').toDoubleOrNull()
                            }
                            .filter { it.isFinite() && it > 0.0 }
                            .toList()

                        if (values.isEmpty()) {
                            Toast.makeText(context, "Tidak ada angka KG yang valid.", Toast.LENGTH_SHORT).show()
                        } else {
                            var importedCount = viewModel.applyScannedWeights(values)

                            // Retry deterministik bila state belum menerima seluruh item.
                            if (importedCount != values.size) {
                                importedCount = viewModel.applyScannedWeights(values)
                            }

                            // Terapkan teks BTB ke Form Stowing setelah pengguna memeriksa/koreksi.
                            // Field kosong tidak menimpa input manual yang sudah ada.
                            if (scannedNoPagText.isNotBlank()) viewModel.updateNoPag(scannedNoPagText)
                            if (scannedCustomerText.isNotBlank()) viewModel.updateCustomer(scannedCustomerText)
                            if (scannedDescriptionText.isNotBlank()) viewModel.updateDescription(scannedDescriptionText)

                            val finalCount = viewModel.currentActiveEntries.size
                            if (finalCount == values.size) {
                                showScanResultDialog = false
                                Toast.makeText(
                                    context,
                                    "Berhasil: $finalCount/${values.size} koli masuk ke Rincian Input KG",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Transfer KG belum lengkap: $finalCount/${values.size}. Coba tekan Gunakan Hasil lagi.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) {
                    Text("Gunakan Hasil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScanResultDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // --- POP-UP DIALOG KONFIRMASI DELETE ---
    if (viewModel.deleteType != DeleteType.NONE) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("Konfirmasi Hapus", fontWeight = FontWeight.Bold) },
            text = {
                val message = when (viewModel.deleteType) {
                    DeleteType.RESET_ALL -> "Apakah Anda yakin ingin menghapus SELURUH data stowing?"
                    DeleteType.CARGO_ITEM -> "Apakah Anda yakin ingin menghapus data customer ini?"
                    DeleteType.KG_ENTRY -> "Apakah Anda yakin ingin menghapus pecahan KG ini?"
                    DeleteType.NONE -> ""
                }
                Text(message)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmDelete(context) { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Hapus", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text("Batal")
                }
            }
        )
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

            Row {
                if (viewModel.cargoList.isNotEmpty()) {
                    IconButton(onClick = { viewModel.showDeleteDialog(DeleteType.RESET_ALL) }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Reset Data", tint = Color.Red)
                    }
                }

                IconButton(onClick = {
                    if (viewModel.cargoList.isNotEmpty()) {
                        exportLauncher.launch("Cargo_Manifest_${System.currentTimeMillis()}.xlsx")
                    } else {
                        Toast.makeText(context, "Data Kosong", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Export 1 Excel", tint = Color(0xFF2E7D32))
                }
            }
        }

        // --- CARD FORM INPUT ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (viewModel.editingIndex != null) Color(0xFFFFF8E1) else Color(0xFFF3EDF7)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (viewModel.editingIndex != null) "Edit Data Stowing" else "Input PAG, Customer, Description & KG",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (viewModel.editingIndex != null) Color(0xFFE65100) else Color(0xFF381E72)
                    )

                    if (viewModel.editingIndex != null) {
                        TextButton(onClick = { viewModel.cancelEdit() }) {
                            Text("Batal Edit", color = Color.Red, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (viewModel.existingPags.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = viewModel.expandedPag,
                            onExpandedChange = { viewModel.updateExpandedPag(!viewModel.expandedPag) },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = viewModel.noPag,
                                onValueChange = { viewModel.updateNoPag(it) },
                                label = { Text("NO PAG") },
                                placeholder = { Text("001 MYI") },
                                prefix = { Text("PAG") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = {
                                        viewModel.commitNoPag()
                                        closeAllDropdowns()
                                        customerFocusRequester.requestFocus()
                                    }
                                ),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewModel.expandedPag) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = viewModel.expandedPag,
                                onDismissRequest = { viewModel.updateExpandedPag(false) }
                            ) {
                                viewModel.existingPags.forEach { pag ->
                                    DropdownMenuItem(
                                        text = { Text(pag) },
                                        onClick = {
                                            viewModel.updateNoPag(pag)
                                            viewModel.updateExpandedPag(false)
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = viewModel.noPag,
                            onValueChange = { viewModel.updateNoPag(it) },
                            label = { Text("NO PAG") },
                            placeholder = { Text("001 MYI") },
                            prefix = { Text("PAG") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    viewModel.commitNoPag()
                                    closeAllDropdowns()
                                    customerFocusRequester.requestFocus()
                                }
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    ExposedDropdownMenuBox(
                        expanded = viewModel.expandedCustomer && customerSuggestions.isNotEmpty(),
                        onExpandedChange = { viewModel.updateExpandedCustomer(!viewModel.expandedCustomer) },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = viewModel.customer,
                            onValueChange = { viewModel.updateCustomer(it) },
                            label = { Text("Customer") },
                            placeholder = { Text("ULIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    closeAllDropdowns()
                                    descriptionFocusRequester.requestFocus()
                                }
                            ),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewModel.expandedCustomer) },
                            modifier = Modifier.menuAnchor().fillMaxWidth().focusRequester(customerFocusRequester)
                        )
                        ExposedDropdownMenu(
                            expanded = viewModel.expandedCustomer && customerSuggestions.isNotEmpty(),
                            onDismissRequest = { viewModel.updateExpandedCustomer(false) }
                        ) {
                            customerSuggestions.forEach { value ->
                                DropdownMenuItem(
                                    text = { Text(value) },
                                    onClick = {
                                        viewModel.updateCustomer(value)
                                        viewModel.updateExpandedCustomer(false)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = viewModel.expandedDescription && descriptionSuggestions.isNotEmpty(),
                        onExpandedChange = { viewModel.updateExpandedDescription(!viewModel.expandedDescription) },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = viewModel.description,
                            onValueChange = { viewModel.updateDescription(it) },
                            label = { Text("Description") },
                            placeholder = { Text("PINANG") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    closeAllDropdowns()
                                    ptiFocusRequester.requestFocus()
                                }
                            ),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewModel.expandedDescription) },
                            modifier = Modifier.menuAnchor().fillMaxWidth().focusRequester(descriptionFocusRequester)
                        )
                        ExposedDropdownMenu(
                            expanded = viewModel.expandedDescription && descriptionSuggestions.isNotEmpty(),
                            onDismissRequest = { viewModel.updateExpandedDescription(false) }
                        ) {
                            descriptionSuggestions.forEach { value ->
                                DropdownMenuItem(
                                    text = { Text(value) },
                                    onClick = {
                                        viewModel.updateDescription(value)
                                        viewModel.updateExpandedDescription(false)
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = viewModel.expandedPti && ptiSuggestions.isNotEmpty(),
                        onExpandedChange = { viewModel.updateExpandedPti(!viewModel.expandedPti) },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = viewModel.pti,
                            onValueChange = { viewModel.updatePti(it) },
                            label = { Text("PTI (opsional)") },
                            placeholder = { Text("001") },
                            prefix = { Text("KAL") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    viewModel.commitPti()
                                    closeAllDropdowns()
                                    kgFocusRequester.requestFocus()
                                }
                            ),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewModel.expandedPti) },
                            modifier = Modifier.menuAnchor().fillMaxWidth().focusRequester(ptiFocusRequester)
                        )
                        ExposedDropdownMenu(
                            expanded = viewModel.expandedPti && ptiSuggestions.isNotEmpty(),
                            onDismissRequest = { viewModel.updateExpandedPti(false) }
                        ) {
                            ptiSuggestions.forEach { value ->
                                DropdownMenuItem(
                                    text = { Text(value) },
                                    onClick = {
                                        viewModel.updatePti(value)
                                        viewModel.updateExpandedPti(false)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = viewModel.inputKg,
                        onValueChange = { viewModel.updateInputKg(it) },
                        label = { Text("Input Berat (KG)") },
                        placeholder = { Text("10") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            viewModel.addKgEntry {
                                Toast.makeText(context, "Masukkan angka KG yang valid", Toast.LENGTH_SHORT).show()
                            }
                        }),
                        modifier = Modifier.weight(1f).focusRequester(kgFocusRequester)
                    )

                    Button(
                        onClick = { scanBtbFromCamera() },
                        enabled = !scanBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                    ) {
                        Text("📷", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (scanBusy) "Scan..." else "Foto BTB")
                    }

                    Button(
                        onClick = { scanBtbFromGallery() },
                        enabled = !scanBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                    ) {
                        Text("🖼️", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Galeri")
                    }

                    Button(
                        onClick = {
                            viewModel.addKgEntry {
                                Toast.makeText(context, "Masukkan angka KG yang valid", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ KG")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- RINCIAN INPUT KG ---
                if (viewModel.currentKgEntries.isNotEmpty()) {
                    Text(
                        text = "Rincian Input KG (${viewModel.currentActiveEntries.size} Koli):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (viewModel.lastScanImportedCount > 0) {
                        Text(
                            text = "Hasil scan terakhir: ${viewModel.lastScanImportedCount} koli berhasil dimasukkan",
                            fontSize = 10.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(viewModel.currentKgEntries) { index, itemVal ->
                            if (itemVal != null) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFE8DEF8), shape = RoundedCornerShape(6.dp))
                                        .padding(vertical = 4.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (itemVal % 1.0 == 0.0) "${itemVal.toInt()}" else "$itemVal",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(
                                            // Hapus pecahan KG langsung tanpa dialog konfirmasi.
                                            // Konfirmasi hanya dipakai untuk data PAG/customer dan hapus semua data.
                                            onClick = { viewModel.deleteKgEntry(index) },
                                            modifier = Modifier.size(14.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red)
                                        }
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.height(28.dp).fillMaxWidth())
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF381E72), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "TOTAL KG:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (viewModel.currentTotalKg % 1.0 == 0.0) "${viewModel.currentTotalKg.toInt()} KG" else "${viewModel.currentTotalKg} KG",
                            color = Color.Yellow,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                Button(
                    onClick = {
                        viewModel.saveCargoItem(
                            context = context,
                            onSuccess = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
                            onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewModel.editingIndex != null) Color(0xFFE65100) else Color(0xFF2E7D32)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (viewModel.editingIndex != null) "Update Data Stowing" else "Simpan ke Cargo Table",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- DAFTAR CARGO TERGROUPING ---
        // --- DAFTAR CARGO TERGROUPING ---
        val grandTotalKg = viewModel.cargoList.sumOf { item -> item.subTotal.toDoubleOrNull() ?: 0.0 }
        val grandTotalKoli = viewModel.cargoList.sumOf { item -> item.pcsQty.toIntOrNull() ?: 0 }

        val formattedGrandTotal = if (grandTotalKg % 1.0 == 0.0) {
            grandTotalKg.toLong().toString()
        } else {
            grandTotalKg.toString()
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daftar Stowing Group (${groupedCargo.size} PAG)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF381E72)
                )
                if (stowingSearchQuery.isNotBlank()) {
                    Text(
                        text = "Ditemukan ${filteredGroupedCargo.size} PAG",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            if (viewModel.cargoList.isNotEmpty()) {
                Surface(color = Color(0xFF2E7D32), shape = RoundedCornerShape(16.dp)) {
                    Text(
                        text = "Total: $formattedGrandTotal KG ($grandTotalKoli Koli)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        OutlinedTextField(
            value = stowingSearchQuery,
            onValueChange = { stowingSearchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari Stowing") },
            label = { Text("Cari data Stowing") },
            placeholder = { Text("PAG / Customer / Description / PTI") },
            trailingIcon = {
                if (stowingSearchQuery.isNotBlank()) {
                    TextButton(onClick = { stowingSearchQuery = "" }) {
                        Text("Hapus")
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF381E72),
                unfocusedBorderColor = Color.LightGray
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredGroupedCargo.entries.toList()) { group ->
                val pagKey = group.key
                val itemsInGroup = group.value

                val groupTotalKg = itemsInGroup.sumOf { pair -> pair.second.subTotal.toDoubleOrNull() ?: 0.0 }
                val groupTotalKoli = itemsInGroup.sumOf { pair -> pair.second.pcsQty.toIntOrNull() ?: 0 }

                val formattedGroupKg = if (groupTotalKg % 1.0 == 0.0) {
                    groupTotalKg.toLong().toString()
                } else {
                    groupTotalKg.toString()
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "NO PAG: $pagKey", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF381E72))

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray)

                        itemsInGroup.forEachIndexed { subIndex, pair ->
                            val originalIndex = pair.first
                            val item = pair.second

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (viewModel.editingIndex == originalIndex) Color(0xFFFFF3E0) else Color(0xFFF8F9FA),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.customer} - ${item.pcsQty} Koli (${item.subTotal} KG)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF381E72)
                                    )
                                    Text(text = "KG: ${item.weight}", fontSize = 11.sp, color = Color.DarkGray)
                                    if (item.pti.isNotBlank()) {
                                        Text(
                                            text = "PTI: ${item.pti}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF5E35B1),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Row {
                                    IconButton(
                                        onClick = { viewModel.startEditCargoItem(originalIndex, item) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Data", tint = Color(0xFF0288D1), modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.showDeleteDialog(DeleteType.CARGO_ITEM, itemIdx = originalIndex) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Hapus Data", tint = Color(0xFFB3261E), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            if (subIndex < itemsInGroup.size - 1) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "TOTAL PAG ($groupTotalKoli Koli):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1B5E20))
                            Text(text = "$formattedGroupKg KG", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF2E7D32))
                        }
                    }
                }
            }
        }
    }
}
