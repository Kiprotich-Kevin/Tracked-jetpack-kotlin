package com.yubytech.tracked.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_events")
data class LocationEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val user_id: Int,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val accuracy: Float,
    val speed: Float?,
    val bearing: Float?,
    val sent: Boolean = false
) 