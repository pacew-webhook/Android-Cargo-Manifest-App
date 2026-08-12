package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Penyimpanan foto BTB permanen di internal app storage.
 *
 * File fisik:
 *   <filesDir>/btb_photos/BTB_yyyyMMdd_HHmmss_SSS.jpg
 *
 * Database hanya menyimpan URI FileProvider.
 */
object BtbPhotoStorage {
    private const val PHOTO_DIR = "btb_photos"

    private fun photoDir(context: Context): File =
        File(context.filesDir, PHOTO_DIR).apply { mkdirs() }

    fun createPhotoUri(context: Context): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(photoDir(context), "BTB_$stamp.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }

    /**
     * Menghapus file berdasarkan URI FileProvider.
     *
     * Uri content://... bukan path filesystem, jadi jangan memakai uri.path
     * sebagai path file. Kita ambil nama file dari lastPathSegment lalu
     * mencocokkannya dengan folder BTB milik aplikasi.
     */
    fun deletePhoto(context: Context, uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            val fileName = Uri.decode(uri.lastPathSegment ?: return)
                .substringAfterLast('/')
            if (fileName.isBlank() || fileName.contains(File.separator)) return

            val root = photoDir(context).canonicalFile
            val target = File(root, fileName).canonicalFile

            if (target.parentFile?.canonicalFile == root && target.exists()) {
                target.delete()
            }
        } catch (_: Exception) {
            // Penghapusan foto bersifat best-effort; jangan membuat UI BTB crash.
        }
    }

    fun deletePhotos(context: Context, uriStrings: Collection<String>) {
        uriStrings.forEach { deletePhoto(context, it) }
    }
}
