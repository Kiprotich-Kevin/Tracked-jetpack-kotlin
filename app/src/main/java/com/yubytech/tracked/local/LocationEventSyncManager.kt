package com.yubytech.tracked.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import com.yubytech.tracked.ui.SharedPrefsUtils

object LocationEventSyncManager {
    
    private fun getAuthenticatedClient(context: Context): OkHttpClient {
        return OkHttpClient.Builder().apply {
            addInterceptor { chain ->
                val jwtToken = SharedPrefsUtils.getJwtToken(context)
                val request = chain.request()
                
                val newRequest = if (jwtToken != null) {
                    request.newBuilder()
                        .addHeader("Authorization", "Bearer $jwtToken")
                        .build()
                } else {
                    request
                }
                
                chain.proceed(newRequest)
            }
        }.build()
    }

    suspend fun postLocationEventsOnline(events: List<LocationEvent>, endpoint: String, context: Context, onFail: ((List<LocationEvent>) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            // Convert events to points array format expected by the server
            val pointsArray = JSONArray()
            events.forEach { event ->
                val point = JSONObject().apply {
                    put("latitude", event.latitude)
                    put("longitude", event.longitude)
                    put("timestamp", event.timestamp)
                    put("accuracy", event.accuracy)
                    put("speed", event.speed ?: JSONObject.NULL)
                    put("bearing", event.bearing ?: JSONObject.NULL)
                }
                pointsArray.put(point)
            }

            // Create the request JSON with user_id and points
            val requestJson = JSONObject().apply {
                put("user_id", events.firstOrNull()?.user_id ?: 0)
                put("points", pointsArray)
            }
            
            val body = requestJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(endpoint)
                .post(body)
                .build()
            val client = getAuthenticatedClient(context)
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            val success = response.isSuccessful
            Log.d("LocationSync", "POST status: ${response.code}, body: $responseBody")
            response.close()
            if (!success) onFail?.invoke(events)
            return@withContext success
        } catch (e: Exception) {
            e.printStackTrace()
            onFail?.invoke(events)
            return@withContext false
        }
    }

    suspend fun syncUnsentLocationEvents(context: Context, endpoint: String, onResult: ((Boolean) -> Unit)? = null, onEventFail: ((List<LocationEvent>) -> Unit)? = null) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.locationEventDao()
        val events = dao.getUnsentEvents()
        
        if (events.isEmpty()) {
            onResult?.invoke(true)
            return@withContext
        }

        // Group events into batches of 10 for efficient syncing
        val batchSize = 10
        var allSuccess = true
        
        events.chunked(batchSize).forEach { batch ->
            val success = postLocationEventsOnline(batch, endpoint, context, onEventFail)
            if (success) {
                // Mark events as sent and delete them
                batch.forEach { event ->
                    dao.delete(event)
                }
            } else {
                allSuccess = false
            }
        }
        
        onResult?.invoke(allSuccess)
    }

    fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return false
        val nc = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
} 