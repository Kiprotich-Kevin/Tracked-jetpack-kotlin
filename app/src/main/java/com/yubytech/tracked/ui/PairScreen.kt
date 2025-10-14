// File: PairScreen.kt
package com.yubytech.tracked.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Divider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.yubytech.tracked.User
import com.yubytech.tracked.PairViewModel
import com.yubytech.tracked.ui.isInternetAvailable
import com.yubytech.tracked.ui.SharedPrefsUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

// Delete state enum
enum class DeleteState {
    IDLE, CONFIRMING, DELETING, SUCCESS, ERROR
}

// Create pair state enum
enum class CreatePairState {
    IDLE, CREATING, SUCCESS, ERROR
}

// User data class for dropdown
data class StaffUser(
    val id: Int,
    val name: String
)

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PairScreen(
    userName: String = "John Doe(You)",
    onBackClick: () -> Unit = {}
) {
    var isBackButtonEnabled by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: PairViewModel = viewModel()

    // --- FIX: Always remember the sheet state outside the conditional ---
    val createSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showCreateSheet by remember { mutableStateOf(false) }
    var createPairState by remember { mutableStateOf(CreatePairState.IDLE) }
    var createErrorMessage by remember { mutableStateOf("") }
    var selectedStaff1 by remember { mutableStateOf<StaffUser?>(null) }
    var selectedStaff2 by remember { mutableStateOf<StaffUser?>(null) }
    var staff1Expanded by remember { mutableStateOf(false) }
    var staff2Expanded by remember { mutableStateOf(false) }

    // Delete states
    var deleteState by remember { mutableStateOf(DeleteState.IDLE) }
    var deleteErrorMessage by remember { mutableStateOf("") }

    // --- ADD THIS: Provide a list of staff users ---
    val staffList = remember {
        listOf(
            StaffUser(1, "Alice Johnson"),
            StaffUser(2, "Bob Smith"),
            StaffUser(3, "Carol Lee"),
            StaffUser(4, "David Kim")
            // Add more staff as needed or fetch from your ViewModel
        )
    }

    LaunchedEffect(Unit) {
        if (isInternetAvailable(context)) {
            val id = SharedPrefsUtils.getUserIdFromPrefs(context).toIntOrNull() ?: 0
            if (id != 0) viewModel.fetchPairs(context, id)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(WindowInsets.safeDrawing.asPaddingValues())
    ) {
        var selectedPair by remember { mutableStateOf<com.yubytech.tracked.api.PairItem?>(null) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        // App bar - with shadow and refresh
        Surface(
            shadowElevation = 6.dp,
            color = Color.White
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isBackButtonEnabled) {
                            isBackButtonEnabled = false
                            onBackClick()
                            scope.launch {
                                delay(1000)
                                isBackButtonEnabled = true
                            }
                        }
                    },
                    enabled = isBackButtonEnabled
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }

                Text(
                    "Pairing",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = {
                    val id = SharedPrefsUtils.getUserIdFromPrefs(context).toIntOrNull() ?: 0
                    if (id != 0) viewModel.fetchPairs(context, id)
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF1976D2))
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = {
                // Always reset state before showing the sheet
                createPairState = CreatePairState.IDLE
                createErrorMessage = ""
                selectedStaff1 = null
                selectedStaff2 = null
                staff1Expanded = false
                staff2Expanded = false
                showCreateSheet = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFF1976D2)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Create Pair",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Pair",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // --- FIX: Use the remembered sheet state and only reset state on dismiss ---
        ModalBottomSheet(
            onDismissRequest = {
                showCreateSheet = false
                createPairState = CreatePairState.IDLE
                createErrorMessage = ""
                selectedStaff1 = null
                selectedStaff2 = null
                staff1Expanded = false
                staff2Expanded = false
            },
            sheetState = createSheetState,
            containerColor = Color.White
        ) {
            if (showCreateSheet) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (createPairState) {
                        CreatePairState.IDLE -> {
                            Text("Create New Pair", fontWeight = FontWeight.Bold, color = Color(0xFF003366), fontSize = 20.sp)
                            Spacer(modifier = Modifier.height(24.dp))

                            // Staff 1 Dropdown
                            ExposedDropdownMenuBox(
                                expanded = staff1Expanded,
                                onExpandedChange = { staff1Expanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedStaff1?.name ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Staff 1") },
                                    placeholder = { Text("Select first staff member") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = staff1Expanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                                )
                                ExposedDropdownMenu(
                                    expanded = staff1Expanded,
                                    onDismissRequest = { staff1Expanded = false }
                                ) {
                                    staffList.forEach { staff ->
                                        DropdownMenuItem(
                                            text = { Text(staff.name) },
                                            onClick = {
                                                selectedStaff1 = staff
                                                staff1Expanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Staff 2 Dropdown
                            ExposedDropdownMenuBox(
                                expanded = staff2Expanded,
                                onExpandedChange = { staff2Expanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedStaff2?.name ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Staff 2") },
                                    placeholder = { Text("Select second staff member") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = staff2Expanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                                )
                                ExposedDropdownMenu(
                                    expanded = staff2Expanded,
                                    onDismissRequest = { staff2Expanded = false }
                                ) {
                                    staffList.forEach { staff ->
                                        DropdownMenuItem(
                                            text = { Text(staff.name) },
                                            onClick = {
                                                selectedStaff2 = staff
                                                staff2Expanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    if (selectedStaff1 != null && selectedStaff2 != null) {
                                        if (selectedStaff1!!.id == selectedStaff2!!.id) {
                                            createPairState = CreatePairState.ERROR
                                            createErrorMessage = "Cannot pair a staff member with themselves. Please select different staff members."
                                            return@Button
                                        }

                                        createPairState = CreatePairState.CREATING
                                        scope.launch {
                                            try {
                                                if (!isInternetAvailable(context)) {
                                                    createPairState = CreatePairState.ERROR
                                                    createErrorMessage = "No internet connection. Please check your network and try again."
                                                    return@launch
                                                }

                                                val userId = SharedPrefsUtils.getUserIdFromPrefs(context).toIntOrNull() ?: 0
                                                val response = createPair(selectedStaff1!!.id, selectedStaff2!!.id, userId)

                                                if (response.success) {
                                                    createPairState = CreatePairState.SUCCESS
                                                    delay(2500)
                                                    // Refresh the list first
                                                    val id = SharedPrefsUtils.getUserIdFromPrefs(context).toIntOrNull() ?: 0
                                                    if (id != 0) viewModel.fetchPairs(context, id)
                                                    // Then close and reset
                                                    showCreateSheet = false
                                                    delay(100) // Small delay to ensure sheet closes
                                                    createPairState = CreatePairState.IDLE
                                                    selectedStaff1 = null
                                                    selectedStaff2 = null
                                                } else {
                                                    createPairState = CreatePairState.ERROR
                                                    createErrorMessage = response.message ?: "Failed to create pair. Please try again."
                                                }
                                            } catch (e: Exception) {
                                                createPairState = CreatePairState.ERROR
                                                createErrorMessage = when {
                                                    e.message?.contains("timeout", ignoreCase = true) == true ->
                                                        "Request timed out. Please check your connection and try again."
                                                    e.message?.contains("unable to resolve host", ignoreCase = true) == true ->
                                                        "Cannot reach server. Please check your internet connection."
                                                    else -> "Something went wrong. Please try again."
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                enabled = selectedStaff1 != null && selectedStaff2 != null
                            ) {
                                Text("Create Pair", fontWeight = FontWeight.Bold)
                            }
                        }

                        CreatePairState.CREATING -> {
                            CircularProgressIndicator(
                                color = Color(0xFF1976D2),
                                modifier = Modifier.size(64.dp),
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Creating Pair", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF003366))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Please wait while we create the pair...",
                                textAlign = TextAlign.Center,
                                color = Color.Gray
                            )
                        }

                        CreatePairState.SUCCESS -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Success!", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "The pair has been created successfully.",
                                textAlign = TextAlign.Center,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }

                        CreatePairState.ERROR -> {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Create Failed", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFFE53935))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                createErrorMessage,
                                textAlign = TextAlign.Center,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    createPairState = CreatePairState.IDLE
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                            ) { Text("OK") }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Filter chips row
        var filter by remember { mutableStateOf("All") }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 56.dp + 8.dp, start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.yubytech.tracked.ui.screens.TasksFilterButton(text = "All", selected = filter == "All", onClick = { filter = "All" })
            Spacer(modifier = Modifier.width(8.dp))
            com.yubytech.tracked.ui.screens.TasksFilterButton(text = "Active", selected = filter == "Active", onClick = { filter = "Active" })
            Spacer(modifier = Modifier.width(8.dp))
            com.yubytech.tracked.ui.screens.TasksFilterButton(text = "Inactive", selected = filter == "Inactive", onClick = { filter = "Inactive" })
        }

        // Content area
        when {
            viewModel.loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 56.dp + 48.dp + 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF1976D2))
                }
            }
            viewModel.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 56.dp + 48.dp + 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val friendly = viewModel.error ?: "Something went wrong. Please try again."
                        Text(friendly, color = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val id = SharedPrefsUtils.getUserIdFromPrefs(context).toIntOrNull() ?: 0
                                if (id != 0) viewModel.fetchPairs(context, id)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
            else -> {
                val filtered = when (filter) {
                    "Active" -> viewModel.createdPairs.filter { it.active == 1 }
                    "Inactive" -> viewModel.createdPairs.filter { it.active != 1 }
                    else -> viewModel.createdPairs
                }

                if (filtered.isEmpty()) {
                    // Show "no pairs" UI
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 56.dp + 48.dp + 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "No pairs",
                                tint = Color.LightGray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "There are no pairs.",
                                color = Color.Gray,
                                fontSize = 18.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 56.dp + 48.dp + 8.dp)
                    ) {
                        items(filtered, key = { it.id }) { pair ->
                            PairCard(pair, onClick = {
                                selectedPair = pair
                                deleteState = DeleteState.IDLE
                            })
                        }
                    }
                }
            }
        }

        // Bottom Sheet with all states
        if (selectedPair != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    if (deleteState != DeleteState.DELETING) {
                        selectedPair = null
                        deleteState = DeleteState.IDLE
                    }
                },
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                val p = selectedPair!!
                val creatorName = (p.created_by_name ?: "").split(" ").joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
                val canModify = (p.created_by?.toString() ?: "") == SharedPrefsUtils.getUserIdFromPrefs(context)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (deleteState) {
                        DeleteState.IDLE -> {
                            // Original pair details view
                            Text("Pair Details", fontWeight = FontWeight.Bold, color = Color(0xFF003366), fontSize = 20.sp)
                            Spacer(modifier = Modifier.height(16.dp))

                            // Names with overlapping avatars
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                val c1 = Color(0xFF1976D2)
                                val c2 = Color(0xFF0D47A1)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(c1),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(p.staff1_name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .offset(x = (-12).dp)
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(c2),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(p.staff2_name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(formatDisplayName(p.staff1_name), color = Color(0xFF003366), fontWeight = FontWeight.Medium)
                                    Text(formatDisplayName(p.staff2_name), color = Color(0xFF003366))
                                }
                                val pill = if (p.active == 1) Color(0xFF4CAF50) else Color(0xFFE53935)
                                Text(if (p.active == 1) "Active" else "Inactive", color = pill, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = Color(0xFFE0E0E0))
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Created by:", fontWeight = FontWeight.Medium, color = Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (creatorName.isNotBlank()) creatorName else "Unknown")
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (canModify) {
                                    Button(
                                        onClick = { /* TODO: Edit pair */ },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                                    ) { Text("Edit") }
                                    Button(
                                        onClick = { deleteState = DeleteState.CONFIRMING },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                                    ) { Text("Delete") }
                                }
                            }
                        }

                        DeleteState.CONFIRMING -> {
                            // Confirmation view
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Delete Pair?", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF003366))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Are you sure you want to delete this pair? This action cannot be undone.",
                                textAlign = TextAlign.Center,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { deleteState = DeleteState.IDLE },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF757575))
                                ) { Text("Cancel") }
                                Button(
                                    onClick = {
                                        deleteState = DeleteState.DELETING
                                        scope.launch {
                                            try {
                                                if (!isInternetAvailable(context)) {
                                                    deleteState = DeleteState.ERROR
                                                    deleteErrorMessage = "No internet connection. Please check your network and try again."
                                                    return@launch
                                                }

                                                val userId = SharedPrefsUtils.getUserIdFromPrefs(context)
                                                val response = deletePair(p.id, userId.toIntOrNull() ?: 0)

                                                if (response.success) {
                                                    deleteState = DeleteState.SUCCESS
                                                    delay(2500)
                                                    selectedPair = null
                                                    deleteState = DeleteState.IDLE
                                                    // Refresh the list
                                                    val id = SharedPrefsUtils.getUserIdFromPrefs(context).toIntOrNull() ?: 0
                                                    if (id != 0) viewModel.fetchPairs(context, id)
                                                } else {
                                                    deleteState = DeleteState.ERROR
                                                    deleteErrorMessage = response.message ?: "Failed to delete pair. Please try again."
                                                }
                                            } catch (e: Exception) {
                                                deleteState = DeleteState.ERROR
                                                deleteErrorMessage = when {
                                                    e.message?.contains("timeout", ignoreCase = true) == true ->
                                                        "Request timed out. Please check your connection and try again."
                                                    e.message?.contains("unable to resolve host", ignoreCase = true) == true ->
                                                        "Cannot reach server. Please check your internet connection."
                                                    else -> "Something went wrong. Please try again."
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                                ) { Text("Delete", fontWeight = FontWeight.Bold) }
                            }
                        }

                        DeleteState.DELETING -> {
                            // Deleting progress view
                            CircularProgressIndicator(
                                color = Color(0xFF1976D2),
                                modifier = Modifier.size(64.dp),
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Deleting Pair", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF003366))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Please wait while we delete the pair...",
                                textAlign = TextAlign.Center,
                                color = Color.Gray
                            )
                        }

                        DeleteState.SUCCESS -> {
                            // Success view
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Success!", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "The pair has been deleted successfully.",
                                textAlign = TextAlign.Center,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }

                        DeleteState.ERROR -> {
                            // Error view
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Delete Failed", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFFE53935))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                deleteErrorMessage,
                                textAlign = TextAlign.Center,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { deleteState = DeleteState.IDLE },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                            ) { Text("OK") }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PairCard(pair: com.yubytech.tracked.api.PairItem, onClick: () -> Unit = {}) {
    val bg = if (pair.active == 1) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val pill = if (pair.active == 1) Color(0xFF4CAF50) else Color(0xFFE53935)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(56.dp)
                    .background(pill)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val c1 = Color(0xFF1976D2)
                val c2 = Color(0xFF0D47A1)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(c1),
                    contentAlignment = Alignment.Center
                ) {
                    Text(pair.staff1_name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .offset(x = (-12).dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(c2),
                    contentAlignment = Alignment.Center
                ) {
                    Text(pair.staff2_name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(formatDisplayName(pair.staff1_name), color = Color(0xFF003366), fontWeight = FontWeight.Medium)
                Text(formatDisplayName(pair.staff2_name), color = Color(0xFF003366))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (pair.active == 1) "Active" else "Inactive", color = pill, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatDisplayName(name: String): String {
    return name.split(" ").joinToString(" ") { part ->
        val lower = part.lowercase()
        lower.replaceFirstChar { c -> c.uppercase() }
    }
}

// Delete API function
data class DeletePairResponse(val success: Boolean, val message: String?)

suspend fun deletePair(pairId: Int, createdBy: Int): DeletePairResponse = withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val json = org.json.JSONObject().apply {
            put("id", pairId)
            put("created_by", createdBy)
        }

        val requestBody = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = okhttp3.Request.Builder()
            .url("https://api.brisk-credit.com/endpoints/delete_pair.php")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful) {
            return@withContext DeletePairResponse(
                success = false,
                message = "Server error: ${response.code}. Please try again."
            )
        }

        if (responseBody.isNullOrEmpty()) {
            return@withContext DeletePairResponse(
                success = false,
                message = "Empty response from server. Please try again."
            )
        }

        val jsonResponse = org.json.JSONObject(responseBody)
        DeletePairResponse(
            success = jsonResponse.optBoolean("success", false),
            message = jsonResponse.optString("message", "Operation completed")
        )
    } catch (e: java.net.SocketTimeoutException) {
        DeletePairResponse(
            success = false,
            message = "Request timed out. Please check your connection and try again."
        )
    } catch (e: java.net.UnknownHostException) {
        DeletePairResponse(
            success = false,
            message = "Cannot reach server. Please check your internet connection."
        )
    } catch (e: java.io.IOException) {
        DeletePairResponse(
            success = false,
            message = "Network error occurred. Please check your connection and try again."
        )
    } catch (e: Exception) {
        DeletePairResponse(
            success = false,
            message = "Something went wrong. Please try again."
        )
    }
}

// Create Pair API function
data class CreatePairResponse(val success: Boolean, val message: String?)

suspend fun createPair(staff1Id: Int, staff2Id: Int, createdBy: Int): CreatePairResponse = withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val json = org.json.JSONObject().apply {
            put("p_1", staff1Id)
            put("p_2", staff2Id)
            put("created_by", createdBy)
        }

        val requestBody = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = okhttp3.Request.Builder()
            .url("https://api.brisk-credit.com/endpoints/create_pair.php")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful) {
            return@withContext CreatePairResponse(
                success = false,
                message = "Server error: ${response.code}. Please try again."
            )
        }

        if (responseBody.isNullOrEmpty()) {
            return@withContext CreatePairResponse(
                success = false,
                message = "Empty response from server. Please try again."
            )
        }

        val jsonResponse = org.json.JSONObject(responseBody)
        CreatePairResponse(
            success = jsonResponse.optBoolean("success", false),
            message = jsonResponse.optString("message", "Operation completed")
        )
    } catch (e: java.net.SocketTimeoutException) {
        CreatePairResponse(
            success = false,
            message = "Request timed out. Please check your connection and try again."
        )
    } catch (e: java.net.UnknownHostException) {
        CreatePairResponse(
            success = false,
            message = "Cannot reach server. Please check your internet connection."
        )
    } catch (e: java.io.IOException) {
        CreatePairResponse(
            success = false,
            message = "Network error occurred. Please check your connection and try again."
        )
    } catch (e: Exception) {
        CreatePairResponse(
            success = false,
            message = "Something went wrong. Please try again."
        )
    }
}