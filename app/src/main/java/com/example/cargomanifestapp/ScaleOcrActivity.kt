package com.example.cargomanifestapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/**
 * OCR angka langsung dari display timbangan.
 *
 * FIX7:
 * - Kamera dan Upload Foto menggunakan pipeline kandidat OCR yang sama.
 * - Upload foto tidak memakai OCR BTB/label paket.
 * - Kandidat diprioritaskan berdasarkan angka desimal, posisi display,
 *   ukuran bounding box, dan kedekatan ke area display.
 * - Foto yang sama harus menghasilkan angka yang sama, misalnya 3.80 -> 3.80.
 */
class ScaleOcrActivity : ComponentActivity() {

    companion object {
        const val EXTRA_WEIGHT = "extra_weight"
        private const val CAMERA_REQUEST = 501
    }

    private lateinit var previewView: PreviewView
    private lateinit var detectedText: TextView
    private lateinit var useButton: Button
    private lateinit var rescanButton: Button
    private lateinit var uploadButton: Button

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val uploadExecutor = Executors.newSingleThreadExecutor()
    private lateinit var recognizer: TextRecognizer

    private var currentWeight: Double? = null
    private var lastCandidate: Double? = null
    private var stableCount = 0
    private var acceptedForSession: Double? = null
    private var lastScanTime = 0L

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) processUploadedPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scale_ocr)

        previewView = findViewById(R.id.previewView)
        detectedText = findViewById(R.id.tvDetected)
        useButton = findViewById(R.id.btnUseWeight)
        rescanButton = findViewById(R.id.btnRescan)
        uploadButton = findViewById(R.id.btnUploadPhoto)
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        useButton.setOnClickListener {
            currentWeight?.let { weight ->
                setResult(Activity.RESULT_OK, intent.putExtra(EXTRA_WEIGHT, weight))
                finish()
            }
        }

        rescanButton.setOnClickListener { resetDetection() }
        uploadButton.setOnClickListener { pickImageLauncher.launch("image/*") }

        if (hasCameraPermission()) startCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
    }

    private fun resetDetection() {
        currentWeight = null
        acceptedForSession = null
        lastCandidate = null
        stableCount = 0
        detectedText.text = "Menunggu angka..."
        useButton.isEnabled = false
        useButton.text = "Gunakan KG"
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST) {
            if (hasCameraPermission()) startCamera()
            else {
                Toast.makeText(this, "Izin kamera diperlukan untuk membaca display timbangan.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, CameraScaleAnalyzer()) }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Kamera gagal dibuka: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private inner class CameraScaleAnalyzer : ImageAnalysis.Analyzer {
        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val now = System.currentTimeMillis()
            if (now - lastScanTime < 350L) {
                imageProxy.close()
                return
            }
            lastScanTime = now

            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }

            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    // Hanya baca area kotak SCAN, bukan seluruh frame kamera.
                    // Ini mencegah angka dari jam/status/objek lain menjadi hasil OCR.
                    val candidate = findBestWeight(text, image.width, image.height, cameraOnly = true)
                    if (candidate != null) processCandidate(candidate)
                }
                .addOnCompleteListener { imageProxy.close() }
        }
    }

    /** Upload memakai OCR recognizer dan findBestWeight yang sama dengan kamera. */
    private fun processUploadedPhoto(uri: android.net.Uri) {
        resetDetection()
        detectedText.text = "Membaca foto..."
        uploadExecutor.execute {
            try {
                val original = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: throw IllegalArgumentException("Foto tidak dapat dibaca")
                val bitmap = rotateFromExif(original, uri)

                // FIX7: JANGAN OCR seluruh foto. Foto timbangan sering berisi
                // label paket, tanggal, nomor resi, dll. Angka-angka tersebut
                // sebelumnya bisa mengalahkan angka pada display timbangan.
                // Upload sekarang hanya membaca beberapa crop ketat di area
                // display bagian atas.
                val candidates = mutableListOf<Double>()
                val displayCrops = listOfNotNull(
                    // Prioritas pertama: cari langsung area LED merah. Ini
                    // mencegah angka pada label paket ikut terbaca.
                    cropRedLedArea(bitmap),
                    cropDisplayArea(bitmap, 0.05f, 0.27f, 0.12f, 0.88f),
                    cropDisplayArea(bitmap, 0.07f, 0.24f, 0.18f, 0.82f),
                    cropDisplayArea(bitmap, 0.04f, 0.32f, 0.08f, 0.92f)
                )

                displayCrops.forEach { crop ->
                    val enlarged = Bitmap.createScaledBitmap(
                        crop,
                        crop.width * 3,
                        crop.height * 3,
                        true
                    )
                    runOcr(enlarged, candidates)
                    runOcr(enhanceForDisplay(enlarged), candidates)
                    runOcr(enhanceRedDisplay(enlarged), candidates)
                }

                val best = candidates
                    .filter { it in 0.01..200.0 }
                    .groupingBy { String.format(Locale.US, "%.2f", it) }
                    .eachCount()
                    .entries
                    .sortedWith(
                        compareByDescending<Map.Entry<String, Int>> { it.value }
                            .thenByDescending { key ->
                                // Timbangan digital umumnya menampilkan dua digit desimal.
                                // Jangan mengubah 13.41 menjadi 13.00 hanya karena pembulatan.
                                key.key.count { it == '.' }
                            }
                            .thenBy { abs((it.key.toDoubleOrNull() ?: 0.0) - 50.0) }
                    )
                    .firstOrNull()
                    ?.key
                    ?.toDoubleOrNull()

                runOnUiThread {
                    if (best != null) {
                        acceptUploadedWeight(best)
                    } else {
                        detectedText.text = "Angka tidak terbaca"
                        Toast.makeText(this, "Coba foto lebih dekat ke display timbangan.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    detectedText.text = "Gagal membaca foto"
                    Toast.makeText(this, "OCR foto gagal: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun runOcr(bitmap: Bitmap, output: MutableList<Double>) {
        val latch = CountDownLatch(1)
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { text ->
                findBestWeight(text, bitmap.width, bitmap.height, cameraOnly = false)?.let { output += it }
            }
            .addOnCompleteListener { latch.countDown() }
        latch.await(4, TimeUnit.SECONDS)
    }

    private fun acceptUploadedWeight(value: Double) {
        val rounded = String.format(Locale.US, "%.2f", value)
        currentWeight = value
        acceptedForSession = value
        lastCandidate = value
        stableCount = 3
        detectedText.text = "$rounded KG"
        useButton.isEnabled = true
        useButton.text = "Gunakan $rounded KG"
        vibrate()
    }

    private fun findBestWeight(
        text: com.google.mlkit.vision.text.Text,
        width: Int,
        height: Int,
        cameraOnly: Boolean = false
    ): Double? {
        data class Candidate(
            val value: Double,
            val decimal: Boolean,
            val score: Double
        )

        val candidates = mutableListOf<Candidate>()

        for (block in text.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val raw = line.text
                    .replace('O', '0', ignoreCase = true)
                    .replace('I', '1')
                    .replace('l', '1')
                    .replace('S', '5', ignoreCase = true)

                val matches = Regex("(?<!\\d)(\\d{1,4}(?:[.,]\\d{1,2})?)(?!\\d)").findAll(raw)
                for (match in matches) {
                    val value = WeightUtils.parse(match.groupValues[1]) ?: continue
                    if (value <= 0.0 || value > 9999.0) continue

                    val cx = (box.centerX().toDouble() / max(1, width)).coerceIn(0.0, 1.0)
                    val cy = (box.centerY().toDouble() / max(1, height)).coerceIn(0.0, 1.0)
                    val h = box.height().toDouble() / max(1, height)
                    val w = box.width().toDouble() / max(1, width)

                    // Kamera: kotak SCAN berada di bagian atas-tengah. Kandidat
                    // di luar zona ini DITOLAK, bukan sekadar diberi skor rendah.
                    // Upload foto tetap memakai scoring karena posisi display pada
                    // foto dapat berubah setelah crop/rotasi.
                    if (cameraOnly && !(cy in 0.06..0.36 && cx in 0.08..0.92)) continue

                    val areaBonus = when {
                        cy in 0.08..0.32 && cx in 0.18..0.82 -> 6.0
                        cy in 0.06..0.36 && cx in 0.08..0.92 -> 3.0
                        else -> -3.0
                    }
                    val centerBonus = 2.0 - (abs(cx - 0.5) + abs(cy - 0.22))
                    val sizeBonus = (h * 20.0).coerceIn(0.0, 4.0) + (w * 4.0).coerceIn(0.0, 2.0)
                    val hasDecimal = match.groupValues[1].contains('.') || match.groupValues[1].contains(',')
                    if (cameraOnly && !hasDecimal) continue
                    if (cameraOnly && h < 0.035) continue
                    val decimalBonus = if (hasDecimal) 7.0 else 0.0
                    val plausibleBonus = if (value <= 200.0) 2.0 else -2.0

                    candidates += Candidate(
                        value = value,
                        decimal = match.groupValues[1].contains('.'),
                        score = areaBonus + centerBonus + sizeBonus + decimalBonus + plausibleBonus
                    )
                }
            }
        }

        return candidates
            .sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.decimal })
            .firstOrNull()
            ?.value
    }

    private fun processCandidate(value: Double) {
        val rounded = String.format(Locale.US, "%.2f", value)
        runOnUiThread { detectedText.text = "$rounded KG" }

        if (lastCandidate != null && abs(lastCandidate!! - value) < 0.011) stableCount++
        else {
            lastCandidate = value
            stableCount = 1
        }

        if (stableCount >= 3 && acceptedForSession == null) {
            acceptedForSession = value
            currentWeight = value
            vibrate()
            runOnUiThread {
                useButton.isEnabled = true
                useButton.text = "Gunakan ${String.format(Locale.US, "%.2f", value)} KG"
            }
        }
    }

    /**
     * Mencari bounding box piksel merah pada 35% bagian atas foto.
     * Display timbangan pada foto pengguna menggunakan LED merah, sedangkan
     * label/resi berada jauh di bawah. Jika area merah ditemukan, crop ini
     * menjadi sumber OCR paling prioritas.
     */
    private fun cropRedLedArea(bitmap: Bitmap): Bitmap? {
        if (bitmap.width < 100 || bitmap.height < 100) return null

        val leftLimit = (bitmap.width * 0.08f).toInt()
        val rightLimit = (bitmap.width * 0.92f).toInt()
        val topLimit = (bitmap.height * 0.03f).toInt()
        val bottomLimit = (bitmap.height * 0.35f).toInt()

        var minX = bitmap.width
        var minY = bitmap.height
        var maxX = -1
        var maxY = -1
        var redPixels = 0

        // Sampling tiap 3 piksel cukup akurat dan jauh lebih ringan untuk foto besar.
        for (y in topLimit until bottomLimit step 3) {
            for (x in leftLimit until rightLimit step 3) {
                val pixel = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                if (r >= 85 && r > g + 25 && r > b + 25) {
                    redPixels++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }

        if (redPixels < 12 || maxX <= minX || maxY <= minY) return null

        val padX = ((maxX - minX) * 0.35f).toInt().coerceAtLeast(20)
        val padY = ((maxY - minY) * 0.70f).toInt().coerceAtLeast(20)
        val left = (minX - padX).coerceAtLeast(0)
        val top = (minY - padY).coerceAtLeast(0)
        val right = (maxX + padX).coerceAtMost(bitmap.width)
        val bottom = (maxY + padY).coerceAtMost(bitmap.height)
        val width = right - left
        val height = bottom - top
        if (width < 40 || height < 25) return null

        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    private fun cropDisplayArea(
        bitmap: Bitmap,
        topRatio: Float = 0.05f,
        bottomRatio: Float = 0.27f,
        leftRatio: Float = 0.12f,
        rightRatio: Float = 0.88f
    ): Bitmap? {
        if (bitmap.width < 100 || bitmap.height < 100) return null
        val left = (bitmap.width * leftRatio).toInt().coerceAtLeast(0)
        val top = (bitmap.height * topRatio).toInt().coerceAtLeast(0)
        val right = (bitmap.width * rightRatio).toInt().coerceAtMost(bitmap.width)
        val bottom = (bitmap.height * bottomRatio).toInt().coerceAtMost(bitmap.height)
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return null
        return Bitmap.createBitmap(bitmap, left, top, w, h)
    }

    private fun enhanceForDisplay(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        matrix.setScale(1.6f, 1.6f, 1.6f, 1f)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }


    /**
     * Display timbangan pada foto contoh menggunakan angka LED merah.
     * Kanal merah diperkuat dan kanal hijau/biru ditekan agar ML Kit
     * lebih mudah mengenali digit merah tanpa ikut membaca label di bawah.
     */
    private fun enhanceRedDisplay(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val matrix = ColorMatrix(floatArrayOf(
            2.0f, 0.0f, 0.0f, 0f, -80f,
            0.15f, 0.15f, 0.15f, 0f, -35f,
            0.10f, 0.10f, 0.10f, 0f, -35f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun rotateFromExif(bitmap: Bitmap, uri: android.net.Uri): Bitmap {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = android.graphics.Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                    else -> return bitmap
                }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } ?: bitmap
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(80)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer.close()
        cameraExecutor.shutdown()
        uploadExecutor.shutdown()
    }
}
