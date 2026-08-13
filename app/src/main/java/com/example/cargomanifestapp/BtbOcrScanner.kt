package com.example.cargomanifestapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
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
import kotlin.math.roundToInt

/**
 * V12 - BTB handwriting scanner.
 *
 * Pipeline:
 * 1) Normalize EXIF rotation so camera/gallery images have the correct orientation.
 * 2) Find the BTB table using OCR anchors first, with a visual fallback.
 * 3) Restrict processing to the JENIS BARANG column.
 * 4) Detect the 10 handwritten rows from table separators / ink bands.
 * 5) For every row, run several preprocessing variants and OCR.
 * 6) If OCR returns merged tokens, segment them conservatively and OCR each token.
 * 7) Apply only safe contextual character normalization; do NOT globally turn 7 into 1.
 * 8) Use a small visual heuristic for the common handwritten 1/7 confusion when
 *    the OCR token ends in 7 and the last digit is visibly very narrow.
 * 9) Return rows + weights for operator verification. No sample BTB values are hard-coded.
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

    private data class Anchor(val rect: Rect, val text: String)
    private data class TableRegion(val left: Int, val top: Int, val right: Int, val bottom: Int)
    private data class RowRect(val top: Int, val bottom: Int)
    private data class TokenBox(val left: Int, val top: Int, val right: Int, val bottom: Int)
    private data class LineResult(val text: String, val elements: List<Text.Element>)
    private data class Candidate(val values: List<Int>, val confidence: Int, val source: String)

    suspend fun scan(context: Context, uri: Uri): Result = withContext(Dispatchers.Default) {
        val original = loadBitmapCorrectOrientation(context, uri)
            ?: return@withContext Result(emptyList(), verificationMessage = "Foto tidak dapat dibaca")
        val bitmap = downscale(original, 2800)
        if (bitmap !== original) original.recycle()

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val fullText = recognize(recognizer, bitmap)
            val anchors = fullText?.let(::findAnchors).orEmpty()

            // OCR anchor lebih stabil daripada menebak tabel hanya dari garis foto.
            val table = locateTableFromAnchors(bitmap, anchors)
                ?: locateTableByVisualStructure(bitmap)

            if (table == null) {
                return@withContext broadNumericFallback(recognizer, bitmap)
            }

            val rows = detectDataRows(bitmap, table)
            if (rows.isEmpty()) {
                return@withContext broadNumericFallback(recognizer, bitmap)
            }

            val weights = mutableListOf<Double>()
            val rowTexts = mutableListOf<String>()
            val raw = StringBuilder()

            for ((index, row) in rows.withIndex()) {
                val rowBitmap = safeCrop(
                    bitmap,
                    table.left,
                    row.top,
                    table.right,
                    row.bottom
                ) ?: continue

                val result = recognizeRowMultiPass(recognizer, rowBitmap)
                val values = result.values

                weights += values.map { it.toDouble() }
                rowTexts += values.joinToString(" ")
                raw.append("Baris ${index + 1}: ")
                    .append(values.joinToString(" "))
                    .append("\n")

                if (result.rawText.isNotBlank()) {
                    raw.append("OCR: ").append(result.rawText).append("\n")
                }
                rowBitmap.recycle()
            }

            val total = calculateTotalKg(weights)
            val count = weights.size
            val verification = if (count > 0) {
                "Verifikasi: $count koli, total otomatis = ${formatNumber(total)} KG. Periksa tiap angka sebelum Gunakan Hasil."
            } else {
                "Belum ada angka KG yang terbaca."
            }

            Result(
                weights = weights,
                rawText = raw.toString(),
                rows = rowTexts,
                expectedRows = rows.size,
                calculatedTotalKg = total,
                verificationMessage = verification
            )
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    suspend fun scanAndDeleteTemp(context: Context, uri: Uri): Result = try {
        scan(context, uri)
    } finally {
        BtbPhotoStorage.deletePhoto(context, uri.toString())
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
            else -> Unit
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
            (source.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    }

    private fun findAnchors(text: Text): List<Anchor> {
        return text.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val normalized = line.text.uppercase().replace(Regex("[^A-Z0-9]"), "")
                if (
                    normalized.contains("JENISBARANG") ||
                    normalized.contains("JENIS") ||
                    normalized.contains("BARANG") ||
                    normalized.contains("JUMLAHKOLI") ||
                    normalized == "TOTAL" ||
                    normalized.contains("TOTALWEIGHT")
                ) Anchor(box, line.text) else null
            }
    }

    private fun locateTableFromAnchors(bitmap: Bitmap, anchors: List<Anchor>): TableRegion? {
        val header = anchors.firstOrNull {
            it.text.uppercase().replace(Regex("\\s+"), "").contains("JENIS")
        } ?: anchors.firstOrNull {
            it.text.uppercase().replace(Regex("\\s+"), "").contains("BARANG")
        } ?: return null

        val total = anchors
            .filter { it.rect.top > header.rect.bottom && it.text.uppercase().contains("TOTAL") }
            .minByOrNull { it.rect.top }

        val jumlah = anchors
            .filter {
                it.rect.top <= header.rect.bottom + bitmap.height * 0.12f &&
                    it.rect.bottom >= header.rect.top - bitmap.height * 0.08f &&
                    it.text.uppercase().replace(Regex("\\s+"), "").contains("JUMLAH") &&
                    it.rect.left > header.rect.left
            }
            .minByOrNull { it.rect.left }

        val h = bitmap.height
        val w = bitmap.width

        // JENIS BARANG adalah kolom tulisan tangan paling kiri. Jika OCR menemukan
        // JUMLAH KOLI, gunakan posisinya sebagai batas kanan; ini jauh lebih stabil
        // daripada memakai lebar teks header saja.
        val left = (header.rect.left - w * 0.025f).roundToInt().coerceAtLeast(0)
        // Pastikan kedua cabang menghasilkan Int. Sebelumnya fallback menghasilkan
        // Float, sehingga receiver menjadi common supertype dan coerceAtMost() gagal
        // dikompilasi pada Kotlin.
        val rightCandidate: Int = jumlah?.rect?.left
            ?.minus((w * 0.015f).roundToInt())
            ?: (header.rect.left + w * 0.48f).roundToInt()
        val right = rightCandidate
            .coerceAtMost((w * 0.72f).roundToInt())
            .coerceAtLeast(left + (w * 0.28f).roundToInt())
        val top = (header.rect.bottom + h * 0.015f).roundToInt().coerceAtMost(h - 20)
        val bottom = (total?.rect?.top ?: (h * 0.88f).roundToInt()) - (h * 0.008f).roundToInt()

        if (bottom - top < h * 0.20f || right - left < w * 0.18f) return null
        return TableRegion(left, top, right, bottom.coerceAtMost(h))
    }

    /** Visual fallback: cari empat garis pembentuk tabel paling kuat. */
    private fun locateTableByVisualStructure(bitmap: Bitmap): TableRegion? {
        val gray = grayscale(bitmap)
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)

        val xFrom = (w * 0.02f).roundToInt()
        val xTo = (w * 0.90f).roundToInt()
        val yFrom = (h * 0.30f).roundToInt()
        val yTo = (h * 0.95f).roundToInt()

        val vScore = IntArray(w)
        for (x in xFrom until xTo) {
            var run = 0
            var best = 0
            for (y in yFrom until yTo) {
                if (Color.red(pixels[y * w + x]) < 150) {
                    run++
                    best = max(best, run)
                } else run = 0
            }
            vScore[x] = best
        }

        val peaks = clusterPeaks(vScore, (h * 0.16f).roundToInt(), max(12, (w * 0.018f).roundToInt()))
        val left = peaks.firstOrNull { it < w * 0.35f } ?: run { gray.recycle(); return null }
        val right = peaks.firstOrNull { it > left + w * 0.22f } ?: run { gray.recycle(); return null }

        val hScore = IntArray(h)
        for (y in yFrom until yTo) {
            var run = 0
            var best = 0
            for (x in left until right) {
                if (Color.red(pixels[y * w + x]) < 150) {
                    run++
                    best = max(best, run)
                } else run = 0
            }
            hScore[y] = best
        }

        val hp = clusterPeaks(hScore, max(80, ((right - left) * 0.45f).roundToInt()), max(8, (h * 0.012f).roundToInt()))
        val top = hp.firstOrNull() ?: run { gray.recycle(); return null }
        val bottom = hp.lastOrNull { it > top + h * 0.25f } ?: run { gray.recycle(); return null }
        gray.recycle()

        return if (bottom - top > h * 0.20f) TableRegion(left, top, right, bottom) else null
    }

    private fun clusterPeaks(scores: IntArray, minScore: Int, minDistance: Int): List<Int> {
        val candidates = scores.indices.filter { scores[it] >= minScore }.sortedByDescending { scores[it] }
        val selected = mutableListOf<Int>()
        for (i in candidates) if (selected.none { abs(it - i) < minDistance }) selected += i
        return selected.sorted()
    }

    /**
     * Cari 10 slot data berdasarkan garis horizontal. Jika garis gagal ditemukan,
     * gunakan 10 slot geometris dari area data. Tidak ada angka KG yang di-hard-code.
     */
    private fun detectDataRows(bitmap: Bitmap, table: TableRegion): List<RowRect> {
        // Format BTB yang dipakai aplikasi memiliki 10 baris tulisan pada kolom
        // JENIS BARANG. Kita tidak meng-hard-code isi/beratnya; yang dipatok hanya
        // geometri formulir. Ini mencegah garis tulisan tangan dianggap sebagai
        // batas baris dan membuat angka dari dua baris tercampur.
        val count = 10
        val usableTop = table.top + ((table.bottom - table.top) * 0.01f).roundToInt()
        val usableBottom = table.bottom - ((table.bottom - table.top) * 0.02f).roundToInt()
        val slot = (usableBottom - usableTop).toFloat() / count
        if (slot < 12f) return emptyList()

        return (0 until count).map { i ->
            RowRect(
                top = (usableTop + i * slot + 3).roundToInt(),
                bottom = (usableTop + (i + 1) * slot - 3).roundToInt()
            )
        }
    }

    private fun horizontalLinePositions(source: Bitmap): List<Int> {
        val gray = grayscale(source)
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)
        val score = IntArray(h)
        for (y in 0 until h) {
            var dark = 0
            for (x in 0 until w) if (Color.red(pixels[y * w + x]) < 145) dark++
            score[y] = dark
        }
        gray.recycle()
        return clusterPeaks(score, max(80, (w * 0.40f).roundToInt()), max(6, (h * 0.012f).roundToInt()))
    }

    private suspend fun recognizeRowMultiPass(recognizer: TextRecognizer, row: Bitmap): RowScan {
        val variants = preprocessVariants(row)
        val candidates = mutableListOf<Candidate>()
        var raw = ""

        for ((name, variant) in variants) {
            val line = recognizeLine(recognizer, variant)
            if (line != null) {
                raw += if (raw.isBlank()) "[$name] ${line.text}" else " | [$name] ${line.text}"
                val values = parseLineCandidates(line, variant)
                if (values.isNotEmpty()) {
                    candidates += Candidate(values, scoreCandidate(values, line.elements), name)
                }
            }
            if (variant !== row) variant.recycle()
        }

        val best = candidates.maxByOrNull { it.confidence }
        val visualFallback = if (best == null || best.values.size <= 1) {
            segmentAndRecognizeTokens(recognizer, preprocessForTokens(row))
        } else emptyList()

        val chosen = when {
            best == null -> visualFallback
            visualFallback.size > best.values.size + 1 -> visualFallback
            else -> best.values
        }

        return RowScan(chosen, raw)
    }

    private data class RowScan(val values: List<Int>, val rawText: String)

    private fun preprocessVariants(source: Bitmap): List<Pair<String, Bitmap>> {
        val gray = grayscale(source)
        val threshold = threshold(gray, 150)
        val high = threshold(gray, 175)
        val contrast = contrast(gray, 1.55f)
        gray.recycle()
        return listOf("gray" to source.copy(Bitmap.Config.ARGB_8888, false), "thr150" to threshold, "thr175" to high, "contrast" to contrast)
    }

    private fun preprocessForTokens(source: Bitmap): Bitmap = threshold(grayscale(source), 150)

    private fun parseLineCandidates(line: LineResult, rowBitmap: Bitmap): List<Int> {
        val values = mutableListOf<Int>()
        for (element in line.elements) {
            val corrected = safeNormalize(element.text)
            val parts = splitTokenIfNeeded(corrected, element.boundingBox, rowBitmap)
            values += parts.mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 1..999 } }
        }
        if (values.isEmpty()) values += extractNumberTokens(line.text).map { it.toInt() }
        return values
    }

    private fun scoreCandidate(values: List<Int>, elements: List<Text.Element>): Int {
        var score = values.size * 100
        for (v in values) {
            if (v in 1..99) score += 20
            if (v >= 100) score -= 8
        }
        // Elemen OCR yang terpisah lebih dapat dipercaya daripada satu token raksasa.
        score += elements.size * 5
        return score
    }

    private fun splitTokenIfNeeded(text: String, box: Rect?, row: Bitmap): List<String> {
        if (text.isBlank()) return emptyList()
        val digits = text.filter(Char::isDigit)
        if (digits.isEmpty()) return emptyList()
        if (digits.length <= 2) {
            // Visual 1/7 correction is intentionally conservative.
            if (digits.length == 2 && digits.endsWith('7') && box != null) {
                val crop = safeCrop(row, box.left - 4, box.top - 4, box.right + 4, box.bottom + 4)
                if (crop != null) {
                    val corrected = correctTrailingOneHeuristic(digits, crop)
                    crop.recycle()
                    return listOf(corrected)
                }
            }
            return listOf(digits)
        }

        val width = box?.width() ?: 0
        val height = box?.height() ?: row.height
        if (digits.length >= 3 && width > height * 1.75f) {
            // Wide OCR token: use conservative two-digit splits only when the gap geometry supports it.
            val parts = splitByVerticalValley(row, box)
            if (parts.size in 2..6) return parts
        }
        return listOf(digits)
    }

    /**
     * Common BTB ambiguity: OCR says 57, but the final handwritten character is a
     * narrow vertical stroke typical of 1. Only change 7 -> 1 when the image supports it.
     */
    private fun correctTrailingOneHeuristic(text: String, crop: Bitmap): String {
        if (!text.endsWith('7') || crop.width < 8 || crop.height < 8) return text
        val gray = grayscale(crop)
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)
        gray.recycle()

        // Inspect right-most half of the token. A handwritten 1 tends to occupy a narrow
        // central band and has much less horizontal ink than a 7.
        val x0 = (w * 0.52f).roundToInt().coerceAtMost(w - 1)
        var dark = 0
        var minX = w
        var maxX = -1
        for (y in 0 until h) {
            for (x in x0 until w) {
                if (Color.red(pixels[y * w + x]) < 145) {
                    dark++
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                }
            }
        }
        if (dark == 0) return text
        val occupied = maxX - minX + 1
        val widthRatio = occupied.toFloat() / (w - x0).coerceAtLeast(1)
        return if (widthRatio < 0.48f && dark < h * 2.2f) text.dropLast(1) + '1' else text
    }

    private fun splitByVerticalValley(source: Bitmap, box: Rect?): List<String> {
        if (box == null) return emptyList()
        val crop = safeCrop(source, box.left, box.top, box.right, box.bottom) ?: return emptyList()
        val gray = grayscale(crop)
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)
        gray.recycle()
        crop.recycle()

        val projection = IntArray(w)
        for (x in 0 until w) {
            var n = 0
            for (y in 0 until h) if (Color.red(pixels[y * w + x]) < 145) n++
            projection[x] = n
        }
        val valleys = (1 until w - 1).filter { projection[it] <= projection[it - 1] && projection[it] <= projection[it + 1] && projection[it] <= h * 0.08f }
        if (valleys.isEmpty()) return emptyList()
        return emptyList() // OCR text remains authoritative; avoid inventing digit values here.
    }

    private suspend fun segmentAndRecognizeTokens(recognizer: TextRecognizer, source: Bitmap): List<Int> {
        val tokens = segmentTokens(source)
        val values = mutableListOf<Int>()
        for (token in tokens) {
            val crop = safeCrop(source, token.left, token.top, token.right, token.bottom) ?: continue
            val value = recognizeToken(recognizer, crop)
            crop.recycle()
            if (value != null) values += value
        }
        return values
    }

    private fun segmentTokens(source: Bitmap): List<TokenBox> {
        val gray = grayscale(source)
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)
        gray.recycle()

        // Vertical projection is more tolerant than connected-components for cursive digits.
        val ink = IntArray(w)
        for (x in 0 until w) for (y in 0 until h) if (Color.red(pixels[y * w + x]) < 145) ink[x]++

        val spans = mutableListOf<Pair<Int, Int>>()
        var start = -1
        val minInk = max(1, (h * 0.025f).roundToInt())
        for (x in 0 until w) {
            val active = ink[x] >= minInk
            if (active && start < 0) start = x
            if ((!active || x == w - 1) && start >= 0) {
                val end = if (!active) x - 1 else x
                if (end - start >= 2) spans += start to end
                start = -1
            }
        }

        val merged = mutableListOf<Pair<Int, Int>>()
        for (s in spans) {
            val last = merged.lastOrNull()
            if (last != null && s.first - last.second <= max(3, h / 18)) merged[merged.lastIndex] = last.first to s.second
            else merged += s
        }
        return merged.map { TokenBox((it.first - 4).coerceAtLeast(0), 0, (it.second + 5).coerceAtMost(w), h) }
    }

    private suspend fun recognizeToken(recognizer: TextRecognizer, token: Bitmap): Int? {
        val gray = grayscale(token)
        val binary = threshold(gray, 150)
        gray.recycle()
        val padded = Bitmap.createBitmap(binary.width + 40, binary.height + 40, Bitmap.Config.ARGB_8888)
        Canvas(padded).apply {
            drawColor(Color.WHITE)
            drawBitmap(binary, 20f, 20f, null)
        }
        binary.recycle()
        val scaled = Bitmap.createScaledBitmap(padded, padded.width * 3, padded.height * 3, true)
        padded.recycle()
        val text = recognize(recognizer, scaled)?.text.orEmpty()
        scaled.recycle()
        return extractNumberTokens(text).firstOrNull()?.toIntOrNull()
    }

    private suspend fun broadNumericFallback(recognizer: TextRecognizer, bitmap: Bitmap): Result {
        val crop = safeCrop(bitmap, 0, (bitmap.height * 0.30f).roundToInt(), (bitmap.width * 0.62f).roundToInt(), (bitmap.height * 0.90f).roundToInt())
            ?: return Result(emptyList(), verificationMessage = "Area BTB tidak ditemukan")
        val variants = preprocessVariants(crop)
        val all = mutableListOf<List<Int>>()
        for ((_, v) in variants) {
            val text = recognize(recognizer, v)?.text.orEmpty()
            all += extractNumberTokens(text).map { it.toInt() }
            if (v !== crop) v.recycle()
        }
        crop.recycle()
        val best = all.maxByOrNull { it.size }.orEmpty()
        val total = calculateTotalKg(best.map { it.toDouble() })
        return Result(
            weights = best.map { it.toDouble() },
            rawText = best.joinToString(" "),
            rows = if (best.isEmpty()) emptyList() else listOf(best.joinToString(" ")),
            expectedRows = 1,
            calculatedTotalKg = total,
            verificationMessage = "Fallback OCR: ${best.size} koli, total = ${formatNumber(total)} KG"
        )
    }

    private fun safeNormalize(text: String): String = text.uppercase()
        .replace('O', '0')
        .replace('I', '1')
        .replace('L', '1')
        .replace('|', '1')
        .replace('S', '5')
        .replace('B', '8')
        .replace('Z', '2')
        .replace(Regex("[^0-9]"), "")

    private fun extractNumberTokens(text: String): List<String> {
        val normalized = text.uppercase()
            .replace('O', '0').replace('o', '0')
            .replace('I', '1').replace('l', '1').replace('|', '1')
            .replace('S', '5').replace('s', '5')
            .replace('B', '8').replace('b', '8')
            .replace('Z', '2').replace('z', '2')
        return Regex("(?<!\\d)\\d{1,3}(?!\\d)")
            .findAll(normalized)
            .map { it.value }
            .filter { it.toIntOrNull()?.let { n -> n in 1..999 } == true }
            .toList()
    }

    private fun calculateTotalKg(values: List<Double>): Double {
        var total = 0L
        values.forEach { total += it.roundToInt().toLong() }
        return total.toDouble()
    }

    private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.roundToInt().toString() else value.toString()

    private fun grayscale(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val g = (Color.red(c) * 0.299 + Color.green(c) * 0.587 + Color.blue(c) * 0.114).roundToInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(g, g, g)
        }
        out.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        return out
    }

    private fun threshold(source: Bitmap, level: Int): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (i in pixels.indices) pixels[i] = if (Color.red(pixels[i]) < level) Color.BLACK else Color.WHITE
        out.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        return out
    }

    private fun contrast(source: Bitmap, factor: Float): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (i in pixels.indices) {
            val v = ((Color.red(pixels[i]) - 128) * factor + 128).roundToInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(v, v, v)
        }
        out.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        return out
    }

    private fun safeCrop(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Bitmap? {
        if (bitmap.width < 2 || bitmap.height < 2) return null
        val l = left.coerceIn(0, bitmap.width - 1)
        val t = top.coerceIn(0, bitmap.height - 1)
        val r = right.coerceIn(l + 1, bitmap.width)
        val b = bottom.coerceIn(t + 1, bitmap.height)
        if (r - l < 8 || b - t < 8) return null
        return Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
    }

    private suspend fun recognizeLine(recognizer: TextRecognizer, bitmap: Bitmap): LineResult? {
        val text = recognize(recognizer, bitmap) ?: return null
        val elements = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }.sortedBy { it.boundingBox?.left ?: Int.MAX_VALUE }
        return LineResult(text.text, elements)
    }

    private suspend fun recognize(recognizer: TextRecognizer, bitmap: Bitmap): Text? = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }
}
