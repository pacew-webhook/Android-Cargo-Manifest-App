package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Penyimpanan foto BTB permanen di internal app storage. */
object BtbPhotoStorage {
    private const val PHOTO_DIR = "btb_photos"

    fun getPhotoDirectory(context: Context): File =
        File(context.filesDir, PHOTO_DIR).apply { mkdirs() }

    fun createPhotoUri(context: Context): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(getPhotoDirectory(context), "BTB_$stamp.jpg")
        // Buat file kosong lebih dulu agar beberapa aplikasi Kamera yang ketat
        // tidak menolak output Uri yang target filenya belum ada.
        file.parentFile?.mkdirs()
        if (!file.exists()) file.createNewFile()
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    fun copyToAppStorage(context: Context, sourceUri: Uri): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val target = File(getPhotoDirectory(context), "BTB_$stamp.jpg")
        context.contentResolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Tidak dapat membaca foto dari Galeri" }
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        require(target.exists() && target.length() > 0L) { "Foto yang dipilih kosong atau gagal disalin" }
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", target)
    }

    fun resolvePhotoFile(context: Context, uriString: String): File? {
        return try {
            val uri = Uri.parse(uriString)
            val lastSegment = uri.lastPathSegment ?: return null
            val fileName = Uri.decode(lastSegment).substringAfterLast('/')

            if (fileName.isBlank() || fileName.contains(File.separator)) {
                return null
            }

            val root = getPhotoDirectory(context).canonicalFile
            val target = File(root, fileName).canonicalFile
            if (target.parentFile?.canonicalFile == root) target else null
        } catch (_: Exception) {
            null
        }
    }

    fun deletePhoto(context: Context, uriString: String) {
        try { resolvePhotoFile(context, uriString)?.takeIf { it.exists() }?.delete() } catch (_: Exception) {}
    }

    fun deletePhotos(context: Context, uriStrings: Collection<String>) {
        uriStrings.forEach { deletePhoto(context, it) }
    }

    fun clearAllPhotos(context: Context) {
        getPhotoDirectory(context).listFiles()?.forEach { file ->
            if (file.isFile) file.delete() else file.deleteRecursively()
        }
    }
}
