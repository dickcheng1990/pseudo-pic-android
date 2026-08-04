package com.example.pseudo.processors

import android.graphics.*
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.*

class ImageProcessor {
    
    private val random = Random()
    
    fun computeHash(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    fun processImage(
        inputPath: String, 
        outputPath: String, 
        params: com.example.pseudo.models.ProcessingParams,
        seed: Long
    ): com.example.pseudo.models.ProcessingResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = false }
            val originalBitmap = BitmapFactory.decodeFile(inputPath, options) 
                ?: return com.example.pseudo.models.ProcessingResult(
                    success = false, inputPath = inputPath, outputPath = null,
                    errorMsg = "Failed to decode image"
                )
            
            // Step 1: File-level processing
            processFileLevel(inputPath, outputPath, seed)
            
            // Step 2: Pixel-level processing
            var processedBitmap = processPixelLevel(originalBitmap, params, seed)
            
            // Step 3: Add interference lines
            processedBitmap = addInterferenceLines(processedBitmap, params, seed)
            
            // Step 4: DCT perturbation
            if (params.dctPerturbation) {
                processedBitmap = modifyDctCoefficients(processedBitmap, params, seed)
            }
            
            // Step 5: Local pixel rearrangement
            processedBitmap = rearrangeLocalPixels(processedBitmap, params, seed)
            
            // Step 6: Add watermark
            if (params.watermarkEnabled) {
                val wmText = if (params.watermarkText.isNotEmpty()) 
                    params.watermarkText 
                else 
                    "pseudo_${seed}"
                processedBitmap = addInvisibleWatermark(processedBitmap, wmText, seed)
            }
            
            // Step 7: Save
            val outputStream = FileOutputStream(outputPath)
            val format = if (inputPath.endsWith(".png", ignoreCase = true)) 
                Bitmap.CompressFormat.PNG 
            else 
                Bitmap.CompressFormat.JPEG
            val quality = if (format == Bitmap.CompressFormat.PNG) 100 else 95
            processedBitmap.compress(format, quality, outputStream)
            outputStream.close()
            
            if (processedBitmap != originalBitmap) originalBitmap.recycle()
            processedBitmap.recycle()
            
            val inputData = File(inputPath).readBytes()
            val outputData = File(outputPath).readBytes()
            val endTime = System.currentTimeMillis()
            
            com.example.pseudo.models.ProcessingResult(
                success = true,
                inputPath = inputPath,
                outputPath = outputPath,
                processingTimeMs = endTime - startTime,
                originalHash = computeHash(inputData),
                processedHash = computeHash(outputData)
            )
            
        } catch (e: Exception) {
            com.example.pseudo.models.ProcessingResult(
                success = false,
                inputPath = inputPath,
                outputPath = null,
                errorMsg = e.message
            )
        }
    }
    
    fun processBatch(
        images: List<Pair<String, String>>,
        params: com.example.pseudo.models.ProcessingParams,
        maxConcurrency: Int = 4
    ): List<com.example.pseudo.models.ProcessingResult> {
        val results = mutableListOf<com.example.pseudo.models.ProcessingResult>()
        val semaphore = java.util.concurrent.Semaphore(maxConcurrency)
        val lock = Any()
        
        val jobs = images.mapIndexed { index, pair ->
            Thread {
                semaphore.acquire()
                try {
                    val seed = System.nanoTime() + index
                    val result = processImage(pair.first, pair.second, params, seed)
                    synchronized(lock) { results.add(result) }
                } finally {
                    semaphore.release()
                }
            }
        }
        
        jobs.forEach { it.start() }
        jobs.forEach { it.join() }
        return results
    }
    
    private fun processFileLevel(inputPath: String, outputPath: String, seed: Long): Boolean {
        return try {
            val bytes = File(inputPath).readBytes()
            val rng = Random(seed)
            var result = bytes
            
            if (bytes.isNotEmpty() && bytes[0].toInt() == 0xFF && bytes[1].toInt() == 0xD8) {
                result = modifyJpegStructure(bytes, rng)
            } else if (bytes.isNotEmpty() && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) {
                result = modifyPngStructure(bytes, rng)
            }
            
            File(outputPath).writeBytes(result)
            true
        } catch (e: Exception) {
            File(outputPath).writeBytes(File(inputPath).readBytes())
            false
        }
    }
    
    private fun modifyJpegStructure(bytes: ByteArray, rng: Random): ByteArray {
        val result = bytes.copyOf()
        var i = 2
        while (i < result.size - 1) {
            if (result[i].toInt() == 0xFF) {
                val marker = result[i + 1].toInt()
                if (marker in 0xD0..0xD9) {
                    val segLen = (result[i + 2].toInt() shl 8) or result[i + 3].toInt()
                    if (i + 4 + segLen <= result.size) {
                        for (j in 0 until minOf(4, segLen - 2)) {
                            if (rng.nextBoolean()) {
                                result[i + 4 + j] = (result[i + 4 + j].toInt() xor 0x01).toByte()
                            }
                        }
                        i += 4 + segLen
                        continue
                    }
                }
                if (marker == 0xDB) {
                    val segLen = (result[i + 2].toInt() shl 8) or result[i + 3].toInt()
                    if (i + 4 + segLen <= result.size && segLen > 65) {
                        val last = i + 4 + segLen - 4
                        for (j in 0 until 4) {
                            result[last + j] = (result[last + j].toInt() xor 0x01).toByte()
                        }
                        i += segLen
                        continue
                    }
                }
                i += 2
            } else { i++ }
        }
        return result
    }
    
    private fun modifyPngStructure(bytes: ByteArray, rng: Random): ByteArray {
        val result = bytes.copyOf()
        var pos = 8
        while (pos < result.size - 8) {
            val length = (result[pos].toInt() shl 24) or
                ((result[pos + 1].toInt() and 0xFF) shl 16) or
                ((result[pos + 2].toInt() and 0xFF) shl 8) or
                (result[pos + 3].toInt() and 0xFF)
            val chunkType = String(byteArrayOf(result[pos + 4], result[pos + 5], result[pos + 6], result[pos + 7]))
            if (chunkType == "IDAT" && pos + 8 + 4 + length + 4 <= result.size) {
                val dataStart = pos + 8
                val modCount = minOf(8, length - 4)
                for (j in 0 until modCount) {
                    if (rng.nextBoolean()) {
                        result[dataStart + length - modCount + j] = 
                            (result[dataStart + length - modCount + j].toInt() xor 0x01).toByte()
                    }
                }
            }
            pos += 12 + length
        }
        return result
    }
    
    private fun processPixelLevel(bitmap: Bitmap, params: com.example.pseudo.models.ProcessingParams, seed: Long): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val processed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(processed).drawBitmap(bitmap, 0f, 0f, null)
        
        val pixels = IntArray(width * height)
        processed.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val rng = Random(seed)
        val cropX = maxOf(1, (width * params.cropAmount / 100).toInt())
        val cropY = maxOf(1, (height * params.cropAmount / 100).toInt())
        val colorShift = params.colorShift / 100f
        val brightShift = params.brightnessShift / 100f
        val noise = params.noiseIntensity
        
        for (y in cropY until height - cropY) {
            for (x in cropX until width - cropX) {
                val idx = y * width + x
                val r = (pixels[idx] ushr 16) and 0xFF
                val g = (pixels[idx] ushr 8) and 0xFF
                val b = pixels[idx] and 0xFF
                
                val rShift = (rng.nextDouble() - 0.5) * colorShift * 255
                val gShift = (rng.nextDouble() - 0.5) * colorShift * 255
                val bShift = (rng.nextDouble() - 0.5) * colorShift * 255
                val bs = (rng.nextDouble() - 0.5) * brightShift * 255
                
                val nr = (r + rShift + bs).toInt().coerceIn(0, 255)
                val ng = (g + gShift + bs).toInt().coerceIn(0, 255)
                val nb = (b + bShift + bs).toInt().coerceIn(0, 255)
                
                pixels[idx] = 0xFF000000.toInt() or (nr shl 16) or (ng shl 8) or nb
            }
        }
        processed.setPixels(pixels, 0, width, 0, 0, width, height)
        return processed
    }
    
    private fun addInterferenceLines(bitmap: Bitmap, params: com.example.pseudo.models.ProcessingParams, seed: Long): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val processed = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val rng = Random(seed)
        val alpha = (params.interferenceDensity * 8).toInt().coerceIn(1, 10)
        val density = params.interferenceDensity
        
        val paint = Paint().apply { this.alpha = alpha; color = Color.WHITE; strokeWidth = 1f }
        val canvas = Canvas(processed)
        
        val vSpacing = maxOf(20, (1.0 / density).toInt())
        for (x in 0 until width step vSpacing) {
            if (rng.nextDouble() < density * 0.5) {
                canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
            }
        }
        
        val hSpacing = maxOf(20, (1.0 / density).toInt())
        for (y in 0 until height step hSpacing) {
            if (rng.nextDouble() < density * 0.5) {
                canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
            }
        }
        
        val diagCount = (width * density * 0.1).toInt()
        for (i in 0 until diagCount) {
            val x = rng.nextInt(width)
            val y = rng.nextInt(height)
            val len = rng.nextInt(5) + 1
            canvas.drawLine(x.toFloat(), y.toFloat(), (x + len).toFloat(), (y + len).toFloat(), paint)
        }
        
        return processed
    }
    
    private fun modifyDctCoefficients(bitmap: Bitmap, params: com.example.pseudo.models.ProcessingParams, seed: Long): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val processed = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        processed.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val rng = Random(seed)
        val blockSize = 8
        val perturbation = params.noiseIntensity * 0.1f
        
        for (by in 0 until (height / blockSize)) {
            for (bx in 0 until (width / blockSize)) {
                for (k in 0 until 3) {
                    val px = bx * blockSize + rng.nextInt(blockSize)
                    val py = by * blockSize + rng.nextInt(blockSize)
                    if (px < width && py < height) {
                        val idx = py * width + px
                        val r = (pixels[idx] ushr 16) and 0xFF
                        val g = (pixels[idx] ushr 8) and 0xFF
                        val b = pixels[idx] and 0xFF
                        val rp = (rng.nextDouble() - 0.5) * perturbation
                        val gp = (rng.nextDouble() - 0.5) * perturbation
                        val bp = (rng.nextDouble() - 0.5) * perturbation
                        pixels[idx] = 0xFF000000.toInt() or 
                            ((rp + r).toInt().coerceIn(0, 255) shl 16) or
                            ((gp + g).toInt().coerceIn(0, 255) shl 8) or
                            (bp + b).toInt().coerceIn(0, 255)
                    }
                }
            }
        }
        processed.setPixels(pixels, 0, width, 0, 0, width, height)
        return processed
    }
    
    private fun rearrangeLocalPixels(bitmap: Bitmap, params: com.example.pseudo.models.ProcessingParams, seed: Long): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val processed = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        processed.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val rng = Random(seed)
        val regionSize = 4
        val swapCount = (width * height / (regionSize * regionSize) * params.interferenceDensity * 0.1).toInt()
        
        for (i in 0 until swapCount) {
            val rx = rng.nextInt(width / regionSize) * regionSize
            val ry = rng.nextInt(height / regionSize) * regionSize
            val x1 = rng.nextInt(minOf(regionSize * 2, width - rx)) + rx
            val y1 = rng.nextInt(minOf(regionSize * 2, height - ry)) + ry
            val x2 = rng.nextInt(minOf(regionSize * 2, width - rx)) + rx
            val y2 = rng.nextInt(minOf(regionSize * 2, height - ry)) + ry
            if (x1 < width && y1 < height && x2 < width && y2 < height) {
                val idx1 = y1 * width + x1
                val idx2 = y2 * width + x2
                val temp = pixels[idx1]
                pixels[idx1] = pixels[idx2]
                pixels[idx2] = temp
            }
        }
        processed.setPixels(pixels, 0, width, 0, 0, width, height)
        return processed
    }
    
    private fun addInvisibleWatermark(bitmap: Bitmap, watermarkText: String, seed: Long): Bitmap {
        if (watermarkText.isEmpty()) return bitmap
        val width = bitmap.width
        val height = bitmap.height
        val processed = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        processed.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val rng = Random(seed + watermarkText.hashCode().toLong())
        val bits = watermarkText.toByteArray()
        val bitCount = bits.size * 8
        var bitIndex = 0
        
        val blockSize = 8
        for (by in 0 until (height / blockSize)) {
            for (bx in 0 until (width / blockSize)) {
                if (bitIndex >= bitCount) break
                val cx = bx * blockSize + blockSize / 2
                val cy = by * blockSize + blockSize / 2
                if (cx < width && cy < height) {
                    val idx = cy * width + cx
                    val r = (pixels[idx] ushr 16) and 0xFF
                    val g = (pixels[idx] ushr 8) and 0xFF
                    val b = pixels[idx] and 0xFF
                    val byteIndex = bitIndex / 8
                    val bitPos = bitIndex % 8
                    val bit = (bits[byteIndex].toInt() ushr (7 - bitPos)) and 1
                    val newG = if (bit == 1) g or 1 else g and 0xFE
                    val change = rng.nextInt(2)
                    val newR = (r + change - 1).coerceIn(0, 255)
                    val newB = (b + change - 1).coerceIn(0, 255)
                    pixels[idx] = 0xFF000000.toInt() or (newR shl 16) or (newG shl 8) or newB
                    bitIndex++
                }
            }
        }
        processed.setPixels(pixels, 0, width, 0, 0, width, height)
        return processed
    }
}
