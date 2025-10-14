// File: TaskScreen.kt
package com.yubytech.tracked.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yubytech.tracked.api.Task
import com.yubytech.tracked.ui.Client
import com.yubytech.tracked.ui.SharedPrefsUtils
import com.yubytech.tracked.ui.isInternetAvailable
import com.yubytech.tracked.TaskViewModel
import com.yubytech.tracked.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.json.JSONObject

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    userName: String = "John Doe(You)",
    onBackClick: () -> Unit = {}
) {

    // Add Task Bottom Sheet states
    var showAddTaskBottomSheet by remember { mutableStateOf(false) }
    var selectedTaskType by remember { mutableStateOf("") }
    var selectedClientId by remember { mutableStateOf("") }
    var selectedClientLabel by remember { mutableStateOf("") }
    var selectedAssigneeId by remember { mutableStateOf("") }
    var selectedAssigneeLabel by remember { mutableStateOf("") }
    var selectedDueDate by remember { mutableStateOf("") }
    var showTaskTypeDropdown by remember { mutableStateOf(false) }
    var showClientDropdown by remember { mutableStateOf(false) }
    var showForDropdown by remember { mutableStateOf(false) }

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isBackButtonEnabled by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val viewModel: TaskViewModel = viewModel()
    var userFilter by remember { mutableStateOf("All") }
    var selectedUser by remember {
        mutableStateOf(
            User(
                id = SharedPrefsUtils.getUserIdFromPrefs(context),
                name = "${SharedPrefsUtils.getUserNameFromPrefs(context)}(You)",
                isActive = true,
                isYou = true
            )
        )
    }

    val displayUsers = listOf(
        User(
            id = SharedPrefsUtils.getUserIdFromPrefs(context),
            name = "${SharedPrefsUtils.getUserNameFromPrefs(context)}(You)",
            isActive = true,
            isYou = true
        )
    ) + viewModel.users.filter {
        when (userFilter) {
            "All" -> true
            "Active" -> it.tr_status == 1
            "Inactive" -> it.tr_status != 1
            else -> true
        }
    }.map {
        User(
            id = it.id.toString(),
            name = it.name,
            isActive = it.tr_status == 1
        )
    }

    LaunchedEffect(Unit) {
        if (isInternetAvailable(context)) {
            viewModel.fetchUsers(SharedPrefsUtils.getUserIdFromPrefs(context), context)
            viewModel.fetchClients(SharedPrefsUtils.getUserIdFromPrefs(context), context)
        } else {
            viewModel.error = "No Internet connection."
        }
    }

    // Fetch timeline and tasks for selected user
    LaunchedEffect(selectedUser.id) {
        viewModel.fetchTimeline(selectedUser.id, context)
        if (isInternetAvailable(context)) {
            viewModel.fetchTasks(selectedUser.id, context)
        } else {
            viewModel.tasksError = "No Internet connection."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(WindowInsets.safeDrawing.asPaddingValues())
    ) {
        // App bar - positioned at top
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
                        // Re-enable after 1000ms to prevent rapid clicks
                        GlobalScope.launch {
                            kotlinx.coroutines.delay(1000)
                            isBackButtonEnabled = true
                        }
                    }
                },
                enabled = isBackButtonEnabled
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }

            // Capitalize first letter of each word in the user's name
            val displayUserName = selectedUser.name.split(" ").joinToString(" ") {
                it.lowercase().replaceFirstChar { c -> c.uppercase() }
            }
            Text(
                displayUserName,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))

            // Outlined date label with icon inside
            val dateLabel = when {
                selectedDate == LocalDate.now() -> "Today"
                selectedDate == LocalDate.now().minusDays(1) -> "Yesterday"
                selectedDate.isAfter(LocalDate.now().minusDays(5)) ->
                    selectedDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
                else -> selectedDate.format(DateTimeFormatter.ofPattern("yy-MM-dd"))
            }

            // Call icon in app bar for selected user (not John Doe(You))
            if (!selectedUser.isYou) {
                val apiUser = viewModel.users.find { it.id.toString() == selectedUser.id }
                if (apiUser != null) {
                    IconButton(onClick = {
                        val phone = "0" + apiUser.contact.toString()
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF1976D2))
                    }
                }
            }

            // Refresh icon
            IconButton(onClick = {
                if (isInternetAvailable(context)) {
                    viewModel.fetchUsers(SharedPrefsUtils.getUserIdFromPrefs(context), context)
                    viewModel.fetchTimeline(selectedUser.id, context)
                    viewModel.fetchTasks(selectedUser.id, context)
                } else {
                    viewModel.error = "No Internet connection."
                    viewModel.tasksError = "No Internet connection."
                }
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF1976D2))
            }

            // Date picker
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .border(1.dp, Color(0xFF1976D2), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    dateLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = "Pick date",
                    tint = Color(0xFF1976D2),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { showDatePicker = true }
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = selectedDate.toEpochDay() * 24L * 60L * 60L * 1000L
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    Button(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDate = LocalDate.ofEpochDay(it / (24L * 60L * 60L * 1000L))
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    Button(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        // Tasks content - positioned below app bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 56.dp, bottom = 0.dp)
                .background(Color(0xFFF5F5F5))
        ) {
            when {
                viewModel.tasksLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                viewModel.tasksError != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(viewModel.tasksError ?: "Error", color = Color.Gray)
                    }
                }
                viewModel.tasks.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            val isYou = selectedUser.isYou
                            Text(
                                if (isYou) "You have no tasks" else "This user has no tasks",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 16.dp, end = 16.dp, bottom = 220.dp, top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(viewModel.tasks) { task ->
//                            TaskCard(task = task)
                            TaskCard(
                                task = task,
                                viewModel = viewModel,           // Add this
                                selectedUserId = selectedUser.id, // Add this
                                context = context                // Add this
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Button - positioned above Available users section
        FloatingActionButton(
            onClick = {
                showAddTaskBottomSheet = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 240.dp), // Positioned above the users section
            containerColor = Color(0xFF1976D2),
            contentColor = Color.White
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Task",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Task",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Available users section - positioned at bottom
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = Color.White,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 20.dp)
            ) {
                // Centered title
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Available users",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TasksFilterButton(
                        text = "All",
                        selected = userFilter == "All",
                        onClick = { userFilter = "All" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TasksFilterButton(
                        text = "Active",
                        selected = userFilter == "Active",
                        onClick = { userFilter = "Active" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TasksFilterButton(
                        text = "Inactive",
                        selected = userFilter == "Inactive",
                        onClick = { userFilter = "Inactive" }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { /* TODO: filter logic */ }) {
                        Icon(Icons.Default.Info, contentDescription = "Filter")
                    }
                }

                // User row (horizontally scrollable)
                if (viewModel.loading) {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (viewModel.error != null) {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(viewModel.error ?: "Error", color = Color.Gray)
                    }
                } else {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayUsers) { user ->
                            Box(modifier = Modifier.clickable { selectedUser = user }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    UserAvatarViewTasks(user)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Task Bottom Sheet - moved outside the Box and properly positioned
    if (showAddTaskBottomSheet) {
        val meUserId = SharedPrefsUtils.getUserIdFromPrefs(context)
        val meUserName = SharedPrefsUtils.getUserNameFromPrefs(context)

        val assigneeOptions = remember(viewModel.users, meUserId, meUserName) {
            listOf(
                OptionItem(id = meUserId, label = "$meUserName(You)")
            ) + viewModel.users.map { OptionItem(id = it.id.toString(), label = it.name) }
        }

        val clientOptions = remember(viewModel.clients) {
            viewModel.clients.map { OptionItem(id = it.id.toString(), label = it.name) }
        }

        AddTaskBottomSheet(
            selectedTaskType = selectedTaskType,
            selectedClientId = selectedClientId,
            selectedClientLabel = selectedClientLabel,
            selectedAssigneeId = selectedAssigneeId,
            selectedAssigneeLabel = selectedAssigneeLabel,
            selectedDueDate = selectedDueDate,
            assigneeOptions = assigneeOptions,
            clientOptions = clientOptions,
            viewModel = viewModel,
            context = context,
            createdByUserId = meUserId,
            selectedUserId = selectedUser.id,
            onTaskTypeChange = { selectedTaskType = it },
            onClientChange = { option ->
                selectedClientId = option.id
                selectedClientLabel = option.label
            },
            onAssigneeChange = { option ->
                selectedAssigneeId = option.id
                selectedAssigneeLabel = option.label
            },
            onDueDateChange = { selectedDueDate = it },
            onDismiss = {
                showAddTaskBottomSheet = false
                selectedTaskType = ""
                selectedClientId = ""
                selectedClientLabel = ""
                selectedAssigneeId = ""
                selectedAssigneeLabel = ""
                selectedDueDate = "" // Don't forget to reset this too
            },
            onCreateTask = { /* handled inside bottom sheet now */ }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TaskCard(
    task: Task,
    viewModel: TaskViewModel, // Add viewModel parameter
    selectedUserId: String,   // Add selectedUserId parameter
    context: Context          // Add context parameter
) {
    val priorityColor = when (task.priority.lowercase()) {
        "high" -> Color(0xFFE57373) // Light red
        "medium" -> Color(0xFFFFB74D) // Light orange
        "low" -> Color(0xFF81C784) // Light green
        else -> Color(0xFF90A4AE) // Light gray
    }

    val statusColor = when (task.status.lowercase()) {
        "completed" -> Color(0xFF4CAF50) // Green
        "pending" -> Color(0xFFFFA000) // Orange
        "ongoing" -> Color(0xFF1976D2) // Blue
        "overdue" -> Color(0xFFE57373) // Light red
        else -> Color.Gray
    }

    // bottomsheet
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }

    // UI states for request
    var isLoading by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf<Boolean?>(null) }
    var responseMessage by remember { mutableStateOf("") }

    val endpoint = "https://api.brisk-credit.net/endpoints/complete_task.php"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { showBottomSheet = true },
        colors = CardDefaults.cardColors(
            containerColor = statusColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 0.5.dp, end = 0.5.dp, bottom = 0.5.dp)
                .background(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Task title
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF333333)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Task description
                Text(
                    text = task.description,
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                val client = task.client_name;
                if (client != null){
                    // Task contact details
                    Text(
                        text = "Name: ${task.client_name}",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    // Task contact details
                    Text(
                        text = "Phone: 0${task.client_contact}" ,
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }


                Spacer(modifier = Modifier.height(15.dp))

                // Due date and created by
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Due date
                    val dueDate = try {
                        val date = LocalDate.parse(task.due_date)
                        date.format(DateTimeFormatter.ofPattern("MMM dd"))
                    } catch (e: Exception) {
                        task.due_date
                    }

                    Text(
                        text = "Due: $dueDate",
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )

                    // Status
                    Text(
                        text = task.status.replaceFirstChar { it.uppercase() }.replace("_", " "),
                        fontSize = 12.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Created by
                Text(
                    text = "Created by: ${task.created_by_name}",
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    // Bottom Sheet
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = bottomSheetState,
            containerColor = Color.White,
            dragHandle = {
                Surface(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color.Gray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 32.dp, height = 4.dp)
                    )
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {

                when {
                    isLoading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF1976D2),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Completing task...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF666666)
                            )
                        }
                    }
                    isSuccess == true -> {
                        LaunchedEffect(Unit) {
                            delay(1500) // Reduced delay to 1.5 seconds

                            // Refresh the tasks list
                            if (isInternetAvailable(context)) {
                                viewModel.fetchTasks(selectedUserId, context)
                            }

                            // Close the bottom sheet
                            showBottomSheet = false

                            // Reset the success state for future use
                            isSuccess = null
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(
                                        Color(0xFF4CAF50).copy(alpha = 0.1f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Task Completed!",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E2E2E)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Refreshing tasks...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF666666)
                            )
                        }
                    }
                    isSuccess == false -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(
                                        Color(0xFFE53935).copy(alpha = 0.1f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Failed",
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Task Failed",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E2E2E)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = responseMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF666666),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        // Header
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Task Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2E2E2E)
                            )
                            Spacer(Modifier.height(4.dp))
                            Divider(
                                color = Color(0xFFE0E0E0),
                                thickness = 1.dp
                            )
                            Spacer(Modifier.height(20.dp))
                        }

                        // Task Title
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1A1A1A),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        // Task Information Cards
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF8F9FA)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                DetailRow(label = "Description", value = task.description)
                                DetailRow(label = "Priority", value = task.priority)
                                StatusRow(label = "Status", value = task.status)
                                DetailRow(label = "Due Date", value = task.due_date)
                                DetailRow(label = "Created by", value = task.created_by_name, isLast = true)
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Action Button (only for the current user viewing their own tasks)
                        val isSelectedUserMe = selectedUserId == SharedPrefsUtils.getUserIdFromPrefs(context)
                        if(task.status != "completed" && isSelectedUserMe){
                            Button(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        isSuccess = null
                                        responseMessage = ""

                                        val clientIdStr = task.client_id.toString()
                                        val currentUserId = SharedPrefsUtils.getUserIdFromPrefs(context)

                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                TaskApiManager.completeTask(
                                                    taskId = task.id,
                                                    clientId = clientIdStr,
                                                    userId = currentUserId,
                                                    endpoint = endpoint,
                                                    context = context
                                                )
                                            }

                                            if (result.success) {
                                                isSuccess = true
                                            } else {
                                                isSuccess = false
                                                responseMessage = result.message.ifBlank { "Server error. Please try again." }
                                            }
                                        } catch (e: Exception) {
                                            isSuccess = false
                                            responseMessage = e.message ?: "An unexpected error occurred"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1976D2)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 2.dp,
                                    pressedElevation = 8.dp
                                )
                            ) {
                                Text(
                                    text = "Mark as Complete",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }


                        // Bottom spacing for safe area
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}


@Composable
private fun DetailRow(
    label: String,
    value: String,
    isLast: Boolean = false
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF666666),
                modifier = Modifier.width(80.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF2E2E2E),
                modifier = Modifier.weight(1f)
            )
        }
        if (!isLast) {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    isLast: Boolean = false
) {
    val statusColor = when (value.lowercase()) {
        "completed", "done" -> Color(0xFF4CAF50)
        "in progress", "in-progress", "active" -> Color(0xFF2196F3)
        "pending", "todo", "new" -> Color(0xFFFF9800)
        "overdue", "cancelled" -> Color(0xFFE53935)
        "on hold", "paused" -> Color(0xFF9C27B0)
        else -> Color(0xFF666666)
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF666666),
                modifier = Modifier.width(80.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = statusColor,
                modifier = Modifier.weight(1f)
            )
        }
        if (!isLast) {
            Spacer(Modifier.height(8.dp))
        }
    }
}

data class OptionItem(val id: String, val label: String)

// Add tasks
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskBottomSheet(
    selectedTaskType: String,
    selectedClientId: String,
    selectedClientLabel: String,
    selectedAssigneeId: String,
    selectedAssigneeLabel: String,
    selectedDueDate: String = "",
    assigneeOptions: List<OptionItem>,
    clientOptions: List<OptionItem>,
    viewModel: TaskViewModel,
    context: Context,
    createdByUserId: String,
    selectedUserId: String,
    onTaskTypeChange: (String) -> Unit,
    onClientChange: (OptionItem) -> Unit,
    onAssigneeChange: (OptionItem) -> Unit,
    onDueDateChange: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onCreateTask: () -> Unit = {}
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val taskTypes = listOf("Client visit")
    val dueDateOptions = listOf("Today", "Tomorrow", "Exact Date")

    var expandedTaskType by remember { mutableStateOf(false) }
    var expandedClient by remember { mutableStateOf(false) }
    var expandedAssignee by remember { mutableStateOf(false) }
    var expandedDueDate by remember { mutableStateOf(false) }

    var clientQuery by remember { mutableStateOf(selectedClientLabel) }
    var assigneeQuery by remember { mutableStateOf(selectedAssigneeLabel) }

    var description by remember { mutableStateOf("") }

    val filteredClientOptions = remember(clientQuery, clientOptions) {
        val base = if (clientQuery.isBlank()) clientOptions else clientOptions.filter { it.label.contains(clientQuery, ignoreCase = true) }
        base.take(50)
    }
    val filteredAssigneeOptions = remember(assigneeQuery, assigneeOptions) {
        val base = if (assigneeQuery.isBlank()) assigneeOptions else assigneeOptions.filter { it.label.contains(assigneeQuery, ignoreCase = true) }
        base.take(50)
    }

    var showExactDatePicker by remember { mutableStateOf(false) }
    var showCreateTask by remember { mutableStateOf(false) }
    val exactDatePickerState = rememberDatePickerState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        containerColor = Color.White,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.Gray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 4.dp)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Create New Task",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2E2E2E)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Fill in the details below",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Divider(
                    color = Color(0xFFE0E0E0),
                    thickness = 1.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Task Type Dropdown
            DropdownSection(
                label = "Task Type",
                selectedValue = selectedTaskType,
                placeholder = "Select task type",
                options = taskTypes,
                expanded = expandedTaskType,
                onExpandedChange = { expandedTaskType = it },
                onValueChange = {
                    onTaskTypeChange(it)
                    expandedTaskType = false
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Description input
            Text(
                text = "Description",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF2E2E2E),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier
                    .fillMaxWidth(),
                placeholder = { Text("Enter task description") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1976D2),
                    unfocusedBorderColor = Color(0xFFDDDDDD)
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Client Dropdown
            SearchableDropdownSection(
                label = "Client",
                query = clientQuery,
                placeholder = "Search or select client",
                options = filteredClientOptions,
                expanded = expandedClient,
                onExpandedChange = { expandedClient = it },
                onQueryChange = {
                    clientQuery = it
                    expandedClient = true
                },
                onSelect = { option ->
                    onClientChange(option)
                    clientQuery = option.label
                    expandedClient = false
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Assignee Dropdown
            SearchableDropdownSection(
                label = "Assignee",
                query = assigneeQuery,
                placeholder = "Search or select assignee",
                options = filteredAssigneeOptions,
                expanded = expandedAssignee,
                onExpandedChange = { expandedAssignee = it },
                onQueryChange = {
                    assigneeQuery = it
                    expandedAssignee = true
                },
                onSelect = { option ->
                    onAssigneeChange(option)
                    assigneeQuery = option.label
                    expandedAssignee = false
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Due Date Dropdown
            DropdownSection(
                label = "Due Date",
                selectedValue = selectedDueDate,
                placeholder = "Select due date",
                options = dueDateOptions,
                expanded = expandedDueDate,
                onExpandedChange = { expandedDueDate = it },
                onValueChange = {
                    val today = LocalDate.now()
                    when (it) {
                        "Today" -> onDueDateChange(today.format(DateTimeFormatter.ISO_DATE))
                        "Tomorrow" -> onDueDateChange(today.plusDays(1).format(DateTimeFormatter.ISO_DATE))
                        "Exact Date" -> {
                            showExactDatePicker = true
                        }
                    }
                    expandedDueDate = false
                }
            )

            if (showExactDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showExactDatePicker = false },
                    confirmButton = {
                        Button(onClick = {
                            exactDatePickerState.selectedDateMillis?.let { millis ->
                                val date = LocalDate.ofEpochDay(millis / (24L * 60L * 60L * 1000L))
                                onDueDateChange(date.format(DateTimeFormatter.ISO_DATE))
                            }
                            showExactDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = { Button(onClick = { showExactDatePicker = false }) { Text("Cancel") } }
                ) {
                    DatePicker(state = exactDatePickerState)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cancel Button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF1976D2)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF1976D2)
                    )
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Create Button
                val isFormValid = selectedTaskType.isNotEmpty() &&
                        selectedClientId.isNotEmpty() &&
                        selectedAssigneeId.isNotEmpty() &&
                        selectedDueDate.isNotEmpty() &&
                        description.isNotBlank()

                Button(
                    onClick = {
                        if (isFormValid) {
                            // Trigger create task flow UI
                            showCreateTask = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFormValid) Color(0xFF1976D2) else Color(0xFFCCCCCC),
                        contentColor = Color.White
                    ),
                    enabled = isFormValid,
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = if (isFormValid) 2.dp else 0.dp
                    )
                ) {
                    Text(
                        text = "Create Task",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Bottom safe area
            Spacer(modifier = Modifier.height(16.dp))

            if (showCreateTask) {
                CreateTaskAction(
                    title = selectedTaskType,
                    description = description,
                    clientId = selectedClientId,
                    assigneeId = selectedAssigneeId,
                    createdById = createdByUserId,
                    dueDate = selectedDueDate,
                    viewModel = viewModel,
                    context = context,
                    onSuccess = {
                        if (isInternetAvailable(context)) {
                            viewModel.fetchTasks(selectedUserId, context)
                        }
                        showCreateTask = false
                        onDismiss()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSection(
    label: String,
    selectedValue: String,
    placeholder: String,
    options: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2E2E2E),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFAFAFA)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedValue,
                    onValueChange = { },
                    readOnly = true,
                    placeholder = {
                        Text(
                            text = placeholder,
                            color = Color(0xFF999999),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = expanded,
                            modifier = Modifier.rotate(if (expanded) 180f else 0f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color(0xFF1976D2),
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF2E2E2E),
                        fontWeight = FontWeight.Medium
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) },
                    modifier = Modifier.exposedDropdownSize()
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (option == selectedValue) Color(0xFF1976D2) else Color(0xFF2E2E2E)
                                )
                            },
                            onClick = {
                                onValueChange(option)
                            },
                            modifier = Modifier.padding(horizontal = 4.dp),
                            colors = MenuDefaults.itemColors(
                                textColor = Color(0xFF2E2E2E)
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchableDropdownSection(
    label: String,
    query: String,
    placeholder: String,
    options: List<OptionItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSelect: (OptionItem) -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2E2E2E),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFAFAFA)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { onQueryChange(it) },
                    readOnly = false,
                    placeholder = {
                        Text(
                            text = placeholder,
                            color = Color(0xFF999999),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                onExpandedChange(true)
                            } else {
                                onExpandedChange(false)
                            }
                        },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color(0xFF1976D2),
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF2E2E2E),
                        fontWeight = FontWeight.Medium
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                if (expanded && options.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = BorderStroke(1.dp, Color(0xFFDDDDDD))
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                        ) {
                            items(options.size) { index ->
                                val option = options[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelect(option)
                                            onExpandedChange(false)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF2E2E2E)
                                    )
                                }
                                if (index < options.size - 1) {
                                    Divider(color = Color(0xFFEAEAEA), thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TasksFilterButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF1976D2) else Color.White,
            contentColor = if (selected) Color.White else Color(0xFF1976D2)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(text, fontSize = 14.sp)
    }
}

@Composable
fun UserAvatarViewTasks(user: User) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val borderColor = when {
            user.isYou -> Color(0xFF1976D2)
            user.isActive -> Color(0xFF4CAF50)
            else -> Color(0xFFD32F2F)
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(3.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (user.avatarUrl != null) {
                // TODO: Load image from URL
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Text(
                    user.name.split(" ").firstOrNull()?.firstOrNull()?.toString() ?: "?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = borderColor
                )
            }
        }
        // Capitalize first letter of each word in the user's name
        val displayUserName = user.name.split(" ").joinToString(" ") {
            it.lowercase().replaceFirstChar { c -> c.uppercase() }
        }
        Text(
            displayUserName,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.Center
        )
    }
}

object TaskApiManager {
    data class CompletionResult(val success: Boolean, val message: String = "")

    fun completeTask(taskId: Int, clientId: String, userId: String, endpoint: String, context: Context): CompletionResult {
        return try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                SharedPrefsUtils.getJwtToken(context)?.let { token ->
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }

            val json = JSONObject().apply {
                put("task_id", taskId)
                if (clientId.isNotBlank()) put("client_id", clientId.toInt())
                if (userId.isNotBlank()) put("user_id", userId.toInt())
            }

            conn.outputStream.use { os ->
                os.write(json.toString().toByteArray())
            }

            val code = conn.responseCode
            val responseText = try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } finally {
                conn.disconnect()
            }

            if (code in 200..299 && responseText.isNotBlank()) {
                try {
                    val obj = JSONObject(responseText)
                    val success = obj.optBoolean("success", code in 200..299)
                    val message = obj.optString("message", "")
                    CompletionResult(success = success, message = message)
                } catch (_: Exception) {
                    CompletionResult(success = true)
                }
            } else {
                // If server returned JSON with error message
                if (responseText.isNotBlank()) {
                    try {
                        val obj = JSONObject(responseText)
                        CompletionResult(
                            success = obj.optBoolean("success", false),
                            message = obj.optString("message", "")
                        )
                    } catch (_: Exception) {
                        CompletionResult(success = false, message = "Server error. Please try again.")
                    }
                } else {
                    CompletionResult(success = false, message = "Server error. Please try again.")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            CompletionResult(success = false, message = e.message ?: "Unknown error")
        }
    }

    private fun getToken(context: Context): String {
        // Retrieve your stored token (SharedPreferences, DB, etc.)
        return "your_token_here"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun CreateTaskAction(
    title: String,
    description: String,
    clientId: String,
    assigneeId: String,
    createdById: String,
    dueDate: String,
    viewModel: TaskViewModel,
    context: Context,
    onSuccess: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var isSuccess by remember { mutableStateOf<Boolean?>(null) }
    var responseMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val endpoint = "https://api.brisk-credit.net/endpoints/create_task.php"
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    // Attach JWT if available
                    SharedPrefsUtils.getJwtToken(context)?.let { token ->
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                }

                val payload = """
                {"title":"${title}","description":"${description}","client_id":${clientId},"assigned_to":${assigneeId},"created_by":${createdById},"priority":"medium","due_date":"${dueDate}"}
                """.trimIndent()
                conn.outputStream.use { it.write(payload.toByteArray()) }
                val code = conn.responseCode
                conn.disconnect()
                withContext(Dispatchers.Main) {
                    isLoading = false
                    if (code in 200..299) {
                        isSuccess = true
                    } else {
                        isSuccess = false
                        responseMessage = when {
                            code in 400..499 -> "Couldn't create task. Please check the details and try again."
                            code >= 500 -> "We’re having trouble on our end. Please try again later."
                            else -> "Something went wrong. Please try again."
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    isSuccess = false
                    responseMessage = when (e) {
                        is java.net.SocketTimeoutException -> "Network timeout. Please check your connection and try again."
                        is java.net.UnknownHostException -> "No internet connection. Please try again."
                        else -> "Couldn't create task. Please try again."
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = bottomSheetState,
            containerColor = Color.White,
            dragHandle = {
                Surface(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color.Gray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                ) { Box(modifier = Modifier.size(width = 32.dp, height = 4.dp)) }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(color = Color(0xFF1976D2), strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Creating task...", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF666666))
                    }
                    isSuccess == true -> {
                        LaunchedEffect(Unit) {
                            delay(1500)
                            showSheet = false
                            onSuccess()
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFF4CAF50).copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(36.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Task Created!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E2E2E))
                    }
                    isSuccess == false -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFFE53935).copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(36.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Task Failed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E2E2E))
                        Spacer(Modifier.height(4.dp))
                        Text(responseMessage, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF666666), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}