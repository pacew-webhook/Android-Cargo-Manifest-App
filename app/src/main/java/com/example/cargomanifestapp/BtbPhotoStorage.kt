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

    fun deletePhoto(context: Context, uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            val dir = File(context.filesDir, "btb_photos").canonicalFile
            val path = uri.path ?: return
            val file = File(path).canonicalFile
            if (file.path.startsWith(dir.path + File.separator)) file.delete()
        } catch (_: Exception) {
        }
    }
}
