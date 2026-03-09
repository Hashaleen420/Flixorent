package com.adeloc.app.data.api

import com.adeloc.app.data.model.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {

    @GET("trending/all/day")
    suspend fun getTrending(@Query("api_key") key: String): MovieResponse

    @GET("trending/movie/week")
    suspend fun getTrendingMovies(@Query("api_key") key: String): MovieResponse

    @GET("trending/tv/week")
    suspend fun getTrendingTV(@Query("api_key") apiKey: String): MovieResponse

    @GET("movie/popular")
    suspend fun getPopular(@Query("api_key") key: String): MovieResponse

    @GET("tv/popular")
    suspend fun getPopularTV(@Query("api_key") key: String): MovieResponse

    @GET("movie/top_rated")
    suspend fun getTopRated(@Query("api_key") key: String): MovieResponse

    @GET("tv/top_rated")
    suspend fun getTopRatedTV(@Query("api_key") apiKey: String): MovieResponse

    @GET("movie/upcoming")
    suspend fun getUpcoming(@Query("api_key") key: String): MovieResponse

    @GET("movie/now_playing")
    suspend fun getInTheaters(@Query("api_key") key: String): MovieResponse

    @GET("tv/on_the_air")
    suspend fun getTvOnTheAir(@Query("api_key") key: String): MovieResponse

    @GET("tv/{id}")
    suspend fun getTvDetails(
        @Path("id") id: Int, 
        @Query("api_key") k: String,
        @Query("append_to_response") append: String = "credits,videos"
    ): TvDetailResponse

    @GET("tv/{id}/season/{season_number}")
    suspend fun getSeasonDetails(
        @Path("id") id: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String
    ): SeasonDetailResponse

    @GET("tv/{id}/season/{season_number}/episode/{episode_number}")
    suspend fun getEpisodeDetails(
        @Path("id") id: Int,
        @Path("season_number") seasonNumber: Int,
        @Path("episode_number") episodeNumber: Int,
        @Query("api_key") apiKey: String
    ): EpisodeDetailResponse

    @GET("movie/{id}")
    suspend fun getMovieDetails(
        @Path("id") id: Int, 
        @Query("api_key") k: String,
        @Query("append_to_response") append: String = "credits,videos"
    ): MovieDetailResponse

    // --- RECOMMENDATIONS ---
    @GET("movie/{id}/recommendations")
    suspend fun getMovieRecommendations(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String
    ): MovieResponse

    @GET("tv/{id}/recommendations")
    suspend fun getTvRecommendations(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String
    ): MovieResponse

    @GET("discover/movie")
    suspend fun getByGenre(
        @Query("api_key") apiKey: String,
        @Query("with_genres") genreId: String,
        @Query("sort_by") sortBy: String = "popularity.desc"
    ): MovieResponse

    @GET("discover/tv")
    suspend fun getTvByGenre(
        @Query("api_key") apiKey: String,
        @Query("with_genres") genreId: String,
        @Query("sort_by") sortBy: String = "popularity.desc"
    ): MovieResponse

    @GET("discover/movie")
    suspend fun getByProvider(
        @Query("api_key") apiKey: String,
        @Query("with_watch_providers") providerId: String,
        @Query("watch_region") region: String = "US",
        @Query("sort_by") sortBy: String = "popularity.desc"
    ): MovieResponse

    @GET("discover/tv")
    suspend fun getTvByProvider(
        @Query("api_key") apiKey: String,
        @Query("with_watch_providers") providerId: String,
        @Query("watch_region") region: String = "US",
        @Query("sort_by") sortBy: String = "popularity.desc"
    ): MovieResponse

    @GET("discover/movie?with_genres=16&with_original_language=ja")
    suspend fun getAnime(
        @Query("api_key") apiKey: String,
        @Query("sort_by") sortBy: String = "popularity.desc"
    ): MovieResponse

    @GET("search/movie")
    suspend fun search(
        @Query("api_key") k: String, 
        @Query("query") q: String,
        @Query("include_adult") adult: Boolean = false
    ): MovieResponse

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") k: String, 
        @Query("query") q: String,
        @Query("include_adult") adult: Boolean = false
    ): MovieResponse

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") k: String,
        @Query("query") q: String,
        @Query("include_adult") adult: Boolean = false
    ): MovieResponse

    @GET("movie/{id}/external_ids")
    suspend fun getIds(@Path("id") id: Int, @Query("api_key") k: String): ExternalIdResponse

    @GET("tv/{id}/external_ids")
    suspend fun getTvIds(@Path("id") id: Int, @Query("api_key") k: String): ExternalIdResponse
}