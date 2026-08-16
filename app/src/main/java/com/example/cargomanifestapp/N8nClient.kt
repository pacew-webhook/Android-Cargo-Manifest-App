package com.example.cargomanifestapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client HTTP untuk integrasi Android -> n8n.
 *
 * Versi V3:
 * - Manifest tetap dapat dikirim sebagai JSON.
 * - Stowing menggunakan file Excel hasil ExcelUtils (bukan Python/JSON).
 * - n8n menerima multipart/form-data dan menyimpan/replace Cargo_Manifest.xlsx di laptop.
 */
object N8nClient {
    // Ubah SATU nilai ini jika IP laptop berubah.
    const val LAPTOP_IP = "10.18.242.83"
    const val N8N_PORT = 5678

    const val WEBHOOK_URL =
        "http://$LAPTOP_IP:$N8N_PORT/webhook/cargo/manifest/items"

    const val STOWING_EXCEL_WEBHOOK_URL =
        "http://$LAPTOP_IP:$N8N_PORT/webhook/cargo/stowing-excel"

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
     * Membuat Cargo_Manifest.xlsx menggunakan logika Export Excel Android
     * yang sama, lalu upload file tersebut ke n8n.
     *
     * n8n akan mengganti/overwrite Cargo_Manifest.xlsx di laptop.
     */
    suspend fun sendStowingExcel(
        context: Context,
        items: List<CargoItem>
    ): Result<String> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Data Stowing kosong")
            )
        }

        val tempFile = File(
            context.cacheDir,
            "Cargo_Manifest_upload.xlsx"
        )

        try {
            // File yang diupload adalah workbook yang dibuat oleh ExcelUtils,
            // sehingga formatnya sama dengan Export Excel Android.
            ExcelUtils.writeCombinedCargoWorkbookToFile(
                context = context,
                file = tempFile,
                cargoList = items
            )

            val boundary = "----CargoManifest${System.currentTimeMillis()}"
            val connection =
                (URL(STOWING_EXCEL_WEBHOOK_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    doOutput = true
                    useCaches = false
                    setRequestProperty(
                        "Content-Type",
                        "multipart/form-data; boundary=$boundary"
                    )
                    setRequestProperty("Accept", "application/json")
                }

            try {
                connection.outputStream.use { output ->
                    val lineBreak = "\r\n"
                    val writer = output.bufferedWriter(Charsets.UTF_8)

                    writer.write("--$boundary$lineBreak")
                    writer.write(
                        "Content-Disposition: form-data; name=\"data\"; filename=\"Cargo_Manifest.xlsx\"$lineBreak"
                    )
                    writer.write("Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet$lineBreak")
                    writer.write(lineBreak)
                    writer.flush()

                    tempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }

                    writer.write(lineBreak)
                    writer.write("--$boundary--$lineBreak")
                    writer.flush()
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
                    Result.success(responseText.ifBlank { "Excel berhasil dikirim ke laptop" })
                } else {
                    Result.failure(
                        Exception(
                            "n8n HTTP $responseCode: ${
                                responseText.ifBlank { "Tidak ada pesan" }
                            }"
                        )
                    )
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Dipertahankan untuk kompatibilitas kode lama.
     * Pengiriman Stowing sekarang diarahkan ke model file Excel V3.
     */
    suspend fun sendStowing(
        context: Context,
        items: List<CargoItem>,
        selectedPag: String? = null
    ): Result<String> = sendStowingExcel(context, items)
}
