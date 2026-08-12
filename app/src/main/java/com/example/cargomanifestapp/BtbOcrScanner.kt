package com.example.cargomanifestapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.resume

/**
 * V10 - Scanner BTB yang lebih toleran terhadap variasi foto dan tulisan tangan.
 *
 * Strategi:
 * 1) OCR beberapa area, bukan satu crop tetap.
 * 2) Gunakan hasil OCR beserta posisi (x/y) untuk mempertahankan urutan baris.
 * 3) Jalankan beberapa preprocessing: asli, grayscale/contrast, threshold.
 * 4) Gabungkan hasil dari beberapa pass dan deduplikasi berdasarkan posisi.
 * 5) Kelompokkan kembali menjadi baris agar pengguna bisa memeriksa hasil.
 *
 * Catatan: ML Kit Text Recognition bukan model handwriting khusus. V10 sengaja
 * tidak menebak angka yang tidak terbaca; angka yang meragukan tetap ditampilkan
 * untuk koreksi manual.
 */
object BtbOcrScanner {

    data class Result(
        val weights: List<Double>,
        val rawText: String = "",
        val rows: List<String> = emptyList()
    )

    private data class CropSpec(
        val name: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    private data class Candidate(
        val value: Int,
        val x: Float,
        val y: Float,
        val source: String
    )

    private data class OcrLine(
        val text: String,
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float
    )

    suspend fun scan(context: Context, uri: Uri): Result = withContext(Dispatchers.Default) {
        val bitmap = loadBitmap(context, uri) ?: return@withContext Result(emptyList())

        // Jangan mengandalkan satu posisi BTB. Foto dari kamera/galeri dapat
        // memiliki crop, zoom, dan posisi kertas yang berbeda.
        val specs = listOf(
            CropSpec("full", 0.00f, 0.20f, 0.92f, 0.98f),
            CropSpec("table-wide", 0.02f, 0.32f, 0.84f, 0.98f),
            CropSpec("table-left", 0.03f, 0.36f, 0.78f, 0.98f),
            CropSpec("legacy", 0.055f, 0.40f, 0.78f, 0.94f)
        )

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val candidates = mutableListOf<Candidate>()
        val rawParts = mutableListOf<String>()

        try {
            for (spec in specs) {
                val crop = cropRelative(bitmap, spec)
                if (crop.width < 120 || crop.height < 120) {
                    crop.recycle()
                    continue
                }

                val variants = listOf(
                    crop,
                    enhance(crop, null),
                    enhance(crop, 150),
                    enhance(crop, 190)
                )

                for ((variantIndex, variant) in variants.withIndex()) {
                    val result = recognize(recognizer, variant) ?: continue
                    if (result.text.isNotBlank()) {
                        rawParts += "[${spec.name}/v$variantIndex] ${result.text}"
                    }

                    result.lines.forEach { line ->
                        val tokens = extractNumberTokens(line.text)
                        if (tokens.isEmpty()) return@forEach

                        // Karena ML Kit kadang mengembalikan satu bounding box untuk
                        // seluruh rangkaian "9.8.26.37.37", bagi posisi token secara
                        // proporsional agar urutan kiri-kanan tetap dapat direkonstruksi.
                        val tokenCount = tokens.size
                        tokens.forEachIndexed { index, token ->
                            val centerX = line.left + line.width * ((index + 0.5f) / tokenCount)
                            val centerY = line.top + line.height / 2f
                            val absoluteX = (spec.left + centerX / crop.width * (spec.right - spec.left)).coerceIn(0f, 1f)
                            val absoluteY = (spec.top + centerY / crop.height * (spec.bottom - spec.top)).coerceIn(0f, 1f)
                            candidates += Candidate(token.toInt(), absoluteX, absoluteY, spec.name)
                        }
                    }
                }

                variants.drop(1).forEach { it.recycle() }
                crop.recycle()
            }

            // Gabungkan hasil dari overlapping crops. Kandidat yang terlalu dekat
            // dianggap angka yang sama agar OCR multi-pass tidak menggandakan data.
            val deduped = deduplicate(candidates)

            // Fokuskan ke bagian bawah-kiri yang memang merupakan area tulisan
            // tangan. Jika hasilnya terlalu sedikit, gunakan semua kandidat.
            val focused = deduped.filter { it.y in 0.32f..0.98f && it.x in 0.02f..0.82f }
            val selected = if (focused.size >= 5) focused else deduped

            val ordered = selected.sortedWith(compareBy<Candidate> { it.y }.thenBy { it.x })
            val rows = buildRows(ordered)
            val weights = rows.flatMap { row ->
                extractNumberTokens(row).mapNotNull { it.toDoubleOrNull() }
            }

            Result(
                weights = weights,
                rawText = rawParts.joinToString("\n"),
                rows = rows
            )
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    suspend fun scanAndDeleteTemp(context: Context, uri: Uri): Result {
        return try {
            scan(context, uri)
        } finally {
            BtbPhotoStorage.deletePhoto(context, uri.toString())
        }
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        val input: InputStream = context.contentResolver.openInputStream(uri) ?: return null
        return input.use { BitmapFactory.decodeStream(it) }
    }

    private fun cropRelative(source: Bitmap, spec: CropSpec): Bitmap {
        val left = (source.width * spec.left).toInt().coerceIn(0, source.width - 1)
        val top = (source.height * spec.top).toInt().coerceIn(0, source.height - 1)
        val right = (source.width * spec.right).toInt().coerceIn(left + 1, source.width)
        val bottom = (source.height * spec.bottom).toInt().coerceIn(top + 1, source.height)
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }

    private fun enhance(source: Bitmap, threshold: Int?): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)

        for (i in pixels.indices) {
            val c = pixels[i]
            val gray = (Color.red(c) * 0.299 + Color.green(c) * 0.587 + Color.blue(c) * 0.114).toInt()
            val value = if (threshold != null) {
                if (gray < threshold) 0 else 255
            } else {
                // Menekan warna kertas dan mengangkat tinta gelap.
                ((gray - 50) * 1.8).toInt().coerceIn(0, 255)
            }
            pixels[i] = Color.rgb(value, value, value)
        }
        out.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        return out
    }

    private suspend fun recognize(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap
    ): Text? = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resume(null) }
    }

    private fun extractNumberTokens(text: String): List<String> {
        // Tangani pemisah umum tulisan tangan/OCR: spasi, titik, koma, dash.
        // Hanya 1-3 digit karena berat per koli pada format ini berada di rentang
        // angka kecil, dan filter ini mengurangi teks header yang ikut terbaca.
        val normalized = text
            .replace('O', '0')
            .replace('o', '0')
            .replace('I', '1')
            .replace('l', '1')
            .replace('|', '1')
            .replace('S', '5')
            .replace('s', '5')
            .replace('B', '8')
            .replace('b', '8')

        return Regex("(?<!\\d)\\d{1,3}(?!\\d)")
            .findAll(normalized)
            .map { it.value }
            .filter { token -> token.toIntOrNull()?.let { it in 1..999 } == true }
            .toList()
    }

    private fun deduplicate(input: List<Candidate>): List<Candidate> {
        val result = mutableListOf<Candidate>()
        // Kandidat dari pass berbeda bisa bergeser sedikit. Toleransi ini cukup
        // longgar untuk foto miring tetapi cukup ketat agar angka berbeda tidak
        // digabungkan.
        for (candidate in input.sortedWith(compareBy<Candidate> { it.y }.thenBy { it.x })) {
            val duplicate = result.any {
                it.value == candidate.value &&
                    kotlin.math.abs(it.x - candidate.x) < 0.018f &&
                    kotlin.math.abs(it.y - candidate.y) < 0.018f
            }
            if (!duplicate) result += candidate
        }
        return result
    }

    private fun buildRows(candidates: List<Candidate>): List<String> {
        if (candidates.isEmpty()) return emptyList()

        val rows = mutableListOf<MutableList<Candidate>>()
        // Jarak antar baris BTB jauh lebih besar daripada pergeseran OCR pada satu
        // baris. Toleransi adaptif membantu foto dari orang yang berbeda.
        val tolerance = 0.032f

        for (candidate in candidates) {
            val row = rows.firstOrNull { existing ->
                val avgY = existing.map { it.y }.average().toFloat()
                kotlin.math.abs(avgY - candidate.y) <= tolerance
            }
            if (row == null) rows += mutableListOf(candidate) else row += candidate
        }

        return rows
            .sortedBy { row -> row.map { it.y }.average() }
            .map { row ->
                row.sortedBy { it.x }
                    .joinToString(" ") { it.value.toString() }
            }
    }
}
