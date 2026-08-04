package com.example.pseudo.utils

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object MediaStoreUtils {

    fun saveImageToGallery(context: Context, sourcePath: String): Boolean {
        return try {
            val file = File(sourcePath)
            if (!file.exists()) return false
            val fileName = file.name
            val mime = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, file, fileName, mime)
            } else {
                saveToLegacyPublicDir(context, file, fileName, mime)
            }
        } catch (t: Throwable) {
            false
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        source: File,
        fileName: String,
        mime: String
    ): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PseudoPic")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri: Uri? = resolver.insert(collection, values)
        if (uri == null) return false

        try {
            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
                out.flush()
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return true
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            return false
        }
    }

    private fun saveToLegacyPublicDir(
        context: Context,
        source: File,
        fileName: String,
        mime: String
    ): Boolean {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "PseudoPic"
        )
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, fileName)
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
        return target.exists()
    }
}
