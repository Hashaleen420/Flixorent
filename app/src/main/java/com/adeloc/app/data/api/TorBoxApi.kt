package com.adeloc.app.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

interface TorBoxApi {
    @FormUrlEncoded
    @POST("torrents/createtorrent")
    suspend fun createTorrent(
        @Header("Authorization") bearerToken: String,
        @Field("magnet") magnet: String,
        @Field("seed") seed: Int = 1,          // ✅ RESTORED (Required by TorBox)
        @Field("allow_zip") allowZip: Boolean = false // ✅ RESTORED (Required by TorBox)
    ): TorBoxCreateResponse

    @GET("torrents/mylist")
    suspend fun getMyList(
        @Header("Authorization") bearerToken: String
    ): TorBoxListResponse

    @GET("torrents/requestdl")
    suspend fun requestDownload(
        @Header("Authorization") bearerToken: String,
        @Query("token") token: String,  // <--- ADD THIS NEW LINE
        @Query("torrent_id") torrentId: Int,
        @Query("file_id") fileId: Int,
        @Query("zip_link") zipLink: Boolean = false
    ): TorBoxDownloadResponse

    @GET("torrents/checkcached")
    suspend fun checkCached(
        @Header("Authorization") bearerToken: String,
        @Query("hash") hash: String,
        @Query("format") format: String = "list"
    ): TorBoxCacheResponse
}

// Data Models (Keep these exactly as they are)
data class TorBoxCreateResponse(
    @SerializedName("success") val success: Boolean?,
    @SerializedName("data") val data: TorBoxCreateData?,
    @SerializedName("detail") val detail: String?
)

data class TorBoxCreateData(
    @SerializedName("torrent_id") val torrent_id: Int?
)

data class TorBoxListResponse(
    @SerializedName("success") val success: Boolean?,
    @SerializedName("data") val data: List<TorBoxTorrent>?
)

data class TorBoxTorrent(
    @SerializedName("id") val id: Int,
    @SerializedName("files") val files: List<TorBoxFile>?
)

data class TorBoxFile(
    @SerializedName("id") val id: Int,
    @SerializedName("size") val size: Long
)

// Delete the old TorBoxDownloadData class completely, you don't need it.
data class TorBoxDownloadResponse(
    @SerializedName("success") val success: Boolean?,
    @SerializedName("data") val data: String? // <-- CHANGED TO STRING
)

data class TorBoxDownloadData(
    @SerializedName("link") val link: String?
)

data class TorBoxCacheResponse(
    @SerializedName("success") val success: Boolean?,
    @SerializedName("data") val data: Map<String, Any>? // TorBox returns a complex map for cached files
)