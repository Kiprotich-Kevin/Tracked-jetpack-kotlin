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
import org.json.JSONObject
import android.util.Log
import com.yubytech.tracked.ui.SharedPrefsUtils

object ActivityEventSyncManager {
    
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

    suspend fun postEventOnline(event: ActivityEvent, endpoint: String, context: Context, onFail: ((ActivityEvent) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("user_id", event.user_id)
                put("event_type", event.event_type)
                put("event_time", event.event_time)
                put("lat", event.lat ?: 0)
                put("lng", event.lng ?: 0)
                put("details", event.details)
                put("session_id", event.session_id ?: 0)
                put("client_id", event.client_id ?: 0)
            }
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(endpoint)
                .post(body)
                .build()
            val client = getAuthenticatedClient(context)
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            val success = response.isSuccessful
            Log.d("EventSync", "POST status: ${response.code}, body: $responseBody")
            response.close()
            if (!success) onFail?.invoke(event)
            return@withContext success
        } catch (e: Exception) {
            e.printStackTrace()
            onFail?.invoke(event)
            return@withContext false
        }
    }

    suspend fun syncUnsentEvents(context: Context, endpoint: String, onResult: ((Boolean) -> Unit)? = null, onEventFail: ((ActivityEvent) -> Unit)? = null) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.activityEventDao()
        val events = dao.getUnsentEvents()
        var allSuccess = true
        for (event in events) {
            val success = postEventOnline(event, endpoint, context, onEventFail)
            if (success) {
                dao.delete(event)
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