package com.example.cargomanifestapp

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client HTTP sederhana untuk mengirim data Stowing ke workflow n8n.
 *
 * Endpoint Production n8n:
 * http://10.18.242.83:5678/webhook/cargo/stowing
 *
 * Jika IP laptop berubah, ubah N8N_STOWING_URL di sini.
 */
object N8nClient {

    const val N8N_STOWING_URL =
        "http://10.18.242.83:5678/webhook/cargo/stowing"

    data class Result(
        val success: Boolean,
        val httpCode: Int,
        val message: String
    )

    fun sendStowing(noPag: String, items: List<CargoItem>): Result {
        if (items.isEmpty()) {
            return Result(false, 0, "Data Stowing kosong.")
        }

        val body = JSONObject().apply {
            put("noPag", noPag)
            put("pag", noPag)

            val jsonItems = JSONArray()
            items.forEach { item ->
                jsonItems.put(
                    JSONObject().apply {
                        put("pti", item.pti)
                        put("pcsQty", item.pcsQty)
                        put("weight", item.weight)
                        put("subTotal", item.subTotal)
                        put("description", item.description)
                        put("customer", item.customer)
                        put("noPag", item.noPag)
                        put("awbNo", item.awbNo)
                        put("flightNo", item.flightNo)
                    }
                )
            }
            put("items", jsonItems)
        }

        var connection: HttpURLConnection? = null

        return try {
            connection = (URL(N8N_STOWING_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 30_000
                doOutput = true
                doInput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
                output.flush()
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val responseText = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }.orEmpty()

            if (code in 200..299) {
                Result(
                    true,
                    code,
                    if (responseText.isBlank()) {
                        "Stowing berhasil dikirim ke n8n."
                    } else {
                        "n8n: $responseText"
                    }
                )
            } else {
                Result(
                    false,
                    code,
                    if (responseText.isBlank()) {
                        "n8n HTTP $code"
                    } else {
                        "n8n HTTP $code: $responseText"
                    }
                )
            }
        } catch (e: Exception) {
            Result(
                false,
                0,
                "Gagal terhubung ke n8n: ${e.message ?: e.javaClass.simpleName}"
            )
        } finally {
            connection?.disconnect()
        }
    }
}
