package com.example.pseudo.processors

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

object ImageHashUtils {
    
    fun computePerceptualHash(bitmap: Bitmap, hashSize: Int = 16): String {
        // Convert to grayscale and resize
        val gray = convertToGrayscale(bitmap)
        val resized = Bitmap.createScaledBitmap(gray, hashSize, hashSize, true)
        
        // Compute DCT-like hash
        val pixels = IntArray(hashSize * hashSize)
        resized.getPixels(pixels, 0, hashSize, 0, 0, hashSize, hashSize)
        
        // Calculate average
        var sum = 0
        for (p in pixels) {
            sum += (p and 0xFF) // Use red channel as grayscale approximation
        }
        val avg = sum.toDouble() / pixels.size
        
        // Build hash based on comparison with average
        val sb = StringBuilder()
        for (i in pixels.indices) {
            sb.append(if ((pixels[i] and 0xFF) >= avg) "1" else "0")
        }
        
        return sb.toString()
    }
    
    fun computeMd5(data: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    fun computeSha1(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    fun hammingDistance(hash1: String, hash2: String): Int {
        require(hash1.length == hash2.length) { "Hashes must be same length" }
        var distance = 0
        for (i in hash1.indices) {
            if (hash1[i] != hash2[i]) distance++
        }
        return distance
    }
    
    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val canvas = android.graphics.Canvas(grayscale)
        val paint = android.graphics.Paint()
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(
            floatArrayOf(
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayscale
    }
    
    fun getImageDimensions(path: String): Pair<Int, Int> {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            Pair(options.outWidth, options.outHeight)
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }
    
    fun getFileSize(path: String): Long {
        return try {
            File(path).length()
        } catch (e: Exception) {
            0L
        }
    }
}
