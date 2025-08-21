package com.yubytech.tracked

//import com.yubytech.tracked.InternetStateReceiver
import DashboardScreen
import TimelineScreen
import TrackScreen
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.android.gms.location.LocationServices
import com.yubytech.tracked.local.ActivityEvent
import com.yubytech.tracked.local.ActivityEventSyncManager
import com.yubytech.tracked.local.AppDatabase
import com.yubytech.tracked.local.LocationEventSyncManager
import com.yubytech.tracked.ui.AttendanceScreen
import com.yubytech.tracked.ui.BottomSheetMessage
import com.yubytech.tracked.ui.OnboardingScreen
import com.yubytech.tracked.ui.PermissionUtils
import com.yubytech.tracked.ui.SharedPrefsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

//import com.yubytech.tracked.utils.PermissionUtils

data class LoginData(
    val status: String,
    val location: android.location.Location?,
    val timestamp: String
)

enum class OnboardingState {
    Idle, Verifying, Error, SettingUpDevice, SetupSuccess, Ready, RequestingPermissions, PermissionsGranted, PermissionsDenied, CheckingAttendance, AttendanceSuccess, AttendanceFailed
}

class OnboardingViewModel : ViewModel() {
    var sessionId by mutableStateOf<String?>(null)
    var jwtToken by mutableStateOf<String?>(null)
    var onboardingState by mutableStateOf(OnboardingState.Idle)
    var errorMessage by mutableStateOf<String?>(null)
    var userId by mutableStateOf<Int?>(null)
    var userName by mutableStateOf<String?>(null)
    var deviceInfo by mutableStateOf<String?>(null)

    fun updateFromDeepLink(sessionId: String?, jwtToken: String?) {
        this.sessionId = sessionId
        this.jwtToken = jwtToken
    }
}

class MainActivity : ComponentActivity() {
    private val onboardingViewModel: OnboardingViewModel by viewModels()
    private var gpsReceiver: GpsStateReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastInternetConnected: Boolean? = null
    
    // Logout geofence verification state
    private var showLogoutGeofenceSheet by mutableStateOf(false)
    private var logoutGeofenceMessage by mutableStateOf("")
    private var lastKnownLocation by mutableStateOf<android.location.Location?>(null)
    private var isLogoutGeofenceLoading by mutableStateOf(false)
    private var logoutRetryCount by mutableStateOf(0)
    
    // Permission sheet state
    private var showPermissionSheet by mutableStateOf(false)
    
    // Helper function to check if a service is running
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun proceedWithSetup(context: Context, uRes: Int, bRes: Int) {
        // Add delay to ensure all data is saved before attendance check
        GlobalScope.launch {
            delay(2000) // Wait 2 seconds for data to be saved
            
            // Debug: Check if data is available
            val bLat = SharedPrefsUtils.getJwtField(context, "b_lat")
            val bLng = SharedPrefsUtils.getJwtField(context, "b_lng")
            val offRad = SharedPrefsUtils.getJwtField(context, "off_rad")
            val uRes = SharedPrefsUtils.getJwtField(context, "u_res")
            val bRes = SharedPrefsUtils.getJwtField(context, "b_res")
            val jwt = SharedPrefsUtils.getJwtField(context, "jwt")
            
            withContext(Dispatchers.Main) {
//                Toast.makeText(context,
//                    "Debug: b_lat=$bLat, b_lng=$bLng, off_rad=$offRad, u_res=$uRes, b_res=$bRes, jwt=${if (jwt != null) "present" else "null"}",
//                    Toast.LENGTH_LONG
//                ).show()
                
                // Show attendance screen
                onboardingViewModel.onboardingState = OnboardingState.CheckingAttendance
            }
        }
    }
    
    private fun saveJwtToken(context: Context) {
        // Get the long token from the API response
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        
        // We need to get the long token from the API response
        // For now, we'll use the original token and set expiration
        val jwtToken = onboardingViewModel.jwtToken
        
        sharedPreferences.edit()
            .putString("jwt", jwtToken)
            .putLong("jwt_expires_at", System.currentTimeMillis() / 1000 + 86400) // 24 hours
            .putInt("user_id", onboardingViewModel.userId ?: -1)
            .putString("user_name", onboardingViewModel.userName ?: "John Doe")
            .apply()
        
        Log.d("MainActivity", "Saved JWT after successful attendance - user_id: ${onboardingViewModel.userId}, user_name: ${onboardingViewModel.userName}")
    }
    
    private fun verifyLogoutGeofence(isAutoLogout: Boolean = false, onSuccess: () -> Unit) {
        val geofence = Geofence(this)
        
        if (isAutoLogout) {
            // For auto-logout, use last known location if available
            lastKnownLocation?.let { location ->
                // Log the event with location coordinates
                val userId = SharedPrefsUtils.getUserIdFromPrefs(this).toIntOrNull() ?: -1
                val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                val db = AppDatabase.getDatabase(this)
                GlobalScope.launch {
                    val event = ActivityEvent(
                        user_id = userId,
                        event_type = "logout",
                        event_time = now,
                        lat = location.latitude,
                        lng = location.longitude,
                        details = "Auto-logout with location verification",
                        session_id = 0,
                        client_id = 0
                    )
                    val endpoint = "https://api.brisk-credit.net/endpoints/activity_logs.php"
                    val posted = ActivityEventSyncManager.isInternetAvailable(this@MainActivity) &&
                        ActivityEventSyncManager.postEventOnline(event, endpoint, this@MainActivity)
                    if (!posted) {
                        db.activityEventDao().insert(event)
                    }
                }
                onSuccess()
            } ?: run {
                // No last known location, proceed with logout anyway
                onSuccess()
            }
        } else {
            // For manual logout, check geofence
            isLogoutGeofenceLoading = true
            showLogoutGeofenceSheet = true
            
            val locationCallback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        if (location.accuracy <= 20) {
                            // Store last known location
                            lastKnownLocation = location
                            
                            // Stop location updates
                            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)
                            fusedLocationClient.removeLocationUpdates(this)
                            
                            // Check if within office radius
                            val officeLocation = geofence.getOfficeLocation()
                            val officeRadius = geofence.getOfficeRadius()
                            val branchRestriction = geofence.getBranchRestriction()
                            val userRestriction = geofence.getUserRestriction()
                            
                            if (officeLocation != null) {
                                val distance = geofence.haversineDistance(
                                    location.latitude,
                                    location.longitude,
                                    officeLocation.first,
                                    officeLocation.second
                                )
                                
                                val isWithinRadius = distance <= officeRadius
                                
                                when {
                                    branchRestriction && userRestriction -> {
                                        if (isWithinRadius) {
                                            // Success - allow logout with location
                                            isLogoutGeofenceLoading = false
                                            showLogoutGeofenceSheet = false
                                            logoutRetryCount = 0
                                            
                                            val userId = SharedPrefsUtils.getUserIdFromPrefs(this@MainActivity).toIntOrNull() ?: -1
                                            val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                                            val db = AppDatabase.getDatabase(this@MainActivity)
                                            GlobalScope.launch {
                                                val event = ActivityEvent(
                                                    user_id = userId,
                                                    event_type = "logout",
                                                    event_time = now,
                                                    lat = location.latitude,
                                                    lng = location.longitude,
                                                    details = "User logout from office",
                                                    session_id = 0,
                                                    client_id = 0
                                                )
                                                val endpoint = "https://api.brisk-credit.net/endpoints/activity_logs.php"
                                                val posted = ActivityEventSyncManager.isInternetAvailable(this@MainActivity) &&
                                                    ActivityEventSyncManager.postEventOnline(event, endpoint, this@MainActivity)
                                                if (!posted) {
                                                    db.activityEventDao().insert(event)
                                                }
                                            }
                                            onSuccess()
                                        } else {
                                            // Failed - show bottom sheet
                                            isLogoutGeofenceLoading = false
                                            logoutGeofenceMessage = "You are not in the office. Please move closer to your office location to logout."
                                            logoutRetryCount++
                                        }
                                    }
                                    else -> {
                                        // No restrictions - allow logout
                                        isLogoutGeofenceLoading = false
                                        showLogoutGeofenceSheet = false
                                        logoutRetryCount = 0
                                        
                                        val userId = SharedPrefsUtils.getUserIdFromPrefs(this@MainActivity).toIntOrNull() ?: -1
                                        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                                        val db = AppDatabase.getDatabase(this@MainActivity)
                                        GlobalScope.launch {
                                            val event = ActivityEvent(
                                                user_id = userId,
                                                event_type = "logout",
                                                event_time = now,
                                                lat = location.latitude,
                                                lng = location.longitude,
                                                details = "Manual logout (no location restriction)",
                                                session_id = 0,
                                                client_id = 0
                                            )
                                            val endpoint = "https://api.brisk-credit.net/endpoints/activity_logs.php"
                                            val posted = ActivityEventSyncManager.isInternetAvailable(this@MainActivity) &&
                                                ActivityEventSyncManager.postEventOnline(event, endpoint, this@MainActivity)
                                            if (!posted) {
                                                db.activityEventDao().insert(event)
                                            }
                                        }
                                        onSuccess()
                                    }
                                }
                            } else {
                                // No office location configured - allow logout
                                isLogoutGeofenceLoading = false
                                showLogoutGeofenceSheet = false
                                logoutRetryCount = 0
                                onSuccess()
                            }
                        }
                    }
                }
            }
            
            val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000
            ).setMinUpdateDistanceMeters(1f).setMinUpdateIntervalMillis(500).build()
            
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                // Permission denied - allow logout anyway
                isLogoutGeofenceLoading = false
                showLogoutGeofenceSheet = false
                logoutRetryCount = 0
                onSuccess()
            }
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        installSplashScreen()
        setContent {
            // Check for saved JWT and user ID
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val sharedPreferences = EncryptedSharedPreferences.create(
                "secure_prefs",
                masterKeyAlias,
                this,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val savedJwt = sharedPreferences.getString("jwt", null)
            val savedUserId = sharedPreferences.getInt("user_id", -1)
            val savedJwtExpiresAt = sharedPreferences.getLong("jwt_expires_at", 0L)
            val isTokenValid = !savedJwt.isNullOrEmpty() && savedUserId != -1 && (savedJwtExpiresAt == 0L || savedJwtExpiresAt > System.currentTimeMillis() / 1000)

            var showOnboardingState by remember { mutableStateOf(!isTokenValid) }
            val context = this@MainActivity
            var loginData by remember { mutableStateOf<LoginData?>(null) }
            
            
            // Navigate to dashboard when onboarding is complete and start location logging
            LaunchedEffect(onboardingViewModel.onboardingState) {
                if (onboardingViewModel.onboardingState == OnboardingState.Ready) {
                    showOnboardingState = false
                    // Check for location permissions
                    val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val hasForegroundService = ContextCompat.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED
                    if (hasFineLocation && hasForegroundService) {
                        val serviceIntent = Intent(context, LocationLoggingService::class.java)
                        // Check if service is already running before starting it
                        if (!isServiceRunning(context, LocationLoggingService::class.java)) {
                            ContextCompat.startForegroundService(context, serviceIntent)
                        }
                    } else {
                        // showPermissionSheet = true
                    }
                }
            }

            // Continuous permission monitoring
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1000) // Check every second
                    
                    // Only monitor if user is logged in (not in onboarding)
                    if (!showOnboardingState) {
                        val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasForegroundService = ContextCompat.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED
                        
                        if (hasFineLocation && hasForegroundService) {
                            // Permissions granted - hide sheet and start service if not already running
                            showPermissionSheet = false
                            val serviceIntent = Intent(context, LocationLoggingService::class.java)
                            // Check if service is already running before starting it
                            if (!isServiceRunning(context, LocationLoggingService::class.java)) {
                                ContextCompat.startForegroundService(context, serviceIntent)
                            }
                        } else {
                            // Permissions missing - show sheet and stop service
                            showPermissionSheet = true
                            val serviceIntent = Intent(context, LocationLoggingService::class.java)
                            context.stopService(serviceIntent)
                        }
                    }
                }
            }

            // Handle deep link on launch
            LaunchedEffect(Unit) {
                intent?.data?.let { uri ->
                    if (uri.scheme == "tracked" && uri.host == "auth" && uri.path == "/login") {
                        onboardingViewModel.updateFromDeepLink(
                            uri.getQueryParameter("session_id"),
                            uri.getQueryParameter("token")
                        )
                    }
                }
            }

            // Handle onboarding logic
            LaunchedEffect(onboardingViewModel.jwtToken) {
                onboardingViewModel.jwtToken?.let { token ->
                    onboardingViewModel.onboardingState = OnboardingState.Verifying
                    val url = "https://api.brisk-credit.net/endpoints/verify_jwt.php?token=$token"
                    try {
                        val response = withContext(Dispatchers.IO) {
                            java.net.URL(url).readText()
                        }
                        val json = JSONObject(response)
                        if (json.optBoolean("success")) {
                            onboardingViewModel.userId = json.optInt("user_id")
                            // Store user name for later use when saving to shared preferences
                            val userName = json.optString("user_name", "")
                            Log.d("MainActivity", "JWT Response - user_id: ${json.optInt("user_id")}, user_name: $userName")
                            // Store user name in ViewModel for later use
                            onboardingViewModel.userName = userName
                            
                            // Parse all JWT response fields
                            val userId = json.optString("user_id")
                            val setup = json.optInt("setup")
                            val uRes = json.optInt("u_res")
                            val bRes = json.optInt("b_res")
                            val bId = json.optInt("b_id")
                            val bLat = json.optDouble("b_lat")
                            val bLng = json.optDouble("b_lng")
                            val offRad = json.optInt("off_rad")
                            val fromTime = json.optString("from_time")
                            val toTime = json.optString("to_time")

                            // Save all JWT fields to SharedPrefs (including temporary JWT token)
                            SharedPrefsUtils.saveJwtResponseToPrefs(
                                context,
                                userId,
                                userName,
                                setup,
                                uRes,
                                bRes,
                                bId,
                                bLat,
                                bLng,
                                offRad,
                                fromTime,
                                toTime
                            )

                            // Check permissions before proceeding

                            val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

                            if (!hasCameraPermission) {
                                onboardingViewModel.onboardingState = OnboardingState.RequestingPermissions
                            } else {
                                // Continue with device setup (don't call proceedWithSetup yet)
                            }
                            
                            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                            val model = Build.MODEL
                            val manufacturer = Build.MANUFACTURER
                            if (json.optInt("setup") == 0) {
                                onboardingViewModel.onboardingState = OnboardingState.SettingUpDevice
                                onboardingViewModel.deviceInfo = "ID: $androidId, Model: $manufacturer $model"
                                // Send device info to endpoint
                                val storeDeviceInfoUrl = "https://api.brisk-credit.net/endpoints/store_device_info.php"
                                val postData = "token=${URLEncoder.encode(token, "UTF-8")}" +
                                        "&device_android_id=${URLEncoder.encode(androidId, "UTF-8")}" +
                                        "&device_model=${URLEncoder.encode(model, "UTF-8")}" +
                                        "&device_manufacturer=${URLEncoder.encode(manufacturer, "UTF-8")}"
                                
                                // Log device info being sent to store_device_info endpoint
//                                Log.d("MainActivity", "Sending device info to store_device_info endpoint:")
//
                                val storeResponse = withContext(Dispatchers.IO) {
                                    val conn = java.net.URL(storeDeviceInfoUrl).openConnection() as java.net.HttpURLConnection
                                    conn.requestMethod = "POST"
                                    conn.doOutput = true
                                    conn.outputStream.use { it.write(postData.toByteArray()) }
                                    conn.inputStream.bufferedReader().readText()
                                }
                                val storeJson = JSONObject(storeResponse)
                                if (storeJson.optBoolean("success")) {
                                    // Now bind device to get long-lived token
                                    val bindDeviceUrl = "https://api.brisk-credit.net/endpoints/bind_device.php"
                                    val bindPostData = "token=${URLEncoder.encode(token, "UTF-8")}" +
                                            "&device_android_id=${URLEncoder.encode(androidId, "UTF-8")}" +
                                            "&device_model=${URLEncoder.encode(model, "UTF-8")}" +
                                            "&device_manufacturer=${URLEncoder.encode(manufacturer, "UTF-8")}"

                                    val bindResponse = withContext(Dispatchers.IO) {
                                        val conn = java.net.URL(bindDeviceUrl).openConnection() as java.net.HttpURLConnection
                                        conn.requestMethod = "POST"
                                        conn.doOutput = true
                                        conn.outputStream.use { it.write(bindPostData.toByteArray()) }
                                        conn.inputStream.bufferedReader().readText()
                                    }
                                    val bindJson = JSONObject(bindResponse)
                                    if (bindJson.optBoolean("success")) {
                                        val longToken = bindJson.optString("long_token")
                                        val expiresAt = bindJson.optLong("expires_at")
                                        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                                        val sharedPreferences = EncryptedSharedPreferences.create(
                                            "secure_prefs",
                                            masterKeyAlias,
                                            context,
                                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                                        )
                                        // Device setup complete - now proceed to attendance check
//                                        Log.d("MainActivity", "Device setup complete - proceeding to attendance check")
                                        proceedWithSetup(context, uRes, bRes)
                                    } else {
                                        onboardingViewModel.onboardingState = OnboardingState.Error
                                        onboardingViewModel.errorMessage = bindJson.optString("error", "Device binding failed")
//                                        Toast.makeText(context, onboardingViewModel.errorMessage, Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    onboardingViewModel.onboardingState = OnboardingState.Error
                                    onboardingViewModel.errorMessage = storeJson.optString("error", "Device setup failed")
//                                    Toast.makeText(context, onboardingViewModel.errorMessage, Toast.LENGTH_LONG).show()
                                }
                            } else {
                                // Setup already done, just bind device to get long-lived token
                                val bindDeviceUrl = "https://api.brisk-credit.net/endpoints/bind_device.php"
                                val bindPostData = "token=${URLEncoder.encode(token, "UTF-8")}" +
                                        "&device_android_id=${URLEncoder.encode(androidId, "UTF-8")}" +
                                        "&device_model=${URLEncoder.encode(model, "UTF-8")}" +
                                        "&device_manufacturer=${URLEncoder.encode(manufacturer, "UTF-8")}" 
                                
                                // Log device info being sent to bind_device endpoint (setup already done)
//
                                val bindResponse = withContext(Dispatchers.IO) {
                                    val conn = java.net.URL(bindDeviceUrl).openConnection() as java.net.HttpURLConnection
                                    conn.requestMethod = "POST"
                                    conn.doOutput = true
                                    conn.outputStream.use { it.write(bindPostData.toByteArray()) }
                                    conn.inputStream.bufferedReader().readText()
                                }
                                val bindJson = JSONObject(bindResponse)
                                if (bindJson.optBoolean("success")) {
                                    val longToken = bindJson.optString("long_token")
                                    val expiresAt = bindJson.optLong("expires_at")
                                    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                                    val sharedPreferences = EncryptedSharedPreferences.create(
                                        "secure_prefs",
                                        masterKeyAlias,
                                        context,
                                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                                    )
                                    // JWT will be saved after successful attendance check
//                                    Log.d("MainActivity", "Device binding complete - proceeding to attendance check")
                                    proceedWithSetup(context, uRes, bRes)
                                } else {
                                    onboardingViewModel.onboardingState = OnboardingState.Error
                                    onboardingViewModel.errorMessage = bindJson.optString("error", "Device binding failed")
//                                    Toast.makeText(context, onboardingViewModel.errorMessage, Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            onboardingViewModel.onboardingState = OnboardingState.Error
                            onboardingViewModel.errorMessage = json.optString("error", "Unknown error")
                        }
                    } catch (e: Exception) {
                        onboardingViewModel.onboardingState = OnboardingState.Error
                        onboardingViewModel.errorMessage = e.localizedMessage ?: "Network error"
                    }
                }
            }

            // Auto-logout monitoring
            LaunchedEffect(Unit) {
                while (true) {
                    delay(30000) // Check every 30 seconds
                    
                    // Only check if user is logged in (not showing onboarding)
                    if (!showOnboardingState) {
                        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                        val sharedPreferences = EncryptedSharedPreferences.create(
                            "secure_prefs",
                            masterKeyAlias,
                            this@MainActivity,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                        )
                        
                        val jwtToken = sharedPreferences.getString("jwt", null)
                        val jwtExpiresAt = sharedPreferences.getLong("jwt_expires_at", 0L)
                        val toTime = sharedPreferences.getString("to_time", null)
                        val userId = sharedPreferences.getInt("user_id", -1)
                        
                        val currentTime = System.currentTimeMillis() / 1000
                        
                        // Check JWT expiration
                        val isJwtExpired = jwtToken == null || jwtExpiresAt == 0L || currentTime > jwtExpiresAt
                        
                        // Check office time
                        val isOfficeTimeExpired = toTime?.let { timeStr ->
                            try {
                                val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                                val officeTime = timeFormat.parse(timeStr)
                                val currentTimeObj = java.util.Date()
                                val currentTimeStr = timeFormat.format(currentTimeObj)
                                val currentTimeParsed = timeFormat.parse(currentTimeStr)
                                
                                // Compare current time with office end time
                                currentTimeParsed.after(officeTime)
                            } catch (e: Exception) {
                                false
                            }
                        } ?: false
                        
                        // Auto-logout if conditions are met
                        if (isJwtExpired || isOfficeTimeExpired) {
                            // Verify geofence for auto-logout
                            verifyLogoutGeofence(isAutoLogout = true) {
                                // Stop location logging service
                                val locationServiceIntent = Intent(this@MainActivity, LocationLoggingService::class.java)
                                stopService(locationServiceIntent)
                                
                                // Clear shared preferences
                                sharedPreferences.edit().clear().apply()
                                
                                // Show onboarding screen
                                showOnboardingState = true
                                onboardingViewModel.onboardingState = OnboardingState.Idle
                                
                                // Show toast notification
                                Toast.makeText(this@MainActivity,
                                    if (isJwtExpired) "Session expired. Please login again."
                                    else "Office time ended. You have been logged out.",
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }

            if (showOnboardingState) {
                OnboardingScreen(
                    onLoginClick = { showOnboardingState = false },
                    sessionId = onboardingViewModel.sessionId,
                    jwtToken = onboardingViewModel.jwtToken,
                    onboardingState = onboardingViewModel.onboardingState,
                    errorMessage = onboardingViewModel.errorMessage,
                    deviceInfo = onboardingViewModel.deviceInfo,
                    onDeepLinkScanned = { sessionId, token ->
                        onboardingViewModel.updateFromDeepLink(sessionId, token)
                    },
                    onPermissionsRequested = { granted ->
                        if (granted) {
                            onboardingViewModel.onboardingState = OnboardingState.PermissionsGranted
                            // After a short delay, proceed with setup
                            GlobalScope.launch {
                                delay(2000)
                                val uRes = SharedPrefsUtils.getJwtField(context, "u_res")?.toIntOrNull() ?: 0
                                val bRes = SharedPrefsUtils.getJwtField(context, "b_res")?.toIntOrNull() ?: 0
                                proceedWithSetup(context, uRes, bRes)
                            }
                        } else {
                            onboardingViewModel.onboardingState = OnboardingState.PermissionsDenied
                        }
                    },
                    onRestartApp = {
                        // Clear shared preferences (same as logout)
                        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                        val sharedPreferences = EncryptedSharedPreferences.create(
                            "secure_prefs",
                            masterKeyAlias,
                            this@MainActivity,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                        )
                        sharedPreferences.edit().clear().apply()
                        
                        // Show onboarding screen (fresh start)
                        showOnboardingState = true
                        onboardingViewModel.onboardingState = OnboardingState.Idle
                    },
                    onAttendanceSuccess = { loginDataFromAttendance ->
                        // Store login data for later posting
                        loginData = loginDataFromAttendance
                        
                        // Get the long-lived token from the API response and save it
                        GlobalScope.launch {
                            try {
                                val token = onboardingViewModel.jwtToken
                                val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                                val model = Build.MODEL
                                val manufacturer = Build.MANUFACTURER
                                
                                // Bind device to get long-lived token
                                val bindDeviceUrl = "https://api.brisk-credit.net/endpoints/bind_device.php"
                                val bindPostData = "token=${URLEncoder.encode(token, "UTF-8")}" +
                                        "&device_android_id=${URLEncoder.encode(androidId, "UTF-8")}" +
                                        "&device_model=${URLEncoder.encode(model, "UTF-8")}" +
                                        "&device_manufacturer=${URLEncoder.encode(manufacturer, "UTF-8")}"

                                val bindResponse = withContext(Dispatchers.IO) {
                                    val conn = java.net.URL(bindDeviceUrl).openConnection() as java.net.HttpURLConnection
                                    conn.requestMethod = "POST"
                                    conn.doOutput = true
                                    conn.outputStream.use { it.write(bindPostData.toByteArray()) }
                                    conn.inputStream.bufferedReader().readText()
                                }
                                val bindJson = JSONObject(bindResponse)
                                if (bindJson.optBoolean("success")) {
                                    val longToken = bindJson.optString("long_token")
                                    val expiresAt = bindJson.optLong("expires_at")
                                    
                                    // Save the long-lived token
                                    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                                    val sharedPreferences = EncryptedSharedPreferences.create(
                                        "secure_prefs",
                                        masterKeyAlias,
                                        this@MainActivity,
                                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                                    )
                                    sharedPreferences.edit()
                                        .putString("jwt", longToken)
                                        .putLong("jwt_expires_at", expiresAt)
                                        .apply()
                                    
                                    // Now post the login event with the long-lived JWT token
                                    loginData?.let { data ->
                                        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                                        val userId = SharedPrefsUtils.getUserIdFromPrefs(this@MainActivity).toIntOrNull() ?: -1
                                        
                                        val event = ActivityEvent(
                                            user_id = userId,
                                            event_type = "login",
                                            event_time = now,
                                            lat = data.location?.latitude,
                                            lng = data.location?.longitude,
                                            details = "User logged in successfully with attendance verification, status: ${data.status}",
                                            session_id = 0,
                                            client_id = 0
                                        )
                                        
                                        val endpoint = "https://api.brisk-credit.net/endpoints/activity_logs.php"
                                        val posted = ActivityEventSyncManager.isInternetAvailable(this@MainActivity) &&
                                            ActivityEventSyncManager.postEventOnline(event, endpoint, this@MainActivity)
                                        if (!posted) {
                                            val db = AppDatabase.getDatabase(this@MainActivity)
                                            db.activityEventDao().insert(event)
                                        }
                                    }
                                    
                                    onboardingViewModel.onboardingState = OnboardingState.Ready
                                } else {
                                    // If binding fails, still proceed with the temporary token
                                    onboardingViewModel.onboardingState = OnboardingState.Ready
                                }
                            } catch (e: Exception) {
                                // If there's an error, still proceed
                                onboardingViewModel.onboardingState = OnboardingState.Ready
                            }
                        }
                    }
                )
            } else {
                    val navController = rememberNavController()
                    Scaffold { innerPadding ->
                        NavHost(navController = navController, startDestination = "dashboard") {
                            composable("dashboard") {
                                DashboardScreen(
                                    onActivityClick = { navController.navigate("activity") },
                                    onTrackingClick = { navController.navigate("tracking") },
                                    onAttendanceClick = { navController.navigate("attendance") },
                                    onTimelineClick = { navController.navigate("activity") },
                                    onLogout = {
                                        // Reset retry count for new logout attempt
                                        logoutRetryCount = 0
                                        // Verify geofence before logout
                                        verifyLogoutGeofence(isAutoLogout = false) {
                                            // Stop location logging service
                                            val locationServiceIntent = Intent(this@MainActivity, LocationLoggingService::class.java)
                                            stopService(locationServiceIntent)
                                            
                                            // Get user ID from shared preferences before clearing
                                            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                                            val sharedPreferences = EncryptedSharedPreferences.create(
                                                "secure_prefs",
                                                masterKeyAlias,
                                                this@MainActivity,
                                                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                                            )
                                            
                                            // Clear shared preferences after logging the event
                                            sharedPreferences.edit().clear().apply()
                                            
                                            // Show onboarding screen
                                            showOnboardingState = true
                                            onboardingViewModel.onboardingState = OnboardingState.Idle
                                        }
                                    }
                                )
                            }
                            composable("activity") {
                                BackHandler {
                                    if (navController.currentBackStackEntry?.destination?.route != "dashboard") {
                                        navController.popBackStack()
                                    }
                                }
                                TimelineScreen(
                                    userName = "John Doe(You)",
                                    onBackClick = { 
                                        if (navController.currentBackStackEntry?.destination?.route != "dashboard") {
                                            navController.popBackStack()
                                        }
                                    }
                                )
                            }
                            composable("tracking") {
                                BackHandler {
                                    if (navController.currentBackStackEntry?.destination?.route != "dashboard") {
                                        navController.popBackStack()
                                    }
                                }
                                TrackScreen(
                                    userName = "John Doe(You)",
                                    onBackClick = { 
                                        if (navController.currentBackStackEntry?.destination?.route != "dashboard") {
                                            navController.popBackStack()
                                        }
                                    }
                                )
                            }
                            composable("attendance") {
                                AttendanceScreen(
                                    onBackClick = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }

            // Show permission bottom sheet for background location tracking (MainActivity)
            if (showPermissionSheet) {
                BottomSheetMessage(
                    message = "This app requires 'Allow all the time' location permission for background location tracking.",
                    buttonText = "Open Settings",
                    onButtonClick = {
                        PermissionUtils.openAppLocationSettings(context as Activity)
                        // Do not dismiss the sheet here - let monitoring handle it
                    },
                    onDismiss = {
                        // Do not allow dismissing the sheet - only close when permission is granted
                        // showPermissionSheet = false
                    }
                )
            }
            
            // Show logout geofence bottom sheet (loading or failure)
            if (showLogoutGeofenceSheet) {
                androidx.compose.material3.ModalBottomSheet(
                    onDismissRequest = { 
                        if (!isLogoutGeofenceLoading) {
                            showLogoutGeofenceSheet = false
                            logoutRetryCount = 0
                        }
                    },
                    sheetState = androidx.compose.material3.rememberModalBottomSheetState(),
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 5.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        if (isLogoutGeofenceLoading) {
                            // Loading state
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = androidx.compose.ui.graphics.Color(0xFF1976D2)
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.Text(
                                "Logging you out...",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                color = androidx.compose.ui.graphics.Color(0xFF1976D2)
                            )
                        } else {
                            // Failure state
                            androidx.compose.material3.Text(
                                "Failed",
                                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color(0xFFD32F2F)
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.material3.Text(
                                logoutGeofenceMessage,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = androidx.compose.ui.graphics.Color.Gray,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))
                            androidx.compose.material3.Button(
                                onClick = {
                                    // Retry geofence verification
                                    verifyLogoutGeofence(isAutoLogout = false) {
                                        // Get user ID from shared preferences before clearing
                                        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                                        val sharedPreferences = EncryptedSharedPreferences.create(
                                            "secure_prefs",
                                            masterKeyAlias,
                                            this@MainActivity,
                                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                                        )
                                        
                                        // Clear shared preferences after logging the event
                                        sharedPreferences.edit().clear().apply()
                                        
                                        // Show onboarding screen
                                        showOnboardingState = true
                                        onboardingViewModel.onboardingState = OnboardingState.Idle
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 5.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.ui.graphics.Color(0xFF1976D2)
                                )
                            ) {
                                androidx.compose.material3.Text("Retry")
                            }
                        }
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            // Persistent GPS dialog
            var showLocationRequiredSheet by remember { mutableStateOf(false) }
            val localContext = LocalContext.current
            // Persistent GPS dialog logic
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(1000)
                    val isGpsEnabled = com.yubytech.tracked.ui.PermissionUtils.isLocationEnabled(localContext)
                    showLocationRequiredSheet = !isGpsEnabled
                }
            }
            if (showLocationRequiredSheet) {
                com.yubytech.tracked.ui.BottomSheetMessage(
                    message = "This app requires location to be enabled. Please turn on location services.",
                    buttonText = "Open Location Settings",
                    onButtonClick = {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        localContext.startActivity(intent)
                    },
                    onDismiss = {}, // Prevent manual dismiss
                    title = "GPS Required",
                    icon = androidx.compose.material.icons.Icons.Default.LocationOn,
                    iconTint = androidx.compose.ui.graphics.Color(0xFF888888), // Grayish color
                    preventDismiss = true
                )
            }
        }
        // Register NetworkCallback for internet state
        setupNetworkCallback()

        // Register GpsStateReceiver dynamically
        gpsReceiver = GpsStateReceiver()
        val gpsFilter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        registerReceiver(gpsReceiver, gpsFilter)
//        Log.d("MainActivity", "GpsStateReceiver registered")
    }

    private fun setupNetworkCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val builder = NetworkRequest.Builder()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                if (lastInternetConnected != true) {
                    lastInternetConnected = true
//                    Log.d("MainActivity", "Internet connection restored")
                    
                    // Sync unsent location events when internet becomes available
                    val locationServiceIntent = Intent(this@MainActivity, LocationLoggingService::class.java)
                    if (isServiceRunning(this@MainActivity, LocationLoggingService::class.java)) {
                        // If service is running, trigger sync
                        GlobalScope.launch {
                            LocationEventSyncManager.syncUnsentLocationEvents(
                                context = this@MainActivity,
                                endpoint = "https://api.brisk-credit.net/Tracked_v1/log_and_snap.php",
                                onResult = { success ->
//                                    Log.d("MainActivity", "Location sync result: $success")
                                }
                            )
                        }
                    }
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                lastInternetConnected = false
                Log.d("MainActivity", "Internet connection lost")
            }
        }
        cm.registerNetworkCallback(builder.build(), networkCallback!!)
    }

    override fun onResume() {
        super.onResume()
        // Re-calculate onboarding state on resume
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val savedJwt = sharedPreferences.getString("jwt", null)
        val savedUserId = sharedPreferences.getInt("user_id", -1)
        val savedJwtExpiresAt = sharedPreferences.getLong("jwt_expires_at", 0L)
        val isTokenValid = !savedJwt.isNullOrEmpty() && savedUserId != -1 && (savedJwtExpiresAt == 0L || savedJwtExpiresAt > System.currentTimeMillis() / 1000)
        if (isTokenValid) {
            onboardingViewModel.onboardingState = OnboardingState.Ready
            // Check permissions on resume and show sheet if needed
            val hasFineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasForegroundService = ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED
            if (hasFineLocation && hasForegroundService) {
                val serviceIntent = Intent(this, LocationLoggingService::class.java)
                // Check if service is already running before starting it
                if (!isServiceRunning(this, LocationLoggingService::class.java)) {
                    ContextCompat.startForegroundService(this, serviceIntent)
                }
            } else {
                // Show permission sheet if permissions are missing
                showPermissionSheet = true
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            if (uri.scheme == "tracked" && uri.host == "auth" && uri.path == "/login") {
                onboardingViewModel.updateFromDeepLink(
                    uri.getQueryParameter("session_id"),
                    uri.getQueryParameter("token")
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gpsReceiver?.let { unregisterReceiver(it) }
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback?.let { cm.unregisterNetworkCallback(it) }
    }
}
