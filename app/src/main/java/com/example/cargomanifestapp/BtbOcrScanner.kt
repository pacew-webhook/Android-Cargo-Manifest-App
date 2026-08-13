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
 * V13 - BTB OCR hybrid: computer vision untuk struktur, OCR per baris/element,
 * koreksi kontekstual, dan verifikasi matematis deterministik.
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
        val expectedRows: Int = 0,
        val calculatedTotalKg: Double = weights.sum(),
        val verificationMessage: String = ""
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
        val bitmap = downscale(original, 2400)
        if (bitmap !== original) original.recycle()

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            // V13 memakai OCR sebagai pembaca karakter, bukan sebagai penentu struktur.
            // Struktur dokumen ditentukan lebih dulu dari geometri tabel + posisi tinta.
            val fullText = recognize(recognizer, bitmap)
            val anchors = fullText?.let(::findAnchors).orEmpty()

            val visualTable = locateTableByVisualStructure(bitmap)
            val table = visualTable ?: locateTable(bitmap, anchors)

            if (table == null) {
                return@withContext fallbackScan(recognizer, bitmap)
            }

            val rows = splitRowsV13(bitmap, table)
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
                } else {
                    val cleaned = cleanTableLinesV13(rowBitmap)

                // Pertama: OCR satu baris. Bounding box element dipakai untuk
                // menjaga urutan dan jarak antar angka.
                val lineResult = recognizeLine(recognizer, cleaned)
                val candidates = parseLineCandidates(lineResult, cleaned)

                // Kedua: jika line OCR terlalu pendek, coba segmentasi visual.
                val visualCandidates = if (candidates.size < 2) {
                    segmentAndRecognizeTokensV13(recognizer, cleaned)
                } else emptyList()

                val chosen = chooseContextualCandidates(
                    primary = candidates,
                    secondary = visualCandidates
                )

                val normalized = chosen
                    .map(::normalizeWeight)
                    .filter { it in 1.0..999.0 }

                allWeights += normalized
                rowResults += normalized.joinToString(" ") { formatNumber(it) }
                raw.append("Baris ${index + 1}: ")
                    .append(normalized.joinToString(" ") { formatNumber(it) })
                    .append("\n")

                if (normalized.isEmpty()) {
                    raw.append("OCR asli: ")
                        .append(lineResult?.text.orEmpty())
                        .append("\n")
                }

                    cleaned.recycle()
                    rowBitmap.recycle()
                }
            }

            val total = calculateTotalKg(allWeights)
            val count = allWeights.size
            val verification = if (count > 0) {
                "Verifikasi: $count koli × hasil pembacaan, total dihitung otomatis = ${formatNumber(total)} KG"
            } else {
                "Verifikasi gagal: belum ada angka KG yang valid."
            }

            Result(
                weights = allWeights,
                rawText = raw.toString(),
                rows = rowResults,
                expectedRows = rows.size,
                calculatedTotalKg = total,
                verificationMessage = verification
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

    /**
     * V13: identifikasi struktur tabel secara visual.
     * Tidak bergantung pada OCR untuk menemukan header. Kami mencari garis
     * vertikal/horizontal yang panjang lalu mengambil kolom JENIS BARANG.
     */
    private fun locateTableByVisualStructure(bitmap: Bitmap): TableRegion? {
        val gray = grayscale(bitmap)
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)

        val yStart = (h * 0.38f).toInt().coerceAtLeast(0)
        val yEnd = (h * 0.94f).toInt().coerceAtMost(h)
        val xStart = (w * 0.03f).toInt()
        val xEnd = (w * 0.97f).toInt()

        val verticalScores = IntArray(w)
        for (x in xStart until xEnd) {
            var run = 0
            var best = 0
            for (y in yStart until yEnd) {
                if (Color.red(pixels[y * w + x]) < 125) {
                    run++
                    best = max(best, run)
                } else {
                    run = 0
                }
            }
            verticalScores[x] = best
        }

        val verticalPeaks = clusterPeaks(
            verticalScores,
            minScore = (h * 0.20f).toInt(),
            minDistance = max(10, (w * 0.015f).toInt())
        )

        // Kandidat kiri: garis panjang di sisi kiri tabel.
        val left = verticalPeaks
            .filter { it in (w * 0.08f).toInt()..(w * 0.30f).toInt() }
            .maxByOrNull { verticalScores[it] }
            ?: run {
                gray.recycle()
                return null
            }

        // Kandidat kanan kolom JENIS BARANG: garis vertikal pertama yang cukup jauh.
        val right = verticalPeaks
            .filter { it > left + (w * 0.25f).toInt() }
            .minByOrNull { it }
            ?: run {
                gray.recycle()
                return null
            }

        val horizontalScores = IntArray(h)
        for (y in yStart until yEnd) {
            var run = 0
            var best = 0
            for (x in left.coerceAtLeast(0) until right.coerceAtMost(w)) {
                if (Color.red(pixels[y * w + x]) < 125) {
                    run++
                    best = max(best, run)
                } else {
                    run = 0
                }
            }
            horizontalScores[y] = best
        }

        val horizontalPeaks = clusterPeaks(
            horizontalScores,
            minScore = max(80, ((right - left) * 0.45f).toInt()),
            minDistance = max(8, (h * 0.012f).toInt())
        )

        val top = horizontalPeaks
            .filter { it in yStart..(h * 0.75f).toInt() }
            .minByOrNull { it }
        val bottom = horizontalPeaks
            .filter { it > (top ?: 0) + h * 0.20f }
            .maxByOrNull { it }

        gray.recycle()

        if (top == null || bottom == null || bottom - top < h * 0.20f) return null

        return TableRegion(
            left = left.coerceAtLeast(0),
            top = top.coerceAtLeast(0),
            right = right.coerceAtMost(w),
            bottom = bottom.coerceAtMost(h)
        )
    }

    private fun clusterPeaks(
        scores: IntArray,
        minScore: Int,
        minDistance: Int
    ): List<Int> {
        val candidates = scores.indices
            .filter { scores[it] >= minScore }
            .sortedByDescending { scores[it] }

        val selected = mutableListOf<Int>()
        for (index in candidates) {
            if (selected.none { abs(it - index) < minDistance }) {
                selected += index
            }
        }
        return selected.sorted()
    }

    /**
     * Row segmentation V13: pakai garis tabel sebagai batas, lalu cari pita tinta
     * di dalam tiap slot. Ini mencegah baris 1 dan 2 tercampur.
     */
    private fun splitRowsV13(bitmap: Bitmap, table: TableRegion): List<Quad> {
        val crop = safeCrop(bitmap, table.left, table.top, table.right, table.bottom)
            ?: return emptyList()
        val cleaned = cleanTableLinesV13(crop)

        val inkBands = detectInkBandsV13(cleaned)
        cleaned.recycle()
        crop.recycle()

        if (inkBands.size >= 3) {
            return inkBands.take(15).map {
                Quad(
                    table.left,
                    table.top + it.first,
                    table.right,
                    table.top + it.second
                )
            }
        }

        // Fallback: BTB umumnya menyediakan 10 baris data sebelum area TOTAL.
        val dataHeight = (table.bottom - table.top) * 0.78f
        val rowHeight = dataHeight / 10f
        return (0 until 10).map { i ->
            Quad(
                table.left + 3,
                table.top + (i * rowHeight).toInt() + 2,
                table.right - 3,
                table.top + ((i + 1) * rowHeight).toInt() - 2
            )
        }
    }

    private fun detectInkBandsV13(source: Bitmap): List<Pair<Int, Int>> {
        val gray = grayscale(source)
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)

        val score = IntArray(h)
        for (y in 0 until h) {
            var dark = 0
            for (x in 0 until w) {
                if (Color.red(pixels[y * w + x]) < 120) dark++
            }
            score[y] = dark
        }

        // Hilangkan puncak yang terlalu panjang: biasanya garis tabel.
        val threshold = max(3, (w * 0.018f).toInt())
        val bands = mutableListOf<Pair<Int, Int>>()
        var start = -1
        for (y in 0 until h) {
            val active = score[y] >= threshold && score[y] < w * 0.70f
            if (active && start < 0) start = y
            if ((!active || y == h - 1) && start >= 0) {
                val end = if (!active) y - 1 else y
                if (end - start >= 5) {
                    bands += (start - 3).coerceAtLeast(0) to
                        (end + 4).coerceAtMost(h)
                }
                start = -1
            }
        }
        gray.recycle()

        // Gabungkan pecahan stroke dalam satu baris.
        val merged = mutableListOf<Pair<Int, Int>>()
        for (band in bands) {
            val last = merged.lastOrNull()
            if (last != null && band.first - last.second <= 12) {
                merged[merged.lastIndex] = last.first to max(last.second, band.second)
            } else merged += band
        }

        return merged
            .filter { (it.second - it.first) in 8..80 }
            .sortedBy { it.first }
    }

    private fun cleanTableLinesV13(source: Bitmap): Bitmap {
        val gray = grayscale(source)
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)

        // Adaptive-ish threshold: kertas terang tetap putih, tinta gelap tetap hitam.
        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            pixels[i] = if (r < 145) Color.BLACK else Color.WHITE
        }

        // Hapus garis horizontal/vertikal yang sangat panjang.
        for (y in 0 until h) {
            var run = 0
            var best = 0
            for (x in 0 until w) {
                if (Color.red(pixels[y * w + x]) == 0) {
                    run++
                    best = max(best, run)
                } else run = 0
            }
            if (best >= w * 0.55f) {
                for (x in 0 until w) pixels[y * w + x] = Color.WHITE
            }
        }

        for (x in 0 until w) {
            var run = 0
            var best = 0
            for (y in 0 until h) {
                if (Color.red(pixels[y * w + x]) == 0) {
                    run++
                    best = max(best, run)
                } else run = 0
            }
            if (best >= h * 0.45f) {
                for (y in 0 until h) pixels[y * w + x] = Color.WHITE
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        gray.recycle()
        return out
    }

    private data class LineResult(
        val text: String,
        val elements: List<Text.Element>
    )

    private suspend fun recognizeLine(
        recognizer: TextRecognizer,
        bitmap: Bitmap
    ): LineResult? {
        val text = recognize(recognizer, bitmap) ?: return null
        val elements = text.textBlocks
            .flatMap { it.lines }
            .flatMap { it.elements }
            .sortedBy { it.boundingBox?.left ?: Int.MAX_VALUE }
        return LineResult(text.text, elements)
    }

    /** Parse OCR + bounding boxes menjadi kandidat angka, dengan koreksi konteks. */
    private fun parseLineCandidates(
        result: LineResult?,
        rowBitmap: Bitmap
    ): List<Double> {
        if (result == null) return emptyList()

        val candidates = mutableListOf<Double>()
        for (element in result.elements) {
            val corrected = contextualNormalize(element.text)
            val numbers = splitSuspiciousToken(corrected, element.boundingBox, rowBitmap.height)
            candidates += numbers.map { it.toDouble() }
        }

        // Jika element OCR tidak memberikan apa-apa, gunakan teks baris sebagai fallback.
        if (candidates.isEmpty()) {
            candidates += contextualExtractNumbers(result.text).map { it.toDouble() }
        }
        return candidates
    }

    private fun splitSuspiciousToken(
        token: String,
        box: android.graphics.Rect?,
        rowHeight: Int
    ): List<String> {
        val digits = token.filter { it.isDigit() }
        if (digits.isEmpty()) return emptyList()
        if (digits.length <= 2) return listOf(digits)

        val width = box?.width() ?: 0
        val height = box?.height() ?: rowHeight
        val unusuallyWide = width > height * 1.35f

        // Konteks BTB: 3 digit yang sangat lebar sering sebenarnya beberapa koli
        // yang tergabung. Jangan membelah semua angka 3 digit karena BTB lain bisa
        // mempunyai berat > 99 kg.
        if (digits.length == 3 && unusuallyWide) {
            if (digits[0] == digits[1] && digits[1] == digits[2]) {
                return listOf(digits[0].toString(), digits[1].toString(), digits[2].toString())
            }
            val a = digits.substring(0, 2)
            val b = digits.substring(2)
            val c = digits.substring(0, 1)
            val d = digits.substring(1)
            return if (a.toIntOrNull()?.let { it in 1..99 } == true && b.toIntOrNull()?.let { it in 1..99 } == true) {
                listOf(a, b)
            } else listOf(c, d)
        }

        return listOf(digits)
    }

    private suspend fun segmentAndRecognizeTokensV13(
        recognizer: TextRecognizer,
        source: Bitmap
    ): List<Double> {
        val tokens = segmentTokensV13(source)
        val values = mutableListOf<Double>()
        for (token in tokens) {
            val crop = safeCrop(source, token.left, token.top, token.right, token.bottom) ?: continue
            val value = recognizeToken(recognizer, crop)
            crop.recycle()
            if (value != null) values += value
        }
        return values
    }

    private fun segmentTokensV13(source: Bitmap): List<TokenBox> {
        val gray = grayscale(source)
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)
        val ink = IntArray(w)
        for (x in 0 until w) {
            var n = 0
            for (y in 0 until h) if (Color.red(pixels[y * w + x]) < 125) n++
            ink[x] = n
        }
        gray.recycle()

        val spans = mutableListOf<Pair<Int, Int>>()
        var start = -1
        for (x in 0 until w) {
            val active = ink[x] >= 1
            if (active && start < 0) start = x
            if ((!active || x == w - 1) && start >= 0) {
                val end = if (!active) x - 1 else x
                if (end - start >= 1) spans += start to end
                start = -1
            }
        }

        val grouped = mutableListOf<Pair<Int, Int>>()
        for (span in spans) {
            val last = grouped.lastOrNull()
            if (last != null && span.first - last.second <= max(3, h / 12)) {
                grouped[grouped.lastIndex] = last.first to span.second
            } else grouped += span
        }

        return grouped.mapNotNull { (l, r) ->
            val top = 0
            val bottom = h
            if (r - l < 2) null else TokenBox(
                (l - 3).coerceAtLeast(0),
                top,
                (r + 4).coerceAtMost(w),
                bottom
            )
        }
    }

    private fun chooseContextualCandidates(
        primary: List<Double>,
        secondary: List<Double>
    ): List<Double> {
        if (primary.isEmpty()) return secondary
        if (secondary.isEmpty()) return primary

        // Pilih hasil yang lebih masuk akal untuk berat/koli: lebih banyak kandidat
        // 1-2 digit dan lebih sedikit token 3 digit yang sangat besar.
        fun score(values: List<Double>): Int {
            var s = values.size * 10
            for (v in values) {
                if (v in 1.0..99.0) s += 4
                if (v in 100.0..999.0) s -= 3
            }
            return s
        }
        return if (score(secondary) > score(primary)) secondary else primary
    }

    private fun contextualNormalize(text: String): String =
        text.uppercase()
            .replace('O', '0')
            .replace('I', '1')
            .replace('L', '1')
            .replace('|', '1')
            .replace('S', '5')
            .replace('B', '8')
            .replace('Z', '2')
            .replace(Regex("[^0-9]"), "")

    private fun contextualExtractNumbers(text: String): List<String> =
        Regex("\\d{1,3}")
            .findAll(
                text.uppercase()
                    .replace('O', '0')
                    .replace('I', '1')
                    .replace('L', '1')
                    .replace('S', '5')
                    .replace('B', '8')
            )
            .map { it.value }
            .filter { it.toIntOrNull()?.let { value -> value in 1..999 } == true }
            .toList()

    private fun calculateTotalKg(values: List<Double>): Double {
        // Berat BTB yang dibaca adalah bilangan bulat. Gunakan Long agar penjumlahan
        // deterministik dan tidak terkena error floating-point seperti penjumlahan biasa.
        var total = 0L
        for (value in values) {
            total += kotlin.math.round(value).toLong()
        }
        return total.toDouble()
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

        val total = calculateTotalKg(values)
        return Result(
            weights = values,
            rawText = text?.text.orEmpty(),
            rows = if (row.isBlank()) emptyList() else listOf(row),
            expectedRows = 1,
            calculatedTotalKg = total,
            verificationMessage = "Verifikasi: ${values.size} koli, total dihitung otomatis = ${formatNumber(total)} KG"
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
