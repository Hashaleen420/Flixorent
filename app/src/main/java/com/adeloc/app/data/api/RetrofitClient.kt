package com.adeloc.app.data.api

import android.content.Context
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val TMDB_URL = "https://api.themoviedb.org/3/"
    private const val TORRENTIO_URL = "https://torrentio.strem.fun/"
    private const val RD_URL = "https://api.real-debrid.com/rest/1.0/"
    private const val AD_URL = "https://api.alldebrid.com/v4/"
    private const val PM_URL = "https://www.premiumize.me/api/"
    private const val TB_URL = "https://api.torbox.app/v1/api/"
    private const val TRAKT_URL = "https://api.trakt.tv/"

    // Added to stop SplashActivity errors
    fun init(context: Context) {}

    private fun createRetrofit(url: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val tmdb: TmdbApi by lazy { createRetrofit(TMDB_URL).create(TmdbApi::class.java) }
    val torrentio: TorrentioApi by lazy { createRetrofit(TORRENTIO_URL).create(TorrentioApi::class.java) }
    val realDebrid: RealDebridApi by lazy { createRetrofit(RD_URL).create(RealDebridApi::class.java) }

    // THE MISSING PLUGS (These fix your 50+ errors)
    val allDebrid: AllDebridApi by lazy { createRetrofit(AD_URL).create(AllDebridApi::class.java) }
    val premiumize: PremiumizeApi by lazy { createRetrofit(PM_URL).create(PremiumizeApi::class.java) }
    val torbox: TorBoxApi by lazy { createRetrofit(TB_URL).create(TorBoxApi::class.java) }
    val trakt: TraktApi by lazy { createRetrofit(TRAKT_URL).create(TraktApi::class.java) }
}