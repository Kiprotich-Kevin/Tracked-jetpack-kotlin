package com.yubytech.tracked

class KalmanLatLng(
    private var q: Double = 0.00001, // process noise
    private var r: Double = 0.0001   // measurement noise
) {
    private var lat: Double? = null
    private var lng: Double? = null
    private var pLat = 1.0
    private var pLng = 1.0

    fun process(newLat: Double, newLng: Double): Pair<Double, Double> {
        if (lat == null || lng == null) {
            lat = newLat
            lng = newLng
            return Pair(newLat, newLng)
        }
        // Kalman for latitude
        pLat += q
        val kLat = pLat / (pLat + r)
        lat = lat!! + kLat * (newLat - lat!!)
        pLat *= (1 - kLat)
        // Kalman for longitude
        pLng += q
        val kLng = pLng / (pLng + r)
        lng = lng!! + kLng * (newLng - lng!!)
        pLng *= (1 - kLng)
        return Pair(lat!!, lng!!)
    }
} 