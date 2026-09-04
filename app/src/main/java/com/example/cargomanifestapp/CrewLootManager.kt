package com.example.cargomanifestapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CrewLootTransaction(
    val id: String = UUID.randomUUID().toString(),
    val manifestGroupKey: String,
    val pti: String,
    val customer: String,
    val description: String,
    val noPag: String,
    val crewName: String,
    val kg: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String = ""
)

/**
 * Ledger LOOT Crew.
 *
 * Tidak pernah mengubah Stowing, Manifest master, detail KG atau BTB.
 * Sisa KG selalu dihitung:
 *   KG real Manifest - total transaksi LOOT Crew.
 */
object CrewLootManager {
    private const val PREFS = "crew_loot_storage"
    private const val KEY = "transactions"

    fun load(context: Context): List<CrewLootTransaction> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    add(
                        CrewLootTransaction(
                            id = o.optString("id"),
                            manifestGroupKey = o.optString("manifestGroupKey"),
                            pti = o.optString("pti"),
                            customer = o.optString("customer"),
                            description = o.optString("description"),
                            noPag = o.optString("noPag"),
                            crewName = o.optString("crewName"),
                            kg = o.optDouble("kg", 0.0),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            note = o.optString("note")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList()).filter { it.manifestGroupKey.isNotBlank() && it.kg > 0.0 }
    }

    fun save(context: Context, items: List<CrewLootTransaction>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("manifestGroupKey", item.manifestGroupKey)
                put("pti", item.pti)
                put("customer", item.customer)
                put("description", item.description)
                put("noPag", item.noPag)
                put("crewName", item.crewName)
                put("kg", item.kg)
                put("createdAt", item.createdAt)
                put("note", item.note)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, array.toString()).apply()
    }
}
