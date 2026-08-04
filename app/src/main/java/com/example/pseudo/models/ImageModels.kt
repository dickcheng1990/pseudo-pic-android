package com.example.pseudo.models

data class ImageSelection(
    val path: String,
    val filename: String,
    val size: Long = 0L,
    val width: Int = 0,
    val height: Int = 0
)

data class ProcessingResult(
    val success: Boolean,
    val inputPath: String,
    val outputPath: String?,
    val errorMsg: String? = null,
    val processingTimeMs: Long = 0L,
    val originalHash: String = "",
    val processedHash: String = ""
)

data class ProcessingParams(
    val cropAmount: Float = 1.5f,
    val colorShift: Float = 3.0f,
    val brightnessShift: Float = 2.0f,
    val noiseIntensity: Float = 15.0f,
    val interferenceDensity: Float = 0.3f,
    val watermarkEnabled: Boolean = true,
    val watermarkText: String = "",
    val useDeepAI: Boolean = false,
    val dctPerturbation: Boolean = true
)
