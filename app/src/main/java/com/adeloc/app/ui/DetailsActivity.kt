package com.adeloc.app.ui

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.adeloc.app.R
import com.adeloc.app.data.api.RetrofitClient
import com.adeloc.app.data.db.AppDatabase
import com.adeloc.app.data.db.WatchEntity
import com.adeloc.app.databinding.ActivityDetailsBinding
import com.adeloc.app.ui.adapters.EpisodeAdapter
import com.adeloc.app.ui.adapters.SeasonAdapter
import com.adeloc.app.utils.Constants
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class DetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailsBinding
    private var trailerKey: String? = null
    private var isFavorite = false
    private var mediaType: String = "movie"
    private var currentTmdbId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportPostponeEnterTransition()
        hideSystemUI()

        currentTmdbId = intent.getIntExtra("TMDB_ID", 0)
        mediaType = intent.getStringExtra("MEDIA_TYPE") ?: "movie"

        // Set up Static Back Button
        binding.backBtn.setOnClickListener { finishAfterTransition() }

        checkFavoriteStatus(currentTmdbId)
        fetchStrictDetails(currentTmdbId)

        binding.playBtn.setOnClickListener {
            startPlayer(currentTmdbId, binding.detailsTitle.text.toString(), intent.getStringExtra("POSTER") ?: "", 0, 0)
        }

        binding.trailerBtn.setOnClickListener {
            if (!trailerKey.isNullOrEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$trailerKey"))
                startActivity(intent)
            } else {
                Toast.makeText(this, getString(R.string.trailer_not_available), Toast.LENGTH_SHORT).show()
            }
        }

        binding.favBtn.setOnClickListener { 
            toggleFavorite(currentTmdbId, binding.detailsTitle.text.toString(), intent.getStringExtra("POSTER") ?: "") 
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller?.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun fetchStrictDetails(id: Int) {
        lifecycleScope.launch {
            try {
                // STRICT FETCH: Try TV first if we suspect it's TV, or try both if unsure
                // But if search results say 'tv', we trust but verify.
                if (mediaType == "tv") {
                    try {
                        val tv = RetrofitClient.tmdb.getTvDetails(id, Constants.TMDB_KEY)
                        if (tv.id != 0) {
                            updateUIWithTvDetails(tv)
                            setupSeasons(id, tv.seasons)
                        } else {
                            fetchMovieAsFallback(id)
                        }
                    } catch (e: Exception) {
                        fetchMovieAsFallback(id)
                    }
                } else {
                    try {
                        val movie = RetrofitClient.tmdb.getMovieDetails(id, Constants.TMDB_KEY)
                        if (movie.id != 0) {
                            updateUIWithMovieDetails(movie)
                        } else {
                            fetchTvAsFallback(id)
                        }
                    } catch (e: Exception) {
                        fetchTvAsFallback(id)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun fetchMovieAsFallback(id: Int) {
        try {
            val movie = RetrofitClient.tmdb.getMovieDetails(id, Constants.TMDB_KEY)
            mediaType = "movie"
            updateUIWithMovieDetails(movie)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private suspend fun fetchTvAsFallback(id: Int) {
        try {
            val tv = RetrofitClient.tmdb.getTvDetails(id, Constants.TMDB_KEY)
            mediaType = "tv"
            updateUIWithTvDetails(tv)
            setupSeasons(id, tv.seasons)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setupSeasons(tmdbId: Int, seasons: List<com.adeloc.app.data.model.Season>) {
        val validSeasons = seasons.filter { it.season_number > 0 }
        binding.rvSeasons.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvSeasons.adapter = SeasonAdapter(validSeasons) { season ->
            loadEpisodes(tmdbId, season.season_number)
        }
        
        // Auto-load Season 1
        if (validSeasons.isNotEmpty()) {
            loadEpisodes(tmdbId, validSeasons[0].season_number)
        }
    }

    private fun loadEpisodes(tmdbId: Int, seasonNumber: Int) {
        lifecycleScope.launch {
            try {
                val seasonDetails = RetrofitClient.tmdb.getSeasonDetails(tmdbId, seasonNumber, Constants.TMDB_KEY)
                binding.rvEpisodes.layoutManager = LinearLayoutManager(this@DetailsActivity)
                binding.rvEpisodes.adapter = EpisodeAdapter(seasonDetails.episodes) { episode ->
                    startPlayer(tmdbId, binding.detailsTitle.text.toString(), intent.getStringExtra("POSTER") ?: "", episode.season_number, episode.episode_number)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun startPlayer(tmdbId: Int, title: String, poster: String, season: Int, episode: Int) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("TMDB_ID", tmdbId)
            putExtra("MOVIE_TITLE", title)
            putExtra("POSTER", poster)
            putExtra("MEDIA_TYPE", mediaType)
            if (season > 0) {
                putExtra("SEASON", season)
                putExtra("EPISODE", episode)
            }
        }
        startActivity(intent)
    }

    private fun checkFavoriteStatus(id: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val progress = AppDatabase.get(applicationContext).movieDao().getProgress(id)
            isFavorite = progress?.isFavorite ?: false
            withContext(Dispatchers.Main) {
                binding.favBtn.setImageResource(if (isFavorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
            }
        }
    }

    private fun toggleFavorite(id: Int, title: String, poster: String) {
        isFavorite = !isFavorite
        binding.favBtn.setImageResource(if (isFavorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.get(applicationContext).movieDao()
            val existing = dao.getProgress(id)
            if (existing != null) {
                dao.updateFavorite(id, isFavorite)
            } else {
                dao.insert(WatchEntity(id, title, poster, System.currentTimeMillis(), 0, 0, "", "", isFavorite, mediaType))
            }
        }
    }

    private fun updateUIWithMovieDetails(movie: com.adeloc.app.data.model.MovieDetailResponse) {
        mediaType = "movie"
        binding.playBtn.visibility = View.VISIBLE
        binding.playBtn.text = getString(R.string.play_movie)
        binding.tvLabel.visibility = View.GONE
        binding.rvSeasons.visibility = View.GONE
        binding.rvEpisodes.visibility = View.GONE

        binding.detailsTitle.text = movie.title ?: intent.getStringExtra("MOVIE_TITLE")
        
        val year = movie.release_date?.take(4) ?: ""
        binding.metaInfo.text = "$year • ${movie.runtime}m • ${movie.genres?.joinToString(", ") { it.name }}"
        binding.detailsOverview.text = movie.overview ?: getString(R.string.no_description)
        binding.detailsCast.text = movie.credits?.cast?.take(10)?.joinToString(", ") { it.name } ?: "N/A"
        
        val rating = movie.vote_average ?: 0f
        binding.ratingText.text = "⭐ TMDB: ${String.format(Locale.getDefault(), "%.1f", rating)}"
        
        trailerKey = movie.videos?.results?.find { it.type == "Trailer" && it.site == "YouTube" }?.key
        binding.trailerBtn.visibility = if (trailerKey != null) View.VISIBLE else View.GONE

        loadImages(movie.poster_path, movie.backdrop_path)
    }

    private fun updateUIWithTvDetails(tv: com.adeloc.app.data.model.TvDetailResponse) {
        mediaType = "tv"
        binding.playBtn.visibility = View.GONE
        binding.tvLabel.visibility = View.VISIBLE
        binding.rvSeasons.visibility = View.VISIBLE
        binding.rvEpisodes.visibility = View.VISIBLE

        binding.detailsTitle.text = tv.name ?: intent.getStringExtra("MOVIE_TITLE")

        val year = tv.first_air_date?.take(4) ?: ""
        binding.metaInfo.text = "$year • ${getString(R.string.tv_series)} • ${tv.genres?.joinToString(", ") { it.name }}"
        binding.detailsOverview.text = tv.overview ?: getString(R.string.no_description)
        binding.detailsCast.text = tv.credits?.cast?.take(10)?.joinToString(", ") { it.name } ?: "N/A"
        
        val rating = tv.vote_average ?: 0f
        binding.ratingText.text = "⭐ TMDB: ${String.format(Locale.getDefault(), "%.1f", rating)}"

        trailerKey = tv.videos?.results?.find { it.type == "Trailer" && it.site == "YouTube" }?.key
        binding.trailerBtn.visibility = if (trailerKey != null) View.VISIBLE else View.GONE

        loadImages(tv.poster_path, tv.backdrop_path)
    }

    private fun loadImages(poster: String?, backdrop: String?) {
        Glide.with(this)
            .load(Constants.IMG_URL + poster)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    supportStartPostponedEnterTransition()
                    return false
                }
                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                    supportStartPostponedEnterTransition()
                    return false
                }
            })
            .into(binding.ivPoster)

        Glide.with(this)
            .load(Constants.IMG_URL + (if (backdrop.isNullOrEmpty()) poster else backdrop))
            .into(binding.detailsBackdrop)
    }
}