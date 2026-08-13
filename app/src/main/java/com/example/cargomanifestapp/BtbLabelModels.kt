package com.example.cargomanifestapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import android.content.Intent
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

object BtbLabelPdfWriter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    fun createPdf(context: Context, data: BtbFormData): File {
        val labels = BtbLabelUtils.createLabels(data)
        require(labels.isNotEmpty()) { "BTB belum mempunyai data timbangan." }

        val document = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 24f
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 16f
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        labels.forEachIndexed { index, label ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            canvas.drawRect(36f, 36f, 559f, 806f, linePaint)
            canvas.drawText("LABEL CARGO", 55f, 80f, titlePaint)
            canvas.drawText("BTB: ${label.btbId}", 55f, 112f, headerPaint)
            canvas.drawText("LABEL: ${label.labelId}", 55f, 138f, headerPaint)

            canvas.drawText("Hari / Tgl", 55f, 185f, smallPaint)
            canvas.drawText(label.hariTanggal, 190f, 185f, bodyPaint)

            canvas.drawText("CUSTOMER", 55f, 220f, smallPaint)
            canvas.drawText(label.customerName, 190f, 220f, bodyPaint)

            canvas.drawText("TRADEMARKS", 55f, 255f, smallPaint)
            canvas.drawText(label.trademarks.ifBlank { "-" }, 190f, 255f, bodyPaint)

            canvas.drawText("JENIS BARANG", 55f, 290f, smallPaint)
            canvas.drawText(label.jenisBarang.ifBlank { "-" }, 190f, 290f, bodyPaint)

            canvas.drawText("TOTAL", 55f, 370f, smallPaint)
            canvas.drawText(String.format(Locale.US, "%.0f KG", label.beratPembulatan), 190f, 370f, headerPaint)

            canvas.drawText("Label ${label.labelNumber} dari ${labels.size}", 55f, 450f, bodyPaint)
            canvas.drawText("ID: ${label.labelId}", 55f, 485f, smallPaint)

            canvas.drawText("Dokumen turunan dari BTB", 55f, 755f, smallPaint)
            canvas.drawText(
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                55f, 780f, smallPaint
            )

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
