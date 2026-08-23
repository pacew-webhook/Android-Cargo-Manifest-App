package com.example.cargomanifestapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Membuat satu gambar panjang untuk dibagikan.
 * KG/Weight sengaja tidak dirender.
 * Mapping: CargoItem.customer -> PENERIMA; senderByGroupKey -> PENGIRIM.
 */
object WmxImageGenerator {
    private const val MARGIN = 24f
    private const val TITLE_HEIGHT = 72f
    private const val HEADER_HEIGHT = 58f
    private const val ROW_HEIGHT = 54f
    private const val BORDER = 1.5f

    private val widths = floatArrayOf(70f, 115f, 170f, 170f, 230f, 80f)
    private val headers = arrayOf("NO", "PTI", "PENGIRIM", "PENERIMA", "JENIS BARANG", "KOLI")

    fun generate(
        groups: List<ManifestGroup>,
        senderByGroupKey: Map<String, String>
    ): Bitmap {
        val width = (MARGIN * 2 + widths.sum()).toInt()
        val height = (MARGIN + TITLE_HEIGHT + HEADER_HEIGHT + groups.size * ROW_HEIGHT + MARGIN).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = BORDER
        }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
        }
        val header = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 18f
        }
        val center = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("CARGO MANIFEST — WMX", MARGIN, 42f, title)

        var x = MARGIN
        headers.forEachIndexed { i, label ->
            canvas.drawRect(x, MARGIN + TITLE_HEIGHT, x + widths[i], MARGIN + TITLE_HEIGHT + HEADER_HEIGHT, border)
            val cy = MARGIN + TITLE_HEIGHT + HEADER_HEIGHT / 2f
            drawCentered(canvas, label, x, cy, widths[i], header)
            x += widths[i]
        }

        groups.forEachIndexed { index, group ->
            val y = MARGIN + TITLE_HEIGHT + HEADER_HEIGHT + index * ROW_HEIGHT
            val sender = senderByGroupKey[group.groupKey].orEmpty().trim().uppercase()
            val values = arrayOf(
                (index + 1).toString(),
                group.summary.pti.trim(),
                sender,
                group.summary.customer.trim(),
                group.summary.description.trim(),
                group.summary.pcsQty.trim()
            )
            x = MARGIN
            values.forEachIndexed { i, value ->
                canvas.drawRect(x, y, x + widths[i], y + ROW_HEIGHT, border)
                if (i == 0 || i == 1 || i == 5) {
                    drawCentered(canvas, fit(value, center, widths[i] - 10f), x, y + ROW_HEIGHT / 2f, widths[i], center)
                } else {
                    canvas.drawText(fit(value, text, widths[i] - 12f), x + 6f, centeredBaseline(y, ROW_HEIGHT, text), text)
                }
                x += widths[i]
            }
        }
        return bitmap
    }

    private fun centeredBaseline(y: Float, h: Float, paint: Paint): Float =
        y + h / 2f - (paint.ascent() + paint.descent()) / 2f

    private fun drawCentered(canvas: Canvas, value: String, x: Float, cy: Float, width: Float, paint: Paint) {
        canvas.drawText(value, x + width / 2f, cy - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    private fun fit(value: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(value) <= maxWidth) return value
        var result = value
        while (result.isNotEmpty() && paint.measureText("$result…") > maxWidth) result = result.dropLast(1)
        return "$result…"
    }
}
