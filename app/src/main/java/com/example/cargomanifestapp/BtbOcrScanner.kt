package com.example.cargomanifestapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/**
 * OCR BTB.
 *
 * BTB yang dipakai aplikasi memiliki pola tabel yang relatif tetap.
 * Scanner tidak mencoba membaca seluruh isi formulir sebagai data KG.
 * Ia mengambil kandidat angka 1-3 digit dari area tabel, lalu pengguna
 * tetap diberi layar koreksi sebelum data dimasukkan ke Form Stowing.
 *
 * Catatan: ML Kit lebih kuat untuk teks cetak daripada tulisan tangan.
 * Karena itu hasil scan WAJIB dapat dikoreksi pengguna.
 */
object BtbOcrScanner {

    data class Result(
        val weights: List<Double>,
        val rawText: String = ""
    )

    suspend fun scan(context: Context, uri: Uri): Result = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            val image = InputImage.fromFilePath(context, uri)
            val visionText = suspendCancellableCoroutine<Text?> { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resume(null) }
            }

            if (visionText == null) {
                return@withContext Result(emptyList())
            }

            val width = image.width.coerceAtLeast(1)
            val height = image.height.coerceAtLeast(1)

            val candidates = mutableListOf<Candidate>()
            visionText.textBlocks
                .flatMap { it.lines }
                .forEach { line ->
                    val box = line.boundingBox ?: return@forEach
                    val topRatio = box.top.toFloat() / height
                    val leftRatio = box.left.toFloat() / width

                    // Area tabel angka pada foto BTB contoh:
                    // bagian bawah formulir dan sisi kiri/menengah.
                    if (topRatio < 0.38f || topRatio > 0.94f) return@forEach
                    if (leftRatio > 0.78f) return@forEach

                    val tokens = Regex("""(?<!\d)\d{1,3}(?!\d)""")
                        .findAll(line.text)
                        .map { it.value }
                        .toList()

                    if (tokens.isEmpty()) return@forEach

                    tokens.forEach { token ->
                        val value = token.toDoubleOrNull() ?: return@forEach
                        if (value > 0.0 && value <= 999.0) {
                            candidates += Candidate(
                                value = value,
                                centerY = (box.top + box.bottom) / 2f,
                                centerX = box.left.toFloat()
                            )
                        }
                    }
                }

            // Urutkan seperti pembacaan manusia: dari atas ke bawah, lalu kiri ke kanan.
            // Jika OCR menggabungkan beberapa angka dalam satu baris, urutan regex
            // sudah mengikuti urutan teks pada baris tersebut.
            val sorted = candidates.sortedWith(
                compareBy<Candidate> { it.centerY }.thenBy { it.centerX }
            )

            Result(
                weights = sorted.map { it.value },
                rawText = visionText.text
            )
        } finally {
            recognizer.close()
        }
    }

    /**
     * Scan URI yang berasal dari FileProvider lalu menghapus file sementara
     * setelah proses selesai.
     */
    suspend fun scanAndDeleteTemp(context: Context, uri: Uri): Result {
        return try {
            scan(context, uri)
        } finally {
            BtbPhotoStorage.deletePhoto(context, uri.toString())
        }
    }

    private data class Candidate(
        val value: Double,
        val centerY: Float,
        val centerX: Float
    )
}
