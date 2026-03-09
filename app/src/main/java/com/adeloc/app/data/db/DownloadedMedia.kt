package com.adeloc.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_media")
data class DownloadedMedia(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val tmdbId: Int,
    val title: String,
    val posterPath: String,
    val backdropPath: String,
    val filePath: String,
    val mediaType: String, // 'movie' or 'tv'
    val downloadStatus: Int, // 0=Downloading, 1=Completed, 2=Failed
    val downloadId: Int // from Fetch library
)
