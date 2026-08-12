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
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * V11 - BTB OCR berbasis segmentasi.
 *
 * Berbeda dari V10 yang mengumpulkan angka dari seluruh foto, V11:
 * 1. mencari header JENIS BARANG untuk mendapatkan posisi tabel;
 * 2. mencari TOTAL sebagai batas bawah bila tersedia;
 * 3. mengambil hanya kolom JENIS BARANG;
 * 4. membagi kolom menjadi baris berdasarkan garis tabel;
 * 5. membersihkan garis horizontal/vertikal;
 * 6. mencari grup tulisan (token) dari setiap baris;
 * 7. OCR setiap token secara terpisah;
 * 8. mempertahankan urutan kiri -> kanan dan baris -> baris.
 *
 * Tujuan utama V11 adalah mencegah OCR membaca seluruh tabel sebagai satu teks.
 */
object BtbOcrScanner {

    data class Result(
        val weights: List<Double>,
        val rawText: String = "",
        val rows: List<String> = emptyList(),
        val expectedRows: Int = 0
    )

    private data class Anchor(val left: Int, val top: Int, val right: Int, val bottom: Int, val text: String)
    private data class TokenBox(val left: Int, val top: Int, val right: Int, val bottom: Int)
    private data class TableRegion(val left: Int, val top: Int, val right: Int, val bottom: Int)

    suspend fun scan(context: Context, uri: Uri): Result = withContext(Dispatchers.Default) {
        val original = loadBitmap(context, uri) ?: return@withContext Result(emptyList())
        val bitmap = downscale(original, 1800)
        if (bitmap !== original) original.recycle()

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val fullText = recognize(recognizer, bitmap)
            val anchors = fullText?.let { findAnchors(it) }.orEmpty()
            val table = locateTable(bitmap, anchors)

            if (table == null) {
                return@withContext fallbackScan(recognizer, bitmap)
            }

            val rows = splitRows(bitmap, table)
            if (rows.isEmpty()) {
                return@withContext fallbackScan(recognizer, bitmap)
            }

            val rowResults = mutableListOf<String>()
            val allWeights = mutableListOf<Double>()
            val raw = StringBuilder()

            for ((index, rowRect) in rows.withIndex()) {
                val rowBitmap = safeCrop(bitmap, rowRect.first, rowRect.second, rowRect.third, rowRect.fourth)
                if (rowBitmap == null) {
                    rowResults += ""
                    continue
                }

                val cleaned = cleanTableLines(rowBitmap)
                val tokens = segmentTokens(cleaned)

                // Bila segmentasi terlalu sedikit, OCR satu baris sebagai fallback,
                // tetapi tetap hanya pada baris ini, bukan seluruh foto.
                val tokenValues = mutableListOf<Double>()
                if (tokens.isNotEmpty()) {
                    for (token in tokens) {
                        val tokenBitmap = safeCrop(cleaned, token.left, token.top, token.right, token.bottom)
                        if (tokenBitmap != null) {
                            val value = recognizeToken(recognizer, tokenBitmap)
                            if (value != null) tokenValues += value
                            tokenBitmap.recycle()
                        }
                    }
                }

                if (tokenValues.isEmpty()) {
                    val rowOcr = recognize(recognizer, cleaned)
                    val values = extractNumberTokens(rowOcr?.text.orEmpty())
                    tokenValues += values.map { it.toDouble() }
                    raw.append("Baris ${index + 1}: ${rowOcr?.text.orEmpty()}\n")
                }

                val normalized = tokenValues.map { normalizeWeight(it) }
                allWeights += normalized
                rowResults += normalized.joinToString(" ") { formatNumber(it) }
                raw.append("Baris ${index + 1}: ")
                    .append(normalized.joinToString(" ") { formatNumber(it) })
                    .append("\n")

                cleaned.recycle()
                rowBitmap.recycle()
            }

            Result(
                weights = allWeights,
                rawText = raw.toString(),
                rows = rowResults,
                expectedRows = rows.size
            )
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    suspend fun scanAndDeleteTemp(context: Context, uri: Uri): Result {
        return try { scan(context, uri) }
        finally { BtbPhotoStorage.deletePhoto(context, uri.toString()) }
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? =
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }

    private fun downscale(source: Bitmap, maxWidth: Int): Bitmap {
        if (source.width <= maxWidth) return source
        val scale = maxWidth.toFloat() / source.width.toFloat()
        return Bitmap.createScaledBitmap(source, maxWidth, (source.height * scale).toInt(), true)
    }

    private fun findAnchors(text: Text): List<Anchor> {
        val result = mutableListOf<Anchor>()
        text.textBlocks.flatMap { it.lines }.forEach { line ->
            val box = line.boundingBox ?: return@forEach
            val normalized = line.text.uppercase().replace(" ", "")
            if (normalized.contains("JENIS") || normalized.contains("BARANG") || normalized.contains("JUMLAHKOLI") || normalized.contains("TOTAL")) {
                result += Anchor(box.left, box.top, box.right, box.bottom, line.text)
            }
        }
        return result
    }

    private fun locateTable(bitmap: Bitmap, anchors: List<Anchor>): TableRegion? {
        val header = anchors.firstOrNull { it.text.uppercase().replace(" ", "").contains("JENIS") }
            ?: anchors.firstOrNull { it.text.uppercase().replace(" ", "").contains("BARANG") }
            ?: return null

        val total = anchors
            .filter { it.text.uppercase().replace(" ", "").contains("TOTAL") && it.top > header.bottom }
            .minByOrNull { it.top }

        // Cari batas kolom pertama dari header JUMLAH KOLI. Jika tidak terbaca,
        // gunakan sekitar 48% lebar gambar sebagai fallback.
        val jumlah = anchors
            .filter {
                it.top >= header.top - bitmap.height * 0.03f &&
                    it.top <= header.bottom + bitmap.height * 0.10f &&
                    it.left > header.left + bitmap.width * 0.08f &&
                    it.text.uppercase().replace(" ", "").contains("JUMLAH")
            }
            .minByOrNull { it.left }

        val left = max(0, header.left - (bitmap.width * 0.025f).toInt())
        val right = min(
            bitmap.width,
            jumlah?.left?.minus((bitmap.width * 0.02f).toInt())
                ?: (bitmap.width * 0.49f).toInt()
        )
        val top = min(bitmap.height - 1, header.bottom + (bitmap.height * 0.045f).toInt())
        val bottom = min(
            bitmap.height,
            total?.top?.minus((bitmap.height * 0.01f).toInt()) ?: (bitmap.height * 0.86f).toInt()
        )

        if (right - left < 100 || bottom - top < 150) return null
        return TableRegion(left, top, right, bottom)
    }

    private fun splitRows(bitmap: Bitmap, table: TableRegion): List<Quad> {
        val crop = safeCrop(bitmap, table.left, table.top, table.right, table.bottom) ?: return emptyList()
        val gray = grayscale(crop)
        val lineYs = horizontalGridLines(gray)
        gray.recycle()
        crop.recycle()

        val boundaries = mutableListOf(0)
        lineYs.filter { it > 8 && it < table.bottom - table.top - 8 }.forEach { y ->
            if (boundaries.lastOrNull()?.let { abs(it - y) > 8 } != false) boundaries += y
        }
        boundaries += table.bottom - table.top
        boundaries.sort()

        val intervals = mutableListOf<Quad>()
        for (i in 0 until boundaries.size - 1) {
            val y1 = boundaries[i] + 2
            val y2 = boundaries[i + 1] - 2
            if (y2 - y1 >= 18) {
                intervals += Quad(table.left, table.top + y1, table.right, table.top + y2)
            }
        }

        // Header/garis yang gagal dideteksi dapat menghasilkan terlalu banyak/terlalu
        // sedikit interval. Untuk BTB standar, 10 baris tulisan adalah fallback yang
        // lebih aman daripada menggabungkan semua tulisan menjadi satu baris.
        return if (intervals.size in 2..25) intervals else {
            val count = 10
            val h = (table.bottom - table.top).toFloat() / count
            (0 until count).map { i ->
                Quad(table.left, table.top + (i * h).toInt() + 2, table.right, table.top + ((i + 1) * h).toInt() - 2)
            }
        }
    }

    private data class Quad(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun horizontalGridLines(gray: Bitmap): List<Int> {
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)
        val result = mutableListOf<Int>()
        for (y in 0 until h) {
            var dark = 0
            for (x in 0 until w step 2) {
                val v = Color.red(pixels[y * w + x])
                if (v < 115) dark++
            }
            val sampled = (w + 1) / 2
            if (dark > sampled * 0.42f) result += y
        }
        return clusterIndices(result)
    }

    private fun clusterIndices(values: List<Int>): List<Int> {
        if (values.isEmpty()) return emptyList()
        val out = mutableListOf<Int>()
        var start = values.first()
        var prev = start
        for (i in 1 until values.size) {
            val v = values[i]
            if (v - prev > 3) {
                out += (start + prev) / 2
                start = v
            }
            prev = v
        }
        out += (start + prev) / 2
        return out
    }

    private fun cleanTableLines(source: Bitmap): Bitmap {
        val gray = grayscale(source)
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)

        // Hapus garis horizontal panjang dan garis vertikal panjang. Tulisan yang
        // pendek tidak memenuhi rasio panjang ini sehingga tetap dipertahankan.
        for (y in 0 until h) {
            var run = 0
            var maxRun = 0
            for (x in 0 until w) {
                val dark = Color.red(pixels[y * w + x]) < 120
                if (dark) { run++; maxRun = max(maxRun, run) } else run = 0
            }
            if (maxRun > w * 0.35f) {
                for (x in 0 until w) pixels[y * w + x] = Color.WHITE
            }
        }
        for (x in 0 until w) {
            var run = 0
            var maxRun = 0
            for (y in 0 until h) {
                val dark = Color.red(pixels[y * w + x]) < 120
                if (dark) { run++; maxRun = max(maxRun, run) } else run = 0
            }
            if (maxRun > h * 0.55f) {
                for (y in 0 until h) pixels[y * w + x] = Color.WHITE
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        gray.recycle()
        return out
    }

    private fun grayscale(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val g = (Color.red(c) * 0.299 + Color.green(c) * 0.587 + Color.blue(c) * 0.114).toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(g, g, g)
        }
        out.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        return out
    }

    private fun segmentTokens(source: Bitmap): List<TokenBox> {
        val gray = grayscale(source)
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)
        val dark = BooleanArray(w * h)

        for (i in pixels.indices) {
            val g = Color.red(pixels[i])
            dark[i] = g < 165
        }

        // Buang komponen kecil (noise) dan ambil komponen tulisan. Komponen digit
        // yang berdempetan kemudian dikelompokkan menjadi satu token berdasarkan gap.
        val visited = BooleanArray(w * h)
        val components = mutableListOf<TokenBox>()
        val queue = IntArray(w * h.coerceAtMost(2000))

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                if (!dark[idx] || visited[idx]) continue
                var head = 0
                var tail = 0
                if (tail >= queue.size) continue
                queue[tail++] = idx
                visited[idx] = true
                var minX = x; var maxX = x; var minY = y; var maxY = y; var count = 0
                while (head < tail) {
                    val p = queue[head++]
                    val px = p % w
                    val py = p / w
                    count++
                    minX = min(minX, px); maxX = max(maxX, px)
                    minY = min(minY, py); maxY = max(maxY, py)
                    val neighbors = intArrayOf(p - 1, p + 1, p - w, p + w)
                    for (n in neighbors) {
                        if (n < 0 || n >= dark.size || visited[n] || !dark[n]) continue
                        val nx = n % w
                        val ny = n / w
                        if (abs(nx - px) <= 1 && abs(ny - py) <= 1) {
                            visited[n] = true
                            if (tail < queue.size) queue[tail++] = n
                        }
                    }
                }
                val cw = maxX - minX + 1
                val ch = maxY - minY + 1
                if (count >= 5 && cw >= 2 && ch >= 5 && cw <= w * 0.55f && ch <= h * 0.85f) {
                    components += TokenBox(max(0, minX - 2), max(0, minY - 2), min(w, maxX + 3), min(h, maxY + 3))
                }
            }
        }
        gray.recycle()

        if (components.isEmpty()) return emptyList()
        val sorted = components.sortedBy { it.left }
        val grouped = mutableListOf<TokenBox>()
        for (c in sorted) {
            val previous = grouped.lastOrNull()
            if (previous != null) {
                val gap = c.left - previous.right
                val baseHeight = max(previous.bottom - previous.top, c.bottom - c.top)
                if (gap <= max(8, baseHeight / 2) && abs(c.top - previous.top) <= baseHeight * 0.65f) {
                    grouped[grouped.lastIndex] = TokenBox(
                        min(previous.left, c.left),
                        min(previous.top, c.top),
                        max(previous.right, c.right),
                        max(previous.bottom, c.bottom)
                    )
                    continue
                }
            }
            grouped += c
        }
        return grouped
    }

    private suspend fun recognizeToken(recognizer: com.google.mlkit.vision.text.TextRecognizer, token: Bitmap): Double? {
        val padded = Bitmap.createBitmap(token.width + 24, token.height + 24, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(padded)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(token, 12f, 12f, null)
        val text = recognize(recognizer, padded)?.text.orEmpty()
        padded.recycle()
        return extractNumberTokens(text).firstOrNull()?.toDoubleOrNull()
    }

    private suspend fun fallbackScan(recognizer: com.google.mlkit.vision.text.TextRecognizer, bitmap: Bitmap): Result {
        val crop = safeCrop(bitmap, 0, (bitmap.height * 0.28f).toInt(), (bitmap.width * 0.60f).toInt(), bitmap.height) ?: return Result(emptyList())
        val text = recognize(recognizer, crop)
        val values = extractNumberTokens(text?.text.orEmpty()).map { it.toDouble() }
        val row = values.joinToString(" ") { formatNumber(it) }
        crop.recycle()
        return Result(values, text?.text.orEmpty(), if (row.isBlank()) emptyList() else listOf(row), 1)
    }

    private fun safeCrop(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Bitmap? {
        val l = left.coerceIn(0, bitmap.width - 1)
        val t = top.coerceIn(0, bitmap.height - 1)
        val r = right.coerceIn(l + 1, bitmap.width)
        val b = bottom.coerceIn(t + 1, bitmap.height)
        if (r - l < 8 || b - t < 8) return null
        return Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
    }

    private fun extractNumberTokens(text: String): List<String> {
        val normalized = text
            .replace('O', '0').replace('o', '0')
            .replace('I', '1').replace('l', '1').replace('|', '1')
            .replace('S', '5').replace('s', '5')
            .replace('B', '8').replace('b', '8')
        return Regex("(?<!\\d)\\d{1,3}(?!\\d)")
            .findAll(normalized)
            .map { it.value }
            .filter { it.toIntOrNull()?.let { n -> n in 1..999 } == true }
            .toList()
    }

    private fun normalizeWeight(value: Double): Double = value.coerceIn(1.0, 999.0)

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private suspend fun recognize(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap
    ): Text? = suspendCancellableCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resume(null) }
    }
}
