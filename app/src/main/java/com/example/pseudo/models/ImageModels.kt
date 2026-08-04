package com.example.pseudo.models

import android.os.Parcel
import android.os.Parcelable

data class ImageSelection(
    val path: String,
    val filename: String,
    val size: Long = 0L,
    val width: Int = 0,
    val height: Int = 0
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readLong(),
        parcel.readInt(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(path)
        parcel.writeString(filename)
        parcel.writeLong(size)
        parcel.writeInt(width)
        parcel.writeInt(height)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<ImageSelection> {
            override fun createFromParcel(parcel: Parcel): ImageSelection = ImageSelection(parcel)
            override fun newArray(size: Int): Array<ImageSelection?> = arrayOfNulls(size)
        }
    }
}

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
    val dctPerturbation: Boolean = true,
    val cropZoomPercent: Float = 2.0f,
    val rotateDegrees: Float = 1.0f
)
