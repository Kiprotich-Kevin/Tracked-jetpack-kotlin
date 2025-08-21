package com.yubytech.tracked.ui

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SharedPrefsUtils {
    // Utility function to get user ID from shared preferences
    fun getUserIdFromPrefs(context: Context): String {
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

    // Utility function to get user name from shared preferences
    fun getUserNameFromPrefs(context: Context): String {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val sharedPreferences = EncryptedSharedPreferences.create(
                "secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            sharedPreferences.getString("user_name", "John Doe") ?: "John Doe"
        } catch (e: Exception) {
            "John Doe" // fallback to "John Doe" if there's any error
        }
    }

    // Utility function to save user name to shared preferences
    fun saveUserNameToPrefs(context: Context, userName: String) {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val sharedPreferences = EncryptedSharedPreferences.create(
                "secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            sharedPreferences.edit()
                .putString("user_name", userName)
                .apply()
            android.util.Log.d("SharedPrefsUtils", "Successfully saved user name: $userName")
        } catch (e: Exception) {
            android.util.Log.e("SharedPrefsUtils", "Error saving user name: ${e.message}")
        }
    }

    // Utility function to save all JWT response fields to shared preferences
    fun saveJwtResponseToPrefs(
        context: Context,
        userId: String?,
        userName: String?,
        setup: Int?,
        uRes: Int?,
        bRes: Int?,
        bId: Int?,
        bLat: Double?,
        bLng: Double?,
        offRad: Int?,
        fromTime: String?,
        toTime: String?
    ) {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val sharedPreferences = EncryptedSharedPreferences.create(
                "secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            sharedPreferences.edit()
                .putInt("user_id", userId?.toIntOrNull() ?: -1)
                .putString("user_name", userName ?: "John Doe")
                .putInt("setup", setup ?: 0)
                .putInt("u_res", uRes ?: 0)
                .putInt("b_res", bRes ?: 0)
                .putInt("b_id", bId ?: 0)
                .putString("b_lat", bLat?.toString() ?: "")
                .putString("b_lng", bLng?.toString() ?: "")
                .putInt("off_rad", offRad ?: 0)
                .putString("from_time", fromTime ?: "")
                .putString("to_time", toTime ?: "")
                .apply()
            android.util.Log.d("SharedPrefsUtils", "Saved all JWT fields to shared prefs")
        } catch (e: Exception) {
            android.util.Log.e("SharedPrefsUtils", "Error saving JWT fields: ${e.message}")
        }
    }

    // Utility function to get JWT field from shared preferences
    fun getJwtField(context: Context, fieldName: String): String? {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val sharedPreferences = EncryptedSharedPreferences.create(
                "secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            when (fieldName) {
                "user_id" -> sharedPreferences.getInt("user_id", -1).toString()
                "user_name" -> sharedPreferences.getString("user_name", "")
                "setup" -> sharedPreferences.getInt("setup", 0).toString()
                "u_res" -> sharedPreferences.getInt("u_res", 0).toString()
                "b_res" -> sharedPreferences.getInt("b_res", 0).toString()
                "b_id" -> sharedPreferences.getInt("b_id", 0).toString()
                "b_lat" -> sharedPreferences.getString("b_lat", "")
                "b_lng" -> sharedPreferences.getString("b_lng", "")
                "off_rad" -> sharedPreferences.getInt("off_rad", 0).toString()
                "from_time" -> sharedPreferences.getString("from_time", "")
                "to_time" -> sharedPreferences.getString("to_time", "")
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    // Debug function to check what's stored in shared preferences
    fun debugSharedPrefs(context: Context) {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val sharedPreferences = EncryptedSharedPreferences.create(
                "secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val userId = sharedPreferences.getInt("user_id", -1)
            val userName = sharedPreferences.getString("user_name", "NOT_FOUND")
            android.util.Log.d("SharedPrefsUtils", "Debug - user_id: $userId, user_name: $userName")
        } catch (e: Exception) {
            android.util.Log.e("SharedPrefsUtils", "Error reading shared prefs: ${e.message}")
        }
    }

    // Utility function to get JWT token from shared preferences
    fun getJwtToken(context: Context): String? {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val sharedPreferences = EncryptedSharedPreferences.create(
                "secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            sharedPreferences.getString("jwt", null)
        } catch (e: Exception) {
            android.util.Log.e("SharedPrefsUtils", "Error getting JWT token: ${e.message}")
            null
        }
    }
} 