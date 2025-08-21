package com.yubytech.tracked.ui

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun rememberCameraImagePicker(context: Context): CameraImagePickerState {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var lastPhotoFile by remember { mutableStateOf<File?>(null) }
    var compressionStatus by remember { mutableStateOf<CompressionStatus>(CompressionStatus.Idle) }
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && lastPhotoFile != null) {
            fileName = lastPhotoFile?.name
            compressionStatus = CompressionStatus.Compressing
            scope.launch {
                compressionStatus = compressImageFile(context, lastPhotoFile!!)
            }
        } else {
            fileName = null
            compressionStatus = CompressionStatus.Idle
        }
    }

    fun launchCamera() {
        val photoFile = createImageFile(context)
        lastPhotoFile = photoFile
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            photoFile
        )
        imageUri = uri
        launcher.launch(uri)
    }

    return remember {
        object : CameraImagePickerState {
            override val selectedFileName: String?
                get() = fileName
            override val compressionStatus: CompressionStatus
                get() = compressionStatus
            override fun launchCamera() = launchCamera()
        }
    }
}

interface CameraImagePickerState {
    val selectedFileName: String?
    val compressionStatus: CompressionStatus
    fun launchCamera()
}

private fun createImageFile(context: Context): File {
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile(
        "JPEG_${timeStamp}_",
        ".jpg",
        storageDir
    )
}

class MultiImageCaptureState(
    private val context: Context,
    private val scope: CoroutineScope
) {
    val imageFiles = mutableStateListOf<File>()
    val compressionStatuses = mutableStateListOf<CompressionStatus>()
    var lastPhotoFile: File? by mutableStateOf(null)
    var isCompressing by mutableStateOf(false)

    fun addImage(file: File) {
        imageFiles.add(file)
        compressionStatuses.add(CompressionStatus.Idle)
    }

    fun removeImage(index: Int) {
        if (index in imageFiles.indices) {
            imageFiles.removeAt(index)
            compressionStatuses.removeAt(index)
        }
    }

    fun clear() {
        imageFiles.clear()
        compressionStatuses.clear()
        lastPhotoFile = null
        isCompressing = false
    }

    fun compressAllImages(onDone: (() -> Unit)? = null) {
        isCompressing = true
        scope.launch {
            for (i in imageFiles.indices) {
                compressionStatuses[i] = CompressionStatus.Compressing
                val result = compressImageFile(context, imageFiles[i])
                compressionStatuses[i] = result
            }
            isCompressing = false
            onDone?.invoke()
        }
    }
}

@Composable
fun rememberMultiImageCaptureState(context: Context): MultiImageCaptureState {
    val scope = rememberCoroutineScope()
    return remember { MultiImageCaptureState(context, scope) }
} 