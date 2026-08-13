package com.example.cargomanifestapp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.net.Uri
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class ScaleOcrActivity : ComponentActivity() {

    companion object {
        const val EXTRA_WEIGHT = "extra_weight"
        private const val CAMERA_REQUEST = 501
    }

    private lateinit var previewView: PreviewView
    private lateinit var detectedText: TextView
    private lateinit var useButton: Button
    private lateinit var rescanButton: Button
    private lateinit var galleryButton: Button

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var recognizer: TextRecognizer
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentWeight: Double? = null
    private var lastCandidate: Double? = null
    private var stableCount = 0
    private var acceptedForSession: Double? = null

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processGalleryImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scale_ocr)

        previewView = findViewById(R.id.previewView)
        detectedText = findViewById(R.id.tvDetected)
        useButton = findViewById(R.id.btnUseWeight)
        rescanButton = findViewById(R.id.btnRescan)
        galleryButton = findViewById(R.id.btnUploadPhoto)
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        useButton.setOnClickListener {
            currentWeight?.let { weight ->
                setResult(Activity.RESULT_OK, intent.putExtra(EXTRA_WEIGHT, weight))
                finish()
            }
        }

        rescanButton.setOnClickListener {
            currentWeight = null
            acceptedForSession = null
            lastCandidate = null
            stableCount = 0
            detectedText.text = "Menunggu angka..."
            useButton.isEnabled = false
        }

        galleryButton.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        if (hasCameraPermission()) startCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST) {
            if (hasCameraPermission()) startCamera()
            else {
                Toast.makeText(this, "Izin kamera diperlukan untuk membaca display timbangan.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun processGalleryImage(uri: Uri) {
        currentWeight = null
        acceptedForSession = null
        lastCandidate = null
        stableCount = 0
        useButton.isEnabled = false
        detectedText.text = "Membaca foto..."
        cameraProvider?.unbindAll()

        // Kamera tetap menjadi mode utama. Saat foto dipilih, OCR dilakukan satu kali
        // pada gambar asli dari galeri tanpa mengubah algoritma pembacaan timbangan.
        cameraExecutor.execute {
            try {
                val image = InputImage.fromFilePath(this, uri)
                recognizer.process(image)
                    .addOnSuccessListener { text ->
                        val candidate = findBestWeight(text, image.width, image.height, restrictScanArea = false)
                        runOnUiThread {
                            if (candidate != null) {
                                currentWeight = candidate
                                acceptedForSession = candidate
                                detectedText.text = String.format(Locale.US, "%.2f KG", candidate)
                                useButton.isEnabled = true
                                useButton.text = "Gunakan ${String.format(Locale.US, "%.2f", candidate)} KG"
                            } else {
                                detectedText.text = "Angka timbangan tidak ditemukan"
                                Toast.makeText(this, "Coba foto lebih dekat dan fokuskan display timbangan.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .addOnFailureListener { error ->
                        runOnUiThread {
                            detectedText.text = "Gagal membaca foto"
                            Toast.makeText(this, "OCR foto gagal: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
            } catch (e: Exception) {
                runOnUiThread {
                    detectedText.text = "Foto tidak dapat dibaca"
                    Toast.makeText(this, "Foto tidak dapat dibuka: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, ScaleAnalyzer()) }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Kamera gagal dibuka: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private inner class ScaleAnalyzer : ImageAnalysis.Analyzer {
        private var lastScanTime = 0L

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
                    val candidate = findBestWeight(text, image.width, image.height)
                    if (candidate != null) processCandidate(candidate)
                }
                .addOnCompleteListener { imageProxy.close() }
        }
    }

    private fun findBestWeight(text: com.google.mlkit.vision.text.Text, width: Int, height: Int, restrictScanArea: Boolean = true): Double? {
        data class Candidate(val value: Double, val decimal: Boolean, val distance: Double)
        val candidates = mutableListOf<Candidate>()

        for (block in text.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val raw = line.text
                    .replace(',', '.')
                    .replace('O', '0', ignoreCase = true)
                    .replace('I', '1')
                    .replace('l', '1')
                val match = Regex("(?<!\\d)(\\d{1,4}(?:\\.\\d{1,2})?)(?!\\d)").find(raw) ?: continue
                val value = match.groupValues[1].toDoubleOrNull() ?: continue
                if (value < 0.0 || value > 9999.0) continue

                val cx = (box.centerX().toDouble() / width).coerceIn(0.0, 1.0)
                val cy = (box.centerY().toDouble() / height).coerceIn(0.0, 1.0)
                // Area scan: mengikuti kotak di bagian atas preview tempat display timbangan diarahkan.
                val inScanArea = cx in 0.08..0.92 && cy in 0.05..0.48
                if (restrictScanArea && !inScanArea) continue

                val decimal = match.groupValues[1].contains('.')
                val distance = abs(cx - 0.5) + abs(cy - 0.25)
                candidates += Candidate(value, decimal, distance)
            }
        }

        // Prioritaskan angka desimal, lalu yang paling dekat dengan pusat kotak scan.
        return candidates.sortedWith(compareByDescending<Candidate> { it.decimal }.thenBy { it.distance }).firstOrNull()?.value
    }

    private fun processCandidate(value: Double) {
        val rounded = String.format(Locale.US, "%.2f", value)
        runOnUiThread {
            detectedText.text = "$rounded KG"
        }

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

    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vibrator.vibrate(80)
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer.close()
        cameraExecutor.shutdown()
    }
}
