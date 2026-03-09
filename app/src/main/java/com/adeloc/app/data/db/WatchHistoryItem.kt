package com.adeloc.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "watch_history_global")
data class WatchHistoryItem(
    @PrimaryKey val tmdbId: Int,
    val title: String,
    val isWatched: Boolean,
    val dateWatched: Long
)

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history_global")
    suspend fun getAllWatched(): List<WatchHistoryItem>

    @Query("SELECT tmdbId FROM watch_history_global WHERE isWatched = 1")
    suspend fun getWatchedIds(): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WatchHistoryItem)

    @Query("DELETE FROM watch_history_global WHERE tmdbId = :id")
    suspend fun delete(id: Int)
}