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

    private const val MODEL = "gemini-3.1-flash-lite"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    /**
     * V13.2 - Gemini 3.1 Flash-Lite BTB scanner.
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
Baca FOTO BTB yang diberikan secara visual. Foto bisa berupa foto penuh dari kamera/galeri
atau sudah dekat dengan kertas BTB. Temukan tabel BTB langsung dari foto dan fokus hanya pada
SEMUA baris tulisan tangan berat yang terlihat. Jangan mengasumsikan jumlah baris.

TUGAS UTAMA:
1. Fokus hanya pada kolom paling kiri bertuliskan JENIS BARANG. Tulisan tangan di kolom itu
   adalah angka berat per koli.
2. Tentukan sendiri jumlah baris data yang benar-benar terlihat pada tabel. Jangan mengasumsikan
   tabel selalu 4 baris dan jangan berhenti setelah 20 koli.
3. Pada setiap baris, baca SEMUA angka berat yang benar-benar tertulis dari kiri ke kanan.
   Jumlah angka per baris boleh berbeda. Jangan membatasi menjadi 5 angka.
4. Kembalikan satu objek untuk setiap baris data yang terlihat, berurutan row=1, row=2, dst.
   Jika satu angka benar-benar tidak terbaca, jangan mengarang; tetap masukkan angka lain yang terlihat.
5. Jangan berhenti setelah 20 angka. Jika ada 21, 30, 40, atau lebih koli yang terlihat, kembalikan semuanya.
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
12. Set isComplete=true hanya jika seluruh baris dan seluruh angka tulisan tangan yang terlihat sudah
    tercakup sampai baris terakhir. Jika ragu masih ada baris/angka yang terlewat, set false.
13. totalDetected wajib sama dengan jumlah seluruh angka pada semua rows.

Kembalikan JSON saja sesuai schema.
"""

    private const val VERIFY_PROMPT = """
Anda adalah pemeriksa kedua untuk tulisan tangan pada Slip BTB.
Foto yang diberikan adalah foto kertas BTB yang sudah di-crop.

Di bawah ini ada HASIL PEMBACAAN PERTAMA. Jangan langsung mempercayainya.
Periksa kembali setiap karakter langsung pada gambar, baris demi baris.

Aturan:
- Pertahankan pemisahan semua baris tabel yang terlihat.
- Jangan membatasi jumlah baris atau jumlah angka per baris.
- Fokus hanya kolom JENIS BARANG.
- Jangan mengambil angka dari kolom lain atau header.
- Jika kandidat salah karena 1 terbaca 7, 5 terbaca 7, 7 terbaca 1, dan sebagainya,
  koreksi berdasarkan bentuk tulisan pada gambar.
- Jangan mengoreksi angka hanya karena pola atau jumlah. Bukti visual adalah prioritas.
- Jangan menambah angka yang tidak terlihat.
- Jangan menggabungkan dua angka terpisah.
- Jika sebuah baris memang kosong/tidak terbaca, gunakan weights=[] .
- Kembalikan semua baris data yang terlihat, bukan tepat 4 baris.
- Set isComplete=true hanya jika seluruh tabel yang terlihat sudah tercakup.
- totalDetected harus sama dengan jumlah angka pada seluruh rows.

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

        val normalized = downscale(source, 2400)
        if (normalized !== source) source.recycle()

        try {
            // V13.4 speed/accuracy: jangan selalu melakukan 3 request Gemini.
            // Satu request langsung membaca seluruh foto. Jika hasil tidak lengkap,
            // barulah jalankan satu request verifikasi tambahan. Ini memangkas waktu
            // scan normal secara signifikan tanpa mengorbankan fallback untuk foto sulit.
            val firstJson = generateJson(apiKey, EXTRACT_PROMPT, normalized, rowsSchema())
            val firstParsed = parseRows(firstJson)
            val firstMeta = parseMeta(firstJson)
            val firstCount = firstParsed.sumOf { it.size }

            // V13.5: jumlah data DINAMIS. Angka 20 bukan batas.
            // Bila Gemini mengembalikan tepat 20, kita lakukan satu pemeriksaan tambahan
            // karena 20 adalah pola lama yang sering membuat hasil terpotong.
            val needsVerification = !firstMeta.isComplete ||
                firstCount == 20 ||
                firstParsed.dropLast(1).any { it.isEmpty() }

            val parsed = if (!needsVerification && firstCount > 0) {
                firstParsed
            } else {
                val verifiedJson = try {
                    generateJson(
                        apiKey,
                        VERIFY_PROMPT + firstJson,
                        normalized,
                        rowsSchema()
                    )
                } catch (_: Exception) {
                    firstJson
                }
                val verified = parseRows(verifiedJson)
                val verifiedMeta = parseMeta(verifiedJson)

                when {
                    verified.isEmpty() -> firstParsed
                    verifiedMeta.isComplete && verified.sumOf { it.size } >= firstCount -> verified
                    verified.sumOf { it.size } > firstCount -> verified
                    else -> firstParsed
                }
            }

            buildResult(parsed, "Gemini: ${parsed.sumOf { it.size }} koli terbaca.")
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
        // V13.5: jangan truncate hasil Gemini. Semua baris dan semua angka dipertahankan.
        val normalizedRows = rows.map { it.toList() }

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
            expectedRows = normalizedRows.size,
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
            .put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))

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
                throw IllegalStateException("Gemini API HTTP $status (model=$MODEL): ${response.take(700)}")
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
                .put("properties", JSONObject()
                    .put("ymin", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 1000))
                    .put("xmin", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 1000))
                    .put("ymax", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 1000))
                    .put("xmax", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 1000)))
                .put("required", JSONArray(listOf("ymin", "xmin", "ymax", "xmax")))
        ))
        .put("required", JSONArray(listOf("btbBox")))

    private fun rowsSchema(): JSONObject {
        val rowSchema = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("row", JSONObject()
                    .put("type", "integer")
                    .put("minimum", 1)
                    .put("maximum", 100))
                .put("weights", JSONObject()
                    .put("type", "array")
                    .put("items", JSONObject()
                        .put("type", "integer")
                        .put("minimum", 0)
                        .put("maximum", 999))
                    .put("minItems", 0)
                    .put("maxItems", 30)))
            .put("required", JSONArray(listOf("row", "weights")))

        return JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("rows", JSONObject()
                    .put("type", "array")
                    .put("items", rowSchema)
                    .put("minItems", 1)
                    .put("maxItems", 100))
                .put("isComplete", JSONObject().put("type", "boolean"))
                .put("totalDetected", JSONObject()
                    .put("type", "integer")
                    .put("minimum", 0)))
            .put("required", JSONArray(listOf("rows", "isComplete", "totalDetected")))
    }

    private data class ParseMeta(
        val isComplete: Boolean,
        val totalDetected: Int
    )

    private fun parseMeta(json: String): ParseMeta {
        val root = JSONObject(json)
        return ParseMeta(
            isComplete = root.optBoolean("isComplete", false),
            totalDetected = root.optInt("totalDetected", 0)
        )
    }

    private fun parseRows(json: String): List<List<Int>> {
        val root = JSONObject(json)
        val array = root.optJSONArray("rows") ?: return emptyList()

        // Map berdasarkan nomor baris lalu urutkan. Tidak ada batas 4 baris.
        // Bila model mengirim nomor baris yang sama dua kali, gabungkan agar data tidak hilang.
        val byRow = sortedMapOf<Int, MutableList<Int>>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val rowNo = obj.optInt("row", i + 1).coerceIn(1, 100)
            val values = obj.optJSONArray("weights") ?: JSONArray()
            val parsed = byRow.getOrPut(rowNo) { mutableListOf() }
            for (j in 0 until values.length()) {
                val v = values.optInt(j, Int.MIN_VALUE)
                if (v != Int.MIN_VALUE && v in 0..999) parsed += v
            }
        }
        return byRow.values.map { it.toList() }
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
