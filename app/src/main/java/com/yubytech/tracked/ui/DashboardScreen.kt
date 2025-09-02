
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.yubytech.tracked.R
import com.yubytech.tracked.WeatherViewModel
import com.yubytech.tracked.api.UserActivityEvent
import com.yubytech.tracked.local.ActivityEventSyncManager
import com.yubytech.tracked.ui.BottomSheetMessage
import com.yubytech.tracked.ui.CheckInPrefs
import com.yubytech.tracked.ui.CheckInUiState
import com.yubytech.tracked.ui.CheckInViewModel
import com.yubytech.tracked.ui.Client
import com.yubytech.tracked.ui.PermissionUtils
import com.yubytech.tracked.ui.QrCodeScanner
import com.yubytech.tracked.ui.SharedPrefsUtils
import com.yubytech.tracked.ui.SwipeToCheckIn
import com.yubytech.tracked.ui.WebViewScreen
import com.yubytech.tracked.getFriendlyTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter


// Function to get dynamic greeting based on time
@RequiresApi(Build.VERSION_CODES.O)
fun getDynamicGreeting(): String {
    val currentTime = LocalTime.now()
    return when {
        currentTime.hour < 12 -> "Good morning,"
        currentTime.hour < 17 -> "Good afternoon,"
        currentTime.hour < 21 -> "Good evening,"
        else -> "Good night,"
    }
}

// Utility function to get user ID from shared preferences
fun getUserIdFromPrefs(context: android.content.Context): String {
    return try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val userId = sharedPreferences.getInt("user_id", -1)
        if (userId != -1) userId.toString() else "2" // fallback to "2" if no user ID found
    } catch (e: Exception) {
        "2" // fallback to "2" if there's any error
    }
}

class DashboardActivityViewModel : ViewModel() {
    var activities by mutableStateOf<List<UserActivityEvent>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun fetchRecentActivity(userId: String, context: Context) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val response = com.yubytech.tracked.api.RetrofitInstance.getClientsApiWithAuth(context).getUserActivityDashboard(userId)
                if (response.isSuccessful) {
                    activities = response.body()?.take(10) ?: emptyList()
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

    fun refreshActivity(userId: String, context: Context) {
        fetchRecentActivity(userId, context)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onActivityClick: () -> Unit = {},
    onTrackingClick: () -> Unit = {},
    onAttendanceClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    onTimelineClick: () -> Unit = {}
) {
    val checkInViewModel: CheckInViewModel = viewModel()
    val uiState by checkInViewModel.uiState.collectAsState()
    val status by checkInViewModel.status.collectAsState()
    val context = LocalContext.current
    var showClientDialog by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var pendingLocation: android.location.Location? by remember { mutableStateOf(null) }
    var progressMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }
    // Note: showPermissionSheet removed - MainActivity handles all permission monitoring
    var showLocationRequiredSheet by remember { mutableStateOf(false) }
    var showLocationFailedSheet by remember { mutableStateOf(false) }
    var hasReturnedFromSettings by remember { mutableStateOf(false) }



    // React to ViewModel UI state
    LaunchedEffect(uiState) {
        when (uiState) {
            is CheckInUiState.ShowClientDialog -> {
                pendingLocation = (uiState as CheckInUiState.ShowClientDialog).location
                showClientDialog = true
            }
            is CheckInUiState.ShowCheckoutDialog -> {
                pendingLocation = (uiState as CheckInUiState.ShowCheckoutDialog).location
                showCheckoutDialog = true
            }
            is CheckInUiState.Progress -> {
                progressMessage = (uiState as CheckInUiState.Progress).message
            }
            is CheckInUiState.Error -> {
                errorMessage = (uiState as CheckInUiState.Error).message
            }
            is CheckInUiState.Success -> {
                showSuccess = true
            }
            is CheckInUiState.PermissionRequired -> {
                // Permission sheet handled by MainActivity - just clear the state
                checkInViewModel.clearUiState()
            }
            is CheckInUiState.LocationRequired -> {
                showLocationRequiredSheet = true
            }
            is CheckInUiState.LocationFailed -> {
                showLocationFailedSheet = true
            }
            else -> {}
        }
    }

//    val activities = remember {
//        listOf(
//            ActivityItem("Logged In", "08:15 AM", "Intime", Color(0xFF4CAF50)),
//            ActivityItem("Check In", "08:15 AM", "Intime", Color(0xFF1976D2)),
//            ActivityItem("Check out", "08:15 AM", "Intime", Color(0xFF1976D2)),
//            ActivityItem("Check In", "08:15 AM", "Intime", Color(0xFF1976D2)),
//            ActivityItem("Check out", "08:15 AM", "Intime", Color(0xFF1976D2)),
//            ActivityItem("Check In", "08:15 AM", "Intime", Color(0xFF1976D2)),
//            ActivityItem("Check out", "08:15 AM", "Intime", Color(0xFF1976D2)),
//            ActivityItem("Logged out", "08:15 AM", "Intime", Color(0xFFFF7043))
//        )
//    }
    var showCheckinDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showClientSearchDialog by remember { mutableStateOf(false) }
    var selectedClient by remember { mutableStateOf<Client?>(null) }
    val bottomSheetState = rememberModalBottomSheetState()
    val viewModel = viewModel<WeatherViewModel>()
    val showSessionExpiredSheet = remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    val qrResult = remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var selectedClientForCheckIn by remember { mutableStateOf<Client?>(null) }
    var showSuccessSheet by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }
    var showSystemWebView by remember { mutableStateOf(false) }
    var showTasksComingSoonSheet by remember { mutableStateOf(false) }
    var showLogoutConfirmationDialog by remember { mutableStateOf(false) }
    var showAttendanceSheet by remember { mutableStateOf(false) }
    val tasksUrl = "https://your-tasks-url.com" // TODO: Replace with actual tasks URL


    // Persistent GPS dialog logic
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val isGpsEnabled = PermissionUtils.isLocationEnabled(context)
            showLocationRequiredSheet = !isGpsEnabled
        }
    }

    // Monitor when user returns from settings (only for location service, not permissions)
    LaunchedEffect(hasReturnedFromSettings) {
        if (hasReturnedFromSettings) {
            delay(100) // Small delay to ensure settings have been applied
            if (PermissionUtils.isLocationEnabled(context)) {
                showLocationRequiredSheet = false
                checkInViewModel.clearUiState()
            }
            hasReturnedFromSettings = false
        }
    }

    val systemUiController = rememberSystemUiController()
    val useDarkIcons = true
    SideEffect {
        systemUiController.setStatusBarColor(
            color = Color.White.copy(alpha = 0.92f),
            darkIcons = useDarkIcons
        )
    }

    // The CheckInViewModel will handle permission requirements when user tries to check-in/check-out

    if (showCheckinDialog) {
        ModalBottomSheet(
            onDismissRequest = { showCheckinDialog = false },
            sheetState = bottomSheetState,
            containerColor = Color.White
        ) {
            selectedClient?.let { client ->
                ClientCheckoutDialog(
                    client = client,
                    onCancel = { showCheckinDialog = false },
                    onSubmit = { showCheckinDialog = false }
                )
            }
        }
    }
    if (showFilterDialog) {
        ModalBottomSheet(
            onDismissRequest = { showFilterDialog = false },
            sheetState = bottomSheetState,
            containerColor = Color.White
        ) {
            FilterDialog(
                onCancel = { showFilterDialog = false },
                onApply = { showFilterDialog = false }
            )
        }
    }
    if (showClientSearchDialog) {
        LaunchedEffect(showClientSearchDialog) {
            viewModel.fetchClientsByUserId(SharedPrefsUtils.getUserIdFromPrefs(context), context)
        }
        ModalBottomSheet(
            onDismissRequest = { showClientSearchDialog = false },
            sheetState = bottomSheetState,
            containerColor = Color.White
        ) {
            when {
                viewModel.loading -> {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                uiState is CheckInUiState.GettingLocation -> {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                viewModel.error != null -> {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No Internet connection.", color = Color.Gray)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.fetchClientsByUserId(SharedPrefsUtils.getUserIdFromPrefs(context), context) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    ClientSearchDialog(
                        clients = viewModel.clients,
                        onCancel = { showClientSearchDialog = false },
                        onSubmit = { showClientSearchDialog = false },
                        onClientSelected = { client ->
                            selectedClient = client
                        }
                    )
                }
            }
        }
    }
    // Add a bottom Surface like a bottom nav
    Box(modifier = Modifier.fillMaxSize()) {
        // Existing content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(getDynamicGreeting(), fontSize = 14.sp, color = Color.Gray)
                    val userName = SharedPrefsUtils.getUserNameFromPrefs(context)
                    val formattedName = userName.split(" ")
                        .joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercaseChar() } }

                    Text(formattedName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF003366))
                    // Debug: Log what's stored in shared preferences
                    LaunchedEffect(Unit) {
                        SharedPrefsUtils.debugSharedPrefs(context)
                    }
                }
                IconButton(onClick = {
                    // Trigger sync when notification icon is clicked
                    coroutineScope.launch {
                        ActivityEventSyncManager.syncUnsentEvents(
                            context = context,
                            endpoint = "https://api.brisk-credit.com/endpoints/activity_logs.php",
                            onResult = { success ->
                                Handler(Looper.getMainLooper()).post {
                                    if (success) {
                                        Toast.makeText(context, "Sync successful!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Sync failed for some events.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onEventFail = { event ->
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(context, "Failed to post event: ${event.event_type}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }, enabled = true) {
                    Icon(
                        painter = painterResource(R.drawable.ic_notifications_24),
                        contentDescription = "Notifications",
                        tint = Color(0xFF1976D2)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                        .clickable { showSessionExpiredSheet.value = true },
                    contentAlignment = Alignment.Center
                ) {
                    val userName = SharedPrefsUtils.getUserNameFromPrefs(context)
                    val initials = userName.split(" ").take(2).joinToString("") { it.firstOrNull()?.uppercase() ?: "" }
                    Text(initials.ifEmpty { "JD" }, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.background)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Main Cards
            Column {
                Row(Modifier.fillMaxWidth()) {
                    DashboardCard(title = "Attendance", subtitle = "Login , checkin and check out", icon = android.R.drawable.ic_menu_agenda, modifier = Modifier.weight(1f), onClick = { showAttendanceSheet = true })
                    Spacer(modifier = Modifier.width(12.dp))
                    DashboardCard(title = "Tracking", subtitle = "Check out you and your staff", icon = android.R.drawable.ic_menu_mylocation, modifier = Modifier.weight(1f), onClick = onTrackingClick)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    DashboardCard(title = "Activity", subtitle = "Check out your activity", icon = android.R.drawable.ic_menu_recent_history, modifier = Modifier.weight(1f), onClick = onActivityClick)
                    Spacer(modifier = Modifier.width(12.dp))
                    DashboardCard(title = "Tasks", subtitle = "View and manage your tasks", icon = android.R.drawable.ic_menu_edit, modifier = Modifier.weight(1f), onClick = { showTasksComingSoonSheet = true } )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            val activityViewModel: DashboardActivityViewModel = viewModel()
            val userId = SharedPrefsUtils.getUserIdFromPrefs(context)
            
            Row(modifier = Modifier.fillMaxWidth()
                .padding(end = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween){
                Text(
                    text = "Recent Activity",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF003366)// Dark Blue Hex
                )


                Text(
                    text = "See all",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary, // Dark Blue Hex
                    modifier = Modifier.clickable { 
                        onTimelineClick()
                    }
                )
            }


            Spacer(modifier = Modifier.height(8.dp))
            // The following LazyColumn is a direct child of Column, so Modifier.weight(1f) is valid
            LaunchedEffect(Unit) {
                activityViewModel.fetchRecentActivity(userId, context)
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    activityViewModel.loading && activityViewModel.activities.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().fillMaxHeight().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    activityViewModel.error != null && activityViewModel.activities.isEmpty() -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val userFriendlyError = when {
                                    activityViewModel.error?.contains("timeout", ignoreCase = true) == true ->
                                        "The request timed out. Please check your internet connection and try again."
                                    activityViewModel.error?.contains("Unable to resolve host", ignoreCase = true) == true ||
                                    activityViewModel.error?.contains("Network error", ignoreCase = true) == true ->
                                        "Unable to connect. Please check your internet connection."
                                    activityViewModel.error?.contains("Server error", ignoreCase = true) == true ->
                                        "Something went wrong. Please try again later."
                                    activityViewModel.error?.contains("Unauthorized", ignoreCase = true) == true ->
                                        "Your session has expired. Please log in again."
                                    else ->
                                        "We couldn't load your recent activity. Please try again."
                                }
                                userFriendlyError.split(". ").forEach { line ->
                                    if (line.isNotBlank()) {
                                        Text(line.trim() + if (!line.trim().endsWith(".")) "." else "", color = Color.Gray, textAlign = TextAlign.Center)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { activityViewModel.refreshActivity(userId, context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                    activityViewModel.activities.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No recent activity", color = Color.Gray)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { activityViewModel.refreshActivity(userId, context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                                ) {
                                    Text("Refresh")
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(activityViewModel.activities) { event ->
                                DashboardActivityCard(event)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
        // Absolute bottom surface
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = Color.White,
            shadowElevation = 8.dp // subtle elevation
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    // Removed .height(64.dp) to allow content to dictate height
            ) {
                Spacer(modifier = Modifier.height(8.dp))
//                SwipeToCheckIn(
//                    isCheckedIn = status.isCheckedIn,
//                    isLoading = uiState is CheckInUiState.GettingLocation,
//                    onSwipeComplete = { checkInViewModel.swipeAction() }
//                )
//                SwipeToCheckIn(
//                    isCheckedIn = status.isCheckedIn,
//                    isLoading = uiState is CheckInUiState.GettingLocation || uiState is CheckInUiState.Success,
//                    showSuccess = uiState is CheckInUiState.Success,
//                    onSwipeComplete = { checkInViewModel.swipeAction() }
//                )

                SwipeToCheckIn(
                    isCheckedIn = status.isCheckedIn,
                    isLoading = uiState is CheckInUiState.GettingLocation ||
                            uiState is CheckInUiState.ShowClientDialog ||
                            uiState is CheckInUiState.ShowCheckoutDialog,
                    onSwipeComplete = { checkInViewModel.swipeAction() },
                    onReset = { checkInViewModel.clearAll() }
                )
                Spacer(modifier = Modifier.height(8.dp))

            }
        }
    }
    // Bottom sheet for session expired
    if (showSessionExpiredSheet.value) {
        ModalBottomSheet(
            onDismissRequest = { showSessionExpiredSheet.value = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Your session has expired.",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD32F2F),
                    modifier = Modifier.padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    "Use the button below to login or scan.",
                    modifier = Modifier.padding(bottom = 16.dp),
                    textAlign = TextAlign.Center
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    Button(
                        onClick = {

                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Login")
                    }
                    Button(
                        onClick = { showQrScanner = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Scan")
                    }
                }
                qrResult.value?.let {
                    Text("Scanned: $it", color = Color(0xFF1976D2), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
    }
    // Full-screen QR scanner overlay
    if (showQrScanner) {
        QrScannerFullScreenOverlay(
            onDismiss = { showQrScanner = false },
            onQrCodeScanned = { scannedValue ->
                qrResult.value = scannedValue
                showQrScanner = false
            }
        )
    }
    // Show ClientSearchDialog in a bottom sheet
    if (showClientDialog && pendingLocation != null) {
        LaunchedEffect(showClientDialog) {
            viewModel.fetchClientsByUserId(SharedPrefsUtils.getUserIdFromPrefs(context), context)
        }
        ModalBottomSheet(
            onDismissRequest = {
                showClientDialog = false
                pendingLocation = null
                selectedClientForCheckIn = null
                checkInViewModel.clearUiState()
            },
            containerColor = Color.White
        ) {
            when {
                viewModel.loading -> {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                viewModel.error != null -> {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No Internet connection.", color = Color.Gray)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.fetchClientsByUserId(SharedPrefsUtils.getUserIdFromPrefs(context), context) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    ClientSearchDialog(
                        clients = viewModel.clients,
                        onCancel = {
                            showClientDialog = false
                            pendingLocation = null
                            selectedClientForCheckIn = null
                            checkInViewModel.clearUiState()
                        },
                        onSubmit = {
                            if (selectedClientForCheckIn != null && pendingLocation != null) {
                                checkInViewModel.onClientSelected(selectedClientForCheckIn!!, pendingLocation!!)
                                showClientDialog = false
                                pendingLocation = null
                                selectedClientForCheckIn = null
                                successMessage = "Check-in Success"
                                showSuccessSheet = true
                            }
                        },
                        onClientSelected = { client ->
                            selectedClientForCheckIn = client
                        }
                    )
                }
            }
        }
    }
    // Show CheckoutDialog as a bottom sheet
    if (showCheckoutDialog && pendingLocation != null) {
        var checkoutSubmitted by remember { mutableStateOf(false) }
        ModalBottomSheet(
            onDismissRequest = {
                showCheckoutDialog = false
                pendingLocation = null
                checkInViewModel.clearUiState()
                checkoutSubmitted = false
            },
            containerColor = Color.White
        ) {
            ClientCheckoutDialog(
                client = Client(
                    id = CheckInPrefs.getClientDbId(context), // or another source of the client DB id
                    name = status.clientName ?: "",
                    idno = status.clientId?.toLongOrNull() ?: 0L,
                    contact = status.clientContact ?: 0L,
                    loan_officer = status.clientLoanOfficer ?: ""
                ),
                onCancel = {
                    showCheckoutDialog = false
                    pendingLocation = null
                    checkInViewModel.clearUiState()
                    checkoutSubmitted = false
                },
                onSubmit = {
                    if (!checkoutSubmitted) {
                        checkInViewModel.onCheckoutSubmit(pendingLocation!!)
                        showCheckoutDialog = false
                        pendingLocation = null
                        checkoutSubmitted = true
                        successMessage = "Check-out Success"
                        showSuccessSheet = true
                    }
                }
            )
        }
    }
    // Show success bottom sheet
    if (showSuccessSheet) {
        LaunchedEffect(showSuccessSheet) {
            kotlinx.coroutines.delay(3000)
            showSuccessSheet = false
            successMessage = ""
            checkInViewModel.clearUiState()
        }
        ModalBottomSheet(
            onDismissRequest = {
                showSuccessSheet = false
                successMessage = ""
                checkInViewModel.clearUiState()
            },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = successMessage,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF003366),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
    // Show progress
    if (progressMessage.isNotEmpty()) {
        // Show a progress indicator with message
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text(progressMessage)
            }
        }
    }
    // Show error
    if (errorMessage.isNotEmpty()) {
        // Show error dialog or snackbar
        BottomSheetMessage(
            message = errorMessage,
            buttonText = null,
            onButtonClick = null,
            onDismiss = {
                errorMessage = ""
                checkInViewModel.clearUiState()
            }
        )
    }
    // Note: Permission sheet removed - MainActivity handles all permission monitoring
    // Show location failed bottom sheet
    if (showLocationFailedSheet) {
        BottomSheetMessage(
            message = "Failed to get your location.",
            buttonText = "Retry",
            onButtonClick = {
                showLocationFailedSheet = false
                checkInViewModel.clearUiState()
                checkInViewModel.retryLocation(!status.isCheckedIn)
            },
            onDismiss = {
                showLocationFailedSheet = false
                checkInViewModel.clearUiState()
            }
        )
    }
    if (showSystemWebView) {
        WebViewScreen(
            url = tasksUrl,
            isServiceRunning = false, // or provide actual state if available
            onStartService = {}, // provide actual implementation if needed
            onStopService = {}   // provide actual implementation if needed
        )
    }

    // Tasks Coming Soon Bottom Sheet
    if (showTasksComingSoonSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTasksComingSoonSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(0.dp))
                Text(
                    "Coming Soon!",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "The Tasks feature is currently under development and will be available soon.",
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(15.dp))
                Button(
                    onClick = { showTasksComingSoonSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Text("Got it!")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Attendance Status Bottom Sheet
    if (showAttendanceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttendanceSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {


                // Login status text
                val userName = SharedPrefsUtils.getUserNameFromPrefs(context)
                val formattedName = userName.split(" ")
                    .joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercaseChar() } }
                val currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))

                Text(
                    "You are logged in as",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Text(
                    formattedName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF003366),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "at $currentTime",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status indicator (assuming logged in successfully)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Active",
                        fontSize = 16.sp,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Logout button
                Button(
                    onClick = {
                        showAttendanceSheet = false
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                ) {
                    Icon(
                        Icons.Default.ExitToApp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Log Out", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Logout Confirmation Dialog
    if (showLogoutConfirmationDialog) {
        Dialog(
            onDismissRequest = { showLogoutConfirmationDialog = false }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.ExitToApp,
                        contentDescription = null,
                        tint = Color(0xFFFF5722),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Confirm Logout",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF003366)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Are you sure you want to logout? You will need to login again to access the app.",
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showLogoutConfirmationDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF757575))
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                showLogoutConfirmationDialog = false
                                onLogout()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                        ) {
                            Text("Logout")
                        }
                    }
                }
            }
        }
    }
}

data class ActivityItem(val title: String, val time: String, val status: String, val color: Color)

@Composable
fun ActivityRow(item: ActivityItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8F8F8))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(item.color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_media_play),
                contentDescription = null,
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.Bold, color = Color(0xFF003366))
            Text(item.time, fontSize = 12.sp, color = Color.Gray)
            Text(item.status, fontSize = 12.sp, color = Color(0xFF4CAF50))
        }
        Column(modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Top) {
            IconButton(onClick = { /* More options */ }) {
                Icon(
                    painter = painterResource(R.drawable.ic_horiz_24),
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
        }

    }
}

@Composable
fun DashboardCard(title: String, subtitle: String, icon: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFB3E5FC), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF003366))
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun DashboardCard(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFB3E5FC), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF003366))
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun QrScannerOverlay(
    boxSize: Dp = 220.dp,
    borderColor: Color = Color.White,
    borderWidth: Dp = 4.dp,
    overlayColor: Color = Color.Black.copy(alpha = 0.5f)
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val boxSide = boxSize.toPx()
            val left = (width - boxSide) / 2f
            val top = (height - boxSide) / 2f
            val right = left + boxSide
            val bottom = top + boxSide

            // Draw the darkened overlay
            drawRect(
                color = overlayColor,
                size = size
            )
            // Clear the center square (scan area)
            drawRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(boxSide, boxSide),
                blendMode = BlendMode.Clear
            )
            // Draw the border for the scan area
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(boxSide, boxSide),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = borderWidth.toPx())
            )
        }
    }
}

@Composable
fun CameraPermissionWrapper(
    content: @Composable (hasPermission: Boolean) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    content(hasPermission)
}

@Composable
fun QrScannerFullScreenOverlay(
    onDismiss: () -> Unit,
    onQrCodeScanned: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            color = Color.Black
        ) {
            Box(Modifier.fillMaxSize()) {
                CameraPermissionWrapper { hasPermission ->
                    if (hasPermission) {
                        QrCodeScanner(
                            modifier = Modifier.matchParentSize(),
                            onQrCodeScanned = {
                                onQrCodeScanned(it)
                                onDismiss()
                            }
                        )
                        QrScannerOverlay()
                    } else {
                        Text(
                            "Camera permission is required to scan QR codes.",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                // Close button (top right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
    

}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DashboardActivityCard(event: UserActivityEvent) {
    val (icon, bgColor) = when (event.event_type.lowercase()) {
        "login" -> Icons.Default.ExitToApp to Color(0xFF4CAF50)
        "logout" -> Icons.Default.ExitToApp to Color(0xFFFF4500)
        "checkin" -> Icons.Default.CheckCircle to Color(0xFF1976D2)
        "checkout" -> Icons.Default.Done to Color(0xFF085FAF)
        "waiting" -> Icons.Default.Info to Color(0xFFFFA000)
        else -> Icons.Default.Info to Color(0xFFFF9800)
    }
    val title = event.event_type.replaceFirstChar { it.uppercase() }
//    val time = try {
//        val dt = LocalDateTime.parse(event.event_time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
//        dt.format(DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a"))
//    } catch (e: Exception) {
//        event.event_time.substringAfter(" ")
//    }

    val time = getFriendlyTime(event.event_time)
//    Text(text = time)
    val subtitle = when (event.event_type.lowercase()) {
        "checkin", "checkout" -> event.client_name?.let { "Client: $it" } ?: ""
        "login" -> {
            val details = event.details?.lowercase() ?: ""
            // Extract status from comma-separated details
            val statusPart = details.split(",").find { it.trim().startsWith("status:") }
            val status = statusPart?.substringAfter("status:")?.trim() ?: "intime"
            when (status) {
                "intime" -> "Intime"
                "ontime" -> "Ontime"
                "late" -> "Late"
                else -> "Intime"
            }
        }
        "logout" -> if (event.details?.contains("auto", true) == true) "Autologged out" else "Logged out"
        "waiting" -> "Stayed in 1 place for long time"
        else -> ""
    }
    val subtitleColor = when (event.event_type.lowercase()) {
        "login" -> when (subtitle.lowercase()) {
            "intime" -> Color(0xFF4CAF50)
            "ontime" -> Color(0xFFFFA000)
            "late" -> Color(0xFFFF4500)
            else -> Color(0xFF4CAF50)
        }
        "logout" -> Color(0xFFFF4500)
        "waiting" -> Color(0xFFFFA000)
        else -> Color.Gray
    }
//    End of declarations
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)),
        elevation = CardDefaults.cardElevation(0.dp) // Removed elevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp) // Make icon background square: width = height = 56.dp
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF003366))
                Text(time, fontSize = 12.sp, color = Color.Gray)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, fontSize = 13.sp, color = subtitleColor)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Show more",
                tint = Color.Gray,
                modifier = Modifier.size(28.dp)
            )
        }


    }

}

 