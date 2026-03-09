package com.adeloc.app.data.api

import com.google.gson.JsonElement // ✅ ADDED THIS IMPORT
import com.google.gson.annotations.SerializedName
import retrofit2.http.*

interface AllDebridApi {
    // --- PIN AUTHENTICATION ---
    @GET("pin/get")
    suspend fun getPin(@Query("agent") agent: String = "Flixorent"): AllDebridPinResponse

    @GET("pin/check")
    suspend fun checkPin(
        @Query("agent") agent: String = "Flixtorrent",
        @Query("pin") pin: String,
        @Query("check") check: String
    ): AllDebridPinCheckResponse

    // --- MAGNETS & LINKS ---
    @GET("magnet/instant")
    suspend fun checkInstant(
        @Header("Authorization") bearerToken: String,
        @Query("magnets[]") magnet: String,
        @Query("agent") agent: String = "Flixtorrent"
    ): AllDebridInstantResponse

    @FormUrlEncoded
    @POST("magnet/upload")
    suspend fun uploadMagnet(
        @Header("Authorization") bearerToken: String,
        @Field("magnets[]") magnet: String,
        @Field("agent") agent: String = "Flixtorrent"
    ): AllDebridUploadResponse

    // ✅ CHANGED THIS LINE TO v4.1 TO FIX THE DEAD ENDPOINT
    @GET("/v4.1/magnet/status")
    suspend fun getStatus(
        @Header("Authorization") bearerToken: String,
        @Query("id") id: String,
        @Query("agent") agent: String = "Flixtorrent"
    ): AllDebridStatusResponse

    @GET("link/unlock")
    suspend fun unlockLink(
        @Header("Authorization") bearerToken: String,
        @Query("link") link: String,
        @Query("agent") agent: String = "Flixtorrent"
    ): AllDebridUnlockResponse
}

data class AllDebridPinResponse(val status: String?, val data: AllDebridPinData?)
data class AllDebridPinData(val pin: String?, val check: String?, val user_url: String?)
data class AllDebridPinCheckResponse(val status: String?, val data: AllDebridPinCheckData?)
data class AllDebridPinCheckData(val apikey: String?, val activated: Boolean?)

data class AllDebridInstantResponse(
    val status: String?,
    val data: AllDebridInstantData?
)

data class AllDebridInstantData(
    val magnets: List<AllDebridInstantItem>?
)

data class AllDebridInstantItem(
    val magnet: String?,
    val instant: Boolean?,
    val files: List<AllDebridFile>?
)

data class AllDebridFile(val n: String?, val l: String?)

data class AllDebridUploadResponse(
    val status: String?,
    val data: AllDebridUploadData?,
    val error: AllDebridError?
)

data class AllDebridUploadData(val magnets: List<AllDebridMagnetItem>?)

// ✅ CHANGED 'Any?' TO 'Int?' SO IT DOESN'T CRASH
data class AllDebridMagnetItem(val id: Int?, val ready: Boolean?)

// ✅ ADDED 'error' SO THE APP DOESN'T GO BLIND
data class AllDebridStatusResponse(
    val status: String?,
    val data: AllDebridStatusData?,
    val error: AllDebridError?
)

// ✅ CHANGED 'Any?' TO 'JsonElement?' FOR SAFE PARSING
data class AllDebridStatusData(val magnets: JsonElement?)

data class AllDebridMagnetDetail(
    val status: String?,
    val filename: String?,
    val links: List<AllDebridLinkItem>?
)

data class AllDebridLinkItem(val link: String?)
data class AllDebridUnlockResponse(val status: String?, val data: AllDebridUnlockData?)
data class AllDebridUnlockData(val link: String?)
data class AllDebridError(val message: String?)