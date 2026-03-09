package com.adeloc.app.data.api

import com.adeloc.app.data.model.TraktUpNextItem
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

interface TraktApi {
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun getToken(
        @Field("code") code: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("grant_type") grantType: String = "authorization_code"
    ): TraktTokenResponse

    @Headers("Content-Type: application/json", "trakt-api-version: 2")
    @POST("scrobble/start")
    suspend fun startScrobble(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") apiKey: String,
        @Body request: Any
    ): Response<Unit>

    @Headers("Content-Type: application/json", "trakt-api-version: 2")
    @POST("scrobble/stop")
    suspend fun stopScrobble(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") apiKey: String,
        @Body request: Any
    ): Response<Unit>

    @GET("sync/playback/episodes")
    suspend fun getUpNext(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") apiKey: String,
        @Query("extended") extended: String = "full",
        @Query("limit") limit: Int = 10
    ): List<TraktUpNextItem>

    @GET("calendars/my/shows")
    suspend fun getCalendar(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") apiKey: String,
        @Query("extended") extended: String = "full"
    ): List<TraktCalendarItem>

    @GET("users/me/watchlist/movies")
    suspend fun getWatchlistMovies(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") apiKey: String,
        @Query("extended") extended: String = "full",
        @Query("sort") sort: String = "added"
    ): List<TraktWatchlistItem>

    @GET("users/me/watchlist/shows")
    suspend fun getWatchlistShows(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") apiKey: String,
        @Query("extended") extended: String = "full",
        @Query("sort") sort: String = "added"
    ): List<TraktWatchlistItem>

    @GET("users/me/history/movies")
    suspend fun getHistory(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") apiKey: String,
        @Query("extended") extended: String = "full",
        @Query("limit") limit: Int = 10
    ): List<TraktHistoryItem>
}

data class TraktTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Long,
    @SerializedName("scope") val scope: String,
    @SerializedName("created_at") val createdAt: Long
)

// TraktScrobbleRequest and other data classes are no longer used for scrobbling 
// but might be kept for other parts of the app if needed.
// For now, keeping them as they were for backward compatibility with other potentially existing code.

data class TraktScrobbleRequest(
    val movie: TraktMovieBody? = null,
    val show: TraktShowBody? = null,
    val episode: TraktEpisodeBody? = null,
    val progress: Float,
    val app_version: String = "1.8",
    val date: String? = null
)

data class TraktMovieBody(
    val ids: TraktIdsResponse,
    val title: String? = null,
    val poster_path: String? = null
)

data class TraktShowBody(
    val ids: TraktIdsResponse,
    val title: String? = null,
    val poster_path: String? = null
)

data class TraktEpisodeBody(
    val ids: TraktIdsResponse? = null,
    val season: Int? = null,
    val number: Int? = null
)

data class TraktIdsResponse(
    val tmdb: Int? = null,
    val imdb: String? = null,
    val trakt: Int? = null
)

data class TraktCalendarItem(
    val show: TraktShowBody?
)

data class TraktWatchlistItem(
    val movie: TraktMovieBody?,
    val show: TraktShowBody?
)

data class TraktHistoryItem(
    val movie: TraktMovieBody?
)
