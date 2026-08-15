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

    companion object {
        // Kolom T pada Excel (index 19) menyimpan NO PAG metadata.
        private const val HIDDEN_PAG_COLUMN = 19
    }

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
     * Import data dari Sheet Manifest hasil export aplikasi.
     *
     * SETIAP BARIS MANIFEST = SATU CargoItem.
     * Tidak ada grouping saat import.
     *
     * V5 FIX:
     * - Jangan menghapus data lama sebelum seluruh file berhasil dibaca.
     * - Tangkap Throwable agar error POI/Android tidak langsung membuat app crash.
     * - Jangan mewajibkan kolom T. Jika NO PAG tersembunyi tidak tersedia,
     *   import tetap berjalan dan NO PAG dibiarkan kosong.
     * - Tidak memakai FormulaEvaluator saat membaca cell biasa; ini lebih aman
     *   untuk file XLSX yang dibuat/diedit oleh Google Sheets/Excel mobile.
     * - Cari sheet Manifest secara case-insensitive sebelum memakai sheet pertama.
     */
    fun importFromManifestExcel(
        context: Context,
        uri: Uri,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var imported: List<CargoItem> = emptyList()

            try {
                val resolver = context.contentResolver

                imported = resolver.openInputStream(uri)?.use { input ->
                    WorkbookFactory.create(input).use { workbook ->

                        // Cari sheet "Manifest" tanpa mempermasalahkan huruf besar/kecil.
                        val sheet = (0 until workbook.numberOfSheets)
                            .map { index -> workbook.getSheetAt(index) }
                            .firstOrNull {
                                it.sheetName.trim().equals("Manifest", ignoreCase = true)
                            }
                            ?: workbook.getSheetAt(0)

                        val formatter = DataFormatter()
                        val result = mutableListOf<CargoItem>()

                        // Cari baris header "No" agar tidak bergantung mutlak pada row 14.
                        // Jika tidak ditemukan, tetap gunakan row 14 (index 13)
                        // sesuai template aplikasi.
                        var startRow = 13
                        for (rowIndex in 0..minOf(sheet.lastRowNum, 30)) {
                            val row = sheet.getRow(rowIndex) ?: continue
                            val firstCell = safeCellText(
                                formatter,
                                row.getCell(0)
                            )
                            val ptiCell = safeCellText(
                                formatter,
                                row.getCell(1)
                            )
                            if (firstCell.equals("No", ignoreCase = true) &&
                                ptiCell.equals("PTI", ignoreCase = true)
                            ) {
                                startRow = rowIndex + 1
                                break
                            }
                        }

                        if (sheet.lastRowNum < startRow) {
                            return@use emptyList<CargoItem>()
                        }

                        for (rowIndex in startRow..sheet.lastRowNum) {
                            val row = sheet.getRow(rowIndex) ?: continue

                            // Jangan gunakan evaluator untuk cell normal.
                            // DataFormatter cukup untuk numeric/text cell dan lebih stabil.
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

                            // Kolom T (index 19) adalah metadata NO PAG dari export V4/V5.
                            // Tidak wajib ada agar file Manifest lama tetap bisa dibaca.
                            val hiddenPag = text(HIDDEN_PAG_COLUMN)

                            // Lewati baris TOTAL, header lanjutan, dan baris kosong.
                            val rowText = listOf(
                                no,
                                ptiValue,
                                pcsValue,
                                subtotalValue,
                                descriptionValue,
                                customerValue
                            ).joinToString(" ")

                            if (rowText.contains("TOTAL", ignoreCase = true)) {
                                continue
                            }

                            if (
                                ptiValue.isBlank() &&
                                descriptionValue.isBlank() &&
                                customerValue.isBlank() &&
                                subtotalValue.isBlank()
                            ) {
                                continue
                            }

                            // Baris data harus mempunyai minimal PTI/Description/Customer
                            // atau subtotal. Nomor A tidak dipakai sebagai syarat karena
                            // file yang diedit manual dapat mengubah kolom No.
                            val pcs = parseNumber(pcsValue)
                                ?.toInt()
                                ?.coerceAtLeast(1)
                                ?: 1

                            val subtotal = parseNumber(subtotalValue) ?: 0.0

                            // Export Manifest mengosongkan kolom D (Pcs/Cly).
                            // Karena detail KG per koli tidak tersedia di Manifest,
                            // subtotal dipertahankan sebagai satu nilai KG untuk import.
                            val weightList = if (subtotal > 0.0) {
                                formatWeight(subtotal)
                            } else {
                                ""
                            }

                            result.add(
                                CargoItem(
                                    noPag = normalizePag(hiddenPag),
                                    customer = customerValue,
                                    description = descriptionValue,
                                    pti = normalizePti(ptiValue),
                                    pcsQty = pcs.toString(),
                                    weight = weightList,
                                    subTotal = formatWeight(subtotal)
                                )
                            )
                        }

                        result
                    }
                } ?: throw IllegalStateException("File tidak dapat dibuka")

                withContext(Dispatchers.Main) {
                    if (imported.isEmpty()) {
                        onError(
                            "Tidak ada data Manifest yang dapat di-import. " +
                                "Pastikan file memiliki Sheet Manifest dan data."
                        )
                    } else {
                        // PENTING: data lama baru diganti setelah seluruh file
                        // berhasil dibaca dan menghasilkan minimal satu data.
                        cargoList.clear()
                        cargoList.addAll(imported)
                        saveCargoListToPrefs(context)
                        resetForm()

                        val missingPag = imported.count { it.noPag.isBlank() }
                        val message = if (missingPag > 0) {
                            "Import berhasil: ${imported.size} data Manifest. " +
                                "$missingPag data tidak memiliki NO PAG."
                        } else {
                            "Import berhasil: ${imported.size} data Manifest"
                        }
                        onSuccess(message)
                    }
                }
            } catch (t: Throwable) {
                // Throwable sengaja digunakan karena beberapa error runtime dari
                // library Excel (mis. linkage/class loading) bukan turunan Exception.
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
        if (index != null && index in cargoList.indices) {
            cargoList[index] = newItem
            onSuccess("Data berhasil diperbarui!")
        } else {
            cargoList.add(0, newItem)
            onSuccess("Data berhasil disimpan!")
        }
        saveCargoListToPrefs(context)
        resetForm()
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
                cargoList.clear()
                saveCargoListToPrefs(context)
                resetForm()
                onDeleted("Semua data berhasil dihapus")
            }
            DeleteType.CARGO_ITEM -> {
                itemIndexToDelete?.let { idx ->
                    if (idx in cargoList.indices) {
                        if (editingIndex == idx) resetForm()
                        cargoList.removeAt(idx)
                        saveCargoListToPrefs(context)
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
