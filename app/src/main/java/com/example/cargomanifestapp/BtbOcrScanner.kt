package com.example.cargomanifestapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * V13 - Gemini Vision BTB scanner.
 *
 * Pipeline:
 *   Foto/Galeri -> normalize/crop -> Gemini finds table + rows -> crop each row
 *   -> Gemini reads one row at a time -> JSON -> deterministic total -> dialog.
 *
 * Important: no BTB sample values are hard-coded. Gemini is asked to read the
 * visible handwriting from the supplied image. The app only validates and sums
 * the returned numbers.
 */
object BtbOcrScanner {

    data class Result(
        val weights: List<Double>,
        val rawText: String = "",
        val rows: List<String> = emptyList(),
        val expectedRows: Int = 0,
        val calculatedTotalKg: Double = weights.sum(),
        val verificationMessage: String = ""
    )

    private data class Box(val ymin: Int, val xmin: Int, val ymax: Int, val xmax: Int)

    private const val MODEL = "gemini-2.5-flash"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    /**
     * V13.1 - Gemini BTB scanner.
     *
     * Important design change from the previous V13:
     * - Do NOT ask Gemini to invent row coordinates for every line.
     * - First locate/crop the BTB document only.
     * - Then ask Gemini to segment the handwriting by the 10 horizontal data rows.
     * - Run a second visual verification pass against the same cropped image.
     *
     * This avoids the previous failure mode where a slightly wrong bounding box
     * caused the app to crop the wrong line and produce values from another row.
     */

    private const val LOCATE_PROMPT = """
Anda adalah vision model untuk aplikasi pembaca Slip Bukti Timbang Barang (BTB) Indonesia.
Foto yang diberikan dapat berisi meja, monitor, atau latar belakang. Temukan KERTAS BTB yang
berisi judul "BUKTI TIMBANG BARANG (BTB)" dan tabel tulisan tangan.

Kembalikan satu bounding box yang menutupi SELURUH KERTAS BTB, termasuk seluruh tabel tulisan
berat. Jangan memilih monitor atau kertas lain.
Koordinat harus skala 0..1000 dengan urutan [ymin, xmin, ymax, xmax].
Jangan memberikan angka berat. Hanya lokasi kertas BTB.
"""

    private const val EXTRACT_PROMPT = """
Baca FOTO BTB yang diberikan secara visual. FOTO SUDAH DI-CROP ke kertas BTB.

TUGAS UTAMA:
1. Fokus hanya pada kolom paling kiri bertuliskan JENIS BARANG. Tulisan tangan di kolom itu
   adalah angka berat per koli.
2. Form BTB pada foto ini memiliki 10 baris data horizontal di bawah header tabel.
3. Segmentasikan berdasarkan GARIS/BARIS TABEL, bukan berdasarkan hasil OCR yang digabung.
4. Kembalikan tepat 10 objek baris, row=1 sampai row=10. Jika satu baris kosong atau tidak
   terbaca, weights untuk baris itu harus [] .
5. Baca angka dari kiri ke kanan dalam setiap baris.
6. Jangan membaca angka dari header, tanggal, nama customer, kolom JUMLAH KOLI, kolom BERAT,
   kolom JUMLAH BERAT (KG), TOTAL, monitor, atau latar belakang.
7. Jangan mengarang angka. Hanya masukkan angka yang benar-benar terlihat sebagai tulisan tangan.
8. Jangan menggabungkan dua angka yang terpisah. Tanda titik/spasi/jarak tulisan dapat menjadi
   pemisah angka.
9. Sangat perhatikan perbedaan bentuk 1 vs 7, 5 vs 7, 3 vs 8, 2 vs 7, dan angka lain yang mirip.
   Nilai harus dipilih dari BENTUK TULISAN pada foto, bukan dari pola angka yang diharapkan.
10. Jangan mengubah angka hanya agar jumlah setiap baris sama. Jumlah angka per baris mengikuti
    yang benar-benar tertulis pada foto.
11. Angka 0 boleh muncul jika memang tertulis sebagai berat, tetapi nilai di luar 0..999 tidak valid.

Kembalikan JSON saja sesuai schema.
"""

    private const val VERIFY_PROMPT = """
Anda adalah pemeriksa kedua untuk tulisan tangan pada Slip BTB.
Foto yang diberikan adalah foto kertas BTB yang sudah di-crop.

Di bawah ini ada HASIL PEMBACAAN PERTAMA. Jangan langsung mempercayainya.
Periksa kembali setiap karakter langsung pada gambar, baris demi baris.

Aturan:
- Pertahankan pemisahan 10 baris tabel.
- Fokus hanya kolom JENIS BARANG.
- Jangan mengambil angka dari kolom lain atau header.
- Jika kandidat salah karena 1 terbaca 7, 5 terbaca 7, 7 terbaca 1, dan sebagainya,
  koreksi berdasarkan bentuk tulisan pada gambar.
- Jangan mengoreksi angka hanya karena pola atau jumlah. Bukti visual adalah prioritas.
- Jangan menambah angka yang tidak terlihat.
- Jangan menggabungkan dua angka terpisah.
- Jika sebuah baris memang kosong/tidak terbaca, gunakan weights=[] .
- Kembalikan tepat 10 baris.

HASIL PEMBACAAN PERTAMA:
"""

    suspend fun scan(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isBlank()) {
            return@withContext Result(
                emptyList(),
                verificationMessage = "Gemini API key belum masuk ke APK. Pastikan GitHub Secret GEMINI_API_KEY dipakai saat build."
            )
        }

        val source = loadBitmapCorrectOrientation(context, uri)
            ?: return@withContext Result(emptyList(), verificationMessage = "Foto BTB tidak dapat dibaca")

        val normalized = downscale(source, 3200)
        if (normalized !== source) source.recycle()

        try {
            val btb = try {
                val locateJson = generateJson(apiKey, LOCATE_PROMPT, normalized, locateSchema())
                val box = parseBox(JSONObject(locateJson).optJSONObject("btbBox"))
                cropBox(normalized, box, 0.03f, 0.03f) ?: normalized
            } catch (_: Exception) {
                normalized
            }

            val ownsCrop = btb !== normalized
            try {
                val firstJson = generateJson(apiKey, EXTRACT_PROMPT, btb, rowsSchema())
                val verifiedJson = try {
                    generateJson(
                        apiKey,
                        VERIFY_PROMPT + firstJson,
                        btb,
                        rowsSchema()
                    )
                } catch (_: Exception) {
                    firstJson
                }

                val parsed = parseRows(verifiedJson)
                if (parsed.all { it.isEmpty() }) {
                    val firstParsed = parseRows(firstJson)
                    return@withContext buildResult(firstParsed, "Gemini visual pass menghasilkan baris kosong; memakai pass pertama.")
                }
                buildResult(parsed, "Gemini 2-pass: pembacaan baris + verifikasi visual.")
            } finally {
                if (ownsCrop) btb.recycle()
            }
        } catch (e: Exception) {
            Result(
                emptyList(),
                verificationMessage = "Gemini gagal membaca BTB: ${e.message ?: "kesalahan tidak diketahui"}"
            )
        } finally {
            normalized.recycle()
        }
    }

    suspend fun scanAndDeleteTemp(context: Context, uri: Uri): Result = try {
        scan(context, uri)
    } finally {
        BtbPhotoStorage.deletePhoto(context, uri.toString())
    }

    private fun buildResult(rows: List<List<Int>>, prefix: String): Result {
        val normalizedRows = rows.take(10).toMutableList()
        while (normalizedRows.size < 10) normalizedRows += emptyList()

        val weights = normalizedRows.flatten().map { it.toDouble() }
        val rowTexts = normalizedRows.map { row -> row.joinToString(" ") }
        val total = weights.sum()
        val count = weights.size

        return Result(
            weights = weights,
            rawText = normalizedRows.mapIndexed { i, row ->
                "Baris ${i + 1}: ${if (row.isEmpty()) "(tidak terbaca)" else row.joinToString(" ")}"
            }.joinToString("\n"),
            rows = rowTexts,
            expectedRows = 10,
            calculatedTotalKg = total,
            verificationMessage = "$prefix Koli terbaca: $count. Total dihitung aplikasi = ${formatKg(total)} KG."
        )
    }

    private fun generateJson(
        apiKey: String,
        prompt: String,
        bitmap: Bitmap,
        schema: JSONObject
    ): String {
        val imageBytes = compressForApi(bitmap)
        val encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        val partText = JSONObject().put("text", prompt)
        val partImage = JSONObject().put(
            "inlineData",
            JSONObject()
                .put("mimeType", "image/jpeg")
                .put("data", encoded)
        )

        val contents = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(partText).put(partImage))
        )

        val generationConfig = JSONObject()
            .put("responseMimeType", "application/json")
            .put("responseJsonSchema", schema)
            .put("temperature", 0.0)

        val body = JSONObject()
            .put("contents", contents)
            .put("generationConfig", generationConfig)

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 45_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("Gemini API HTTP $status: ${response.take(700)}")
            }

            val root = JSONObject(response)
            val candidates = root.optJSONArray("candidates")
                ?: throw IllegalStateException("Gemini tidak mengembalikan candidates")
            if (candidates.length() == 0) throw IllegalStateException("Gemini tidak mengembalikan hasil")

            val candidate = candidates.optJSONObject(0)
                ?: throw IllegalStateException("Candidate Gemini kosong")
            val finishReason = candidate.optString("finishReason", "")
            if (finishReason == "SAFETY") {
                throw IllegalStateException("Respons Gemini dihentikan oleh safety filter")
            }

            val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                ?: throw IllegalStateException("Gemini tidak mengembalikan content")

            val text = buildString {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val t = part.optString("text", "")
                    if (t.isNotBlank()) append(t)
                }
            }.trim()

            if (text.isBlank()) throw IllegalStateException("Gemini mengembalikan teks kosong")
            return cleanJson(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun locateSchema(): JSONObject = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject().put(
            "btbBox",
            JSONObject()
                .put("type", "object")
                .put("properties", boxProperties())
                .put("required", JSONArray(listOf("ymin", "xmin", "ymax", "xmax")))
        ))
        .put("required", JSONArray(listOf("btbBox")))

    private fun rowsSchema(): JSONObject {
        val rowSchema = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("row", JSONObject().put("type", "integer"))
                .put("weights", JSONObject()
                    .put("type", "array")
                    .put("items", JSONObject().put("type", "integer"))
                    .put("maxItems", 12)))
            .put("required", JSONArray(listOf("row", "weights")))

        return JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("rows", JSONObject()
                    .put("type", "array")
                    .put("items", rowSchema)
                    .put("minItems", 10)
                    .put("maxItems", 10)))
            .put("required", JSONArray(listOf("rows")))
    }

    private fun boxProperties(): JSONObject = JSONObject()
        .put("ymin", JSONObject().put("type", "integer"))
        .put("xmin", JSONObject().put("type", "integer"))
        .put("ymax", JSONObject().put("type", "integer"))
        .put("xmax", JSONObject().put("type", "integer"))

    private fun parseRows(json: String): List<List<Int>> {
        val root = JSONObject(json)
        val array = root.optJSONArray("rows") ?: return List(10) { emptyList() }
        val byRow = Array(10) { emptyList<Int>() }
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val rowNo = obj.optInt("row", i + 1)
            if (rowNo !in 1..10) continue
            val values = obj.optJSONArray("weights") ?: JSONArray()
            val parsed = mutableListOf<Int>()
            for (j in 0 until values.length()) {
                val v = values.optInt(j, Int.MIN_VALUE)
                if (v != Int.MIN_VALUE && v in 0..999) parsed += v
            }
            byRow[rowNo - 1] = parsed
        }
        return byRow.toList()
    }

    private fun parseBox(obj: JSONObject?): Box? {
        if (obj == null) return null
        val box = Box(
            obj.optInt("ymin", 0),
            obj.optInt("xmin", 0),
            obj.optInt("ymax", 1000),
            obj.optInt("xmax", 1000)
        ).clamp()
        return if (box.ymax > box.ymin && box.xmax > box.xmin) box else null
    }

    private fun Box.clamp(): Box = Box(
        ymin.coerceIn(0, 1000),
        xmin.coerceIn(0, 1000),
        ymax.coerceIn(0, 1000),
        xmax.coerceIn(0, 1000)
    )

    private fun cropBox(bitmap: Bitmap, box: Box?, padXRatio: Float, padYRatio: Float): Bitmap? {
        if (box == null) return null
        val x1 = (box.xmin / 1000f * bitmap.width).roundToInt()
        val x2 = (box.xmax / 1000f * bitmap.width).roundToInt()
        val y1 = (box.ymin / 1000f * bitmap.height).roundToInt()
        val y2 = (box.ymax / 1000f * bitmap.height).roundToInt()
        val padX = max(8, ((x2 - x1) * padXRatio).roundToInt())
        val padY = max(8, ((y2 - y1) * padYRatio).roundToInt())
        val left = (x1 - padX).coerceIn(0, bitmap.width - 1)
        val top = (y1 - padY).coerceIn(0, bitmap.height - 1)
        val right = (x2 + padX).coerceIn(left + 1, bitmap.width)
        val bottom = (y2 + padY).coerceIn(top + 1, bitmap.height)
        if (right - left < 100 || bottom - top < 100) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun compressForApi(bitmap: Bitmap): ByteArray {
        val maxSide = 2048
        val image = if (max(bitmap.width, bitmap.height) > maxSide) {
            val scale = maxSide.toFloat() / max(bitmap.width, bitmap.height).toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * scale).roundToInt()),
                max(1, (bitmap.height * scale).roundToInt()),
                true
            )
        } else bitmap

        val out = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, 95, out)
        if (image !== bitmap) image.recycle()
        return out.toByteArray()
    }

    private fun loadBitmapCorrectOrientation(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

        val orientation = try {
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(270f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(90f)
            }
        }

        if (matrix.isIdentity) return bitmap
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun downscale(source: Bitmap, maxWidth: Int): Bitmap {
        if (source.width <= maxWidth) return source
        val scale = maxWidth.toFloat() / source.width.toFloat()
        return Bitmap.createScaledBitmap(
            source,
            maxWidth,
            max(1, (source.height * scale).roundToInt()),
            true
        )
    }

    private fun cleanJson(text: String): String {
        var value = text.trim()
        if (value.startsWith("```")) {
            value = value.removePrefix("```").trimStart()
            if (value.startsWith("json", ignoreCase = true)) value = value.substring(4).trimStart()
            if (value.endsWith("```")) value = value.dropLast(3).trimEnd()
        }
        val first = value.indexOf('{')
        val last = value.lastIndexOf('}')
        if (first >= 0 && last > first) return value.substring(first, last + 1)
        throw IllegalStateException("Respons Gemini bukan JSON")
    }

    private fun formatKg(value: Double): String =
        if (value % 1.0 == 0.0) value.roundToInt().toString() else "%.1f".format(value)
}
