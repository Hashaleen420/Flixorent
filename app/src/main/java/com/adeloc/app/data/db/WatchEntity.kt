package com.adeloc.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchEntity(
    @PrimaryKey val tmdbId: Int,
    val title: String,
    val posterPath: String?,
    val timestamp: Long,
    val position: Long,
    val duration: Long,
    val lastUrl: String,
    val lastQuality: String,
    val isFavorite: Boolean = false,
    val mediaType: String? = "movie" // "movie" or "tv"
)