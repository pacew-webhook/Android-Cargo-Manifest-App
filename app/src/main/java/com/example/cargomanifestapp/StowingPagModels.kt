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
