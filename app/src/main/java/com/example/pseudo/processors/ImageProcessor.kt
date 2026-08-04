package com.example.pseudo.processors

import android.graphics.*
import com.example.pseudo.models.ProcessingParams
import com.example.pseudo.models.ProcessingResult
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.*
import java.util.zip.CRC32
import kotlin.math.abs

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
        result = applyFilter(result, params.filterType)
        if (params.cropZoomPercent > 0.5f) result = cropAndZoom(result, params.cropZoomPercent)
        if (params.rotateDegrees > 0.2f) result = rotateWithFill(result, params.rotateDegrees, seed)
        result = addInterferenceLines(result, params, seed, deep = false)
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
        result = applyFilter(result, params.filterType)
        if (params.cropZoomPercent > 0.5f) result = cropAndZoom(result, params.cropZoomPercent * 1.2f)
        if (params.rotateDegrees > 0.2f) result = rotateWithFill(result, params.rotateDegrees * 1.5f, seed)
        // Multi-round frequency perturbation
        if (params.dctPerturbation) {
            result = modifyDctCoefficients(result, params, seed, rounds = 3, strength = 2.5f)
        }
        // Denser interference lines with lower alpha (invisible but breaks continuity)
        result = addInterferenceLines(result, params.copy(interferenceDensity = 0.6f), seed + 1, deep = true)
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

    private fun addInterferenceLines(

        bitmap: Bitmap,
        params: ProcessingParams,
        seed: Long,
        deep: Boolean = false
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val processed = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        processed.getPixels(pixels, 0, width, 0, 0, width, height)

        val rng = Random(seed)
        val density = params.interferenceDensity.coerceIn(0.05f, 1f)
        // Amplitude is always 1-2 levels (1/255 or 2/255): imperceptible to the eye
        // but large enough to flip aHash/pHash bits at thumbnail scale.
        val amplitude = if (deep) 2 else 1
        val baseSpacing = if (deep) 16 else 32
        val spacing = maxOf(10, (baseSpacing / density).toInt())

        // Structured prime-like intervals so machines can detect a repeating pattern
        val intervals = intArrayOf(13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59)

        // 1) Whole-row micro modulation (DC offset): changes row statistics and thumbnail cells
        val rowStep = maxOf(4, spacing)
        var y = rng.nextInt(rowStep)
        while (y < height) {
            val sign = if (rng.nextBoolean()) amplitude else -amplitude
            val rowStart = y * width
            var x = 0
            while (x < width) {
                val idx = rowStart + x
                val r = (pixels[idx] ushr 16) and 0xFF
                val g = (pixels[idx] ushr 8) and 0xFF
                val b = pixels[idx] and 0xFF
                pixels[idx] = 0xFF000000.toInt() or
                    ((r + sign).coerceIn(0, 255) shl 16) or
                    ((g + sign).coerceIn(0, 255) shl 8) or
                    (b + sign).coerceIn(0, 255)
                x++
            }
            y += rowStep
        }

        // 2) Whole-column micro modulation
        val colStep = maxOf(4, spacing)
        var cx = rng.nextInt(colStep)
        while (cx < width) {
            val sign = if (rng.nextBoolean()) amplitude else -amplitude
            var yy = 0
            while (yy < height) {
                val idx = yy * width + cx
                val r = (pixels[idx] ushr 16) and 0xFF
                val g = (pixels[idx] ushr 8) and 0xFF
                val b = pixels[idx] and 0xFF
                pixels[idx] = 0xFF000000.toInt() or
                    ((r + sign).coerceIn(0, 255) shl 16) or
                    ((g + sign).coerceIn(0, 255) shl 8) or
                    (b + sign).coerceIn(0, 255)
                yy++
            }
            cx += colStep
        }

        // 3) Visual-masking guided micro lines: only in textured regions (edge masking),
        //    color-matched to the local background so contrast stays at +/-1 level.
        val lineStep = maxOf(4, (spacing / 2).coerceAtLeast(4))
        var ly = rng.nextInt(lineStep)
        while (ly < height) {
            if (rng.nextDouble() < density) {
                var lx = 0
                while (lx < width) {
                    val idx = ly * width + lx
                    val r = (pixels[idx] ushr 16) and 0xFF
                    val g = (pixels[idx] ushr 8) and 0xFF
                    val b = pixels[idx] and 0xFF
                    val diff = if (lx + 1 < width) {
                        val nr = (pixels[idx + 1] ushr 16) and 0xFF
                        abs(r - nr)
                    } else 0
                    if (diff > 12) {
                        val sign = if (rng.nextBoolean()) 1 else -1
                        pixels[idx] = 0xFF000000.toInt() or
                            ((r + sign).coerceIn(0, 255) shl 16) or
                            ((g + sign).coerceIn(0, 255) shl 8) or
                            (b + sign).coerceIn(0, 255)
                    }
                    lx++
                }
            }
            ly += lineStep
        }

        // 4) Deep mode: block-level DC offset on 32x32 tiles to strengthen thumbnail-scale change
        if (deep) {
            val tile = 32
            var ty = 0
            while (ty < height) {
                var tx = 0
                while (tx < width) {
                    if (rng.nextDouble() < 0.5) {
                        val sign = if (rng.nextBoolean()) 1 else -1
                        val endY = minOf(ty + tile, height)
                        val endX = minOf(tx + tile, width)
                        var py = ty
                        while (py < endY) {
                            var px = tx
                            while (px < endX) {
                                val idx = py * width + px
                                val r = (pixels[idx] ushr 16) and 0xFF
                                val g = (pixels[idx] ushr 8) and 0xFF
                                val b = pixels[idx] and 0xFF
                                pixels[idx] = 0xFF000000.toInt() or
                                    ((r + sign).coerceIn(0, 255) shl 16) or
                                    ((g + sign).coerceIn(0, 255) shl 8) or
                                    (b + sign).coerceIn(0, 255)
                                px++
                            }
                            py++
                        }
                    }
                    tx += tile
                }
                ty += tile
            }
        }

        processed.setPixels(pixels, 0, width, 0, 0, width, height)
        return processed
    }

    private fun cropAndZoom(bitmap: Bitmap, percent: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxCrop = minOf(width, height) / 4
        val crop = (minOf(width, height) * percent / 100f).toInt().coerceIn(1, maxCrop)
        if (width - 2 * crop < 8 || height - 2 * crop < 8) return bitmap.copy(Bitmap.Config.ARGB_8888, true)

        // Crop the inner region into a fresh bitmap (independent copy)
        val cropped = Bitmap.createBitmap(width - 2 * crop, height - 2 * crop, Bitmap.Config.ARGB_8888)
        val cropCanvas = Canvas(cropped)
        cropCanvas.drawBitmap(bitmap, -crop.toFloat(), -crop.toFloat(), null)

        // Scale back to original dimensions with high-quality filtering
        val scaled = Bitmap.createScaledBitmap(cropped, width, height, true)
        cropped.recycle()
        return scaled
    }

    private fun rotateWithFill(bitmap: Bitmap, degrees: Float, seed: Long): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val rng = Random(seed)
        val angle = if (rng.nextBoolean()) degrees else -degrees
        if (angle == 0f) return bitmap.copy(Bitmap.Config.ARGB_8888, true)

        // Padding large enough for the rotated corners plus a safety margin
        val rad = Math.toRadians(angle.toDouble())
        val pad = (maxOf(width, height) * kotlin.math.abs(rad)).toInt() + 8
        val extW = width + 2 * pad
        val extH = height + 2 * pad

        // Build an extended canvas: original centered + edges stretched outward
        val ext = Bitmap.createBitmap(extW, extH, Bitmap.Config.ARGB_8888)
        val extCanvas = Canvas(ext)
        extCanvas.drawBitmap(bitmap, pad.toFloat(), pad.toFloat(), null)
        val smoothPaint = Paint(Paint.FILTER_BITMAP_FLAG)

        // Stretch the four edge stripes outward
        if (height > 0 && pad > 0) {
            val left = Bitmap.createBitmap(bitmap, 0, 0, 1, height)
            extCanvas.drawBitmap(left, Rect(0, 0, 1, height), Rect(0, pad, pad, pad + height), smoothPaint)
            left.recycle()
            val right = Bitmap.createBitmap(bitmap, width - 1, 0, 1, height)
            extCanvas.drawBitmap(right, Rect(0, 0, 1, height), Rect(width + pad, pad, extW, pad + height), smoothPaint)
            right.recycle()
        }
        if (width > 0 && pad > 0) {
            val top = Bitmap.createBitmap(bitmap, 0, 0, width, 1)
            extCanvas.drawBitmap(top, Rect(0, 0, width, 1), Rect(pad, 0, width + pad, pad), smoothPaint)
            top.recycle()
            val bottom = Bitmap.createBitmap(bitmap, 0, height - 1, width, 1)
            extCanvas.drawBitmap(bottom, Rect(0, 0, width, 1), Rect(pad, height + pad, width + pad, extH), smoothPaint)
            bottom.recycle()
        }

        // Fill the four corners with stretched corner pixels
        val corner = Bitmap.createBitmap(bitmap, 0, 0, 1, 1)
        extCanvas.drawBitmap(corner, Rect(0, 0, 1, 1), Rect(0, 0, pad, pad), smoothPaint)
        val cornerTR = Bitmap.createBitmap(bitmap, width - 1, 0, 1, 1)
        extCanvas.drawBitmap(cornerTR, Rect(0, 0, 1, 1), Rect(width + pad, 0, extW, pad), smoothPaint)
        cornerTR.recycle()
        val cornerBL = Bitmap.createBitmap(bitmap, 0, height - 1, 1, 1)
        extCanvas.drawBitmap(cornerBL, Rect(0, 0, 1, 1), Rect(0, height + pad, pad, extH), smoothPaint)
        cornerBL.recycle()
        val cornerBR = Bitmap.createBitmap(bitmap, width - 1, height - 1, 1, 1)
        extCanvas.drawBitmap(cornerBR, Rect(0, 0, 1, 1), Rect(width + pad, height + pad, extW, extH), smoothPaint)
        cornerBR.recycle()
        corner.recycle()

        // Rotate the extended canvas
        val matrix = Matrix()
        matrix.postRotate(angle, extW / 2f, extH / 2f)
        val rotated = Bitmap.createBitmap(extW, extH, Bitmap.Config.ARGB_8888)
        val rotCanvas = Canvas(rotated)
        rotCanvas.drawBitmap(ext, matrix, smoothPaint)
        ext.recycle()

        // Crop the center back to the original size (fresh copy)
        val cx = (extW - width) / 2
        val cy = (extH - height) / 2
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val resultCanvas = Canvas(result)
        resultCanvas.drawBitmap(rotated, -cx.toFloat(), -cy.toFloat(), null)
        rotated.recycle()
        return result
    }

    private fun applyFilter(bitmap: Bitmap, filterType: Int): Bitmap {
        if (filterType <= 0) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val matrix = ColorMatrix()
        when (filterType) {
            1 -> { // 自然美食: 暖调 + 轻微饱和 + 对比
                matrix.setSaturation(1.12f)
                matrix.postConcat(colorScale(1.05f, 1.02f, 0.97f, 6f, 2f, -3f))
            }
            2 -> { // 鲜亮美食: 更亮更饱和
                matrix.setSaturation(1.22f)
                matrix.postConcat(colorScale(1.06f, 1.03f, 1.0f, 10f, 5f, 2f))
            }
            3 -> { // 清新自然: 提亮 + 轻微冷调
                matrix.setSaturation(1.05f)
                matrix.postConcat(colorScale(1.03f, 1.04f, 1.09f, 12f, 9f, 13f))
            }
            4 -> { // 复古胶片: 褪色 + 暖黄 + 暗角
                matrix.setSaturation(0.72f)
                matrix.postConcat(colorScale(1.06f, 0.98f, 0.88f, 7f, -2f, -7f))
            }
            5 -> { // 黑白经典
                matrix.setSaturation(0f)
                matrix.postConcat(colorScale(1.08f, 1.08f, 1.08f, 6f, 6f, 6f))
            }
            6 -> { // 暖阳: 强烈暖调
                matrix.setSaturation(1.15f)
                matrix.postConcat(colorScale(1.09f, 1.0f, 0.9f, 12f, 0f, -10f))
            }
            7 -> { // 冷调极简
                matrix.setSaturation(0.88f)
                matrix.postConcat(colorScale(0.95f, 1.0f, 1.07f, -5f, 0f, 7f))
            }
            else -> return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        val width = bitmap.width
        val height = bitmap.height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        if (filterType == 4) {
            applyVignette(out)
        }
        return out
    }

    private fun colorScale(r: Float, g: Float, b: Float, rt: Float, gt: Float, bt: Float): ColorMatrix =
        ColorMatrix(floatArrayOf(
            r, 0f, 0f, 0f, rt,
            0f, g, 0f, 0f, gt,
            0f, 0f, b, 0f, bt,
            0f, 0f, 0f, 1f, 0f
        ))

    private fun applyVignette(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val canvas = Canvas(bitmap)
        val cx = w / 2f
        val cy = h / 2f
        val radius = maxOf(w, h) * 0.72f
        val shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(0x00000000, 0x33000000),
            floatArrayOf(0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint().apply {
            this.shader = shader
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
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
