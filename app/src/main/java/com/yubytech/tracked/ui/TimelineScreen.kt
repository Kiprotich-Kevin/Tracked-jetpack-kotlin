
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yubytech.tracked.api.HierarchyUser
import com.yubytech.tracked.api.RetrofitInstance
import com.yubytech.tracked.api.UserActivityEvent
import com.yubytech.tracked.ui.SharedPrefsUtils
import com.yubytech.tracked.ui.isInternetAvailable
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


data class TimelineItem(
    val time: String,
    val status: String,
    val intime: String?,
    val client: String?,
    val extra: String?,
    val color: Color
)

data class User(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val isActive: Boolean = false,
    val isYou: Boolean = false
)

class TimelineViewModel : ViewModel() {
    var users by mutableStateOf<List<HierarchyUser>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var timeline by mutableStateOf<List<UserActivityEvent>>(emptyList())
    var timelineLoading by mutableStateOf(false)
    var timelineError by mutableStateOf<String?>(null)

    fun fetchUsers(userId: String, context: Context) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val response = RetrofitInstance.getClientsApiWithAuth(context).getHierarchyUsers(userId)
                if (response.isSuccessful) {
                    users = response.body() ?: emptyList()
                } else {
                    error = "Server error: ${response.message()}"
                }
            } catch (e: Exception) {
                error = "Network error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                loading = false
            }
        }
    }

    fun fetchTimeline(userId: String, context: Context) {
        viewModelScope.launch {
            timelineLoading = true
            timelineError = null
            try {
                val response = RetrofitInstance.getClientsApiWithAuth(context).getUserActivity(userId)
                if (response.isSuccessful) {
                    timeline = response.body() ?: emptyList()
                } else {
                    timelineError = "Server error: ${response.message()}"
                }
            } catch (e: Exception) {
                timelineError = "Network error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                timelineLoading = false
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    userName: String = "John Doe(You)",
    onBackClick: () -> Unit = {}
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isBackButtonEnabled by remember { mutableStateOf(true) }
   
    val context = LocalContext.current
    val viewModel: TimelineViewModel = viewModel()
    var userFilter by remember { mutableStateOf("All") }
    var selectedUser by remember { mutableStateOf(User(id = SharedPrefsUtils.getUserIdFromPrefs(context), name = "${SharedPrefsUtils.getUserNameFromPrefs(context)}(You)", isActive = true, isYou = true)) }
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
        } else {
            viewModel.error = "No Internet connection."
        }
    }

    // Fetch timeline for selected user
    LaunchedEffect(selectedUser.id) {
        viewModel.fetchTimeline(selectedUser.id, context)
    }

    // Filter timeline events by selected date
    val filteredTimeline = viewModel.timeline.filter {
        try {
            val eventDate = LocalDateTime.parse(it.event_time, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toLocalDate()
            eventDate == selectedDate
        } catch (e: Exception) {
            false
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
                        kotlinx.coroutines.GlobalScope.launch {
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
            val displayUserName = selectedUser.name.split(" ").joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
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
                selectedDate.isAfter(LocalDate.now().minusDays(5)) -> selectedDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
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
            // Outlined date label with icon inside
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
            // Always add margin (Spacer) after the date label
            Spacer(modifier = Modifier.width(12.dp))
        }
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.toEpochDay() * 24L * 60L * 60L * 1000L)
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
        
        // Timeline content - positioned below app bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 56.dp, bottom = 0.dp) // Account for app bar height and bottom surface
                .background(Color(0xFFF5F5F5)) // Subtle gray background
        ) {
            when {
                viewModel.timelineLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                viewModel.timelineError != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(viewModel.timelineError ?: "Error", color = Color.Gray)
                    }
                }
                filteredTimeline.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            val isYou = selectedUser.isYou
                            Text(
                                if (isYou) "You have no activity for this day" else "This user has no activity for this day",
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
                    .padding(start = 16.dp, end = 16.dp, bottom = 220.dp, top = 10.dp)
                    ) {
                        itemsIndexed(filteredTimeline) { idx, event ->
                            TimelineStepModernApi(event, isLast = idx == filteredTimeline.lastIndex, timeline = filteredTimeline)
                        }
                    }
                }
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
                    TimelineFilterButton(
                        text = "All",
                        selected = userFilter == "All",
                        onClick = { userFilter = "All" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TimelineFilterButton(
                        text = "Active",
                        selected = userFilter == "Active",
                        onClick = { userFilter = "Active" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TimelineFilterButton(
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
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (viewModel.error != null) {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(viewModel.error ?: "Error", color = Color.Gray)
                    }
                } else {
                    // Compose the users list for display: always start with John Doe(You)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        items(displayUsers) { user ->
                            Box(modifier = Modifier.clickable { selectedUser = user }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        UserAvatarViewModern(user)
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
fun TimelineStepModern(item: TimelineItem, isLast: Boolean) {
    Row(
        Modifier.fillMaxWidth(),) {

        // Time
        Column(
            modifier = Modifier.width(64.dp)
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(item.time, fontSize = 13.sp, color = Color.Gray)
        }
        // Timeline icon and line
        Box(
            modifier = Modifier.width(56.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(item.color),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getTimelineIcon(item.status),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                if (!isLast) {
                    Canvas(modifier = Modifier
                        .width(4.dp)
                        .height(44.dp)) {
                        drawLine(
                            color = Color(0xFFB3E5FC),
                            start = Offset(size.width / 2, 0f),
                            end = Offset(size.width / 2, size.height),
                            strokeWidth = 4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                        )
                    }
                }
            }
        }
        // Content
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(item.status, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            when (item.status) {
                "Check In", "Check out" -> {
                    if (!item.client.isNullOrEmpty())
                        Text("Client: ${item.client}", fontSize = 13.sp)
                    if (!item.extra.isNullOrEmpty())
                        Text("Time: ${item.extra}", fontSize = 13.sp, color = Color(0xFFFFA000))
                }
                "Logged In", "Logged out" -> {
                    item.intime?.let { Text(it, fontSize = 13.sp, color = Color(0xFF4CAF50)) }
                }
                "Waiting" -> {
                    item.extra?.let { Text(it, fontSize = 13.sp, color = Color(0xFFFFA000)) }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

fun getTimelineIcon(status: String): ImageVector = when (status) {
    "Logged In" -> Icons.Default.ExitToApp //Login
    "Logged out" -> Icons.Default.ExitToApp
    "Check In" -> Icons.Default.CheckCircle
    "Check out" -> Icons.Default.Done
    "Waiting" -> Icons.Default.Info
    "Gps_on" -> Icons.Default.LocationOn
    "Gps_off" -> Icons.Default.LocationOn
    "Waiting" -> Icons.Default.Info

    else -> Icons.Default.Info
}

@Composable
fun TimelineFilterButton(text: String, selected: Boolean, onClick: () -> Unit) {
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
fun UserAvatarViewModern(user: User) {
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
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
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
        val displayUserName = user.name.split(" ").joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
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

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TimelineStepModernApi(event: UserActivityEvent, isLast: Boolean, timeline: List<UserActivityEvent>) {
    val (color, icon) = when (event.event_type.lowercase()) {
        "login" -> Color(0xFF4CAF50) to Icons.Default.ExitToApp
        "logout" -> Color(0xFFFF4500) to Icons.Default.ExitToApp
        "checkin" -> Color(0xFF1976D2) to Icons.Default.CheckCircle
        "checkout" -> Color(0xFF085FAF) to Icons.Default.Done
        "waiting" -> Color(0xFFFFA000) to null
        else -> Color.Gray to Icons.Default.Info
    }
    // Precompute minutes for checkout
    var minutes: Long? = null
    if (event.event_type.lowercase() == "checkout" && event.session_id != null) {
        // Find the most recent checkin before this checkout with the same session_id
        val checkin = timeline
            .filter { 
                it.event_type.lowercase() == "checkin" && 
                it.session_id == event.session_id && 
                it.event_time < event.event_time 
            }
            .maxByOrNull { it.event_time }
        
        if (checkin != null) {
            minutes = try {
                val outTime = LocalDateTime.parse(event.event_time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                val inTime = LocalDateTime.parse(checkin.event_time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                val duration = java.time.Duration.between(inTime, outTime)
                val calculatedMinutes = duration.toMinutes()
                
                // Debug: Add some logging to see what's happening
                println("Checkout time: ${event.event_time}")
                println("Checkin time: ${checkin.event_time}")
                println("Calculated minutes: $calculatedMinutes")
                
                calculatedMinutes
            } catch (e: Exception) { 
                println("Error calculating duration: ${e.message}")
                null 
            }
        }
    }
    Row(
        Modifier.fillMaxWidth(),
    ) {
        // Time
        Column(
            modifier = Modifier.width(64.dp).padding(top = 10.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom
        ) {
            val time = try {
                val dt = LocalDateTime.parse(event.event_time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                dt.format(DateTimeFormatter.ofPattern("hh:mm a"))
            } catch (e: Exception) {
                event.event_time.substringAfter(" ")
            }
            Text(time, fontSize = 13.sp, color = Color.Gray)
        }
        // Timeline icon and line
        Box(
            modifier = Modifier.width(56.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    if (event.event_type.lowercase() == "waiting") {
                        Text("W", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                if (!isLast) {
                    Canvas(modifier = Modifier
                        .width(4.dp)
                        .height(44.dp)) {
                        drawLine(
                            color = Color(0xFFB3E5FC),
                            start = Offset(size.width / 2, 0f),
                            end = Offset(size.width / 2, size.height),
                            strokeWidth = 4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                        )
                    }
                }
            }
        }
        // Content
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(event.event_type.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (event.event_type.lowercase() == "login") {
                // For login events, show only the part before comma in details
                val details = event.details?.split(",")?.firstOrNull()?.trim() ?: ""
                if (details.isNotEmpty()) {
                    Text(details, fontSize = 13.sp)
                }
                // Extract status from details for login events
                val fullDetails = event.details?.lowercase() ?: ""
                val statusPart = fullDetails.split(",").find { it.trim().startsWith("status:") }
                val status = statusPart?.substringAfter("status:")?.trim() ?: "intime"
                val statusText = when (status) {
                    "intime" -> "Intime"
                    "ontime" -> "Ontime"
                    "late" -> "Late"
                    else -> "Intime"
                }
                val statusColor = when (status) {
                    "intime" -> Color(0xFF4CAF50)
                    "ontime" -> Color(0xFFFFA000)
                    "late" -> Color(0xFFFF4500)
                    else -> Color(0xFF4CAF50)
                }
                Text(statusText, fontSize = 13.sp, color = statusColor)
            } else if (event.event_type.lowercase() == "waiting" && event.details != null) {
                Text(event.details, fontSize = 13.sp, color = Color(0xFFFFA000))
            } else {
                event.details?.let { Text(it, fontSize = 13.sp) }
            }
            if (event.event_type.lowercase() != "login") {
                event.client_name?.let { Text("Client: ${event.client_name}", fontSize = 13.sp) }
                event.client_name?.let { Text("Phone: 0${event.client_co}", fontSize = 13.sp) }
            }
            if (minutes != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Time: ", fontSize = 13.sp)
                    Text("$minutes minutes", fontSize = 13.sp, color = Color(0xFFFFA000))
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}