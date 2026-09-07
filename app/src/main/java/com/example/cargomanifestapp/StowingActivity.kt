package com.example.cargomanifestapp

import android.net.Uri
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StowingActivity : ComponentActivity() {
    companion object {
        // Compatibility key used by navigation/edit flows.
        const val EXTRA_EDIT_CARGO_KEY = "edit_cargo_key"
        // Manifest mengirim index sumber dari saved_cargo_list untuk membuka
        // form edit Stowing asli tanpa kehilangan metadata metode input.
        const val EXTRA_EDIT_CARGO_INDEX = EXTRA_EDIT_CARGO_KEY
    }
    private val stowingViewModel: StowingViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        // Manifest dapat mengedit master saved_cargo_list. Reload saat Form
        // Stowing kembali aktif agar perubahan Manifest langsung terlihat.
        stowingViewModel.loadCargoListFromPrefs(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CargoRetroTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StowingInputScreen(onBack = { finish() }, viewModel = stowingViewModel)
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
    LaunchedEffect(Unit) {
        viewModel.attachContext(context)

        // Jika dibuka dari Manifest, edit langsung master Stowing asli.
        // Ini penting untuk KOLI × KG karena ManifestEditDialog hanya membaca
        // rincian KG manual dan sebelumnya tidak dapat memulihkan metadata mode.
        val activity = context as? StowingActivity
        val editIndex = activity?.intent?.getIntExtra(
            StowingActivity.EXTRA_EDIT_CARGO_INDEX,
            -1
        ) ?: -1
        if (editIndex >= 0 && editIndex < viewModel.cargoList.size) {
            viewModel.startEditCargoItem(editIndex, viewModel.cargoList[editIndex])
            activity?.intent?.removeExtra(StowingActivity.EXTRA_EDIT_CARGO_INDEX)
        }
    }
    val scanScope = rememberCoroutineScope()
    val customerFocusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }
    val ptiFocusRequester = remember { FocusRequester() }
    val kgFocusRequester = remember { FocusRequester() }
    val kgGridState = rememberLazyGridState()
    var scrollToKgIndex by remember { mutableStateOf<Int?>(null) }
    var showBtbPicker by remember { mutableStateOf(false) }
    var showPagPicker by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var flightNumber by remember { mutableStateOf("2") }
    val backupDateText = remember {
        SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
            .format(Date())
            .uppercase(Locale("id", "ID"))
    }

    LaunchedEffect(scrollToKgIndex) {
        val targetIndex = scrollToKgIndex ?: return@LaunchedEffect
        if (targetIndex >= 0 && targetIndex < viewModel.currentKgEntries.size) {
            // Tunggu satu frame agar item KG yang baru sudah masuk ke grid,
            // lalu langsung arahkan tampilan ke item terakhir yang ditambahkan.
            kotlinx.coroutines.yield()
            kgGridState.animateScrollToItem(targetIndex)
        }
        scrollToKgIndex = null
    }

    fun addKgAndScroll() {
        val beforeActiveCount = viewModel.currentActiveEntries.size
        val firstEmptyIndex = viewModel.currentKgEntries.indexOfFirst { it == null }
        val targetIndex = if (firstEmptyIndex >= 0) firstEmptyIndex else viewModel.currentKgEntries.size

        viewModel.addKgEntry {
            Toast.makeText(context, "Masukkan angka KG yang valid", Toast.LENGTH_SHORT).show()
        }

        // Hanya scroll jika KG benar-benar berhasil ditambahkan.
        if (viewModel.currentActiveEntries.size > beforeActiveCount) {
            scrollToKgIndex = targetIndex
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshBtbReferences()
    }

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
    var scannedPhotoUri by remember { mutableStateOf("") }
    var scanBusy by remember { mutableStateOf(false) }
    var stowingSearchQuery by remember { mutableStateOf("") }
    var selectedStowingPag by remember { mutableStateOf("SEMUA PAG") }
    var stowingPagDropdownExpanded by remember { mutableStateOf(false) }
    var sendingToN8n by remember { mutableStateOf(false) }
    var showStowingGroupPage by remember { mutableStateOf(false) }

    suspend fun processBtbUri(uri: Uri) {
        try {
            // Foto sudah disimpan permanen di storage aplikasi. OCR hanya membaca
            // file tersebut dan TIDAK boleh menghapusnya karena foto harus ikut
            // tersimpan pada data Stowing/Manifest.
            val result = BtbOcrScanner.scan(context, uri)

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

    var requestCameraCapture by remember { mutableStateOf(false) }

    val scanCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingScanUri
        pendingScanUri = null

        if (!success || uri == null) {
            if (uri != null) BtbPhotoStorage.deletePhoto(context, uri.toString())
            return@rememberLauncherForActivityResult
        }

        // Kamera menulis langsung ke file internal aplikasi. Hubungkan foto
        // sekarang juga ke form, sehingga foto tetap tersimpan walaupun OCR gagal.
        scannedPhotoUri = uri.toString()
        viewModel.attachBtbPhoto(scannedPhotoUri)
        scanBusy = true
        scanScope.launch {
            try {
                processBtbUri(uri)
            } finally {
                scanBusy = false
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            requestCameraCapture = false
            Toast.makeText(context, "Izin Kamera diperlukan untuk Foto BTB", Toast.LENGTH_LONG).show()
        } else {
            requestCameraCapture = true
        }
    }

    LaunchedEffect(requestCameraCapture) {
        if (requestCameraCapture && !scanBusy) {
            requestCameraCapture = false
            val uri = BtbPhotoStorage.createPhotoUri(context)
            pendingScanUri = uri
            scanCameraLauncher.launch(uri)
        }
    }

    // Memilih foto BTB yang sudah ada di Galeri/Google Photos/File Picker.
    // Tidak membutuhkan izin READ_EXTERNAL_STORAGE karena Android memberikan
    // akses sementara langsung ke URI yang dipilih pengguna.
    val scanGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null || scanBusy) return@rememberLauncherForActivityResult

        scanScope.launch {
            try {
                // Salin ke internal app storage supaya URI Galeri tidak hilang
                // setelah aplikasi restart atau izin sementara berakhir.
                val permanentUri = BtbPhotoStorage.copyToAppStorage(context, uri)
                scannedPhotoUri = permanentUri.toString()
                viewModel.attachBtbPhoto(scannedPhotoUri)
                scanBusy = true
                processBtbUri(permanentUri)
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyimpan foto BTB: ${e.localizedMessage ?: "error"}", Toast.LENGTH_LONG).show()
            } finally {
                scanBusy = false
            }
        }
    }

    fun scanBtbFromCamera() {
        if (scanBusy) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            requestCameraCapture = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun scanBtbFromGallery() {
        if (scanBusy) return
        scanGalleryLauncher.launch("image/*")
    }

    LaunchedEffect(Unit) {
        viewModel.loadCargoListFromPrefs(context)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult

        viewModel.importFromManifestExcel(
            context = context,
            uri = uri,
            onSuccess = { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            },
            onError = { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        )
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

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        if (viewModel.cargoList.isEmpty()) {
            Toast.makeText(context, "Data Kosong", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.exportBackupZip(
                context, uri,
                onSuccess = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
                onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            )
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.restoreBackupZip(
            context, uri,
            onSuccess = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
            onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        )
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Backup Manifest") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tanggal: $backupDateText")
                    OutlinedTextField(
                        value = flightNumber,
                        onValueChange = { value ->
                            flightNumber = value.filter { it.isDigit() }
                        },
                        label = { Text("Nomor Flight") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text(
                        "Nama file: MANIFES $backupDateText FLIGHT ${flightNumber.ifBlank { "-" }}.zip",
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val flight = flightNumber.trim()
                        if (flight.isBlank()) {
                            Toast.makeText(context, "Nomor Flight wajib diisi", Toast.LENGTH_SHORT).show()
                        } else {
                            showBackupDialog = false
                            backupLauncher.launch("MANIFES $backupDateText FLIGHT $flight.zip")
                        }
                    }
                ) { Text("BUAT BACKUP") }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) { Text("BATAL") }
            }
        )
    }

    val groupedCargo = remember(viewModel.cargoList.toList()) {
        viewModel.cargoList.mapIndexed { originalIndex, item ->
            Pair(originalIndex, item)
        }.groupBy { it.second.noPag }
    }

    val stowingPagOptions = remember(groupedCargo) {
        listOf("SEMUA PAG") + groupedCargo.keys.toList()
    }

    // Pencarian + filter PAG khusus daftar Stowing Group. Hanya memfilter tampilan.
    val filteredGroupedCargo = remember(groupedCargo, stowingSearchQuery, selectedStowingPag) {
        val query = stowingSearchQuery.trim()
        val pagFiltered = if (selectedStowingPag == "SEMUA PAG") {
            groupedCargo
        } else {
            groupedCargo.filterKeys { it == selectedStowingPag }
        }

        if (query.isBlank()) {
            pagFiltered
        } else {
            pagFiltered.mapNotNull { (pag, entries) ->
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
                            if (scannedPhotoUri.isNotBlank()) viewModel.attachBtbPhoto(scannedPhotoUri)

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

    // --- DIALOG WARNING VALIDASI SILANG MANIFEST vs STOWING ---
    // Muncul setelah Import jika total KG/Pcs per Customer+Description+PTI
    // antara Sheet Manifest dan data Stowing yang terbentuk tidak sama.
    if (viewModel.manifestValidationWarning != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissManifestValidationWarning() },
            title = {
                Text(
                    "⚠ Selisih Manifest vs Stowing",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB00020)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Data hasil import berbeda dengan Sheet Manifest. " +
                            "Periksa kembali sebelum melanjutkan, kemungkinan ada NO PAG " +
                            "yang salah pasang atau baris yang hilang/dobel:",
                        fontSize = 12.sp
                    )
                    Text(
                        viewModel.manifestValidationWarning ?: "",
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissManifestValidationWarning() }) {
                    Text("Mengerti")
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

    if (showBtbPicker) {
        AlertDialog(
            onDismissRequest = { showBtbPicker = false },
            title = {
                Text(
                    "Ambil Data BTB",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF381E72)
                )
            },
            text = {
                if (viewModel.btbReferenceList.isEmpty()) {
                    Text("Belum ada data BTB tersimpan. Simpan BTB terlebih dahulu.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = viewModel.btbReferenceList,
                            key = { it.id }
                        ) { btb ->
                            val btbAlreadyUsed = viewModel.isBtbAlreadyUsed(btb.id)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !btbAlreadyUsed,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (btbAlreadyUsed) Color(0xFFE8F5E9) else Color(0xFFF3EDF7)
                                ),
                                onClick = {
                                    if (viewModel.applyBtbReference(btb)) {
                                        showBtbPicker = false
                                        Toast.makeText(
                                            context,
                                            "Data BTB ${btb.trademarks.ifBlank { "TRADEMARKS" }} berhasil diambil ke Form Stowing. Tekan Simpan untuk menandai BTB sudah masuk Stowing Cargo.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "BTB ini sudah dimasukkan ke Stowing Cargo.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        btb.trademarks.ifBlank { "TRADEMARKS" },
                                        fontWeight = FontWeight.Bold,
                                        color = if (btbAlreadyUsed) Color(0xFF2E7D32) else Color(0xFF381E72)
                                    )
                                    if (btbAlreadyUsed) {
                                        Text(
                                            "✅ Sudah masuk Stowing Cargo",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                    Text(
                                        "${btb.jenisBarang.ifBlank { "-" }} | ${btb.jumlahKoli} Koli | Total ${btb.totalBerat.toCleanString()} KG",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (btb.daftarTimbangan.isNotEmpty()) {
                                        Text(
                                            "Rincian: ${btb.daftarTimbangan.joinToString(" + ") { it.toCleanString() }} KG",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    if (btb.hariTanggal.isNotBlank()) {
                                        Text(
                                            "Tgl BTB: ${btb.hariTanggal}",
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBtbPicker = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    if (showPagPicker) {
        AlertDialog(
            onDismissRequest = { showPagPicker = false },
            title = { Text("Ambil Data PAG Prepare", fontWeight = FontWeight.Bold, color = Color(0xFF381E72)) },
            text = {
                if (viewModel.pagReferenceList.isEmpty()) Text("Belum ada data PAG Prepare tersimpan.")
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(viewModel.pagReferenceList, key = { it.id }) { pag ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !pag.usedInStowing && !pag.inactive,
                            colors = CardDefaults.cardColors(containerColor = if (pag.inactive) Color(0xFFE0E0E0) else if (pag.usedInStowing) Color(0xFFFFF3CD) else Color(0xFFF3EDF7)),
                            onClick = {
                                if (viewModel.applyPagReference(pag)) {
                                    showPagPicker = false
                                    Toast.makeText(context, "Data PAG Prepare berhasil diambil. Tekan Simpan untuk memasukkan ke Stowing Cargo.", Toast.LENGTH_LONG).show()
                                } else Toast.makeText(context, "Data PAG ini sudah masuk Stowing Cargo.", Toast.LENGTH_SHORT).show()
                            }
                        ) { Column(Modifier.padding(12.dp)) {
                            Text("NO PAG: ${pag.noPag}", fontWeight = FontWeight.Bold)
                            Text("${pag.description} | ${pag.pcs} Koli | ${if (pag.totalKg % 1.0 == 0.0) pag.totalKg.toInt() else pag.totalKg} KG", fontSize = 12.sp)
                            Text(pag.customer + if (pag.pti.isBlank()) "" else " • ${pag.pti}", fontSize = 11.sp)
                            when {
                                pag.inactive -> Text("⚫ SUDAH MASUK • INAKTIF", color = Color.DarkGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                pag.usedInStowing -> Text("🟡 SEDANG DIGUNAKAN", color = Color(0xFF9A6700), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }}
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPagPicker = false }) { Text("Tutup") } }
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

                // =========================
                // IMPORT EXCEL MANIFEST
                // =========================
                TextButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                                "application/octet-stream"
                            )
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.FileOpen,
                        contentDescription = "Import Excel Manifest",
                        tint = Color(0xFF1565C0)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Import",
                        color = Color(0xFF1565C0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Tombol Backup / Restore / Export sengaja tidak diletakkan di toolbar.
                // Pada layar kecil Android, 5 tombol toolbar dapat terpotong.
                // Ketiga tombol ditampilkan penuh di bawah tombol Kirim Excel ke Laptop.
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
                if (viewModel.currentPhotoUris.isNotEmpty()) {
                    Text(
                        text = "📷 Foto BTB terhubung: ${viewModel.currentPhotoUris.size}",
                        fontSize = 11.sp,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

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

                // ===== METODE INPUT =====
                // Default MANUAL KG agar tampilan dan mekanisme lama Stowing Cargo tetap sama.
                Text("METODE INPUT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        PagInputMode.TOTAL to "TIMBANG TOTAL",
                        PagInputMode.KOLI_KG to "KOLI × KG",
                        PagInputMode.MANUAL_KG to "MANUAL KG"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = viewModel.stowingInputMode == mode,
                            onClick = { viewModel.selectStowingInputMode(mode) },
                            label = { Text(label, maxLines = 1, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (viewModel.stowingInputMode) {
                    PagInputMode.TOTAL -> {
                        OutlinedTextField(
                            value = viewModel.modePcsText,
                            onValueChange = { viewModel.updateModePcsText(it) },
                            label = { Text("KOLI / PCS") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = viewModel.modeTotalText,
                            onValueChange = { viewModel.updateModeTotalText(it) },
                            label = { Text("TOTAL KG") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    PagInputMode.KOLI_KG -> {
                        OutlinedTextField(
                            value = viewModel.modePcsText,
                            onValueChange = { viewModel.updateModePcsText(it) },
                            label = { Text("KOLI / PCS") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = viewModel.modeKgPerText,
                            onValueChange = { viewModel.updateModeKgPerText(it) },
                            label = { Text("KG / KOLI") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth()
                        )
                        val pcs = viewModel.modePcsText.toDoubleOrNull() ?: 0.0
                        val kgPer = viewModel.modeKgPerText.replace(',', '.').toDoubleOrNull() ?: 0.0
                        val total = pcs * kgPer
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = if (total % 1.0 == 0.0) total.toInt().toString() else total.toString(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("TOTAL KG (OTOMATIS)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    PagInputMode.MANUAL_KG -> {
                        // Mekanisme lama dipertahankan sepenuhnya: Enter/+KG, slot kosong
                        // diisi terlebih dahulu, Foto BTB dan Galeri tetap tersedia.
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
                                keyboardActions = KeyboardActions(onDone = { addKgAndScroll() }),
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
                                onClick = { addKgAndScroll() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ KG")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.refreshBtbReferences()
                        showBtbPicker = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF381E72)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF381E72))
                ) {
                    Text("📋 Ambil Data BTB", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.refreshPagReferences()
                        showPagPicker = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1565C0)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1565C0))
                ) { Text("📦 Ambil Data PAG Prepare", fontWeight = FontWeight.Bold) }

                Spacer(modifier = Modifier.height(8.dp))
                viewModel.importedPagSummary?.let { summary ->
                    Text(
                        text = summary,
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp)).padding(10.dp),
                        color = Color(0xFF0D47A1),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // --- RINCIAN INPUT KG ---
                // Hanya MANUAL KG yang menampilkan rincian satu-per-satu.
                if (viewModel.stowingInputMode == PagInputMode.MANUAL_KG && viewModel.currentKgEntries.isNotEmpty()) {
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
                        state = kgGridState,
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
                        Column {
                            Text(text = "TOTAL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = if (viewModel.currentTotalKg % 1.0 == 0.0) "${viewModel.currentTotalKg.toInt()} KG" else "${viewModel.currentTotalKg} KG",
                                color = Color.Yellow,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "JUMLAH SELURUH PAG", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                text = "${groupedCargo.size} PAG",
                                color = Color.Yellow,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                        }
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

        // ===== DAFTAR STOWING GROUP =====
        // Dipisahkan ke halaman khusus agar daftar data dapat dilihat penuh.
        Button(
            onClick = { showStowingGroupPage = true },
            enabled = viewModel.cargoList.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF546E7A)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = "Daftar Stowing")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Daftar Stowing Group (${groupedCargo.size} PAG)",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                sendingToN8n = true
                scanScope.launch {
                    val selected = if (selectedStowingPag.equals("SEMUA PAG", true)) null else selectedStowingPag
                    val result = N8nClient.sendStowingExcel(context, viewModel.cargoList.toList())
                    sendingToN8n = false
                    result.onSuccess {
                        Toast.makeText(context, "Cargo_Manifest.xlsx berhasil dikirim ke laptop", Toast.LENGTH_LONG).show()
                    }.onFailure {
                        Toast.makeText(context, "Gagal kirim Stowing ke n8n: ${it.localizedMessage ?: "koneksi gagal"}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            enabled = viewModel.cargoList.isNotEmpty() && !sendingToN8n,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = if (sendingToN8n) "Membuat & mengirim Excel..." else "Kirim Excel ke Laptop (n8n)",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ===== BACKUP / RESTORE / EXPORT FILE =====
        // Dipisahkan dari toolbar agar selalu terlihat pada semua ukuran layar.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (viewModel.cargoList.isNotEmpty()) {
                        showBackupDialog = true
                    } else {
                        Toast.makeText(context, "Data Kosong", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Backup ZIP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Restore ZIP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (viewModel.cargoList.isNotEmpty()) {
                    exportLauncher.launch("Cargo_Manifest_${System.currentTimeMillis()}.xlsx")
                } else {
                    Toast.makeText(context, "Data Kosong", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Export Excel",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Excel ke File", fontWeight = FontWeight.Bold)
        }

        if (showStowingGroupPage) {
            StowingGroupListPage(
                viewModel = viewModel,
                onClose = { showStowingGroupPage = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StowingGroupListPage(
    viewModel: StowingViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedPag by remember { mutableStateOf("SEMUA PAG") }
    var pagExpanded by remember { mutableStateOf(false) }

    val groupedCargo = remember(viewModel.cargoList.toList()) {
        viewModel.cargoList.mapIndexed { index, item -> index to item }
            .groupBy { it.second.noPag }
    }
    val pagOptions = remember(groupedCargo) { listOf("SEMUA PAG") + groupedCargo.keys.toList() }
    val filteredGroups = remember(groupedCargo, searchQuery, selectedPag) {
        val base = if (selectedPag == "SEMUA PAG") groupedCargo else groupedCargo.filterKeys { it == selectedPag }
        val q = searchQuery.trim()
        if (q.isBlank()) base else base.mapNotNull { (pag, entries) ->
            val filtered = entries.filter { (_, item) ->
                item.noPag.contains(q, true) || item.customer.contains(q, true) ||
                    item.description.contains(q, true) || item.pti.contains(q, true) || item.weight.contains(q, true)
            }
            when {
                pag.contains(q, true) -> pag to entries
                filtered.isNotEmpty() -> pag to filtered
                else -> null
            }
        }.toMap()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color(0xFF381E72))
                    }
                    Text(
                        text = "Daftar Stowing Group (${groupedCargo.size} PAG)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF381E72)
                    )
                }

                val totalKg = viewModel.cargoList.sumOf { it.subTotal.toDoubleOrNull() ?: 0.0 }
                val totalText = if (totalKg % 1.0 == 0.0) totalKg.toLong().toString() else totalKg.toString()
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Total Data: ${viewModel.cargoList.size} • $totalText KG",
                        modifier = Modifier.padding(10.dp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20)
                    )
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari") },
                    label = { Text("Cari data Stowing") },
                    placeholder = { Text("PAG / Customer / Description / PTI") }
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = pagExpanded,
                    onExpandedChange = { pagExpanded = !pagExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedPag,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Pilih PAG yang ditampilkan") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pagExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = pagExpanded, onDismissRequest = { pagExpanded = false }) {
                        pagOptions.forEach { pag ->
                            DropdownMenuItem(
                                text = { Text(pag) },
                                onClick = { selectedPag = pag; pagExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredGroups.entries.toList()) { group ->
                        val pagKey = group.key
                        val entries = group.value
                        val groupKg = entries.sumOf { it.second.subTotal.toDoubleOrNull() ?: 0.0 }
                        val groupKgText = if (groupKg % 1.0 == 0.0) groupKg.toLong().toString() else groupKg.toString()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("NO PAG: $pagKey", fontWeight = FontWeight.Bold, color = Color(0xFF381E72))
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                entries.forEachIndexed { itemIndex, pair ->
                                    val originalIndex = pair.first
                                    val item = pair.second
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(Color(0xFFF8F9FA), RoundedCornerShape(6.dp)).padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text("${item.customer} - ${item.pcsQty} Koli (${item.subTotal} KG)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF381E72))
                                            Text("KG: ${item.weight}", fontSize = 11.sp, color = Color.DarkGray)
                                            if (item.pti.isNotBlank()) Text("PTI: ${item.pti}", fontSize = 11.sp, color = Color(0xFF5E35B1), fontWeight = FontWeight.SemiBold)
                                        }
                                        IconButton(onClick = {
                                            val pagId = viewModel.pagSourceIdForCargo(item)
                                            if (pagId != null) {
                                                context.startActivity(Intent(context, StowingPagActivity::class.java).putExtra(StowingPagActivity.EXTRA_EDIT_PAG_ID, pagId))
                                            } else {
                                                viewModel.startEditCargoItem(originalIndex, item)
                                                onClose()
                                            }
                                        }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF0288D1))
                                        }
                                        IconButton(onClick = { viewModel.showDeleteDialog(DeleteType.CARGO_ITEM, itemIdx = originalIndex) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFB3261E))
                                        }
                                    }
                                    if (itemIndex < entries.lastIndex) Spacer(Modifier.height(4.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFFE8F5E9), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("TOTAL PAG:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1B5E20))
                                    Text("$groupKgText KG", fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

