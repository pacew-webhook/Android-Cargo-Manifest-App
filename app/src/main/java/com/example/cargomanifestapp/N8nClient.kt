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
}
