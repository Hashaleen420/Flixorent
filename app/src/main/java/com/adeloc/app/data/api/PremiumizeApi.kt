package com.adeloc.app.data.api

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query

interface PremiumizeApi {
    // --- PIN AUTHENTICATION ---
    @GET("pin/get")
    suspend fun getPin(): PremiumizePinResponse

    @GET("pin/check")
    suspend fun checkPin(
        @Query("pin") pin: String
    ): PremiumizePinCheckResponse

    // --- MAGNETS & LINKS ---
    @FormUrlEncoded
    @POST("transfer/directdl")
    suspend fun directDownload(
        @Field("src") src: String,
        @Field("apikey") apiKey: String, // Corrected from "pin" to "apikey"
        @Field("customer_id") customerId: String? = null
    ): PremiumizeResponse
}

data class PremiumizePinResponse(
    val status: String,
    val pin: String?,
    val user_url: String?
)

data class PremiumizePinCheckResponse(
    val status: String,
    val apikey: String?
)

data class PremiumizeResponse(
    val status: String,
    val location: String?,
    val filename: String?,
    val filesize: Long?
)