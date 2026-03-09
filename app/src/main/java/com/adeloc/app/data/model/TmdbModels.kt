package com.adeloc.app.data.model

import com.google.gson.annotations.SerializedName

data class MovieResponse(val results: List<Movie>)

data class Movie(
    val id: Int,
    @SerializedName("title") val _title: String?,
    @SerializedName("name") val _name: String?,
    @SerializedName("poster_path") val poster_path: String?,
    @SerializedName("backdrop_path") val backdrop_path: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("release_date") val release_date: String?,
    @SerializedName("first_air_date") val first_air_date: String?,
    @SerializedName("vote_average") val rating: Float?,
    @SerializedName("media_type") val media_type: String?
) {
    val title: String get() = _title ?: _name ?: "Unknown Title"
}

data class MovieDetailResponse(
    val id: Int,
    val title: String?,
    val runtime: Int?,
    val genres: List<Genre>?,
    val release_date: String?,
    val backdrop_path: String?,
    @SerializedName("poster_path") val poster_path: String?,
    val overview: String?,
    val credits: CreditsResponse?,
    val videos: VideoResponse?,
    val vote_average: Float?
)

data class TvDetailResponse(
    val id: Int,
    val name: String?,
    val seasons: List<Season>,
    val genres: List<Genre>?,
    val first_air_date: String?,
    @SerializedName("backdrop_path") val backdrop_path: String?,
    @SerializedName("poster_path") val poster_path: String?,
    val overview: String?,
    val episode_run_time: List<Int>?,
    val credits: CreditsResponse?,
    val videos: VideoResponse?,
    val vote_average: Float?
)

data class SeasonDetailResponse(
    val _id: String,
    val air_date: String?,
    val episodes: List<Episode>,
    val name: String,
    val overview: String?,
    val season_number: Int,
    val poster_path: String?
)

data class EpisodeDetailResponse(
    val air_date: String?,
    val episode_number: Int,
    val id: Int,
    val name: String,
    val overview: String?,
    val runtime: Int?,
    val season_number: Int,
    val still_path: String?,
    val vote_average: Float?
)

data class Episode(
    val air_date: String?,
    val episode_number: Int,
    val id: Int,
    val name: String,
    val overview: String?,
    val runtime: Int?,
    val season_number: Int,
    val still_path: String?,
    val vote_average: Float?
)

data class Genre(val name: String)
data class CreditsResponse(val cast: List<Cast>)
data class Cast(val name: String)
data class VideoResponse(val results: List<Video>)
data class Video(val key: String, val site: String, val type: String)

data class Season(
    val season_number: Int,
    val episode_count: Int,
    val name: String?,
    val air_date: String?,
    @SerializedName("poster_path") val poster_path: String?
)

data class ExternalIdResponse(val imdb_id: String?)
