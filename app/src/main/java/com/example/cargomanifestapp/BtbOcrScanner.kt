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
    private data class RowBox(val row: Int, val box: Box)
    private data class Layout(val weightColumn: Box?, val rows: List<RowBox>)

    private const val MODEL = "gemini-2.5-flash"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private const val LAYOUT_PROMPT = """
Anda adalah pemeriksa dokumen BTB (Bukti Timbang Barang) Indonesia.
Analisis FOTO BTB yang diberikan secara visual, bukan sekadar OCR teks mentah.

Tugas:
1. Temukan tabel BTB dan kolom paling kiri berjudul JENIS BARANG. Kolom inilah yang berisi tulisan tangan angka berat per koli.
2. Temukan batas x kolom JENIS BARANG dan batas y setiap baris data tulisan tangan.
3. Abaikan header, kolom JUMLAH KOLI, BERAT, JUMLAH BERAT, TOTAL, tanda tangan, dan tulisan di luar tabel.
4. Urutkan baris dari atas ke bawah.
5. Jangan membuat angka contoh. Jangan menebak nilai yang tidak terlihat.
6. Jika ada 10 baris formulir tetapi hanya 6 yang berisi tulisan, kembalikan hanya baris yang benar-benar memiliki tulisan angka.
7. Koordinat menggunakan skala 0..1000: [ymin, xmin, ymax, xmax].
8. Kotak kolom berat harus mencakup area tulisan tangan angka pada kolom JENIS BARANG, tetapi jangan mengambil kolom JUMLAH KOLI di sebelahnya.

Kembalikan JSON sesuai schema. Tidak boleh ada komentar atau markdown.
"""

    private const val ROW_PROMPT = """
Baca SATU BARIS tulisan tangan BTB pada gambar yang diberikan.

Aturan sangat penting:
- Gambar ini hanya mewakili SATU baris tabel. Jangan membaca angka dari baris lain.
- Baca angka tulisan tangan yang benar-benar terlihat pada baris tersebut, dari kiri ke kanan.
- Setiap kelompok angka dipisahkan oleh tanda titik, spasi, garis, atau jarak tulisan.
- Angka seperti 51, 57, 20, 42, 11, 13 adalah contoh FORMAT saja, bukan nilai yang harus dipaksakan.
- Jangan mengubah 1 menjadi 7 atau 7 menjadi 1 hanya karena konteks. Pilih berdasarkan bentuk tulisan pada gambar.
- Jangan memasukkan nomor baris, nomor halaman, angka tanggal, header, atau angka dari kolom lain.
- Jangan menebak angka yang tidak terlihat.
- Jika hanya sebagian angka yang terlihat jelas, keluarkan hanya angka yang dapat dibaca dengan wajar.
- Jangan menggabungkan dua angka terpisah menjadi satu angka besar.
- Hasil harus berupa JSON sesuai schema.
"""

    private const val FULL_FALLBACK_PROMPT = """
Analisis foto BTB ini secara visual. Baca tulisan tangan angka berat per koli yang berada di kolom JENIS BARANG, dari baris paling atas ke paling bawah.
Pisahkan hasil berdasarkan baris. Jangan membaca angka dari header, kolom JUMLAH KOLI, kolom BERAT, kolom JUMLAH BERAT, tanggal, atau TOTAL.
Jangan menebak angka yang tidak terlihat. Jangan menggunakan contoh angka sebagai jawaban.
Untuk setiap baris, keluarkan hanya angka yang benar-benar terlihat dan dapat dibaca.
Kembalikan JSON sesuai schema.
"""

    suspend fun scan(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isBlank()) {
            return@withContext Result(
                emptyList(),
                verificationMessage = "Gemini API key belum dikonfigurasi. Tambahkan GEMINI_API_KEY pada Gradle/GitHub Actions."
            )
        }

        val source = loadBitmapCorrectOrientation(context, uri)
            ?: return@withContext Result(emptyList(), verificationMessage = "Foto BTB tidak dapat dibaca")

        val image = downscale(source, 3200)
        if (image !== source) source.recycle()

        try {
            val layoutJson = generateJson(apiKey, LAYOUT_PROMPT, image, layoutSchema())
            val layout = parseLayout(layoutJson)

            val rows = layout.rows.sortedBy { it.row }
            val usefulRows = if (rows.size >= 2) rows else emptyList()

            if (usefulRows.isEmpty()) {
                val fallback = generateJson(apiKey, FULL_FALLBACK_PROMPT, image, fullRowsSchema())
                return@withContext resultFromFullJson(fallback, "Gemini fallback: layout baris tidak terdeteksi")
            }

            val allWeights = mutableListOf<Double>()
            val rowTexts = mutableListOf<String>()
            val raw = StringBuilder()

            for (row in usefulRows) {
                val rowCrop = cropRow(image, row.box, layout.weightColumn)
                if (rowCrop == null) {
                    rowTexts += ""
                    raw.append("Baris ${row.row}: (crop gagal)\n")
                    continue
                }

                try {
                    val rowJson = generateJson(apiKey, ROW_PROMPT, rowCrop, rowSchema())
                    val values = parseWeights(rowJson)
                    allWeights += values.map { it.toDouble() }
                    rowTexts += values.joinToString(" ")
                    raw.append("Baris ${row.row}: ")
                        .append(if (values.isEmpty()) "(tidak terbaca)" else values.joinToString(" "))
                        .append("\n")
                } catch (e: Exception) {
                    rowTexts += ""
                    raw.append("Baris ${row.row}: (error ${e.message ?: "Gemini"})\n")
                } finally {
                    rowCrop.recycle()
                }
            }

            val total = allWeights.sum()
            val message = if (allWeights.isNotEmpty()) {
                "Gemini membaca ${allWeights.size} koli. Total dihitung aplikasi = ${formatKg(total)} KG."
            } else {
                "Gemini belum membaca angka KG. Coba foto lebih dekat, lurus, dan seluruh tabel terlihat."
            }

            Result(
                weights = allWeights,
                rawText = raw.toString(),
                rows = rowTexts,
                expectedRows = usefulRows.size,
                calculatedTotalKg = total,
                verificationMessage = message
            )
        } finally {
            image.recycle()
        }
    }

    suspend fun scanAndDeleteTemp(context: Context, uri: Uri): Result = try {
        scan(context, uri)
    } finally {
        BtbPhotoStorage.deletePhoto(context, uri.toString())
    }

    private fun generateJson(
        apiKey: String,
        prompt: String,
        bitmap: Bitmap,
        schema: JSONObject
    ): String {
        val imageBytes = compressForApi(bitmap)
        val encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        val partImage = JSONObject()
            .put("inlineData", JSONObject()
                .put("mimeType", "image/jpeg")
                .put("data", encoded))

        val partText = JSONObject().put("text", prompt)
        val contents = JSONArray().put(
            JSONObject().put("role", "user").put(
                "parts", JSONArray().put(partText).put(partImage)
            )
        )

        val generationConfig = JSONObject()
            .put("responseMimeType", "application/json")
            .put("responseJsonSchema", schema)
            .put("temperature", 0.1)

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
                throw IllegalStateException("Gemini API HTTP $status: ${response.take(500)}")
            }

            val root = JSONObject(response)
            val candidates = root.optJSONArray("candidates")
                ?: throw IllegalStateException("Gemini tidak mengembalikan candidates")
            if (candidates.length() == 0) throw IllegalStateException("Gemini tidak mengembalikan hasil")

            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts")
                ?: throw IllegalStateException("Gemini tidak mengembalikan content")

            val text = buildString {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val t = part.optString("text", "")
                    if (t.isNotBlank()) append(t)
                }
            }.trim()

            return cleanJson(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun layoutSchema(): JSONObject = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject()
            .put("weightColumn", JSONObject()
                .put("type", "object")
                .put("properties", boxProperties())
                .put("required", JSONArray(listOf("ymin", "xmin", "ymax", "xmax"))))
            .put("rows", JSONObject()
                .put("type", "array")
                .put("items", JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject()
                        .put("row", JSONObject().put("type", "integer"))
                        .put("box", JSONObject()
                            .put("type", "object")
                            .put("properties", boxProperties())
                            .put("required", JSONArray(listOf("ymin", "xmin", "ymax", "xmax"))))
                    )
                    .put("required", JSONArray(listOf("row", "box")))))
        .put("required", JSONArray(listOf("rows")))

    private fun rowSchema(): JSONObject = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject()
            .put("weights", JSONObject()
                .put("type", "array")
                .put("items", JSONObject().put("type", "integer"))))
        .put("required", JSONArray(listOf("weights")))

    private fun fullRowsSchema(): JSONObject = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject()
            .put("rows", JSONObject()
                .put("type", "array")
                .put("items", JSONObject()
                    .put("type", "array")
                    .put("items", JSONObject().put("type", "integer")))))
        .put("required", JSONArray(listOf("rows")))

    private fun boxProperties(): JSONObject = JSONObject()
        .put("ymin", JSONObject().put("type", "integer"))
        .put("xmin", JSONObject().put("type", "integer"))
        .put("ymax", JSONObject().put("type", "integer"))
        .put("xmax", JSONObject().put("type", "integer"))

    private fun parseLayout(json: String): Layout {
        val root = JSONObject(json)
        val column = root.optJSONObject("weightColumn")?.let(::parseBox)
        val array = root.optJSONArray("rows") ?: JSONArray()
        val rows = mutableListOf<RowBox>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val box = obj.optJSONObject("box")?.let(::parseBox) ?: continue
            val row = obj.optInt("row", i + 1)
            if (box.ymax > box.ymin && box.xmax > box.xmin) rows += RowBox(row, box.clamp())
        }
        return Layout(column?.clamp(), rows.distinctBy { it.row })
    }

    private fun parseBox(obj: JSONObject): Box = Box(
        obj.optInt("ymin", 0),
        obj.optInt("xmin", 0),
        obj.optInt("ymax", 1000),
        obj.optInt("xmax", 1000)
    )

    private fun Box.clamp(): Box = Box(
        ymin.coerceIn(0, 1000), xmin.coerceIn(0, 1000),
        ymax.coerceIn(0, 1000), xmax.coerceIn(0, 1000)
    )

    private fun parseWeights(json: String): List<Int> {
        val root = JSONObject(json)
        val array = root.optJSONArray("weights") ?: return emptyList()
        val out = mutableListOf<Int>()
        for (i in 0 until array.length()) {
            val value = array.optInt(i, Int.MIN_VALUE)
            if (value != Int.MIN_VALUE && value in 1..999) out += value
        }
        return out
    }

    private fun resultFromFullJson(json: String, prefix: String): Result {
        val root = JSONObject(json)
        val rowsArray = root.optJSONArray("rows") ?: JSONArray()
        val rows = mutableListOf<String>()
        val weights = mutableListOf<Double>()
        for (i in 0 until rowsArray.length()) {
            val arr = rowsArray.optJSONArray(i)
            val values = mutableListOf<Int>()
            if (arr != null) {
                for (j in 0 until arr.length()) {
                    val v = arr.optInt(j, Int.MIN_VALUE)
                    if (v != Int.MIN_VALUE && v in 1..999) values += v
                }
            }
            rows += values.joinToString(" ")
            weights += values.map { it.toDouble() }
        }
        val total = weights.sum()
        return Result(
            weights = weights,
            rawText = prefix,
            rows = rows,
            expectedRows = rows.size,
            calculatedTotalKg = total,
            verificationMessage = "Gemini fallback membaca ${weights.size} koli. Total dihitung aplikasi = ${formatKg(total)} KG."
        )
    }

    private fun cropRow(bitmap: Bitmap, row: Box, column: Box?): Bitmap? {
        val x1 = ((column?.xmin ?: 20) / 1000f * bitmap.width).roundToInt()
        val x2 = ((column?.xmax ?: 650) / 1000f * bitmap.width).roundToInt()
        val y1 = (row.ymin / 1000f * bitmap.height).roundToInt()
        val y2 = (row.ymax / 1000f * bitmap.height).roundToInt()

        val padX = max(8, ((x2 - x1) * 0.04f).roundToInt())
        val padY = max(8, ((y2 - y1) * 0.10f).roundToInt())
        val left = (x1 - padX).coerceIn(0, bitmap.width - 1)
        val top = (y1 - padY).coerceIn(0, bitmap.height - 1)
        val right = (x2 + padX).coerceIn(left + 1, bitmap.width)
        val bottom = (y2 + padY).coerceIn(top + 1, bitmap.height)

        if (right - left < 20 || bottom - top < 12) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun compressForApi(bitmap: Bitmap): ByteArray {
        val maxSide = 1800
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
        image.compress(Bitmap.CompressFormat.JPEG, 94, out)
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
