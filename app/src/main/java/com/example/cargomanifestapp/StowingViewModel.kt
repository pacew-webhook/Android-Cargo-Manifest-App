package com.example.cargomanifestapp

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import org.json.JSONArray
import org.json.JSONObject

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

    // Kode disimpan dengan format standar, tetapi saat mengetik prefix tidak
    // ditambahkan ke state pada setiap karakter. Prefix ditampilkan oleh UI.
    private fun stripPagPrefix(value: String): String {
        var result = value.trim()
        while (result.startsWith("PAG", ignoreCase = true)) {
            result = result.substring(3).trim()
        }
        return result
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
            .map { normalizePag(it.noPag) }
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
    fun updateNoPag(value: String) { noPag = stripPagPrefix(value).uppercase() }
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
