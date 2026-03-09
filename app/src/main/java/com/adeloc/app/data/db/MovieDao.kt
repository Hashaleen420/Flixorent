package com.adeloc.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MovieDao {
    @Query("SELECT * FROM watch_history WHERE position > 0 ORDER BY timestamp DESC")
    suspend fun getHistory(): List<WatchEntity>

    @Query("SELECT * FROM watch_history WHERE isFavorite = 1")
    suspend fun getFavorites(): List<WatchEntity>

    @Query("SELECT * FROM watch_history WHERE tmdbId = :id LIMIT 1")
    suspend fun getProgress(id: Int): WatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movie: WatchEntity)

    @Query("UPDATE watch_history SET isFavorite = :fav WHERE tmdbId = :id")
    suspend fun updateFavorite(id: Int, fav: Boolean)

    // FIX: Only reset position so it stays in Library if favorited
    @Query("UPDATE watch_history SET position = 0 WHERE tmdbId = :id")
    suspend fun deleteHistory(id: Int)

    @Query("UPDATE watch_history SET position = 0, lastUrl = '' WHERE tmdbId = :id")
    suspend fun clearProgress(id: Int)
}