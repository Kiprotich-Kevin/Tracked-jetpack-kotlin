package com.yubytech.tracked.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_events")
data class ActivityEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val user_id: Int,
    val event_type: String,
    val event_time: String,
    val lat: Double?,
    val lng: Double?,
    val details: String?,
    val session_id: Int?,
    val client_id: Int?,
    val sent: Boolean = false
) 