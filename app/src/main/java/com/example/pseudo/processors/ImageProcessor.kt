package com.example.pseudo.processors

import android.graphics.*
import com.example.pseudo.models.ProcessingParams
import com.example.pseudo.models.ProcessingResult
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.*
import java.util.zip.CRC32

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
        params: ProcessingParams,
        seed: Long
    ): ProcessingResult {
        val startTime = System.currentTimeMillis()
        var originalBitmap: Bitmap? = null
        var processedBitmap: Bitmap? = null

        return try {
            // Decode with memory-safe sampling to prevent OOM on large photos
            originalBitmap = decodeSampledBitmap(inputPath)
                ?: return ProcessingResult(false, inputPath, null, "无法解码图片")

            // Pixel-level processing (in memory)
            processedBitmap = if (params.useDeepAI) {
                processDeep(originalBitmap, params, seed)
            } else {
                processStandard(originalBitmap, params, seed)
            }

            // Save pixel result first, then apply file-level binary modification
            saveBitmap(processedBitmap, inputPath, outputPath)
            processFileLevel(outputPath, seed)

            val inputData = File(inputPath).readBytes()
            val outputData = File(outputPath).readBytes()
            val endTime = System.currentTimeMillis()

            ProcessingResult(
                success = true,
                inputPath = inputPath,
                outputPath = outputPath,
                processingTimeMs = endTime - startTime,
                originalHash = computeHash(inputData),
                processedHash = computeHash(outputData)
            )
        } catch (t: Throwable) {
            // Catch OutOfMemoryError too so batch processing never crashes the app
            ProcessingResult(
                success = false,
                inputPath = inputPath,
                outputPath = null,
                errorMsg = t.message ?: "处理失败"
            )
        } finally {
            try { processedBitmap?.recycle() } catch (_: Throwable) {}
            try { originalBitmap?.recycle() } catch (_: Throwable) {}
        }
    }

    fun processBatch(
        images: List<Pair<String, String>>,
        params: ProcessingParams,
        maxConcurrency: Int = 2
    ): List<ProcessingResult> {
        val results = mutableListOf<ProcessingResult>()
        val semaphore = java.util.concurrent.Semaphore(maxConcurrency)
        val lock = Any()

        val jobs = images.mapIndexed { index, pair ->
            Thread {
                semaphore.acquire()
                try {
                    val seed = System.nanoTime() + index
                    val result = processImage(pair.first, pair.second, params, seed)
                    synchronized(lock) { results.add(result) }
                } catch (t: Throwable) {
                    synchronized(lock) {
                        results.add(
                            ProcessingResult(false, pair.first, null, t.message ?: "处理失败")
                        )
                    }
                } finally {
                    semaphore.release()
                }
            }
        }

        jobs.forEach { it.start() }
        jobs.forEach { it.join() }
        return results
    }

    private fun processStandard(bitmap: Bitmap, params: ProcessingParams, seed: Long): Bitmap {
        var result = processPixelLevel(bitmap, params, seed)
        result = addInterferenceLines(result, params, seed)
        if (params.dctPerturbation) {
            result = modifyDctCoefficients(result, params, seed, rounds = 1, strength = 1f)
        }
        result = rearrangeLocalPixels(result, params, seed, regionSize = 4, factor = 0.1f)
        if (params.watermarkEnabled) {
            val wmText = if (params.watermarkText.isNotEmpty()) params.watermarkText else "pseudo_$seed"
            result = addInvisibleWatermark(result, wmText, seed)
        }
        return result
    }

    private fun processDeep(bitmap: Bitmap, params: ProcessingParams, seed: Long): Bitmap {
        var result = processPixelLevel(bitmap, params, seed)
        // Multi-round frequency perturbation
        if (params.dctPerturbation) {
            result = modifyDctCoefficients(result, params, seed, rounds = 3, strength = 2.5f)
        }
        // Denser interference lines with lower alpha (invisible but breaks continuity)
        result = addInterferenceLines(result, params.copy(interferenceDensity = 0.6f), seed + 1)
        // Larger-region local pixel rearrangement
        result = rearrangeLocalPixels(result, params, seed + 2, regionSize = 8, factor = 0.25f)
        // Micro row displacement to break pixel-row continuity
        result = applyMicroDisplacement(result, seed + 3)
        // Stronger watermark embedding
        if (params.watermarkEnabled) {
            val wmText = if (params.watermarkText.isNotEmpty()) params.watermarkText else "pseudo_deep_$seed"
            result = addInvisibleWatermark(result, wmText, seed + 4)
            result = modifyDctCoefficients(result, params, seed + 5, rounds = 1, strength = 1.2f)
        }
        return result
    }

    private fun decodeSampledBitmap(inputPath: String): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(inputPath, bounds)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(inputPath, options)
        } catch (t: Throwable) {
            null
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        val maxPixels = 16_000_000L
        while ((width.toLong() / sample) * (height.toLong() / sample) > maxPixels) {
            sample *= 2
        }
        while (maxOf(width / sample, height / sample) > 4096) {
            sample *= 2
        }
        return sample
    }

    private fun saveBitmap(bitmap: Bitmap, inputPath: String, outputPath: String) {
        val format = if (inputPath.endsWith(".png", ignoreCase = true))
            Bitmap.CompressFormat.PNG
        else
            Bitmap.CompressFormat.JPEG
        val quality = if (format == Bitmap.CompressFormat.PNG) 100 else 95
        FileOutputStream(outputPath).use { out ->
            bitmap.compress(format, quality, out)
            out.flush()
        }
    }

    // ------------------------------------------------------------------
    // File-level processing: modify binary structure to break file hashes
    // ------------------------------------------------------------------

    private fun processFileLevel(outputPath: String, seed: Long): Boolean {
        return try {
            val bytes = File(outputPath).readBytes()
            val rng = Random(seed)
            val result = when {
                bytes.size > 2 && bytes[0].toInt() == 0xFF && bytes[1].toInt() == 0xD8 ->
                    insertJpegComment(bytes, rng)
                bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() ->
                    insertPngTextChunk(bytes, rng)
                else -> bytes
            }
            File(outputPath).writeBytes(result)
            true
        } catch (t: Throwable) {
            false
        }
    }

    private fun insertJpegComment(bytes: ByteArray, rng: Random): ByteArray {
        // Insert a random-length COM (0xFFFE) segment just before the SOS marker
        val comment = generateRandomText(rng, 8 + rng.nextInt(24))
        val commentData = comment.toByteArray(Charsets.ISO_8859_1)
        val segLen = 2 + commentData.size

        var i = 2
        while (i < bytes.size - 1) {
            if (bytes[i].toInt() == 0xFF) {
                val marker = bytes[i + 1].toInt() and 0xFF
                if (marker == 0xDA) { // SOS: image data starts here, insert before it
                    return buildJpegWithComment(bytes, i, commentData, segLen)
                }
                if (marker == 0xFF || marker == 0x00 || marker == 0x01 || marker in 0xD0..0xD7) {
                    i += 2
                    continue
                }
                if (i + 4 <= bytes.size) {
                    val len = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
                    if (len >= 2 && i + 2 + len <= bytes.size) {
                        i += 2 + len
                        continue
                    }
                }
                i += 2
            } else {
                i++
            }
        }
        return bytes
    }

    private fun buildJpegWithComment(
        bytes: ByteArray,
        insertPos: Int,
        commentData: ByteArray,
        segLen: Int
    ): ByteArray {
        val newSize = bytes.size + 2 + segLen
        val result = ByteArray(newSize)
        System.arraycopy(bytes, 0, result, 0, insertPos)
        result[insertPos] = 0xFF.toByte()
        result[insertPos + 1] = 0xFE.toByte()
        result[insertPos + 2] = (segLen ushr 8).toByte()
        result[insertPos + 3] = (segLen and 0xFF).toByte()
        System.arraycopy(commentData, 0, result, insertPos + 4, commentData.size)
        System.arraycopy(bytes, insertPos, result, insertPos + 2 + segLen, bytes.size - insertPos)
        return result
    }

    private fun insertPngTextChunk(bytes: ByteArray, rng: Random): ByteArray {
        // Insert a tEXt chunk before IEND; CRC is computed so the PNG stays valid
        val keyword = "Comment"
        val text = keyword + "\u0000" + generateRandomText(rng, 8 + rng.nextInt(24))
        val data = text.toByteArray(Charsets.ISO_8859_1)

        val chunkType = "tEXt".toByteArray(Charsets.ISO_8859_1)
        val crc = CRC32()
        crc.update(chunkType)
        crc.update(data)
        val crcValue = crc.value

        val chunk = ByteArray(4 + 4 + data.size + 4)
        chunk[0] = (data.size ushr 24).toByte()
        chunk[1] = (data.size ushr 16).toByte()
        chunk[2] = (data.size ushr 8).toByte()
        chunk[3] = data.size.toByte()
        System.arraycopy(chunkType, 0, chunk, 4, 4)
        System.arraycopy(data, 0, chunk, 8, data.size)
        chunk[8 + data.size] = (crcValue ushr 24).toByte()
        chunk[9 + data.size] = (crcValue ushr 16).toByte()
        chunk[10 + data.size] = (crcValue ushr 8).toByte()
        chunk[11 + data.size] = crcValue.toByte()

        val iendPos = findIend(bytes)
        if (iendPos < 0) return bytes

        val result = ByteArray(bytes.size + chunk.size)
        System.arraycopy(bytes, 0, result, 0, iendPos)
        System.arraycopy(chunk, 0, result, iendPos, chunk.size)
        System.arraycopy(bytes, iendPos, result, iendPos + chunk.size, bytes.size - iendPos)
        return result
    }

    private fun findIend(bytes: ByteArray): Int {
        val iend = byteArrayOf('I'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'D'.code.toByte())
        outer@ for (i in bytes.size - 20 downTo 8) {
            if (bytes[i] == iend[0]) {
                for (j in 1 until 4) {
                    if (bytes[i + j] != iend[j]) continue@outer
                }
                return i
            }
        }
        return -1
    }

    private fun generateRandomText(rng: Random, length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val sb = StringBuilder(length)
        repeat(length) { sb.append(chars[rng.nextInt(chars.length)]) }
        return sb.toString()
    }

    // ------------------------------------------------------------------
    // Pixel-level processing
    // ------------------------------------------------------------------

    private fun processPixelLevel(bitmap: Bitmap, params: ProcessingParams, seed: Long): Bitmap {
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

        for (y in cropY until height - cropY) {
            var idx = y * width + cropX
            for (x in cropX until width - cropX) {
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
                idx++
            }
        }
        processed.setPixels(pixels, 0, width, 0, 0, width, height)
        return processed
    }

    private fun addInterferenceLines(bitmap: Bitmap, params: ProcessingParams, seed: Long): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val processed = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val rng = Random(seed)
        val alpha = (params.interferenceDensity * 8).toInt().coerceIn(1, 10)
        val density = params.interferenceDensity.coerceIn(0.05f, 1f)

        val paint = Paint().apply {
            this.alpha = alpha
            color = Color.WHITE
            strokeWidth = 1f
        }
        val canvas = Canvas(processed)

        val vSpacing = maxOf(20, (1.0 / density).toInt())
        var x = 0
        while (x < width) {
            if (rng.nextDouble() < density * 0.5) {
                canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
            }
            x += vSpacing
        }

        val hSpacing = maxOf(20, (1.0 / density).toInt())
        var y = 0
        while (y < height) {
            if (rng.nextDouble() < density * 0.5) {
                canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
            }
            y += hSpacing
        }

        val diagCount = (width * density * 0.1).toInt().coerceAtMost(20000)
        for (i in 0 until diagCount) {
            val dx = rng.nextInt(width)
            val dy = rng.nextInt(height)
            val len = rng.nextInt(5) + 1
            canvas.drawLine(dx.toFloat(), dy.toFloat(), (dx + len).toFloat(), (dy + len).toFloat(), paint)
        }

        return processed
    }

    private fun modifyDctCoefficients(
        bitmap: Bitmap,
        params: ProcessingParams,
        seed: Long,
        rounds: Int,
        strength: Float
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val processed = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        processed.getPixels(pixels, 0, width, 0, 0, width, height)

        val rng = Random(seed)
        val blockSize = 8
        val perturbation = params.noiseIntensity * 0.1f * strength

        repeat(rounds) {
            var by = 0
            while (by < height / blockSize) {
                var bx = 0
                while (bx < width / blockSize) {
                    repeat(if (rounds > 1) 6 else 3) {
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
                    bx++
                }
                by++
            }
        }
        processed.setPixels(pixels, 0, width, 0, 0, width, height)
        return processed
    }

    private fun rearrangeLocalPixels(
        bitmap: Bitmap,
        params: ProcessingParams,
        seed: Long,
        regionSize: Int,
        factor: Float
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val processed = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        processed.getPixels(pixels, 0, width, 0, 0, width, height)

        val rng = Random(seed)
        val base = width * height / (regionSize * regionSize)
        val swapCount = (base * params.interferenceDensity * factor).toInt().coerceAtMost(base)

        var i = 0
        while (i < swapCount) {
            val rx = rng.nextInt(maxOf(1, width / regionSize)) * regionSize
            val ry = rng.nextInt(maxOf(1, height / regionSize)) * regionSize
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
            i++
        }
        processed.setPixels(pixels, 0, width, 0, 0, width, height)
        return processed
    }

    private fun applyMicroDisplacement(bitmap: Bitmap, seed: Long): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val processed = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        processed.getPixels(pixels, 0, width, 0, 0, width, height)

        val rng = Random(seed)
        var y = 32
        while (y < height) {
            val offset = if (rng.nextBoolean()) 1 else -1
            val rowStart = y * width
            if (offset > 0) {
                val last = pixels[rowStart + width - 1]
                var x = width - 1
                while (x > 0) {
                    pixels[rowStart + x] = pixels[rowStart + x - 1]
                    x--
                }
                pixels[rowStart] = last
            } else {
                val first = pixels[rowStart]
                var x = 0
                while (x < width - 1) {
                    pixels[rowStart + x] = pixels[rowStart + x + 1]
                    x++
                }
                pixels[rowStart + width - 1] = first
            }
            y += 96
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
        var by = 0
        while (by < height / blockSize && bitIndex < bitCount) {
            var bx = 0
            while (bx < width / blockSize && bitIndex < bitCount) {
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
                bx++
            }
            by++
        }
        processed.setPixels(pixels, 0, width, 0, 0, width, height)
        return processed
    }
}
