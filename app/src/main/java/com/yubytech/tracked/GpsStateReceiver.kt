package com.yubytech.tracked

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.yubytech.tracked.local.AppDatabase
import com.yubytech.tracked.local.ActivityEvent
import com.yubytech.tracked.local.ActivityEventSyncManager
import com.yubytech.tracked.ui.SharedPrefsUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class GpsStateReceiver : BroadcastReceiver() {
    companion object {
        private var lastGpsEnabled: Boolean? = null
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            android.util.Log.d("GpsStateReceiver", "Triggered! isGpsEnabled=$isGpsEnabled, lastGpsEnabled=$lastGpsEnabled")
            val toastMsg = if (isGpsEnabled) "GPS switched on" else "GPS switched off"
//            android.widget.Toast.makeText(context, toastMsg, android.widget.Toast.LENGTH_SHORT).show()
            if (lastGpsEnabled == null || lastGpsEnabled != isGpsEnabled) {
                lastGpsEnabled = isGpsEnabled
                val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                val db = AppDatabase.getDatabase(context)
                val eventType = if (isGpsEnabled) "gps_on" else "gps_off"
                val details = if (isGpsEnabled) "GPS switched on" else "GPS switched off"
//                android.util.Log.d("GpsStateReceiver", "Posting to DB: $eventType")
                GlobalScope.launch {
                    val userId = SharedPrefsUtils.getUserIdFromPrefs(context)
                    val event = ActivityEvent(
                        user_id = userId.toIntOrNull() ?: -1,
                        event_type = eventType,
                        event_time = now,
                        lat = 0.0,
                        lng = 0.0,
                        details = details,
                        session_id = 0,
                        client_id = 0
                    )
                    val endpoint = "https://api.brisk-credit.net/endpoints/activity_logs.php"
                    val posted = ActivityEventSyncManager.isInternetAvailable(context) &&
                        ActivityEventSyncManager.postEventOnline(event, endpoint, context)
                    if (!posted) {
                        db.activityEventDao().insert(event)
                    }
                }
            }
        }
    }
} 