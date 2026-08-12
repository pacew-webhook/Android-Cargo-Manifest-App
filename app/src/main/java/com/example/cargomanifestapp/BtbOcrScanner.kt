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
 * Scanner BTB khusus format slip pada project ini.
 *
 * V9 tidak lagi mengirim seluruh foto ke OCR sekaligus. Foto dipotong ke area
 * tabel tulisan tangan, dibuat beberapa versi (asli + kontras + threshold),
 * lalu diproses per baris. Ini mengurangi angka dari header/kolom lain dan
 * membuat angka yang hilang lebih mudah ditemukan.
 *
 * Catatan penting: ML Kit Text Recognition adalah OCR umum dan bukan mesin
 * handwriting khusus. Karena itu hasil tetap ditampilkan di dialog koreksi.
 */
object BtbOcrScanner {

    data class Result(
        val weights: List<Double>,
        val rawText: String = "",
        val rows: List<String> = emptyList()
    )

    private data class Candidate(
        val value: Double,
        val row: Int,
        val centerX: Float
    )

    suspend fun scan(context: Context, uri: Uri): Result = withContext(Dispatchers.Default) {
        val bitmap = loadBitmap(context, uri) ?: return@withContext Result(emptyList())

        // Berdasarkan layout BTB yang dipakai project: area tulisan tangan berada
        // di kiri, mulai setelah header dan sebelum kolom JUMLAH KOLI.
        val table = cropTable(bitmap)
        if (table.width < 100 || table.height < 100) {
            return@withContext Result(emptyList())
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val allCandidates = mutableListOf<Candidate>()
            val raw = StringBuilder()
            val rowTexts = MutableList(10) { "" }

            // 10 baris adalah pola BTB pada contoh. Baris terakhir boleh hanya
            // berisi sebagian angka; kita tidak memaksa jumlah 5.
            for (row in 0 until 10) {
                val y0 = (row * table.height / 10f).toInt().coerceIn(0, table.height - 1)
                val y1 = (((row + 1) * table.height / 10f).toInt()).coerceIn(y0 + 1, table.height)
                val rowBitmap = Bitmap.createBitmap(table, 0, y0, table.width, y1 - y0)

                val variants = listOf(
                    rowBitmap,
                    enhance(rowBitmap, threshold = null),
                    enhance(rowBitmap, threshold = 165)
                )

                var bestTokens = emptyList<String>()
                var bestRaw = ""
                for (variant in variants) {
                    val text = recognize(recognizer, variant) ?: continue
                    val tokens = extractNumberTokens(text)
                    if (tokens.size > bestTokens.size) {
                        bestTokens = tokens
                        bestRaw = text
                    }
                }

                rowTexts[row] = bestTokens.joinToString(" ")
                if (bestRaw.isNotBlank()) raw.append(bestRaw).append('\n')

                bestTokens.forEachIndexed { index, token ->
                    token.toDoubleOrNull()?.let { value ->
                        if (value in 1.0..999.0) {
                            allCandidates += Candidate(value, row, index.toFloat())
                        }
                    }
                }

                rowBitmap.recycle()
                variants.drop(1).forEach { it.recycle() }
            }

            val ordered = allCandidates.sortedWith(compareBy<Candidate> { it.row }.thenBy { it.centerX })
            Result(
                weights = ordered.map { it.value },
                rawText = raw.toString(),
                rows = rowTexts
            )
        } finally {
            recognizer.close()
            table.recycle()
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

    private fun cropTable(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        // Area relatif terhadap foto penuh. Memberi sedikit margin agar angka
        // di tepi tidak terpotong.
        val left = (w * 0.055f).toInt()
        val top = (h * 0.485f).toInt()
        val right = (w * 0.73f).toInt()
        val bottom = (h * 0.765f).toInt()
        return Bitmap.createBitmap(
            source,
            left.coerceIn(0, w - 1),
            top.coerceIn(0, h - 1),
            (right - left).coerceAtLeast(1).coerceAtMost(w - left),
            (bottom - top).coerceAtLeast(1).coerceAtMost(h - top)
        )
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
                // Contrast stretch sederhana agar tinta hitam lebih dominan
                // terhadap kertas kuning/cokelat.
                ((gray - 45) * 1.65).toInt().coerceIn(0, 255)
            }
            pixels[i] = Color.rgb(value, value, value)
        }
        out.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        return out
    }

    private suspend fun recognize(recognizer: com.google.mlkit.vision.text.TextRecognizer, bitmap: Bitmap): String? =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { continuation.resume(it.text) }
                .addOnFailureListener { continuation.resume(null) }
        }

    private fun extractNumberTokens(text: String): List<String> {
        // OCR sering mengubah pemisah titik menjadi spasi atau sebaliknya.
        // Hanya angka 1-3 digit yang diterima agar header/teks BTB tidak masuk.
        return Regex("(?<!\\d)\\d{1,3}(?!\\d)")
            .findAll(text)
            .map { it.value }
            .toList()
    }
}
