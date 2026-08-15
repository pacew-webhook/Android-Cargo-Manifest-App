package com.example.cargomanifestapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client HTTP sederhana untuk mengirim data Manifest Android ke n8n.
 *
 * Ganti WEBHOOK_URL setelah n8n berjalan di laptop.
 * Contoh: http://192.168.1.10:5678/webhook/cargo/manifest/items
 */
object N8nClient {
    const val WEBHOOK_URL = "http://192.168.1.100:5678/webhook/cargo/manifest/items"
    const val STOWING_WEBHOOK_URL = "http://192.168.1.100:5678/webhook/cargo/stowing-3"

    suspend fun sendManifest(items: List<CargoItem>): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (items.isEmpty()) return@withContext Result.failure(IllegalStateException("Data Manifest kosong"))

            val itemArray = JSONArray()
            items.forEach { item ->
                itemArray.put(JSONObject().apply {
                    put("pti", item.pti)
                    put("pcsQty", item.pcsQty)
                    put("weight", item.weight)
                    put("subTotal", item.subTotal)
                    put("description", item.description)
                    put("customer", item.customer)
                    put("noPag", item.noPag)
                })
            }

            val body = JSONObject().apply {
                put("items", itemArray)
            }.toString()

            val connection = (URL(WEBHOOK_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            try {
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val responseText = stream?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                }.orEmpty()

                if (responseCode in 200..299) {
                    Result.success(responseText.ifBlank { "OK" })
                } else {
                    Result.failure(Exception("n8n HTTP $responseCode: ${responseText.ifBlank { "Tidak ada pesan" }}"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengirim data Form Stowing Cargo ke n8n.
     * selectedPag = null berarti seluruh PAG; jika diisi, hanya PAG tersebut.
     */
    suspend fun sendStowing(items: List<CargoItem>, selectedPag: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val filtered = if (selectedPag.isNullOrBlank() || selectedPag.equals("SEMUA PAG", true)) {
                    items
                } else {
                    items.filter { it.noPag.equals(selectedPag, ignoreCase = true) }
                }
                if (filtered.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("Tidak ada data Stowing untuk dikirim")
                    )
                }

                val itemArray = JSONArray()
                filtered.forEach { item ->
                    itemArray.put(JSONObject().apply {
                        put("pti", item.pti)
                        put("pcsQty", item.pcsQty)
                        put("weight", item.weight)
                        put("subTotal", item.subTotal)
                        put("description", item.description)
                        put("customer", item.customer)
                        put("noPag", item.noPag)
                    })
                }

                val body = JSONObject().apply {
                    put("source", "android-stowing")
                    put("selectedPag", selectedPag ?: "SEMUA PAG")
                    put("items", itemArray)
                }.toString()

                val connection = (URL(STOWING_WEBHOOK_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 30_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                }

                try {
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val response = stream?.use {
                        BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText()
                    }.orEmpty()
                    if (code in 200..299) Result.success(response.ifBlank { "OK" })
                    else Result.failure(Exception("n8n HTTP $code: ${response.ifBlank { "Tidak ada pesan" }}"))
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

}
