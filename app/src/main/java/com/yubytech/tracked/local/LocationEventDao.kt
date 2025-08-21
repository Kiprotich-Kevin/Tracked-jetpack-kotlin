package com.yubytech.tracked.local

import androidx.room.*

@Dao
interface LocationEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: LocationEvent)

    @Query("SELECT * FROM location_events WHERE sent = 0 ORDER BY timestamp ASC")
    suspend fun getUnsentEvents(): List<LocationEvent>

    @Update
    suspend fun update(event: LocationEvent)

    @Delete
    suspend fun delete(event: LocationEvent)

    @Query("DELETE FROM location_events WHERE sent = 1")
    suspend fun deleteSentEvents()

    @Query("SELECT COUNT(*) FROM location_events WHERE sent = 0")
    suspend fun getUnsentCount(): Int
} 