package com.example.cargomanifestapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BtbLabelItem(
    val btbId: String,
    val labelNumber: Int,
    val hariTanggal: String,
    val customerName: String,
    val trademarks: String,
    val jenisBarang: String,
    val beratAsli: Double,
    val beratPembulatan: Double
) {
    val labelId: String
        get() = "$btbId-L${labelNumber.toString().padStart(2, '0')}"
}

object BtbLabelUtils {
    fun createLabels(data: BtbFormData): List<BtbLabelItem> =
        data.daftarTimbangan.mapIndexed { index, weight ->
            BtbLabelItem(
                btbId = data.id,
                labelNumber = index + 1,
                hariTanggal = data.hariTanggal,
                customerName = data.customerName,
                trademarks = data.trademarks,
                jenisBarang = data.jenisBarang,
                beratAsli = weight,
                beratPembulatan = roundWeight(weight)
            )
        }

    fun encode(data: BtbFormData): String {
        val root = org.json.JSONObject()
        root.put("id", data.id)
        root.put("hariTanggal", data.hariTanggal)
        root.put("customerName", data.customerName)
        root.put("trademarks", data.trademarks)
        root.put("jenisBarang", data.jenisBarang)
        root.put("weights", org.json.JSONArray().apply {
            data.daftarTimbangan.forEach { put(it) }
        })
        return root.toString()
    }

    fun decode(json: String): BtbFormData? {
        return try {
            val root = org.json.JSONObject(json)
            val arr = root.optJSONArray("weights") ?: org.json.JSONArray()
            val weights = buildList {
                for (i in 0 until arr.length()) {
                    val v = arr.optDouble(i, Double.NaN)
                    if (v.isFinite() && v > 0.0) add(v)
                }
            }
            BtbFormData(
                id = root.optString("id", System.currentTimeMillis().toString()),
                hariTanggal = root.optString("hariTanggal"),
                customerName = root.optString("customerName"),
                trademarks = root.optString("trademarks"),
                jenisBarang = root.optString("jenisBarang"),
                daftarTimbangan = weights
            )
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Generator PDF label cargo.
 *
 * Desain mengikuti template LABEWA CARGO:
 * - Logo + MY INDO AIRLINES di bagian atas
 * - Header oranye LABEWA CARGO
 * - No. SMU/Air Waybill No.
 * - Tujuan / Jumlah Kiriman
 * - Transit / Berat tiap koli
 * - Keterangan lain
 * - Origin Station / House Waybill No.
 *
 * Data yang memang tersedia dari BTB tetap diambil otomatis.
 * Field yang belum tersedia pada model BTB ditampilkan "-" agar tidak
 * mengarang data operasional.
 */
object BtbLabelPdfWriter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    private const val LEFT = 36f
    private const val RIGHT = 559f
    private const val TOP = 32f
    private const val BOTTOM = 810f
    private const val ORANGE = 0xFFE66A00.toInt()

    fun createPdf(context: Context, data: BtbFormData): File {
        val labels = BtbLabelUtils.createLabels(data)
        require(labels.isNotEmpty()) { "BTB belum mempunyai data timbangan." }

        val document = PdfDocument()

        labels.forEachIndexed { index, label ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            drawLabelPage(context, canvas, data, label, labels.size)
            document.finishPage(page)
        }

        val file = File(
            context.cacheDir,
            "LABEL_${data.id}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"
        )

        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun drawLabelPage(
        context: Context,
        canvas: Canvas,
        data: BtbFormData,
        label: BtbLabelItem,
        totalLabels: Int
    ) {
        canvas.drawColor(android.graphics.Color.WHITE)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = ORANGE
            strokeWidth = 2.2f
        }
        val thinOrangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = ORANGE
            strokeWidth = 1.4f
        }
        val orangeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ORANGE
        }
        val blackBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 15f
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(80, 65, 60)
            textSize = 13f
        }
        val bodyBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(80, 65, 60)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
        }
        val whiteBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 29f
        }
        val airlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(30, 55, 100)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 17f
            textAlign = Paint.Align.CENTER
        }
        val centerBlackBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
            textAlign = Paint.Align.CENTER
        }

        // Outer page/template border.
        canvas.drawRect(LEFT, TOP, RIGHT, BOTTOM, borderPaint)

        // Logo area.
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.logo_app)
        if (logo != null) {
            val maxW = 95f
            val maxH = 72f
            val scale = minOf(maxW / logo.width, maxH / logo.height)
            val w = logo.width * scale
            val h = logo.height * scale
            val dst = RectF(
                (PAGE_WIDTH - w) / 2f,
                TOP + 8f,
                (PAGE_WIDTH + w) / 2f,
                TOP + 8f + h
            )
            canvas.drawBitmap(logo, null, dst, null)
        }
        canvas.drawText("MY INDO AIRLINES", PAGE_WIDTH / 2f, TOP + 88f, airlinePaint)

        // Orange LABEWA CARGO title bar.
        val titleTop = TOP + 102f
        val titleBottom = titleTop + 54f
        canvas.drawRect(LEFT, titleTop, RIGHT, titleBottom, orangeFillPaint)
        canvas.drawText("LABEWA CARGO", (LEFT + RIGHT) / 2f, titleTop + 39f, whiteBold)

        // No. SMU/Air Waybill No.
        val awbTop = titleBottom
        val awbBottom = awbTop + 98f
        canvas.drawLine(LEFT, awbBottom, RIGHT, awbBottom, thinOrangePaint)
        drawLabel(canvas, "No. SMU/Air Waybill No.", LEFT + 7f, awbTop + 24f, body)
        drawValue(canvas, label.btbId, LEFT + 7f, awbTop + 56f, blackBold)

        // Main two-column grid.
        val gridTop = awbBottom
        val colX = 297.5f
        val row1Bottom = gridTop + 112f
        val row2Bottom = row1Bottom + 112f
        val row3Bottom = row2Bottom + 62f
        val gridBottom = row3Bottom + 112f

        canvas.drawLine(colX, gridTop, colX, row2Bottom, thinOrangePaint)
        canvas.drawLine(LEFT, row1Bottom, RIGHT, row1Bottom, thinOrangePaint)
        canvas.drawLine(LEFT, row2Bottom, RIGHT, row2Bottom, thinOrangePaint)
        canvas.drawLine(LEFT, row3Bottom, RIGHT, row3Bottom, thinOrangePaint)

        // Row 1: Destination / total pcs.
        drawLabel(canvas, "Tujuan/Destination", LEFT + 7f, gridTop + 23f, body)
        drawValue(canvas, "-", LEFT + 7f, gridTop + 55f, bodyBold)
        drawLabel(canvas, "Jumlah Kiriman/Ttl. No. of Pcs", colX + 7f, gridTop + 23f, body)
        drawValue(canvas, "${totalLabels} PCS", colX + 7f, gridTop + 55f, bodyBold)

        // Row 2: Transit / weight.
        drawLabel(canvas, "Stn. Transit/Transfer Points", LEFT + 7f, row1Bottom + 23f, body)
        drawValue(canvas, "-", LEFT + 7f, row1Bottom + 55f, bodyBold)
        drawLabel(canvas, "Berat tiap koli/weight of this piece", colX + 7f, row1Bottom + 23f, body)
        drawValue(
            canvas,
            String.format(Locale.US, "%.0f KG", label.beratPembulatan),
            colX + 7f,
            row1Bottom + 55f,
            bodyBold
        )

        // Row 3: Other information.
        drawLabel(canvas, "Keterangan lain/Other Information", LEFT + 7f, row2Bottom + 22f, body)
        drawValue(canvas, "Customer: ${label.customerName.ifBlank { "-" }}", LEFT + 7f, row2Bottom + 47f, bodyBold)
        if (label.trademarks.isNotBlank()) {
            drawValue(canvas, "Trademark: ${label.trademarks}", LEFT + 210f, row2Bottom + 47f, body)
        }

        // Row 4: Origin / House Waybill.
        drawLabel(canvas, "Bandara asal/Origin Station", LEFT + 7f, row3Bottom + 23f, body)
        drawValue(canvas, "-", LEFT + 7f, row3Bottom + 55f, bodyBold)
        drawLabel(canvas, "House Waybill No.", colX + 7f, row3Bottom + 23f, body)
        drawValue(canvas, label.labelId, colX + 7f, row3Bottom + 55f, bodyBold)

        // Footer metadata kept inside the template border.
        drawValue(canvas, "Label ${label.labelNumber} dari $totalLabels", LEFT + 7f, gridBottom + 36f, body)
        drawValue(canvas, "ID: ${label.labelId}", LEFT + 7f, gridBottom + 62f, body)
        drawValue(canvas, "Dokumen turunan dari BTB", LEFT + 7f, BOTTOM - 42f, body)
        drawValue(
            canvas,
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
            LEFT + 7f,
            BOTTOM - 18f,
            body
        )
    }

    private fun drawLabel(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        canvas.drawText(text, x, y, paint)
    }

    private fun drawValue(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        canvas.drawText(text, x, y, paint)
    }

    fun sharePdf(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan Label via..."))
    }
}
