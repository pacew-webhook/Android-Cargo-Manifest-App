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

    /**
     * Cari sumber PAG Prepare untuk sebuah CargoItem.
     *
     * Jangan hanya mengandalkan fingerprint lengkap (PCS/weight/subtotal), karena
     * data tersebut memang dapat berubah setelah edit dari Manifest/Stowing.
     * Identitas bisnis yang stabil adalah NO PAG + CUSTOMER + DESCRIPTION, dengan
     * PTI sebagai pembeda tambahan jika kedua sisi sama-sama memiliki PTI.
     */
    fun pagIdForCargo(context: Context, item: CargoItem): String? {
        val key = cargoKey(item)
        loadMap(context).entries.firstOrNull { it.value == key }?.key?.let { return it }

        fun norm(value: String): String = value.trim().uppercase()
        fun normPag(value: String): String = norm(value).removePrefix("PAG ").trim()
        fun normPti(value: String): String = norm(value).removePrefix("PTI ").trim()
        fun parseNumber(value: String): Double? {
            val clean = value.trim().replace(".", "").replace(',', '.')
            return clean.toDoubleOrNull() ?: value.trim().replace(',', '.').toDoubleOrNull()
        }

        val pagItems = StowingPagStorage.load(context)
        val noPag = normPag(item.noPag)
        val customer = norm(item.customer)
        val description = norm(item.description)
        val pti = normPti(item.pti)
        val pcs = item.pcsQty.toIntOrNull()
        val total = parseNumber(item.subTotal)

        // Tahap 1: identitas stabil. usedInStowing tidak dijadikan syarat agar
        // link lama yang statusnya belum tersimpan tetap dapat dipulihkan.
        var candidates = pagItems.filter { pag ->
            normPag(pag.noPag) == noPag &&
                norm(pag.customer) == customer &&
                norm(pag.description) == description
        }

        // Tahap 2: PTI hanya wajib bila kedua data memilikinya.
        val ptiMatches = candidates.filter { pag ->
            val p = normPti(pag.pti)
            pti.isNotBlank() && p.isNotBlank() && p == pti
        }
        if (ptiMatches.isNotEmpty()) candidates = ptiMatches

        // Tahap 3: jika masih lebih dari satu, pilih yang paling cocok berdasarkan
        // PCS/TOTAL. Ini hanya pemecah seri, bukan syarat wajib.
        val match = candidates.minWithOrNull(
            compareBy<StowingPagItem> { if (it.usedInStowing) 0 else 1 }
                .thenBy {
                    val pcsDiff = if (pcs == null) 0 else kotlin.math.abs(it.pcs - pcs)
                    val totalDiff = if (total == null) 0.0 else kotlin.math.abs(it.totalKg - total)
                    pcsDiff.toDouble() + totalDiff
                }
        )

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
