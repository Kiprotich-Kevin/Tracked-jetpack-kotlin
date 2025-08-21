package com.yubytech.tracked.local

import androidx.room.*

@Dao
interface ActivityEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ActivityEvent)

    @Query("SELECT * FROM activity_events WHERE sent = 0")
    suspend fun getUnsentEvents(): List<ActivityEvent>

    @Update
    suspend fun update(event: ActivityEvent)

    @Delete
    suspend fun delete(event: ActivityEvent)

    @Query("DELETE FROM activity_events WHERE sent = 1")
    suspend fun deleteSentEvents()
} 