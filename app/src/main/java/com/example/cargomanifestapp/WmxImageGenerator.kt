package com.example.cargomanifestapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renderer foto Manifest WMX.
 *
 * Foto sengaja hanya menampilkan:
 * No | PTI | PENGIRIM | PENERIMA | JENIS BARANG | KOLI
 *
 * KG tetap boleh ada di CargoItem/Excel, tetapi TIDAK dirender ke foto.
 */
object WmxImageGenerator {

    private const val MARGIN = 24f
    private const val INFO_HEIGHT = 220f
    private const val ROW_HEIGHT = 58f
    private const val HEADER_HEIGHT = ROW_HEIGHT * 2f

    // Proporsi mengikuti template WMX asli, dengan kolom KG dihilangkan.
    private val columnWidths = floatArrayOf(
        95f,   // No (A)
        132f,  // PTI (B)
        116f,  // PENGIRIM (C)
        300f,  // PENERIMA (D)
        222f,  // JENIS BARANG (E)
        95f    // KOLI (F)
    )

    private val headers = arrayOf(
        "No", "PTI", "PENGIRIM", "PENERIMA", "JENIS BARANG", "KOLI"
    )

    data class Header(
        val date: String = "",
        val acReg: String = "",
        val from: String = "DJJ",
        val to: String = "WMX",
        val flightNo: String = "",
        val fltFreq: String = ""
    )

    /**
     * Dibuat dari ManifestGroup agar langsung cocok dengan struktur project sekarang.
     * Karena CargoItem saat ini belum mempunyai field PENGIRIM/PENERIMA terpisah,
     * customer dipakai sebagai PENGIRIM dan PENERIMA dibiarkan kosong.
     */
    fun generateFromManifestGroups(
        header: Header,
        groups: List<ManifestGroup>
    ): Bitmap {
        val rows = groups.mapIndexed { index, group ->
            val item = group.summary
            Row(
                no = index + 1,
                pti = item.pti.trim(),
                pengirim = item.customer.trim(),
                penerima = "",
                jenisBarang = item.description.trim(),
                koli = item.pcsQty.trim()
            )
        }
        return generateBitmap(header, rows)
    }

    data class Row(
        val no: Int,
        val pti: String,
        val pengirim: String,
        val penerima: String,
        val jenisBarang: String,
        val koli: String
    )

    fun generateBitmap(header: Header, rows: List<Row>): Bitmap {
        val width = (MARGIN * 2f + columnWidths.sum()).toInt()
        val height = (INFO_HEIGHT + HEADER_HEIGHT + rows.size * ROW_HEIGHT + MARGIN).toInt()

        require(width > 0 && height > 0) { "Ukuran gambar tidak valid" }
        // Android/Skia pada banyak perangkat mempunyai batas dimensi bitmap sekitar 32K px.
        require(height <= 32000) {
            "Data terlalu banyak untuk satu gambar panjang (${rows.size} baris). " +
                "Kurangi jumlah baris atau gunakan beberapa gambar."
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        drawFlightHeader(canvas, header)
        drawTableHeader(canvas, INFO_HEIGHT)
        drawRows(canvas, INFO_HEIGHT + HEADER_HEIGHT, rows)

        return bitmap
    }

    private fun drawFlightHeader(canvas: Canvas, header: Header) {
        val title = paint(30f, bold = true)
        canvas.drawText("CARGO MANIFEST — WMX", MARGIN, 38f, title)

        val label = paint(22f, bold = true)
        val value = paint(22f)
        val rightX = MARGIN + columnWidths.sum() / 2f

        val left = listOf(
            "DATE" to header.date,
            "FROM" to header.from,
            "TO" to header.to
        )
        val right = listOf(
            "A/C REG" to header.acReg,
            "FLIGHT NO" to header.flightNo,
            "FLT FREQ" to header.fltFreq
        )

        var y = 86f
        for (i in 0..2) {
            canvas.drawText(left[i].first, MARGIN, y, label)
            canvas.drawText(": ${left[i].second}", MARGIN + 105f, y, value)
            canvas.drawText(right[i].first, rightX, y, label)
            canvas.drawText(": ${right[i].second}", rightX + 145f, y, value)
            y += 45f
        }
    }

    private fun drawTableHeader(canvas: Canvas, top: Float) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val text = paint(21f, bold = true).apply { textAlign = Paint.Align.CENTER }

        var x = MARGIN
        headers.forEachIndexed { index, header ->
            val width = columnWidths[index]
            canvas.drawRect(x, top, x + width, top + HEADER_HEIGHT, border)
            val centerY = top + HEADER_HEIGHT / 2f
            val baseline = centerY - (text.ascent() + text.descent()) / 2f
            canvas.drawText(header, x + width / 2f, baseline, text)
            x += width
        }
    }

    private fun drawRows(canvas: Canvas, top: Float, rows: List<Row>) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val regular = paint(20f)
        val centered = paint(20f).apply { textAlign = Paint.Align.CENTER }
        val centeredBold = paint(20f, bold = true).apply { textAlign = Paint.Align.CENTER }

        rows.forEachIndexed { index, row ->
            val y = top + index * ROW_HEIGHT
            val values = arrayOf(
                row.no.toString(), row.pti, row.pengirim,
                row.penerima, row.jenisBarang, row.koli
            )

            var x = MARGIN
            values.forEachIndexed { col, value ->
                val width = columnWidths[col]
                canvas.drawRect(x, y, x + width, y + ROW_HEIGHT, border)
                val p = when (col) {
                    0 -> centeredBold
                    1, 5 -> centered
                    else -> regular
                }
                val clipped = clipText(value, p, width - 16f)
                val baseline = y + ROW_HEIGHT / 2f - (p.ascent() + p.descent()) / 2f
                val tx = if (col == 0 || col == 1 || col == 5) {
                    x + width / 2f
                } else {
                    x + 8f
                }
                canvas.drawText(clipped, tx, baseline, p)
                x += width
            }
        }
    }

    private fun clipText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var result = text
        while (result.isNotEmpty() && paint.measureText("$result…") > maxWidth) {
            result = result.dropLast(1)
        }
        return "$result…"
    }

    private fun paint(size: Float, bold: Boolean = false): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Simpan ke cache agar bisa dibagikan lewat FileProvider yang sudah ada di project. */
    fun saveToCache(context: Context, bitmap: Bitmap): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(context.cacheDir, "WMX_Manifest_$stamp.png")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Gagal membuat file gambar WMX"
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }
}
