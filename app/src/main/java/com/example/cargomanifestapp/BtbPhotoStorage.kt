package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BtbPhotoStorage {
    fun createPhotoUri(context: Context): Uri {
        val dir = File(context.filesDir, "btb_photos").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(dir, "BTB_$stamp.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    fun resolveFile(context: Context, uri: Uri): File? {
        val root = File(context.filesDir, "btb_photos").canonicalFile
        val name = uri.lastPathSegment ?: return null
        val candidate = File(root, name).canonicalFile
        return if (candidate.path.startsWith(root.path + File.separator) || candidate == root) candidate else null
    }

    fun deletePhoto(context: Context, uriString: String) {
        resolveFile(context, Uri.parse(uriString))?.delete()
    }
}
