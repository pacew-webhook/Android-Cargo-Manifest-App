package com.example.cargomanifestapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * V12 - BTB OCR dengan segmentasi baris + token yang lebih ketat.
 *
 * Prinsip V12:
 * 1. OCR seluruh foto hanya dipakai untuk menemukan header/titik acuan.
 * 2. Hanya kolom JENIS BARANG yang diproses.
 * 3. Baris tulisan dideteksi dari kepadatan tinta, bukan dari hasil OCR.
 * 4. Bila garis tabel membuat deteksi gagal, digunakan 10 slot baris BTB sebagai
 *    fallback. Angka tidak pernah di-hard-code.
 * 5. Garis tabel dibuang sebelum segmentasi.
 * 6. Komponen tulisan dikelompokkan dengan jarak yang kecil saja. V11 terlalu
 *    agresif sehingga "5 5 5" dapat berubah menjadi "555".
 * 7. Setiap token diperbesar sebelum OCR.
 * 8. Bila token gagal, OCR satu baris digunakan sebagai cadangan.
 */
object BtbOcrScanner {

    data class Result(
        val weights: List<Double>,
        val rawText: String = "",
        val rows: List<String> = emptyList(),
        val expectedRows: Int = 0
    )

    private data class Anchor(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val text: String
    )

    private data class TokenBox(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private data class TableRegion(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private data class Quad(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private data class Component(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val pixels: Int
    )

    suspend fun scan(context: Context, uri: Uri): Result = withContext(Dispatchers.Default) {
        val original = loadBitmap(context, uri) ?: return@withContext Result(emptyList())
        val bitmap = downscale(original, 2200)
        if (bitmap !== original) original.recycle()

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            val fullText = recognize(recognizer, bitmap)
            val anchors = fullText?.let(::findAnchors).orEmpty()
            val table = locateTable(bitmap, anchors)

            if (table == null) {
                return@withContext fallbackScan(recognizer, bitmap)
            }

            val rows = splitRows(bitmap, table)
            if (rows.isEmpty()) {
                return@withContext fallbackScan(recognizer, bitmap)
            }

            val allWeights = mutableListOf<Double>()
            val rowResults = mutableListOf<String>()
            val raw = StringBuilder()

            for ((index, rowRect) in rows.withIndex()) {
                val rowBitmap = safeCrop(
                    bitmap,
                    rowRect.left,
                    rowRect.top,
                    rowRect.right,
                    rowRect.bottom
                )

                if (rowBitmap == null) {
                    rowResults += ""
                    continue
                }

                val cleaned = cleanTableLines(rowBitmap)
                val tokens = segmentTokens(cleaned)

                val tokenValues = mutableListOf<Double>()

                for (token in tokens) {
                    val tokenBitmap = safeCrop(
                        cleaned,
                        token.left,
                        token.top,
                        token.right,
                        token.bottom
                    ) ?: continue

                    val value = recognizeToken(recognizer, tokenBitmap)
                    if (value != null && value in 1.0..999.0) {
                        tokenValues += value
                    }
                    tokenBitmap.recycle()
                }

                /*
                 * Jangan gunakan OCR satu baris jika segmentasi sudah menghasilkan
                 * token yang masuk akal. OCR baris penuh sering menggabungkan angka.
                 */
                if (tokenValues.isEmpty()) {
                    val rowOcr = recognize(recognizer, cleaned)
                    val fallbackValues = extractNumberTokens(rowOcr?.text.orEmpty())
                        .map { it.toDouble() }
                    tokenValues += fallbackValues

                    raw.append("Baris ${index + 1} OCR: ")
                        .append(rowOcr?.text.orEmpty())
                        .append('\n')
                }

                val normalized = tokenValues
                    .map(::normalizeWeight)
                    .filter { it in 1.0..999.0 }

                allWeights += normalized
                rowResults += normalized.joinToString(" ") { formatNumber(it) }

                raw.append("Baris ${index + 1}: ")
                    .append(normalized.joinToString(" ") { formatNumber(it) })
                    .append('\n')

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
        return try {
            scan(context, uri)
        } finally {
            BtbPhotoStorage.deletePhoto(context, uri.toString())
        }
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? =
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        }

    private fun downscale(source: Bitmap, maxWidth: Int): Bitmap {
        if (source.width <= maxWidth) return source
        val scale = maxWidth.toFloat() / source.width.toFloat()
        return Bitmap.createScaledBitmap(
            source,
            maxWidth,
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun findAnchors(text: Text): List<Anchor> {
        val result = mutableListOf<Anchor>()

        text.textBlocks
            .flatMap { it.lines }
            .forEach { line ->
                val box = line.boundingBox ?: return@forEach
                val normalized = line.text
                    .uppercase()
                    .replace(Regex("[^A-Z0-9]"), "")

                if (
                    normalized.contains("JENIS") ||
                    normalized.contains("BARANG") ||
                    normalized.contains("JUMLAHKOLI") ||
                    normalized == "TOTAL" ||
                    normalized.contains("TOTAL")
                ) {
                    result += Anchor(
                        box.left,
                        box.top,
                        box.right,
                        box.bottom,
                        line.text
                    )
                }
            }

        return result
    }

    private fun locateTable(
        bitmap: Bitmap,
        anchors: List<Anchor>
    ): TableRegion? {
        val header = anchors.firstOrNull {
            it.text.uppercase().replace(" ", "").contains("JENIS")
        } ?: anchors.firstOrNull {
            it.text.uppercase().replace(" ", "").contains("BARANG")
        } ?: return null

        val total = anchors
            .filter {
                it.text.uppercase().replace(" ", "").contains("TOTAL") &&
                    it.top > header.bottom
            }
            .minByOrNull { it.top }

        val jumlah = anchors
            .filter {
                it.top >= header.top - bitmap.height * 0.05f &&
                    it.top <= header.bottom + bitmap.height * 0.16f &&
                    it.left > header.left &&
                    it.text.uppercase()
                        .replace(" ", "")
                        .contains("JUMLAH")
            }
            .minByOrNull { it.left }

        val left = max(
            0,
            (header.left - bitmap.width * 0.025f).toInt()
        )

        /*
         * Pada BTB foto miring, OCR header JENIS BARANG tidak selalu mempunyai
         * lebar yang sama. Prioritaskan batas JUMLAH KOLI; fallback memakai
         * 45% lebar gambar.
         */
        val right = min(
            bitmap.width,
            jumlah?.left?.minus((bitmap.width * 0.018f).toInt())
                ?: (header.left + bitmap.width * 0.42f).toInt()
        )

        val top = min(
            bitmap.height - 1,
            header.bottom + (bitmap.height * 0.028f).toInt()
        )

        val bottom = min(
            bitmap.height,
            total?.top?.minus((bitmap.height * 0.012f).toInt())
                ?: (bitmap.height * 0.86f).toInt()
        )

        if (right - left < 100 || bottom - top < 120) return null

        return TableRegion(left, top, right, bottom)
    }

    private fun splitRows(
        bitmap: Bitmap,
        table: TableRegion
    ): List<Quad> {
        val crop = safeCrop(
            bitmap,
            table.left,
            table.top,
            table.right,
            table.bottom
        ) ?: return emptyList()

        val cleaned = cleanTableLines(crop)
        val detected = detectInkRows(cleaned)

        cleaned.recycle()
        crop.recycle()

        /*
         * Untuk BTB yang sama formatnya, 10 baris tulisan adalah struktur formulir,
         * tetapi angka di dalamnya tidak di-hard-code. Jika deteksi tinta berhasil
         * menemukan 8-12 baris, gunakan hasil tersebut. Jika tidak, gunakan 10 slot.
         */
        if (detected.size in 8..12) {
            return detected.map {
                Quad(
                    table.left,
                    table.top + it.first,
                    table.right,
                    table.top + it.second
                )
            }
        }

        val count = 10
        val height = (table.bottom - table.top).toFloat() / count

        return (0 until count).map { i ->
            Quad(
                table.left,
                table.top + (i * height).toInt() + 2,
                table.right,
                table.top + ((i + 1) * height).toInt() - 2
            )
        }
    }

    /**
     * Mencari pita tulisan tangan dari kepadatan tinta.
     * Garis tabel panjang dibuang dulu sehingga tidak dianggap sebagai baris.
     */
    private fun detectInkRows(source: Bitmap): List<Pair<Int, Int>> {
        val gray = grayscale(source)
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)

        val scores = IntArray(h)

        for (y in 0 until h) {
            var count = 0
            for (x in 3 until max(4, w - 3)) {
                val value = Color.red(pixels[y * w + x])
                if (value < 125) count++
            }
            scores[y] = count
        }

        // Smooth agar tulisan yang terputus tetap dianggap satu pita.
        val smooth = IntArray(h)
        for (y in 0 until h) {
            var sum = 0
            var n = 0
            for (dy in -2..2) {
                val yy = y + dy
                if (yy in 0 until h) {
                    sum += scores[yy]
                    n++
                }
            }
            smooth[y] = if (n == 0) 0 else sum / n
        }

        val threshold = max(4, (w * 0.012f).toInt())
        val bands = mutableListOf<Pair<Int, Int>>()

        var start = -1
        for (y in 0 until h) {
            val active = smooth[y] >= threshold && smooth[y] < w * 0.48f

            if (active && start < 0) {
                start = y
            }

            val endBand = (!active || y == h - 1) && start >= 0
            if (endBand) {
                val end = if (!active) y - 1 else y
                if (end - start + 1 >= 5) {
                    bands += (start - 3).coerceAtLeast(0) to
                        (end + 4).coerceAtMost(h)
                }
                start = -1
            }
        }

        gray.recycle()

        if (bands.isEmpty()) return emptyList()

        // Gabungkan pita yang terlalu dekat.
        val merged = mutableListOf<Pair<Int, Int>>()
        for (band in bands) {
            val last = merged.lastOrNull()
            if (last != null && band.first - last.second <= 7) {
                merged[merged.lastIndex] = last.first to
                    max(last.second, band.second)
            } else {
                merged += band
            }
        }

        /*
         * Hanya pertahankan pita dengan ukuran tulisan yang wajar.
         * Garis tabel yang lolos biasanya sangat tipis/panjang.
         */
        val filtered = merged.filter {
            val height = it.second - it.first
            height in 8..70
        }

        return filtered.take(12)
    }

    /**
     * Menghapus garis horizontal/vertikal dan menekan noise latar.
     */
    private fun cleanTableLines(source: Bitmap): Bitmap {
        val gray = grayscale(source)
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)

        // Hilangkan garis horizontal yang panjang.
        for (y in 0 until h) {
            var run = 0
            var maxRun = 0

            for (x in 0 until w) {
                val dark = Color.red(pixels[y * w + x]) < 125
                if (dark) {
                    run++
                    if (run > maxRun) maxRun = run
                } else {
                    run = 0
                }
            }

            if (maxRun > w * 0.28f) {
                for (x in 0 until w) {
                    pixels[y * w + x] = Color.WHITE
                }
            }
        }

        // Hilangkan garis vertikal yang panjang.
        for (x in 0 until w) {
            var run = 0
            var maxRun = 0

            for (y in 0 until h) {
                val dark = Color.red(pixels[y * w + x]) < 125
                if (dark) {
                    run++
                    if (run > maxRun) maxRun = run
                } else {
                    run = 0
                }
            }

            if (maxRun > h * 0.45f) {
                for (y in 0 until h) {
                    pixels[y * w + x] = Color.WHITE
                }
            }
        }

        // Threshold ringan agar warna kertas tidak ikut dianggap tulisan.
        for (i in pixels.indices) {
            val value = Color.red(pixels[i])
            pixels[i] = if (value < 135) Color.BLACK else Color.WHITE
        }

        val out = Bitmap.createBitmap(
            w,
            h,
            Bitmap.Config.ARGB_8888
        )
        out.setPixels(pixels, 0, w, 0, 0, w, h)

        gray.recycle()
        return out
    }

    /**
     * Segmentasi token lebih konservatif daripada V11.
     *
     * V11 memakai gap <= 50% tinggi sehingga beberapa angka yang berdiri
     * sendiri bisa digabung menjadi "555"/"224". V12 hanya menggabungkan
     * komponen jika gap benar-benar kecil dan overlap vertikal kuat.
     */
    private fun segmentTokens(source: Bitmap): List<TokenBox> {
        val gray = grayscale(source)
        val w = gray.width
        val h = gray.height

        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)

        val dark = BooleanArray(w * h)
        for (i in pixels.indices) {
            dark[i] = Color.red(pixels[i]) < 150
        }

        val components = connectedComponents(dark, w, h)
        gray.recycle()

        if (components.isEmpty()) return emptyList()

        val usable = components
            .filter { component ->
                val cw = component.right - component.left + 1
                val ch = component.bottom - component.top + 1

                component.pixels >= 7 &&
                    cw >= 2 &&
                    ch >= 5 &&
                    cw <= w * 0.45f &&
                    ch <= h * 0.85f
            }
            .sortedBy { it.left }

        if (usable.isEmpty()) return emptyList()

        val grouped = mutableListOf<TokenBox>()

        for (component in usable) {
            val current = TokenBox(
                (component.left - 3).coerceAtLeast(0),
                (component.top - 3).coerceAtLeast(0),
                (component.right + 4).coerceAtMost(w),
                (component.bottom + 4).coerceAtMost(h)
            )

            val previous = grouped.lastOrNull()

            if (previous == null) {
                grouped += current
                continue
            }

            val previousHeight = previous.bottom - previous.top
            val currentHeight = current.bottom - current.top
            val baseHeight = min(previousHeight, currentHeight).coerceAtLeast(1)

            val gap = current.left - previous.right

            val overlapTop = max(previous.top, current.top)
            val overlapBottom = min(previous.bottom, current.bottom)
            val overlap = max(0, overlapBottom - overlapTop)

            val overlapRatio = overlap.toFloat() / baseHeight.toFloat()

            /*
             * Hanya gabungkan digit yang benar-benar berdempetan.
             * Angka berbeda biasanya memiliki gap lebih besar.
             */
            val shouldMerge =
                gap <= max(2, (baseHeight * 0.16f).toInt()) &&
                    overlapRatio >= 0.48f &&
                    previous.right - previous.left < w * 0.30f

            if (shouldMerge) {
                grouped[grouped.lastIndex] = TokenBox(
                    min(previous.left, current.left),
                    min(previous.top, current.top),
                    max(previous.right, current.right),
                    max(previous.bottom, current.bottom)
                )
            } else {
                grouped += current
            }
        }

        return grouped
    }

    private fun connectedComponents(
        dark: BooleanArray,
        width: Int,
        height: Int
    ): List<Component> {
        val visited = BooleanArray(dark.size)
        val queue = IntArray(dark.size)
        val result = mutableListOf<Component>()

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val startIndex = y * width + x

                if (!dark[startIndex] || visited[startIndex]) continue

                var head = 0
                var tail = 0

                queue[tail++] = startIndex
                visited[startIndex] = true

                var minX = x
                var maxX = x
                var minY = y
                var maxY = y
                var count = 0

                while (head < tail) {
                    val p = queue[head++]
                    val px = p % width
                    val py = p / width

                    count++
                    minX = min(minX, px)
                    maxX = max(maxX, px)
                    minY = min(minY, py)
                    maxY = max(maxY, py)

                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue

                            val nx = px + dx
                            val ny = py + dy

                            if (
                                nx <= 0 ||
                                nx >= width - 1 ||
                                ny <= 0 ||
                                ny >= height - 1
                            ) continue

                            val n = ny * width + nx

                            if (!visited[n] && dark[n]) {
                                visited[n] = true
                                if (tail < queue.size) {
                                    queue[tail++] = n
                                }
                            }
                        }
                    }
                }

                result += Component(
                    minX,
                    minY,
                    maxX,
                    maxY,
                    count
                )
            }
        }

        return result
    }

    private suspend fun recognizeToken(
        recognizer: TextRecognizer,
        token: Bitmap
    ): Double? {
        val scale = 3
        val scaled = Bitmap.createScaledBitmap(
            token,
            (token.width * scale).coerceAtLeast(24),
            (token.height * scale).coerceAtLeast(24),
            true
        )

        val padded = Bitmap.createBitmap(
            scaled.width + 36,
            scaled.height + 36,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(padded)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(scaled, 18f, 18f, null)

        scaled.recycle()

        val text = recognize(recognizer, padded)?.text.orEmpty()
        padded.recycle()

        return extractNumberTokens(text)
            .firstOrNull()
            ?.toDoubleOrNull()
    }

    private suspend fun fallbackScan(
        recognizer: TextRecognizer,
        bitmap: Bitmap
    ): Result {
        val crop = safeCrop(
            bitmap,
            0,
            (bitmap.height * 0.35f).toInt(),
            (bitmap.width * 0.55f).toInt(),
            (bitmap.height * 0.88f).toInt()
        ) ?: return Result(emptyList())

        val cleaned = cleanTableLines(crop)
        val text = recognize(recognizer, cleaned)

        val values = extractNumberTokens(
            text?.text.orEmpty()
        ).map { it.toDouble() }

        val row = values.joinToString(" ") {
            formatNumber(it)
        }

        cleaned.recycle()
        crop.recycle()

        return Result(
            weights = values,
            rawText = text?.text.orEmpty(),
            rows = if (row.isBlank()) emptyList() else listOf(row),
            expectedRows = 1
        )
    }

    private fun safeCrop(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Bitmap? {
        if (bitmap.width <= 1 || bitmap.height <= 1) return null

        val l = left.coerceIn(0, bitmap.width - 1)
        val t = top.coerceIn(0, bitmap.height - 1)
        val r = right.coerceIn(l + 1, bitmap.width)
        val b = bottom.coerceIn(t + 1, bitmap.height)

        if (r - l < 8 || b - t < 8) return null

        return Bitmap.createBitmap(
            bitmap,
            l,
            t,
            r - l,
            b - t
        )
    }

    private fun grayscale(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(
            source.width,
            source.height,
            Bitmap.Config.ARGB_8888
        )

        val pixels = IntArray(source.width * source.height)
        source.getPixels(
            pixels,
            0,
            source.width,
            0,
            0,
            source.width,
            source.height
        )

        for (i in pixels.indices) {
            val c = pixels[i]
            val g = (
                Color.red(c) * 0.299 +
                    Color.green(c) * 0.587 +
                    Color.blue(c) * 0.114
                ).toInt().coerceIn(0, 255)

            pixels[i] = Color.rgb(g, g, g)
        }

        out.setPixels(
            pixels,
            0,
            source.width,
            0,
            0,
            source.width,
            source.height
        )

        return out
    }

    private fun extractNumberTokens(text: String): List<String> {
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
            .filter {
                it.toIntOrNull()?.let { value ->
                    value in 1..999
                } == true
            }
            .toList()
    }

    private fun normalizeWeight(value: Double): Double =
        value.coerceIn(1.0, 999.0)

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString()
        }

    private suspend fun recognize(
        recognizer: TextRecognizer,
        bitmap: Bitmap
    ): Text? = suspendCancellableCoroutine { continuation ->
        recognizer
            .process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener {
                continuation.resume(it)
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }
}
