package com.yubytech.tracked.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class CompressionStatus {
    object Idle : CompressionStatus()
    object Compressing : CompressionStatus()
    data class Success(val file: File) : CompressionStatus()
    data class Failed(val reason: String) : CompressionStatus()
}


suspend fun compressImageFile(
    context: Context,
    inputFile: File,
    maxWidth: Int = 1280,
    maxHeight: Int = 720
): CompressionStatus = withContext(Dispatchers.IO) {
    try {
        // Load the bitmap
        val originalBitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
            ?: return@withContext CompressionStatus.Failed("Unable to decode bitmap.")

        // Calculate aspect ratio
        val aspectRatio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
        var width = maxWidth
        var height = maxHeight

        // Adjust width/height to preserve aspect ratio
        if (originalBitmap.width > originalBitmap.height) {
            height = (width / aspectRatio).toInt()
        } else {
            width = (height * aspectRatio).toInt()
        }

        // Scale down the bitmap
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)

        val compressedFileName = "compressed_${inputFile.nameWithoutExtension}.jpg"
        val compressedFile = File(inputFile.parent, compressedFileName)

        val outputStream = FileOutputStream(compressedFile)
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        outputStream.flush()
        outputStream.close()

        CompressionStatus.Success(compressedFile)

    } catch (e: Exception) {
        CompressionStatus.Failed(e.message ?: "Compression failed")
    }
}

//}