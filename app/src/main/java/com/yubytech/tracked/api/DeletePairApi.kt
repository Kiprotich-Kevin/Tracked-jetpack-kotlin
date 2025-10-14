// File: DeletePairApi.kt
package com.yubytech.tracked.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DeletePairRequest(
    val id: Int,
    val created_by: Int
)

data class DeletePairResponse(
    val success: Boolean,
    val message: String?
)

suspend fun deletePair(pairId: Int, createdBy: Int): DeletePairResponse = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val json = JSONObject().apply {
            put("id", pairId)
            put("created_by", createdBy)
        }

        val requestBody = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
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

        val jsonResponse = JSONObject(responseBody)
        DeletePairResponse(
            success = jsonResponse.optBoolean("success", false),
            message = jsonResponse.optString("message", null)
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