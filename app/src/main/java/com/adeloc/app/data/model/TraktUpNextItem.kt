package com.adeloc.app.data.model

import com.google.gson.annotations.SerializedName

data class TraktUpNextItem(
    val type: String?,
    val movie: TraktMovieItem?,
    val show: TraktShowItem?,
    val episode: TraktEpisodeItem?,
    var finalImageUrl: String? = null
)

data class TraktMovieItem(
    val title: String?,
    val ids: TraktIds,
    val poster_path: String? = null
)

data class TraktShowItem(
    val title: String?,
    val ids: TraktIds,
    val poster_path: String? = null
)

data class TraktEpisodeItem(
    val season: Int?,
    val number: Int?,
    val title: String?,
    val ids: TraktIds
)

data class TraktIds(
    val trakt: Int?,
    val tmdb: Int?,
    val imdb: String?
)
