import android.content.Context
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.yubytech.tracked.api.RetrofitInstance
import com.yubytech.tracked.ui.CheckInPrefs
import com.yubytech.tracked.ui.Client
import com.yubytech.tracked.ui.CompressionStatus
import com.yubytech.tracked.ui.SharedPrefsUtils
import com.yubytech.tracked.ui.rememberMultiImageCaptureState
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat


fun createImageFile(context: Context): File {
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile(
        "JPEG_${timeStamp}_",
        ".jpg",
        storageDir
    )
}

@Composable
fun ClientCheckoutDialog(client: Client, onCancel: () -> Unit, onSubmit: (List<File>) -> Unit) {
    var interaction by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("D+1") }
    val types = listOf("D0", "D1", "D7", "D14", "D21", "Due Date", "DD+7", "DD+14", "Compliance Report", "Collection Agent", "Management Report", "Customer Visit", )

    val context = LocalContext.current
    val multiImageState = rememberMultiImageCaptureState(context)
    var compressionDone by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && multiImageState.lastPhotoFile != null) {
            multiImageState.addImage(multiImageState.lastPhotoFile!!)
        }
        multiImageState.lastPhotoFile = null
    }

    fun launchCamera() {
        val photoFile = createImageFile(context)
        multiImageState.lastPhotoFile = photoFile
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            photoFile
        )
        launcher.launch(uri)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCamera() // <-- Calls your existing camera function
        } else {
            fun launchCamera() {
                val photoFile = createImageFile(context)
                multiImageState.lastPhotoFile = photoFile
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    photoFile
                )
                launcher.launch(uri)
            }
        }
    }


    fun launchCameraWithPermissionCheck() {
        val permission = Manifest.permission.CAMERA
        val isGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (isGranted) {
            launchCamera()
        } else {
            permissionLauncher.launch(permission)
        }
    }



    val coroutineScope = rememberCoroutineScope()
    var isPosting by remember { mutableStateOf(false) }
    var postError by remember { mutableStateOf<String?>(null) }

    Surface(
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
        color = Color.White // Explicitly set background to white
    ) {
        if (isPosting) {
            // Show only spinner and message when submitting
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Checking out...", fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Text("Client Checkout", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))
            // Header row
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Name", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), textAlign = TextAlign.Start)
                Text("Phone", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), textAlign = TextAlign.Start)
                Text("ID", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), textAlign = TextAlign.Start)
            }
            Divider(color = Color(0xFFE0E0E0), thickness = 1.dp, modifier = Modifier.fillMaxWidth())
            // Data row (striped background)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8F8F8))
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(client.name, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), textAlign = TextAlign.Start)
                Text(client.contact.toString(), fontSize = 14.sp, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), textAlign = TextAlign.Start)
                Text(client.idno.toString(), fontSize = 14.sp, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), textAlign = TextAlign.Start)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Interactions", modifier = Modifier.weight(1f))
                Box {
                    var expanded by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(type)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        types.forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = {
                                type = it
                                expanded = false
                            })
                        }
                    }
                }
            }
            OutlinedTextField(
                value = interaction,
                onValueChange = { interaction = it },
                placeholder = { Text("Enter your interaction") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Photos:", fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                multiImageState.imageFiles.forEachIndexed { idx, file ->
                    val status = multiImageState.compressionStatuses.getOrNull(idx) ?: CompressionStatus.Idle
                    val (statusText, statusColor) = when (status) {
                        is CompressionStatus.Idle -> Pair(file.name, Color(0xFFB3E5FC))
                        is CompressionStatus.Compressing -> Pair("Compressing...", Color(0xFFFFA000))
                        is CompressionStatus.Success -> Pair("Ready: ${status.file.name}", Color(0xFF4CAF50))
                        is CompressionStatus.Failed -> Pair("Failed: ${status.reason}", Color(0xFFD32F2F))
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(1.dp, Color(0xFFB3E5FC), RoundedCornerShape(8.dp))
                            .background(Color.White),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_camera),
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        Text(
                            statusText,
                            color = statusColor,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { multiImageState.removeImage(idx) }) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_delete),
                                contentDescription = "Remove",
                                tint = Color.Red
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (!compressionDone) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = { launchCameraWithPermissionCheck() }, enabled = !multiImageState.isCompressing) { Text("Add Photo") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { multiImageState.compressAllImages { compressionDone = true } },
                        enabled = multiImageState.imageFiles.isNotEmpty() && !multiImageState.isCompressing
                    ) { Text("Done") }
                }
            }
            if (postError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(postError!!, color = Color.Red)
            }
            if (compressionDone) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val readyFiles = multiImageState.compressionStatuses.mapIndexedNotNull { i, status ->
                                (status as? CompressionStatus.Success)?.file
                            }
                            val imagePaths = readyFiles.map { it.absolutePath }
                            val clientDbId = CheckInPrefs.getClientDbId(context)
                            val userId = SharedPrefsUtils.getUserIdFromPrefs(context)
                            val data = mutableMapOf<String, okhttp3.RequestBody>(
                                "client" to clientDbId.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
                                "comment" to interaction.toRequestBody("text/plain".toMediaTypeOrNull()),
                                "source" to userId.toRequestBody("text/plain".toMediaTypeOrNull()),
                                "time" to (System.currentTimeMillis() / 1000).toString().toRequestBody("text/plain".toMediaTypeOrNull()),
                                "type" to type.toRequestBody("text/plain".toMediaTypeOrNull()),
                                "tracked" to "1".toRequestBody("text/plain".toMediaTypeOrNull())
                            )
                            val images = readyFiles.map { file ->
                                val reqFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                                MultipartBody.Part.createFormData("images[]", file.name, reqFile)
                            }
                            
                            // If no JWT token, try the original method directly
                            val testJwtToken = SharedPrefsUtils.getJwtToken(context)
                            if (testJwtToken == null) {
                                coroutineScope.launch {
                                    isPosting = true
                                    postError = null
                                    try {
                                        val response = RetrofitInstance.clientsApi.postInteractionMultipart(data, images)
                                        isPosting = false
                                        if (response.isSuccessful) {
                                            onSubmit(readyFiles)
                                        } else {
                                            postError = "Failed to submit. Server error: ${response.code()}"
                                        }
                                    } catch (e: Exception) {
                                        isPosting = false
                                        postError = "Network error: ${e.localizedMessage ?: "Unknown error"}"
                                    }
                                }
                            } else {
                                coroutineScope.launch {
                                    isPosting = true
                                    postError = null
                                    try {
                                        val response = RetrofitInstance.getClientsApiWithAuth(context).postInteractionMultipart(data, images)
                                        isPosting = false
                                        if (response.isSuccessful) {
                                            onSubmit(readyFiles)
                                        } else {
                                            val errorBody = response.errorBody()?.string()
                                            postError = "Failed to submit. Server error: ${response.code()}"
                                        }
                                    } catch (e: Exception) {
                                        isPosting = false
                                        postError = "Network error: ${e.localizedMessage ?: "Unknown error"}"
                                    }
                                }
                            }
                        },
                        enabled = (
                            multiImageState.compressionStatuses.all { it is CompressionStatus.Success }
                            && multiImageState.imageFiles.isNotEmpty()
                            && !multiImageState.isCompressing
                            && interaction.isNotBlank()
                            && type.isNotBlank()
                        )
                    ) { Text("Submit") }
                }
            }
        }
        }     
    }
} 