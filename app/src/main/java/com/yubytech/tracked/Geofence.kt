package com.yubytech.tracked

import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yubytech.tracked.ui.SharedPrefsUtils
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class Geofence(private val context: Context) {
    
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                if (location.accuracy <= 20) { // Only accept accurate locations
                    checkGeofence(location)
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }
        }
    }
    
    fun getOfficeLocation(): Pair<Double, Double>? {
        val bLat = SharedPrefsUtils.getJwtField(context, "b_lat")
        val bLng = SharedPrefsUtils.getJwtField(context, "b_lng")
        
        return if (bLat != null && bLng != null && bLat.isNotEmpty() && bLng.isNotEmpty()) {
            Pair(bLat.toDouble(), bLng.toDouble())
        } else {
            null
        }
    }
    
    fun getOfficeRadius(): Double {
        val offRad = SharedPrefsUtils.getJwtField(context, "off_rad")
        return offRad?.toDoubleOrNull() ?: 100.0
    }
    
    fun getBranchRestriction(): Boolean {
        val bRes = SharedPrefsUtils.getJwtField(context, "b_res")
        return bRes == "1"
    }
    
    fun getUserRestriction(): Boolean {
        val uRes = SharedPrefsUtils.getJwtField(context, "u_res")
        return uRes == "1"
    }
    
    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth's radius in meters
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return R * c // Distance in meters
    }
    
    private fun checkGeofence(userLocation: Location) {
        val officeLocation = getOfficeLocation()
        val officeRadius = getOfficeRadius()
        val branchRestriction = getBranchRestriction()
        val userRestriction = getUserRestriction()
        
        if (officeLocation == null) {
            return
        }
        
        val distance = haversineDistance(
            userLocation.latitude,
            userLocation.longitude,
            officeLocation.first,
            officeLocation.second
        )
        
        val isWithinRadius = distance <= officeRadius
        
        when {
            branchRestriction && userRestriction -> {
                // Result handled by caller
            }
            else -> {
                // No restrictions - always allow
            }
        }
    }
    
    fun startLocationCheck() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateDistanceMeters(1f)
            .setMinUpdateIntervalMillis(500)
            .build()
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            
        } catch (e: SecurityException) {
            // Permission denied
        }
    }
    
    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
    
    // Login-specific location check with callback
    fun startLocationCheckForLogin(onResult: (Boolean) -> Unit, onLocationReceived: ((android.location.Location) -> Unit)? = null) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onResult(false)
            return
        }
        
        val loginLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    if (location.accuracy <= 20) { // Only accept accurate locations
                        // Store last known location for MainActivity
                        onLocationReceived?.invoke(location)
                        
                        // Check geofence
                        val officeLocation = getOfficeLocation()
                        val officeRadius = getOfficeRadius()
                        val branchRestriction = getBranchRestriction()
                        val userRestriction = getUserRestriction()
                        

                        
                        if (officeLocation != null) {
                            val distance = haversineDistance(
                                location.latitude,
                                location.longitude,
                                officeLocation.first,
                                officeLocation.second
                            )
                            
                            val isWithinRadius = distance <= officeRadius
                            
                            when {
                            branchRestriction && userRestriction -> {
                                onResult(isWithinRadius)
                            }
                            else -> {
                                // No restrictions - always allow
                                onResult(true)
                            }
                        }
                        } else {
                            // No office location configured - allow
                            onResult(true)
                        }
                        
                        // Stop location updates
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            }
        }
        
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateDistanceMeters(1f)
            .setMinUpdateIntervalMillis(500)
            .build()
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                loginLocationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            onResult(false)
        }
    }
} 