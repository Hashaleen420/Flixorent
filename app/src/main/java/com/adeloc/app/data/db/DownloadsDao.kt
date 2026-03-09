package com.adeloc.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadsDao {
    @Query("SELECT * FROM downloaded_media")
    fun getAllDownloads(): Flow<List<DownloadedMedia>>

    @Query("SELECT * FROM downloaded_media WHERE tmdbId = :id LIMIT 1")
    suspend fun getDownloadByTmdb(id: Int): DownloadedMedia?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: DownloadedMedia)

    @Delete
    suspend fun delete(media: DownloadedMedia)
}
