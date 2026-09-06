package com.example.cargomanifestapp

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class PagInputMode { TOTAL, KOLI_KG, MANUAL_KG }

data class StowingPagItem(
    val id: String = UUID.randomUUID().toString(),
    val noPag: String,
    val customer: String,
    val description: String,
    val pti: String,
    val mode: PagInputMode,
    val pcs: Int,
    val kgPerKoli: Double? = null,
    val totalKg: Double,
    val weights: List<Double?> = emptyList(),
    val usedInStowing: Boolean = false
)

object StowingPagStorage {
    private const val PREF = "stowing_pag_prepare"
    private const val KEY = "items"

    fun load(context: Context): List<StowingPagItem> = runCatching {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val weights = mutableListOf<Double?>()
                val wa = o.optJSONArray("weights") ?: JSONArray()
                for (j in 0 until wa.length()) weights += if (wa.isNull(j)) null else wa.optDouble(j)
                add(StowingPagItem(
                    id = o.optString("id"), noPag = o.optString("noPag"), customer = o.optString("customer"),
                    description = o.optString("description"), pti = o.optString("pti"),
                    mode = runCatching { PagInputMode.valueOf(o.optString("mode")) }.getOrDefault(PagInputMode.TOTAL),
                    pcs = o.optInt("pcs"), kgPerKoli = if (o.has("kgPerKoli") && !o.isNull("kgPerKoli")) o.optDouble("kgPerKoli") else null,
                    totalKg = o.optDouble("totalKg"), weights = weights, usedInStowing = o.optBoolean("usedInStowing", false)
                ))
            }
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, items: List<StowingPagItem>) {
        val arr = JSONArray()
        items.forEach { x -> arr.put(JSONObject().apply {
            put("id", x.id); put("noPag", x.noPag); put("customer", x.customer); put("description", x.description); put("pti", x.pti)
            put("mode", x.mode.name); put("pcs", x.pcs); put("kgPerKoli", x.kgPerKoli ?: JSONObject.NULL); put("totalKg", x.totalKg); put("usedInStowing", x.usedInStowing)
            put("weights", JSONArray().apply { x.weights.forEach { put(it ?: JSONObject.NULL) } })
        }) }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun markUsed(context: Context, id: String) {
        val items = load(context).map { if (it.id == id) it.copy(usedInStowing = true) else it }
        save(context, items)
    }
}


/**
 * Menjaga hubungan permanen antara data PAG Prepare dan baris Stowing Cargo
 * yang dibuat dari data tersebut. Hubungan memakai fingerprint Cargo karena
 * daftar Stowing saat ini disimpan sebagai JSON SharedPreferences.
 */
object StowingPagLinkStorage {
    private const val PREF = "stowing_pag_links"
    private const val KEY = "links"

    private fun cargoKey(item: CargoItem): String = listOf(
        item.noPag, item.customer, item.description, item.pti,
        item.pcsQty, item.weight, item.subTotal
    ).joinToString("\u001F") { it.trim().uppercase() }

    private fun loadMap(context: Context): MutableMap<String, String> = runCatching {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "{}") ?: "{}"
        val obj = JSONObject(raw)
        buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val key = obj.optString(id)
                if (id.isNotBlank() && key.isNotBlank()) put(id, key)
            }
        }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun saveMap(context: Context, map: Map<String, String>) {
        val obj = JSONObject()
        map.forEach { (id, key) -> obj.put(id, key) }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, obj.toString()).apply()
    }

    fun link(context: Context, pagId: String, item: CargoItem) {
        if (pagId.isBlank()) return
        val map = loadMap(context)
        map[pagId] = cargoKey(item)
        saveMap(context, map)
    }

    fun pagIdForCargo(context: Context, item: CargoItem): String? {
        val key = cargoKey(item)
        loadMap(context).entries.firstOrNull { it.value == key }?.key?.let { return it }

        // Kompatibilitas data PAG yang sudah masuk Stowing sebelum fitur link ini
        // ditambahkan. Cari berdasarkan identitas cargo, lalu langsung buat link.
        val pagItems = StowingPagStorage.load(context)
        val total = item.subTotal.replace(".", "").replace(',', '.').toDoubleOrNull()
            ?: item.subTotal.replace(',', '.').toDoubleOrNull()
        val pcs = item.pcsQty.toIntOrNull()

        // 1) Cocokkan identitas lengkap terlebih dahulu. Angka PCS/TOTAL boleh
        // berubah setelah proses edit, sehingga jangan jadikan angka sebagai syarat
        // wajib untuk menemukan kembali sumber PAG Prepare.
        val identityMatches = pagItems.filter { pag ->
            pag.usedInStowing &&
                pag.noPag.trim().equals(item.noPag.trim(), ignoreCase = true) &&
                pag.customer.trim().equals(item.customer.trim(), ignoreCase = true) &&
                pag.description.trim().equals(item.description.trim(), ignoreCase = true) &&
                pag.pti.trim().equals(item.pti.trim(), ignoreCase = true)
        }

        val match = when {
            identityMatches.size == 1 -> identityMatches.first()
            identityMatches.isNotEmpty() -> identityMatches.firstOrNull { pag ->
                (pcs == null || pag.pcs == pcs) &&
                    (total == null || kotlin.math.abs(pag.totalKg - total) < 0.01)
            }
            else -> null
        }
        if (match != null) {
            link(context, match.id, item)
            return match.id
        }
        return null
    }

    /** Update langsung JSON Stowing Cargo ketika sumber PAG Prepare berubah. */
    fun syncCargoFromPag(context: Context, pag: StowingPagItem): Boolean {
        val map = loadMap(context)
        val oldKey = map[pag.id] ?: return false
        val prefs = context.getSharedPreferences("stowing_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("saved_cargo_list", "[]") ?: "[]"
        val arr = JSONArray(raw)
        var updated = false
        var newKey: String? = null

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val item = CargoItem(
                noPag = obj.optString("noPag"),
                customer = obj.optString("customer"),
                description = obj.optString("description"),
                pti = obj.optString("pti"),
                pcsQty = obj.optString("pcsQty"),
                weight = obj.optString("weight"),
                subTotal = obj.optString("subTotal")
            )
            if (cargoKey(item) == oldKey) {
                val weight = when (pag.mode) {
                    PagInputMode.MANUAL_KG -> pag.weights.filterNotNull().joinToString(", ") {
                        if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                    }
                    PagInputMode.KOLI_KG -> pag.kgPerKoli?.let {
                        "${if (it % 1.0 == 0.0) it.toInt() else it} KG/KOLI"
                    } ?: "KOLI × KG"
                    PagInputMode.TOTAL -> "TIMBANG TOTAL"
                }
                val total = if (pag.totalKg % 1.0 == 0.0) pag.totalKg.toInt().toString() else pag.totalKg.toString()
                obj.put("noPag", pag.noPag)
                obj.put("customer", pag.customer)
                obj.put("description", pag.description)
                obj.put("pti", pag.pti)
                obj.put("pcsQty", pag.pcs.toString())
                obj.put("weight", weight)
                obj.put("subTotal", total)
                val newItem = item.copy(
                    noPag = pag.noPag, customer = pag.customer, description = pag.description,
                    pti = pag.pti, pcsQty = pag.pcs.toString(), weight = weight, subTotal = total
                )
                newKey = cargoKey(newItem)
                updated = true
                break
            }
        }

        if (updated) {
            prefs.edit().putString("saved_cargo_list", arr.toString()).apply()
            if (newKey != null) {
                map[pag.id] = newKey!!
                saveMap(context, map)
            }
        }
        return updated
    }

    fun unlinkCargo(context: Context, item: CargoItem) {
        val key = cargoKey(item)
        val map = loadMap(context)
        val ids = map.filterValues { it == key }.keys
        ids.forEach { map.remove(it) }
        saveMap(context, map)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
