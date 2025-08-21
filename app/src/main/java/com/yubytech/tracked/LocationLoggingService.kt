package com.yubytech.tracked

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yubytech.tracked.local.ActivityEvent
import com.yubytech.tracked.local.ActivityEventSyncManager
import com.yubytech.tracked.local.AppDatabase
import com.yubytech.tracked.local.LocationEvent
import com.yubytech.tracked.local.LocationEventSyncManager
import com.yubytech.tracked.ui.SharedPrefsUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class LocationLoggingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private val client = OkHttpClient()
    private val executor = Executors.newSingleThreadExecutor()
    private val endpointUrl = "https://api.brisk-credit.net/Tracked_v1/log_and_snap.php" // TODO: Replace with your actual endpoint
    private val kalman = KalmanLatLng()
    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var lastTimestamp: Long? = null
    private var lastPoint: JSONObject? = null
    private var lastLocation: android.location.Location? = null
    private var lastMovedTime: Long = System.currentTimeMillis()
    private val WAITING_THRESHOLD_METERS = 20.0
    private val WAITING_THRESHOLD_MILLIS = 5 * 60 * 1000L // 5 minutes
    private var waitingEventPosted = false

    override fun onCreate() {
        super.onCreate()
        Log.i("LocationLoggingService", "Service created")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(5000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location: Location in result.locations) {
                    processLocation(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("LocationLoggingService", "Service start command received")
        startForegroundServiceWithNotification()
        startLocationUpdates()
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "location_logging_channel"
        val channelName = "Location Logging"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Tracking Location")
            .setContentText("Logging location every 15 seconds")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        startForeground(1, notification)
    }

    private fun startLocationUpdates() {
        Log.i("LocationLoggingService", "Attempting to start location updates")
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("LocationLoggingService", "Location permission not granted!")
            stopSelf()
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        Log.i("LocationLoggingService", "Location updates started")
    }

    private fun processLocation(location: Location) {
        Log.d("LocationLoggingService", "Raw location: lat=${location.latitude}, lng=${location.longitude}, accuracy=${location.accuracy}, timestamp=${location.time}, speed=${location.speed}, bearing=${location.bearing}")
        // Filter: accuracy
        if (location.accuracy > 20) {
            Log.w("LocationLoggingService", "Location discarded due to low accuracy: ${location.accuracy}")
            return
        }
        // Kalman smoothing
        val (smoothLat, smoothLng) = kalman.process(location.latitude, location.longitude)
        
        // Filter: Check if user has moved significantly (more than 3 meters)
        if (lastLat != null && lastLng != null) {
            val dist = haversine(lastLat!!, lastLng!!, smoothLat, smoothLng)
            if (dist > 100) {
                Log.w("LocationLoggingService", "Location discarded due to large jump: $dist meters")
                return
            }
            if (dist < 3.0) {
                Log.w("LocationLoggingService", "Location discarded due to minimal movement: $dist meters")
                return
            }
        }
        
        // Filter: stationary (speed-based, as backup)
        if (location.hasSpeed() && location.speed < 0.5f) {
            Log.w("LocationLoggingService", "Location discarded due to low speed: ${location.speed}")
            return
        }
        // Prepare JSON object with all location data
        val point = JSONObject().apply {
            put("latitude", smoothLat)
            put("longitude", smoothLng)
            put("timestamp", location.time)
            put("accuracy", location.accuracy)
            put("speed", if (location.hasSpeed()) location.speed else JSONObject.NULL)
            put("bearing", if (location.hasBearing()) location.bearing else JSONObject.NULL)
        }
        Log.d("LocationLoggingService", "Logging: lat=$smoothLat, lng=$smoothLng, accuracy=${location.accuracy}, timestamp=${location.time}, speed=${location.speed}, bearing=${location.bearing}")
        // Only send if we have both lastPoint and current point
        if (lastPoint != null) {
            val pointsArray = JSONArray()
            pointsArray.put(lastPoint)
            pointsArray.put(point)
            Log.v("LocationLoggingService", "Request body: ${pointsArray.toString()}")
            sendPointsToServer(pointsArray)
        }
        lastLat = smoothLat
        lastLng = smoothLng
        lastTimestamp = location.time
        lastPoint = point

        onLocationChanged(location)
    }

    private fun sendPointsToServer(points: JSONArray) {
        executor.execute {
            // Get user_id from shared preferences
            val userId = SharedPrefsUtils.getUserIdFromPrefs(this).toIntOrNull() ?: -1
            
            // Convert JSONArray to LocationEvent objects
            val locationEvents = mutableListOf<LocationEvent>()
            for (i in 0 until points.length()) {
                val point = points.getJSONObject(i)
                val event = LocationEvent(
                    user_id = userId,
                    latitude = point.getDouble("latitude"),
                    longitude = point.getDouble("longitude"),
                    timestamp = point.getLong("timestamp"),
                    accuracy = point.getDouble("accuracy").toFloat(),
                    speed = if (point.has("speed") && !point.isNull("speed")) point.getDouble("speed").toFloat() else null,
                    bearing = if (point.has("bearing") && !point.isNull("bearing")) point.getDouble("bearing").toFloat() else null
                )
                locationEvents.add(event)
            }
            
            // Try to post online first, if fails store locally
            GlobalScope.launch {
                val posted = LocationEventSyncManager.isInternetAvailable(this@LocationLoggingService) &&
                    LocationEventSyncManager.postLocationEventsOnline(locationEvents, endpointUrl, this@LocationLoggingService)
                
                if (!posted) {
                    // Store locally for later sync
                    val db = AppDatabase.getDatabase(this@LocationLoggingService)
                    locationEvents.forEach { event ->
                        db.locationEventDao().insert(event)
                    }
                    Log.d("LocationLoggingService", "Stored ${locationEvents.size} location events locally")
                } else {
                    Log.d("LocationLoggingService", "Successfully posted ${locationEvents.size} location events online")
                }
            }
        }
    }

    // Method to sync unsent location events (can be called when internet becomes available)
    fun syncUnsentLocationEvents() {
        GlobalScope.launch {
            LocationEventSyncManager.syncUnsentLocationEvents(
                context = this@LocationLoggingService,
                endpoint = endpointUrl,
                onResult = { success ->
                    if (success) {
                        Log.d("LocationLoggingService", "Successfully synced all unsent location events")
                    } else {
                        Log.w("LocationLoggingService", "Failed to sync some location events")
                    }
                },
                onEventFail = { events ->
                    Log.e("LocationLoggingService", "Failed to sync ${events.size} location events")
                }
            )
        }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.i("LocationLoggingService", "Service destroyed and location updates stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onLocationChanged(location: android.location.Location) {
        if (lastLocation == null || location.distanceTo(lastLocation!!) > WAITING_THRESHOLD_METERS) {
            lastLocation = location
            lastMovedTime = System.currentTimeMillis()
            waitingEventPosted = false
        } else {
            val now = System.currentTimeMillis()
            if (!waitingEventPosted && now - lastMovedTime > WAITING_THRESHOLD_MILLIS) {
                // Log waiting event
                val db = AppDatabase.getDatabase(this)
                val eventTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                GlobalScope.launch {
                    val userId = SharedPrefsUtils.getUserIdFromPrefs(this@LocationLoggingService)
                    val event = ActivityEvent(
                        user_id = userId.toIntOrNull() ?: -1,
                        event_type = "waiting",
                        event_time = eventTime,
                        lat = location.latitude ?: 0.0,
                        lng = location.longitude ?: 0.0,
                        details = "User has been stationary for 5 minutes",
                        session_id = 0,
                        client_id = 0
                    )
                    val endpoint = "https://api.brisk-credit.com/endpoints/activity_logs.php"
                    val posted = ActivityEventSyncManager.isInternetAvailable(this@LocationLoggingService) &&
                        ActivityEventSyncManager.postEventOnline(event, endpoint, this@LocationLoggingService)
                    if (!posted) {
                        db.activityEventDao().insert(event)
                    }
                }
                waitingEventPosted = true
            }
        }
    }
} 