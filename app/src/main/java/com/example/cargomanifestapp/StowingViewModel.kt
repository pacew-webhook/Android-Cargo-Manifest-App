package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory

enum class DeleteType {
    NONE, RESET_ALL, CARGO_ITEM, KG_ENTRY
}

class StowingViewModel : ViewModel() {

    // --- STATE FORM INPUT ---
    var noPag by mutableStateOf("")
        private set
    var customer by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set
    var pti by mutableStateOf("")
        private set
    var inputKg by mutableStateOf("")
        private set

    // Jumlah item yang benar-benar berhasil dipindahkan dari dialog scan ke form.
    var lastScanImportedCount by mutableStateOf(0)
        private set

    var editingIndex by mutableStateOf<Int?>(null)
        private set

    // --- STATE LIST CARGO & KG ---
    val cargoList = mutableStateListOf<CargoItem>()
    val currentKgEntries = mutableStateListOf<Double?>()

    // --- STATE DROPDOWN & DIALOG ---
    var expandedPag by mutableStateOf(false)
        private set
    var expandedCustomer by mutableStateOf(false)
        private set
    var expandedDescription by mutableStateOf(false)
        private set
    var expandedPti by mutableStateOf(false)
        private set
    var deleteType by mutableStateOf(DeleteType.NONE)
        private set
    var itemIndexToDelete by mutableStateOf<Int?>(null)
        private set
    var kgIndexToDelete by mutableStateOf<Int?>(null)
        private set

    // Pesan peringatan hasil validasi silang Manifest vs Stowing setelah Import.
    // Null berarti tidak ada selisih terdeteksi (atau belum pernah import).
    var manifestValidationWarning by mutableStateOf<String?>(null)
        private set

    fun dismissManifestValidationWarning() {
        manifestValidationWarning = null
    }

    // Kode disimpan dengan format standar, tetapi saat mengetik prefix tidak
    // ditambahkan ke state pada setiap karakter. Prefix ditampilkan oleh UI.
    private fun stripPagPrefix(value: String): String {
        var result = value.trim()
        while (result.startsWith("PAG", ignoreCase = true)) {
            result = result.substring(3).trim()
        }
        return result
    }

    // NO PAG adalah input bebas. Prefix PAG hanya boleh dibersihkan jika
    // memang ikut masuk ke field, tetapi jangan trim() seluruh isi saat user
    // masih mengetik karena Space adalah bagian dari input yang valid.
    private fun stripPagPrefixWhileTyping(value: String): String {
        return value.replaceFirst(Regex("^\\s*PAG(?:\\s+)?", RegexOption.IGNORE_CASE), "")
    }

    private fun normalizePag(value: String): String {
        val raw = stripPagPrefix(value)
        return if (raw.isBlank()) "" else "PAG $raw"
    }

    private fun stripPtiPrefix(value: String): String {
        var result = value.trim()
        while (result.startsWith("KAL", ignoreCase = true)) {
            result = result.substring(3).trim()
        }
        return result
    }

    private fun normalizePti(value: String): String {
        val raw = stripPtiPrefix(value)
        return if (raw.isBlank()) "" else "KAL$raw"
    }

    // --- DERIVED STATES ---
    val existingPags: List<String>
        get() = cargoList.asSequence()
            // Dropdown juga harus menampilkan nilai yang bisa langsung diedit,
            // bukan nilai penyimpanan yang memiliki prefix "PAG ".
            .map { stripPagPrefix(it.noPag) }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

    val existingCustomers: List<String>
        get() = cargoList.asSequence()
            .map { it.customer.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

    fun descriptionsForCustomer(customerValue: String = customer): List<String> {
        val key = customerValue.trim().uppercase()
        if (key.isBlank()) return emptyList()
        return cargoList.asSequence()
            .filter { it.customer.trim().equals(key, ignoreCase = true) }
            .map { it.description.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    fun ptisForCustomer(customerValue: String = customer): List<String> {
        val key = customerValue.trim().uppercase()
        if (key.isBlank()) return emptyList()
        return cargoList.asSequence()
            .filter { it.customer.trim().equals(key, ignoreCase = true) }
            .map { normalizePti(it.pti) }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    fun availablePtisForCustomer(customerValue: String = customer): List<String> {
        val key = customerValue.trim().uppercase()
        return cargoList.asSequence()
            .map { normalizePti(it.pti) to it.customer.trim() }
            .filter { it.first.isNotBlank() }
            .filter { it.second.isBlank() || it.second.equals(key, ignoreCase = true) }
            .map { it.first }
            .distinct()
            .toList()
    }

    val currentActiveEntries: List<Double>
        get() = currentKgEntries.filterNotNull()

    val currentTotalKg: Double
        get() = currentActiveEntries.sum()

    // --- SETTER UNTUK INPUT UI ---
    // Saat mengetik, pertahankan spasi yang diketik user (mis. "001 MYI").
    // Prefix PAG hanya dibersihkan jika memang diketik/dipilih, dan trimming
    // dilakukan saat commit, bukan setiap karakter.
    fun updateNoPag(value: String) {
        // Jangan trim() di sini. Contoh: "001" -> Space -> "001 MYI"
        // harus tetap mempertahankan Space yang baru diketik.
        val result = stripPagPrefixWhileTyping(value)
        noPag = result.uppercase()
    }
    fun commitNoPag() { noPag = stripPagPrefix(noPag).uppercase() }
    fun updateCustomer(value: String) {
        customer = value.uppercase()
        expandedCustomer = true

        val descriptions = descriptionsForCustomer(customer)
        val customerPtis = ptisForCustomer(customer)
        if (descriptions.size == 1) description = descriptions.first()
        if (customerPtis.size == 1) pti = stripPtiPrefix(customerPtis.first())
    }
    fun updateDescription(value: String) { description = value.uppercase() }
    fun updatePti(value: String) { pti = stripPtiPrefix(value).uppercase() }
    fun commitPti() { pti = stripPtiPrefix(pti).uppercase() }
    fun updateExpandedCustomer(expanded: Boolean) { expandedCustomer = expanded }
    fun updateExpandedDescription(expanded: Boolean) { expandedDescription = expanded }
    fun updateExpandedPti(expanded: Boolean) { expandedPti = expanded }
    fun updateInputKg(value: String) { inputKg = value }
    fun updateExpandedPag(expanded: Boolean) { expandedPag = expanded }

    // --- LOCAL STORAGE ---
    fun saveCargoListToPrefs(context: Context) {
        val prefs = context.getSharedPreferences("stowing_prefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (item in cargoList) {
            val obj = JSONObject().apply {
                put("noPag", item.noPag)
                put("customer", item.customer)
                put("description", item.description)
                put("pti", item.pti)
                put("pcsQty", item.pcsQty)
                put("weight", item.weight)
                put("subTotal", item.subTotal)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("saved_cargo_list", jsonArray.toString()).apply()
    }

    /**
     * Import file Excel hasil Export aplikasi.
     *
     * SUMBER YANG DIBACA HANYA KOLOM YANG TERLIHAT:
     * - Sheet Manifest, kolom A:G untuk data per baris.
     * - Sheet STOWING_DATA untuk NO PAG pada file hasil Export V8.
     * - Hanya sebagai fallback, file lama boleh membaca Stowing Checklist.
     *
     * V8 tidak membaca kolom T atau metadata lama. STOWING_DATA adalah
     * sheet data khusus yang memang dibuat oleh aplikasi sendiri.
     *
     * Manifest:
     *   1 baris Excel = 1 CargoItem.
     *
     * NO PAG:
     *   diambil dari kolom NO PAG pada Stowing Checklist, kemudian
     *   dipasangkan kembali ke baris Manifest berdasarkan total NET
     *   serta Customer/Description yang terlihat di checklist.
     */
    fun importFromManifestExcel(
        context: Context,
        uri: Uri,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Diisi di dalam blok pembacaan workbook, dibaca lagi setelah
                // pindah ke Main dispatcher untuk ditampilkan sebagai warning.
                var validationWarningResult: String? = null

                val imported = context.contentResolver.openInputStream(uri)?.use { input ->
                    WorkbookFactory.create(input).use { workbook ->
                        val formatter = DataFormatter()

                        val manifestSheet = (0 until workbook.numberOfSheets)
                            .map { workbook.getSheetAt(it) }
                            .firstOrNull {
                                it.sheetName.trim().equals("Manifest", ignoreCase = true)
                            }
                            ?: workbook.getSheetAt(0)

                        val manifestStartRow = findManifestDataStartRow(
                            manifestSheet,
                            formatter
                        )

                        val manifestItems = mutableListOf<CargoItem>()
                        val manifestEndRow = findManifestTotalRow(
                            manifestSheet,
                            formatter
                        )

                        if (manifestSheet.lastRowNum >= manifestStartRow) {
                            val endRow = if (manifestEndRow >= manifestStartRow) {
                                manifestEndRow - 1
                            } else {
                                manifestSheet.lastRowNum
                            }

                            for (rowIndex in manifestStartRow..endRow) {
                                val row = manifestSheet.getRow(rowIndex) ?: continue

                                fun text(col: Int): String = safeCellText(
                                    formatter,
                                    row.getCell(col)
                                )

                                val no = text(0)
                                val ptiValue = text(1)
                                val pcsValue = text(2)
                                val subtotalValue = text(4)
                                val descriptionValue = text(5)
                                val customerValue = text(6)

                                // TOTAL WEIGHT / baris total dan baris kosong dilewati.
                                val rowText = listOf(
                                    no,
                                    ptiValue,
                                    pcsValue,
                                    subtotalValue,
                                    descriptionValue,
                                    customerValue
                                ).joinToString(" ")

                                if (rowText.contains("TOTAL", ignoreCase = true)) continue
                                if (ptiValue.isBlank() &&
                                    pcsValue.isBlank() &&
                                    subtotalValue.isBlank() &&
                                    descriptionValue.isBlank() &&
                                    customerValue.isBlank()
                                ) continue

                                val pcs = parseNumber(pcsValue)
                                    ?.toInt()
                                    ?.coerceAtLeast(1)
                                    ?: 1

                                val subtotal = parseNumber(subtotalValue) ?: 0.0

                                manifestItems.add(
                                    CargoItem(
                                        noPag = "",
                                        customer = customerValue,
                                        description = descriptionValue,
                                        pti = normalizePti(ptiValue),
                                        pcsQty = pcs.toString(),
                                        weight = if (subtotal > 0.0) formatWeight(subtotal) else "",
                                        subTotal = formatWeight(subtotal)
                                    )
                                )
                            }
                        }

                        // V8: file hasil export aplikasi memiliki sheet khusus
                        // STOWING_DATA. Sheet ini berisi satu baris per input + NO PAG,
                        // sehingga tidak perlu menebak NO PAG dari checklist yang sudah
                        // digabung.
                        val stowingDataSheet = (0 until workbook.numberOfSheets)
                            .map { workbook.getSheetAt(it) }
                            .firstOrNull {
                                it.sheetName.trim().equals("STOWING_DATA", ignoreCase = true)
                            }

                        val stowingData = if (stowingDataSheet != null) {
                            readStowingDataSheet(stowingDataSheet, formatter)
                        } else {
                            emptyList()
                        }

                        val stowingDataHasDetails = stowingData.any { raw ->
                            raw.pti.isNotBlank() ||
                                raw.customer.isNotBlank() ||
                                raw.description.isNotBlank() ||
                                raw.pcsQty.isNotBlank() ||
                                raw.weight.isNotBlank() ||
                                raw.subTotal.isNotBlank()
                        }

                        val finalList = if (stowingData.isNotEmpty() && stowingDataHasDetails) {
                            /*
                             * V14 FIX: untuk file hasil Export aplikasi, STOWING_DATA
                             * adalah sumber kebenaran untuk data Stowing.
                             *
                             * Sebelumnya importer hanya mengambil NO PAG dari
                             * STOWING_DATA lalu mengambil Pcs/Sub Total dari sheet
                             * Manifest. Masalahnya, sheet Manifest memang sengaja
                             * merangkum satu PAG/customer/description menjadi satu
                             * baris. Akibatnya input 50, 50, 50, 50 kg berubah saat
                             * import menjadi satu item: 4 koli / 200 kg.
                             *
                             * STOWING_DATA menyimpan kembali data asli per CargoItem,
                             * termasuk Weight Detail (50, 50, 50, 50), Pcs/Cly, dan
                             * Sub Total. Karena itu seluruh CargoItem direkonstruksi
                             * langsung dari sheet ini dan tidak lagi dipasangkan
                             * dengan baris Manifest yang sudah diringkas.
                             */
                            stowingData.map { raw ->
                                CargoItem(
                                    noPag = normalizePag(raw.noPag),
                                    customer = raw.customer.trim(),
                                    description = raw.description.trim(),
                                    pti = normalizePti(raw.pti),
                                    pcsQty = raw.pcsQty.trim(),
                                    weight = raw.weight.trim(),
                                    subTotal = raw.subTotal.trim()
                                )
                            }
                        } else if (manifestItems.isEmpty()) {
                            emptyList()
                        } else if (stowingData.isNotEmpty()) {
                            // Kompatibilitas export lama yang hanya menyimpan NO PAG
                            // di STOWING_DATA: pertahankan data Manifest dan tempelkan
                            // NO PAG berdasarkan urutan baris.
                            manifestItems.mapIndexed { index, item ->
                                val raw = stowingData.getOrNull(index)
                                if (raw != null && raw.noPag.isNotBlank()) {
                                    item.copy(noPag = normalizePag(raw.noPag))
                                } else {
                                    item
                                }
                            }
                        } else {
                            // Kompatibilitas file V7/lama: fallback membaca checklist
                            // yang terlihat. Tidak digunakan untuk file V8.
                            val pagGroups = readVisibleStowingChecklist(
                                manifestSheet,
                                formatter
                            )

                            if (pagGroups.isEmpty()) {
                                manifestItems
                            } else {
                                applyVisiblePagGroups(manifestItems, pagGroups)
                            }
                        }

                        // --- VALIDASI SILANG MANIFEST vs STOWING ---
                        // manifestItems = baris asli Sheet "Manifest" (sumber kebenaran
                        // pcs/berat). finalList = data yang akan dipakai sebagai
                        // cargoList (Stowing). Bandingkan totalnya per Customer +
                        // Description + PTI supaya bug pemasangan NO PAG yang salah
                        // (atau baris yang hilang/dobel saat import) ketahuan sejak awal.
                        validationWarningResult = validateManifestVsStowing(
                            manifestItems,
                            finalList
                        )

                        finalList
                    }
                } ?: throw IllegalStateException("File tidak dapat dibuka")

                if (imported.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onError(
                            "Tidak ada data Manifest yang dapat di-import. " +
                                "Pastikan file Excel memiliki data pada kolom Manifest."
                        )
                    }
                } else {
                    // Import Stowing menjadi sumber data Manifest juga.
                    // Database Manifest diperbarui di thread IO sebelum UI
                    // diberi pesan sukses, sehingga Manifest langsung membaca
                    // data import yang sama.
                    try {
                        val dao = CargoDatabase.getDatabase(context).cargoDao()
                        val manifestItems = groupImportedForManifest(imported)
                        dao.deleteAllCargo()
                        dao.insertAll(manifestItems.map { it.copy(id = 0L) })
                    } catch (syncError: Throwable) {
                        withContext(Dispatchers.Main) {
                            onError(
                                "Import Stowing berhasil, tetapi gagal menyinkronkan data ke Manifest Cargo. " +
                                    (syncError.message?.takeIf { it.isNotBlank() } ?: syncError.javaClass.simpleName)
                            )
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        // Data lama baru diganti setelah seluruh file selesai dibaca
                        // dan database Manifest berhasil disinkronkan.
                        cargoList.clear()
                        cargoList.addAll(imported)
                        saveCargoListToPrefs(context)
                        resetForm()

                        // Tampilkan warning selisih Manifest vs Stowing (jika ada)
                        // sebagai dialog terpisah, tidak mengganggu pesan sukses.
                        manifestValidationWarning = validationWarningResult

                        val pagCount = imported.count { it.noPag.isNotBlank() }
                        onSuccess(
                            "Import berhasil: ${imported.size} data Manifest" +
                                if (pagCount > 0) {
                                    " • ${pagCount} data berhasil dipasangkan NO PAG"
                                } else {
                                    " • NO PAG tidak ditemukan"
                                }
                        )
                    }
                }
            } catch (t: Throwable) {
                val detail = t.message
                    ?.takeIf { it.isNotBlank() }
                    ?: t.javaClass.simpleName

                withContext(Dispatchers.Main) {
                    onError(
                        "Gagal Import Excel. Data lama tetap aman.\n" +
                            "Detail: $detail"
                    )
                }
            }
        }
    }

    /**
     * Bentuk data import yang disimpan di Manifest Cargo.
     *
     * Stowing tetap menyimpan setiap input sebagai record terpisah, tetapi
     * salinan ke database Manifest langsung digabung jika dan hanya jika
     * PTI + NO PAG + Customer + Description sama.
     *
     * Pcs/Qty dan Sub Total dijumlahkan. Semua rincian KG digabung dan
     * dipertahankan urutannya. Weight Pcs/Cly tetap kosong karena Manifest
     * hanya menjadi ringkasan dari data Stowing.
     */
    private fun groupImportedForManifest(items: List<CargoItem>): List<CargoItem> {
        fun key(value: String): String = value.trim().uppercase()

        return items
            .groupBy { item ->
                listOf(
                    key(item.pti),
                    key(item.noPag),
                    key(item.customer),
                    key(item.description)
                ).joinToString("\u001f")
            }
            .map { (_, group) ->
                val totalPcs = group.sumOf {
                    parseNumber(it.pcsQty)?.toInt() ?: 0
                }
                val totalKg = group.sumOf {
                    parseNumber(it.subTotal) ?: 0.0
                }

                val weightDetails = group
                    .flatMap { splitImportedWeightDetails(it.weight) }
                    .joinToString(", ")

                group.first().copy(
                    pcsQty = totalPcs.toString(),
                    weight = weightDetails,
                    subTotal = formatWeight(totalKg)
                )
            }
    }

    private fun splitImportedWeightDetails(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return value
            .split(Regex("\\s*[,;]\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private data class ImportedStowingData(
        val noPag: String,
        val pti: String,
        val customer: String,
        val description: String,
        val pcsQty: String,
        val weight: String,
        val subTotal: String
    )

    /**
     * Baca sheet STOWING_DATA yang dibuat oleh Export aplikasi.
     *
     * Sheet ini adalah snapshot data asli per CargoItem dan menjadi sumber
     * utama saat re-import file hasil export.
     */
    private fun readStowingDataSheet(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        formatter: DataFormatter
    ): List<ImportedStowingData> {
        if (sheet.lastRowNum < 1) return emptyList()

        val result = mutableListOf<ImportedStowingData>()
        for (r in 1..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue

            fun text(col: Int): String = safeCellText(formatter, row.getCell(col))

            val noPag = text(1)
            val pti = text(2)
            val customer = text(3)
            val description = text(4)
            val pcs = text(5)
            val weight = text(6)
            val subtotal = text(7)

            if (noPag.isBlank() && pti.isBlank() && customer.isBlank() &&
                description.isBlank() && pcs.isBlank() && weight.isBlank() &&
                subtotal.isBlank()
            ) continue

            result.add(
                ImportedStowingData(
                    noPag = noPag,
                    pti = pti,
                    customer = customer,
                    description = description,
                    pcsQty = pcs,
                    weight = weight,
                    subTotal = subtotal
                )
            )
        }
        return result
    }

    private data class VisiblePagGroup(
        val noPag: String,
        val descriptions: Set<String>,
        val customers: Set<String>,
        val net: Double
    )

    private fun findManifestDataStartRow(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        formatter: DataFormatter
    ): Int {
        for (rowIndex in 0..minOf(sheet.lastRowNum, 40)) {
            val row = sheet.getRow(rowIndex) ?: continue
            val a = safeCellText(formatter, row.getCell(0))
            val b = safeCellText(formatter, row.getCell(1))
            if (a.equals("No", ignoreCase = true) &&
                b.equals("PTI", ignoreCase = true)
            ) {
                return rowIndex + 1
            }
        }
        return 13
    }

    private fun findManifestTotalRow(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        formatter: DataFormatter
    ): Int {
        for (r in 0..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            val text = (0..6)
                .map { safeCellText(formatter, row.getCell(it)) }
                .joinToString(" ")

            if (text.contains("TOTAL WEIGHT", ignoreCase = true) ||
                text.equals("TOTAL", ignoreCase = true)
            ) {
                return r
            }
        }
        return -1
    }

    /**
     * Membaca H:M pada Sheet Manifest, yaitu Stowing Checklist yang memang
     * terlihat oleh pengguna.
     */
    private fun readVisibleStowingChecklist(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        formatter: DataFormatter
    ): List<VisiblePagGroup> {
        var headerRow = -1

        for (r in 0..minOf(sheet.lastRowNum, 80)) {
            val row = sheet.getRow(r) ?: continue
            val noPag = safeCellText(formatter, row.getCell(8))
            val desc = safeCellText(formatter, row.getCell(9))
            val weight = safeCellText(formatter, row.getCell(10))
            val customer = safeCellText(formatter, row.getCell(12))

            if (noPag.equals("NO PAG", ignoreCase = true) &&
                desc.equals("DESCRIPTION", ignoreCase = true) &&
                weight.equals("Net", ignoreCase = true) &&
                (customer.contains("CUSTOM", ignoreCase = true) ||
                    customer.contains("COSTUM", ignoreCase = true))
            ) {
                headerRow = r
                break
            }
        }

        if (headerRow < 0) return emptyList()

        val result = mutableListOf<VisiblePagGroup>()

        for (r in headerRow + 1..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue

            val noPag = safeCellText(formatter, row.getCell(8)).trim()
            val description = safeCellText(formatter, row.getCell(9)).trim()
            val net = parseNumber(
                safeCellText(formatter, row.getCell(10))
            ) ?: 0.0
            val customer = safeCellText(formatter, row.getCell(12)).trim()

            if (noPag.isBlank()) {
                // Berhenti setelah TOTAL WEIGHT / akhir data.
                continue
            }

            if (noPag.contains("TOTAL", ignoreCase = true)) break

            if (description.isBlank() && customer.isBlank() && net == 0.0) continue

            result.add(
                VisiblePagGroup(
                    noPag = normalizePag(noPag),
                    descriptions = splitChecklistValues(description),
                    customers = splitChecklistValues(customer),
                    net = net
                )
            )
        }

        return result
    }

    private fun splitChecklistValues(value: String): Set<String> {
        return value
            .split("/")
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    /**
     * Pasangkan NO PAG berdasarkan isi kolom yang terlihat.
     *
     * Prioritas:
     * 1. Customer/Description harus cocok dengan daftar yang tampil.
     * 2. Total KG group harus habis tepat.
     *
     * Jika sebuah group tidak bisa dicocokkan secara unik, item tersebut
     * dibiarkan kosong daripada menebak PAG yang salah.
     */
    private fun applyVisiblePagGroups(
        items: List<CargoItem>,
        groups: List<VisiblePagGroup>
    ): List<CargoItem> {
        if (items.isEmpty() || groups.isEmpty()) return items

        val weights = items.map {
            kotlin.math.round(
                (it.subTotal.toDoubleOrNull() ?: 0.0) * 100.0
            ).toLong()
        }
        val targets = groups.map {
            kotlin.math.round(it.net * 100.0).toLong()
        }

        val assigned = IntArray(items.size) { -1 }

        fun matches(item: CargoItem, group: VisiblePagGroup): Boolean {
            val customer = item.customer.trim().uppercase()
            val description = item.description.trim().uppercase()
            return (customer.isNotBlank() && customer in group.customers) ||
                (description.isNotBlank() && description in group.descriptions)
        }

        fun solveGroup(
            groupIndex: Int,
            available: MutableList<Int>
        ): Boolean {
            // Group terakhir harus menerima semua baris yang tersisa.
            if (groupIndex == groups.lastIndex) {
                val total = available.sumOf { weights[it] }
                if (total != targets[groupIndex]) return false

                for (index in available) {
                    assigned[index] = groupIndex
                }
                return true
            }

            val group = groups[groupIndex]
            val target = targets[groupIndex]

            var candidates = available.filter { matches(items[it], group) }

            // Karena Checklist hanya menampilkan maksimal 4 Customer dan
            // 4 Description, sebuah data valid bisa saja tidak masuk daftar
            // yang terlihat. Jika kandidat yang cocok tidak cukup berat,
            // fallback ke seluruh baris yang tersisa agar tidak kehilangan data.
            if (candidates.sumOf { weights[it] } < target) {
                candidates = available.toList()
            }

            if (candidates.isEmpty()) return false

            // Urutkan berat terbesar dulu agar pencarian subset lebih cepat.
            candidates = candidates.sortedByDescending { weights[it] }

            val suffix = LongArray(candidates.size + 1)
            for (i in candidates.indices.reversed()) {
                suffix[i] = suffix[i + 1] + weights[candidates[i]]
            }

            val chosen = mutableListOf<Int>()
            val dead = HashSet<String>()

            fun choose(position: Int, remainingTarget: Long): Boolean {
                if (remainingTarget == 0L) {
                    val nextAvailable = available
                        .filterNot { it in chosen }
                        .toMutableList()

                    for (index in chosen) assigned[index] = groupIndex

                    if (solveGroup(groupIndex + 1, nextAvailable)) {
                        return true
                    }

                    for (index in chosen) assigned[index] = -1
                    return false
                }

                if (remainingTarget < 0L || position >= candidates.size) return false
                if (suffix[position] < remainingTarget) return false

                val key = "$position:$remainingTarget:${chosen.size}"
                if (!dead.add(key)) return false

                val index = candidates[position]
                val weight = weights[index]

                // Ambil item ini.
                if (weight <= remainingTarget) {
                    chosen.add(index)
                    if (choose(position + 1, remainingTarget - weight)) return true
                    chosen.removeAt(chosen.lastIndex)
                }

                // Lewati item ini.
                return choose(position + 1, remainingTarget)
            }

            return choose(0, target)
        }

        val available = items.indices.toMutableList()
        val solved = solveGroup(0, available)

        if (!solved) {
            // Jangan pernah menebak PAG secara paksa. Jika kombinasi visible
            // tidak dapat direkonstruksi, hanya pasangkan group yang unik.
            for (index in items.indices) {
                val item = items[index]
                val matches = groups.indices.filter { matches(item, groups[it]) }
                if (matches.size == 1) {
                    assigned[index] = matches.first()
                }
            }
        }

        return items.mapIndexed { index, item ->
            val groupIndex = assigned[index]
            if (groupIndex >= 0) {
                item.copy(noPag = groups[groupIndex].noPag)
            } else {
                item
            }
        }
    }

    private fun safeCellText(
        formatter: DataFormatter,
        cell: org.apache.poi.ss.usermodel.Cell?
    ): String {
        if (cell == null) return ""
        return try {
            formatter.formatCellValue(cell).trim()
        } catch (_: Throwable) {
            try {
                when (cell.cellType) {
                    org.apache.poi.ss.usermodel.CellType.STRING ->
                        cell.stringCellValue.trim()

                    org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                        formatter.formatCellValue(cell).trim()

                    org.apache.poi.ss.usermodel.CellType.BOOLEAN ->
                        cell.booleanCellValue.toString()

                    else -> ""
                }
            } catch (_: Throwable) {
                ""
            }
        }
    }

    private fun parseNumber(value: String): Double? {
        val raw = value.trim().replace(" ", "")
        if (raw.isBlank()) return null

        // Menangani format angka yang umum di Excel/Google Sheets:
        // 1250      -> 1250
        // 1250.5    -> 1250.5
        // 1.250     -> 1250 (pemisah ribuan Indonesia)
        // 1,5       -> 1.5
        // 1.250,5   -> 1250.5
        return when {
            raw.contains('.') && raw.contains(',') -> {
                // Anggap titik = ribuan dan koma = desimal.
                raw.replace(".", "").replace(",", ".").toDoubleOrNull()
            }
            raw.count { it == ',' } == 1 -> {
                val commaIndex = raw.indexOf(',')
                val digitsAfter = raw.length - commaIndex - 1
                if (digitsAfter in 1..2) {
                    raw.replace(',', '.').toDoubleOrNull()
                } else {
                    raw.replace(",", "").toDoubleOrNull()
                }
            }
            raw.count { it == '.' } == 1 -> {
                val dotIndex = raw.indexOf('.')
                val digitsAfter = raw.length - dotIndex - 1
                if (digitsAfter in 1..2) {
                    raw.toDoubleOrNull()
                } else {
                    raw.replace(".", "").toDoubleOrNull()
                }
            }
            else -> raw.toDoubleOrNull()
        }
    }

    private fun formatWeight(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }

    // --- VALIDASI SILANG MANIFEST vs STOWING ---

    // Key pengelompokan dibuat dari Customer + Description + PTI, bukan NO PAG,
    // karena NO PAG justru yang paling sering salah pasang. Menggunakan key ini
    // membuat perbandingan tetap valid walau pemasangan NO PAG-nya keliru.
    private fun validationGroupKey(item: CargoItem): String {
        val customer = item.customer.trim().uppercase()
        val description = item.description.trim().uppercase()
        val pti = normalizePti(item.pti)
        return "$customer|$description|$pti"
    }

    private fun summarizeByGroup(items: List<CargoItem>): Map<String, Pair<Double, Int>> {
        val result = mutableMapOf<String, Pair<Double, Int>>()
        for (item in items) {
            val key = validationGroupKey(item)
            val kg = parseNumber(item.subTotal) ?: 0.0
            val pcs = item.pcsQty.trim().toIntOrNull() ?: 0
            val existing = result[key] ?: (0.0 to 0)
            result[key] = (existing.first + kg) to (existing.second + pcs)
        }
        return result
    }

    /**
     * Membandingkan total KG & Pcs per Customer/Description/PTI antara data
     * mentah Sheet Manifest (sumber kebenaran) dengan data final yang akan
     * dipakai sebagai daftar Stowing.
     *
     * Return null jika tidak ada selisih (dalam toleransi pembulatan), atau
     * teks ringkasan selisih yang siap ditampilkan ke pengguna.
     */
    private fun validateManifestVsStowing(
        manifestItems: List<CargoItem>,
        finalItems: List<CargoItem>
    ): String? {
        if (manifestItems.isEmpty()) return null

        val manifestSummary = summarizeByGroup(manifestItems)
        val stowingSummary = summarizeByGroup(finalItems)

        val kgTolerance = 0.5 // toleransi pembulatan angka Excel
        val mismatchLines = mutableListOf<String>()

        val allKeys = (manifestSummary.keys + stowingSummary.keys).distinct()
        for (key in allKeys) {
            val (manifestKg, manifestPcs) = manifestSummary[key] ?: (0.0 to 0)
            val (stowingKg, stowingPcs) = stowingSummary[key] ?: (0.0 to 0)

            val kgDiff = kotlin.math.abs(manifestKg - stowingKg)
            val pcsDiff = kotlin.math.abs(manifestPcs - stowingPcs)

            if (kgDiff > kgTolerance || pcsDiff > 0) {
                val parts = key.split("|")
                val label = listOf(parts.getOrNull(0).orEmpty(), parts.getOrNull(1).orEmpty())
                    .filter { it.isNotBlank() }
                    .joinToString(" - ")
                    .ifBlank { "(tanpa Customer/Description)" }

                mismatchLines.add(
                    "$label: Manifest ${formatWeight(manifestKg)} KG/$manifestPcs koli " +
                        "≠ Stowing ${formatWeight(stowingKg)} KG/$stowingPcs koli"
                )
            }
        }

        if (mismatchLines.isEmpty()) return null

        val grandManifestKg = manifestSummary.values.sumOf { it.first }
        val grandStowingKg = stowingSummary.values.sumOf { it.first }

        return buildString {
            append("Total Manifest: ${formatWeight(grandManifestKg)} KG\n")
            append("Total Stowing: ${formatWeight(grandStowingKg)} KG\n\n")
            append("Selisih per Customer/Description:\n")
            mismatchLines.forEach { append("• $it\n") }
        }.trim()
    }

    fun loadCargoListFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("stowing_prefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("saved_cargo_list", null) ?: return
        try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<CargoItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    CargoItem(
                        noPag = normalizePag(obj.optString("noPag")),
                        customer = obj.optString("customer"),
                        description = obj.optString("description"),
                        pti = normalizePti(obj.optString("pti")),
                        pcsQty = obj.optString("pcsQty"),
                        weight = obj.optString("weight"),
                        subTotal = obj.optString("subTotal")
                    )
                )
            }
            cargoList.clear()
            cargoList.addAll(list)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * V13.3 FIX: memindahkan SELURUH hasil scan BTB ke Rincian Input KG.
     *
     * Jangan mengandalkan referensi List dari dialog/Compose. Buat snapshot baru,
     * bersihkan state lama, lalu masukkan satu per satu ke SnapshotStateList.
     * Cara ini menjaga jumlah dan urutan item tetap sama dengan hasil scan.
     */
    fun applyScannedWeights(weights: List<Double>): Int {
        val cleanWeights = weights
            .asSequence()
            .filter { it.isFinite() && it > 0.0 }
            .map { it }
            .toList()

        currentKgEntries.clear()
        cleanWeights.forEach { weight ->
            currentKgEntries.add(weight)
        }

        inputKg = ""
        lastScanImportedCount = currentKgEntries.count { it != null }
        return lastScanImportedCount
    }

    fun addKgEntry(onInvalidInput: () -> Unit) {
        val kgVal = inputKg.toDoubleOrNull()
        if (kgVal != null && kgVal > 0) {
            val emptyIndex = currentKgEntries.indexOfFirst { it == null }
            if (emptyIndex != -1) currentKgEntries[emptyIndex] = kgVal
            else currentKgEntries.add(kgVal)
            inputKg = ""
        } else onInvalidInput()
    }

    fun saveCargoItem(context: Context, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        if (noPag.isBlank() || customer.isBlank() || description.isBlank()) {
            onError("Mohon isi NO PAG, Customer dan Description")
            return
        }
        if (currentActiveEntries.isEmpty()) {
            onError("Masukkan minimal 1 nilai KG")
            return
        }

        val normalizedCustomer = customer.trim()
        val normalizedPti = normalizePti(pti)
        if (normalizedPti.isNotBlank()) {
            val conflict = cargoList.withIndex().any { (idx, item) ->
                idx != editingIndex &&
                    normalizePti(item.pti).isNotBlank() &&
                    normalizePti(item.pti).equals(normalizedPti, ignoreCase = true) &&
                    !item.customer.trim().equals(normalizedCustomer, ignoreCase = true)
            }
            if (conflict) {
                onError("PTI $normalizedPti sudah digunakan Customer lain")
                return
            }
        }

        val formattedWeightList = currentActiveEntries.joinToString(", ") {
            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
        }
        val formattedTotalKg = if (currentTotalKg % 1.0 == 0.0) currentTotalKg.toInt().toString() else currentTotalKg.toString()

        val newItem = CargoItem(
            noPag = normalizePag(noPag),
            customer = customer.trim(),
            description = description.trim(),
            pti = normalizePti(pti),
            pcsQty = currentActiveEntries.size.toString(),
            weight = formattedWeightList,
            subTotal = formattedTotalKg
        )

        val index = editingIndex
        val oldItem = if (index != null && index in cargoList.indices) {
            cargoList[index]
        } else {
            null
        }

        if (index != null && index in cargoList.indices) {
            cargoList[index] = newItem
        } else {
            // V9: data baru selalu ditambahkan ke AKHIR daftar.
            // Dengan begitu data lama hasil Import tetap berada di atas
            // dan data baru akan menjadi baris baru di bawahnya saat Export Manifest.
            cargoList.add(newItem)
        }

        saveCargoListToPrefs(context)
        resetForm()

        // Satu input Stowing juga langsung disalin ke tabel data Manifest
        // (Room: cargo_table). Saat edit, baris lama dicari berdasarkan isi
        // data sebelumnya agar tidak membuat duplikat.
        syncStowingToManifest(
            context = context,
            oldItem = oldItem,
            newItem = newItem,
            onSuccess = {
                onSuccess(
                    if (oldItem != null) {
                        "Data berhasil diperbarui! Data juga diperbarui di Tabel Manifest."
                    } else {
                        "Data berhasil disimpan! Data juga masuk ke Tabel Manifest."
                    }
                )
            },
            onError = { error ->
                // Data Stowing lokal tetap tersimpan. Hanya sinkronisasi ke
                // tabel Manifest yang gagal.
                onError(
                    "Data Stowing berhasil disimpan, tetapi gagal menyalin ke Tabel Manifest.\n$error"
                )
            }
        )
    }

    /**
     * Sinkronisasi satu item Stowing -> tabel data Manifest.
     *
     * Mapping:
     * - PTI        -> PTI
     * - jumlah KG  -> Pcs / Qty
     * - rincian KG -> Pcs/Qty Wt
     * - total KG   -> Sub Total (Kg)
     * - Description -> Description
     * - Customer    -> Customer
     * - NO PAG      -> NO PAG
     *
     * AWB dan Flight tidak diisi dari Stowing karena form Stowing memang
     * tidak memiliki kedua field tersebut.
     */
    private fun syncStowingToManifest(
        context: Context,
        oldItem: CargoItem?,
        newItem: CargoItem,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = CargoDatabase.getDatabase(context).cargoDao()

                val existing = if (oldItem != null) {
                    dao.findExactCargo(
                        pti = oldItem.pti,
                        pcsQty = oldItem.pcsQty,
                        weight = oldItem.weight,
                        subTotal = oldItem.subTotal,
                        description = oldItem.description,
                        customer = oldItem.customer,
                        noPag = oldItem.noPag
                    )
                } else {
                    null
                }

                if (existing != null) {
                    // Jangan menghapus AWB/Flight yang mungkin sudah ada pada
                    // baris Manifest hasil import. Yang diubah hanya field Stowing.
                    dao.updateCargo(
                        newItem.copy(
                            id = existing.id,
                            awbNo = existing.awbNo,
                            flightNo = existing.flightNo
                        )
                    )
                } else {
                    dao.insertCargo(newItem.copy(id = 0L))
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError(
                        t.message?.takeIf { it.isNotBlank() }
                            ?: t.javaClass.simpleName
                    )
                }
            }
        }
    }

    fun startEditCargoItem(indexInOriginalList: Int, item: CargoItem) {
        editingIndex = indexInOriginalList
        noPag = stripPagPrefix(item.noPag)
        customer = item.customer
        description = item.description
        pti = stripPtiPrefix(item.pti)
        inputKg = ""
        currentKgEntries.clear()
        currentKgEntries.addAll(item.weight.split(",").mapNotNull { it.trim().toDoubleOrNull() })
    }

    fun cancelEdit() {
        editingIndex = null
        resetForm()
    }

    private fun resetForm() {
        noPag = ""
        customer = ""
        description = ""
        pti = ""
        inputKg = ""
        currentKgEntries.clear()
        editingIndex = null
        expandedCustomer = false
        expandedDescription = false
        expandedPti = false
    }

    /**
     * Menghapus satu pecahan KG secara langsung tanpa konfirmasi.
     *
     * KG adalah data detail yang sering perlu dikoreksi satu per satu, sehingga
     * klik ikon 🗑️ langsung menghilangkan item dan menghitung ulang total.
     * Konfirmasi tetap digunakan untuk data PAG/customer dan reset seluruh data.
     */
    fun deleteKgEntry(index: Int) {
        if (index in currentKgEntries.indices) {
            // Pertahankan posisi/kolom seperti V13.5.2: jangan removeAt(),
            // karena removeAt() akan menggeser semua KG setelahnya.
            // Dengan null, slot yang dihapus tetap kosong pada posisinya.
            currentKgEntries[index] = null
            lastScanImportedCount = 0
            inputKg = ""
        }
    }

    fun showDeleteDialog(type: DeleteType, itemIdx: Int? = null, kgIdx: Int? = null) {
        deleteType = type
        itemIndexToDelete = itemIdx
        kgIndexToDelete = kgIdx
    }

    fun dismissDeleteDialog() {
        deleteType = DeleteType.NONE
        itemIndexToDelete = null
        kgIndexToDelete = null
    }

    fun confirmDelete(context: Context, onDeleted: (String) -> Unit) {
        when (deleteType) {
            DeleteType.RESET_ALL -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        CargoDatabase.getDatabase(context).cargoDao().deleteAllCargo()
                    } catch (_: Throwable) {
                        // Data Stowing tetap dihapus walaupun tabel Manifest gagal dihapus.
                    }
                    withContext(Dispatchers.Main) {
                        cargoList.clear()
                        saveCargoListToPrefs(context)
                        resetForm()
                        onDeleted("Semua data berhasil dihapus")
                    }
                }
            }
            DeleteType.CARGO_ITEM -> {
                itemIndexToDelete?.let { idx ->
                    if (idx in cargoList.indices) {
                        val item = cargoList[idx]
                        if (editingIndex == idx) resetForm()
                        cargoList.removeAt(idx)
                        saveCargoListToPrefs(context)

                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                CargoDatabase.getDatabase(context).cargoDao().deleteExactCargo(
                                    pti = item.pti,
                                    pcsQty = item.pcsQty,
                                    weight = item.weight,
                                    subTotal = item.subTotal,
                                    description = item.description,
                                    customer = item.customer,
                                    noPag = item.noPag
                                )
                            } catch (_: Throwable) {
                                // Jangan batalkan penghapusan Stowing jika sinkronisasi Manifest gagal.
                            }
                        }
                        onDeleted("Data berhasil dihapus")
                    }
                }
            }
            DeleteType.KG_ENTRY -> {
                kgIndexToDelete?.let { idx -> deleteKgEntry(idx) }
            }
            DeleteType.NONE -> Unit
        }
        dismissDeleteDialog()
    }
}
