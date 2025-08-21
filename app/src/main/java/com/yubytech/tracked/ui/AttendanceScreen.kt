package com.yubytech.tracked.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yubytech.tracked.Geofence
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class AttendanceState {
    object Idle : AttendanceState()
    object CheckingIn : AttendanceState()
    data class Success(val message: String) : AttendanceState()
    data class Failed(val reason: String) : AttendanceState()
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AttendanceScreen(
    onBackClick: () -> Unit,
    onSuccess: ((loginData: com.yubytech.tracked.LoginData) -> Unit)? = null
) {
    val context = LocalContext.current
    var attendanceState by remember { mutableStateOf<AttendanceState>(AttendanceState.CheckingIn) }
    var showPermissionSheet by remember { mutableStateOf(false) }
    var showGpsSheet by remember { mutableStateOf(false) }
    var initialCheckDone by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    
            @RequiresApi(Build.VERSION_CODES.O)
        fun performAttendanceCheck() {
            attendanceState = AttendanceState.CheckingIn
        
        coroutineScope.launch {
            
            
            val geofence = Geofence(context)
            var capturedLocation: android.location.Location? = null
            
            geofence.startLocationCheckForLogin(
                onResult = { isWithinRadius ->
                    if (isWithinRadius) {
                        // Success - check time status
                        val fromTime = SharedPrefsUtils.getJwtField(context, "from_time") ?: "08:00:00"
                        val now = java.time.LocalTime.now()
                        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
                        val from = try {
                            java.time.LocalTime.parse(fromTime, formatter)
                        } catch (e: Exception) {
                            java.time.LocalTime.of(8, 0) // Default to 8:00 AM
                        }
                        
                        val status = when {
                            now.isBefore(from) -> "intime"
                            now == from -> "ontime"
                            now.isAfter(from) -> "late"
                            else -> "intime"
                        }
                        
                        val message = when (status) {
                            "intime" -> "You're early!"
                            "ontime" -> "Perfect timing!"
                            "late" -> "You're late"
                            else -> "Login successful."
                        }
                        
                                                     attendanceState = AttendanceState.Success(message)
                        
                        // Log login event with location coordinates after successful attendance
                        coroutineScope.launch {
                            // Calculate status again for the event logging
                            val fromTime = SharedPrefsUtils.getJwtField(context, "from_time") ?: "08:00:00"
                            val currentTime = java.time.LocalTime.now()
                            val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
                            val from = try {
                                java.time.LocalTime.parse(fromTime, formatter)
                            } catch (e: Exception) {
                                java.time.LocalTime.of(8, 0) // Default to 8:00 AM
                            }
                            
                            val loginStatus = when {
                                currentTime.isBefore(from) -> "intime"
                                currentTime == from -> "ontime"
                                currentTime.isAfter(from) -> "late"
                                else -> "intime"
                            }
                            
                            // Store login data for later posting in MainActivity
                            // The login event will be posted after long-lived JWT token is obtained
                            
                            // Add 5-second delay before calling onSuccess
                            delay(2000) // 5 seconds delay
                            onSuccess?.invoke(com.yubytech.tracked.LoginData(loginStatus, capturedLocation, currentTime.format(formatter)))
                        }
                                             } else {
                             // Failed
                             val reason = "You are not within the office radius. Please move closer to your office location."
                             attendanceState = AttendanceState.Failed(reason)
                         }
                },
                onLocationReceived = { location ->
                    // Capture the location for use in login event
                    capturedLocation = location
                }
            )
        }
    }
    
    fun retryAttendanceCheck() {
        attendanceState = AttendanceState.CheckingIn
        performAttendanceCheck()
    }
    
    // Check GPS first, then permissions before starting attendance
    LaunchedEffect(Unit) {
                    if (!initialCheckDone) {
                initialCheckDone = true
                val isGpsEnabled = PermissionUtils.isLocationEnabled(context)
                
                if (!isGpsEnabled) {
                    showGpsSheet = true
                    attendanceState = AttendanceState.Idle
                } else {
                    // GPS is enabled, now check permission
                    val hasLocationPermission = PermissionUtils.hasFineLocation(context)
                    if (!hasLocationPermission) {
                        showPermissionSheet = true
                        attendanceState = AttendanceState.Idle
                    } else {
                        // Both GPS and permission are granted
                        performAttendanceCheck()
                    }
                }
            }
    }
    
    // Monitor GPS first, then permission changes - wait for each to be completed
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000) // Check every second
            
            // Wait for GPS to be enabled
            if (showGpsSheet) {
                val isGpsEnabled = PermissionUtils.isLocationEnabled(context)
                if (isGpsEnabled) {
                    showGpsSheet = false
                    // Always show permission sheet after GPS is enabled, regardless of current permission status
                    showPermissionSheet = true
                }
            }
            
            // Wait for permission to be granted
            if (showPermissionSheet) {
                val hasLocationPermission = PermissionUtils.hasFineLocation(context)
                if (hasLocationPermission) {
                    showPermissionSheet = false
                    performAttendanceCheck()
                }
            }
            
            delay(1000)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FB))
            .padding(24.dp)
    ) {
        val currentState = attendanceState
        when (currentState) {
            is AttendanceState.Idle -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showGpsSheet) {
                        // GPS Request UI
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF666666),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "GPS Required",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF666666)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Please enable GPS/Location services to continue with attendance check.",
                            textAlign = TextAlign.Center,
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                        ) {
                            Text("Open Settings")
                        }
                    } else if (showPermissionSheet) {
                        // Permission Request UI
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF666666),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Location Permission Required",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF666666)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "This app requires location permission for attendance check. Please enable it in settings.",
                            textAlign = TextAlign.Center,
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                PermissionUtils.openAppLocationSettings(context as android.app.Activity)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                        ) {
                            Text("Open Settings")
                        }
                    } else {
                        // Default idle state
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF666666),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Setting up location...",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF666666)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Please grant location permission and enable GPS",
                            textAlign = TextAlign.Center,
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            is AttendanceState.CheckingIn -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF1976D2)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Checking your location...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF1976D2)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Please wait while we verify your location",
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            is AttendanceState.Success -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Login Successful!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "You have logged in successfully.",
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Status with color
                    val statusColor = when {
                        currentState.message.contains("early") -> Color(0xFF4CAF50) // Green
                        currentState.message.contains("Perfect") -> Color(0xFF2196F3) // Blue
                        currentState.message.contains("late") -> Color(0xFFFF9800) // Orange
                        else -> Color(0xFF4CAF50) // Default green
                    }
                    
                    Text(
                        currentState.message,
                        textAlign = TextAlign.Center,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Current time in 12-hour format
                    val currentTime = java.time.LocalTime.now()
                    val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a")
                    val formattedTime = currentTime.format(timeFormatter)
                    
                    Text(
                        formattedTime,
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            is AttendanceState.Failed -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Login Failed",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD32F2F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        currentState.reason,
                        modifier = Modifier.padding(horizontal = 30.dp),
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { retryAttendanceCheck() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Try Again")
                        }
                    }
                }
            }

            AttendanceState.Idle -> TODO()
        }
    }
} 