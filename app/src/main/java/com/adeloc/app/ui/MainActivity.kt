package com.adeloc.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.adeloc.app.R
import com.adeloc.app.data.api.RetrofitClient
import com.adeloc.app.data.db.AppDatabase
import com.adeloc.app.data.model.Movie
import com.adeloc.app.databinding.ActivityMainBinding
import com.adeloc.app.ui.adapters.HeroAdapter
import com.adeloc.app.ui.adapters.MainAdapter
import com.adeloc.app.ui.fragments.TraktFragment
import com.adeloc.app.utils.Constants
import kotlinx.coroutines.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder

data class RowItem(val title: String, val movies: List<Movie>)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isSearching = false
    private var watchedIds: Set<Int> = emptySet()
    private var currentViewMode = "HOME"
    private var searchJob: Job? = null

    private val sliderHandler = Handler(Looper.getMainLooper())
    private val sliderRunnable = object : Runnable {
        override fun run() {
            binding.heroViewPager.adapter?.let {
                if (it.itemCount > 0) {
                    val nextItem = (binding.heroViewPager.currentItem + 1) % it.itemCount
                    binding.heroViewPager.setCurrentItem(nextItem, true)
                }
            }
            sliderHandler.postDelayed(this, 5000)
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentViewMode == "HOME" && !isSearching) {
            sliderHandler.postDelayed(sliderRunnable, 5000)
        }
        refreshWatchedData()
    }

    private fun refreshWatchedData() {
        lifecycleScope.launch {
            val ids = withContext(Dispatchers.IO) {
                try {
                    AppDatabase.get(applicationContext).watchHistoryDao().getWatchedIds().toSet()
                } catch (e: Exception) {
                    emptySet()
                }
            }
            watchedIds = ids
            val adapter = binding.mainRecycler.adapter
            if (adapter is MainAdapter) {
                adapter.updateWatchedIds(watchedIds)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sliderHandler.removeCallbacks(sliderRunnable)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemUI()

        // 1. Setup UI
        binding.mainRecycler.layoutManager = LinearLayoutManager(this)

        // Setup Search and Nav
        setupSearch()
        setupNavigation()

        // Start pulse animation on logo
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        binding.appLogo.startAnimation(pulse)

        binding.swipeRefresh.setOnRefreshListener {
            if (isSearching) {
                binding.swipeRefresh.isRefreshing = false
                return@setOnRefreshListener
            }
            when (currentViewMode) {
                "HOME" -> loadHomeContent()
                "MOVIES" -> loadMoviesOnly()
                "TV" -> loadTvOnly()
                "ANIME" -> loadAnimeOnly()
                "HISTORY" -> loadHistoryPage()
                "LIB_MOVIES" -> loadLibraryMovies()
                "LIB_TV" -> loadLibraryTv()
                else -> binding.swipeRefresh.isRefreshing = false
            }
        }

        binding.appLogo.setOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }
        binding.searchBtn.setOnClickListener {
            if (binding.searchView.visibility == View.VISIBLE) {
                closeSearch()
            } else {
                openSearch()
            }
        }
        binding.settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 2. Load the content exactly like v1.9
        loadHomeContent()
    }

    private fun openSearch() {
        isSearching = true
        binding.searchView.visibility = View.VISIBLE
        binding.heroViewPager.visibility = View.GONE
        binding.searchView.isIconified = false
        binding.searchView.requestFocus()
    }

    private fun closeSearch() {
        isSearching = false
        binding.searchView.setQuery("", false)
        binding.searchView.visibility = View.GONE
        if (currentViewMode == "HOME") {
            binding.heroViewPager.visibility = View.VISIBLE
            loadHomeContent()
        } else {
            // Reload the current view mode content
            when (currentViewMode) {
                "MOVIES" -> loadMoviesOnly()
                "TV" -> loadTvOnly()
                "ANIME" -> loadAnimeOnly()
                "HISTORY" -> loadHistoryPage()
                "LIB_MOVIES" -> loadLibraryMovies()
                "LIB_TV" -> loadLibraryTv()
            }
        }
    }

    private fun loadHomeContent() {
        lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true
            showLoading()
            
            // Refresh watched data before loading content to ensure checkmarks are accurate
            val ids = withContext(Dispatchers.IO) {
                try {
                    AppDatabase.get(applicationContext).watchHistoryDao().getWatchedIds().toSet()
                } catch (e: Exception) {
                    emptySet()
                }
            }
            watchedIds = ids

            val rows = mutableListOf<RowItem>()
            val key = Constants.TMDB_KEY

            try {
                // 1. Continue Watching
                val history = withContext(Dispatchers.IO) { AppDatabase.get(applicationContext).movieDao().getHistory() }
                if (history.isNotEmpty()) {
                    rows.add(RowItem("Continue Watching", history.take(15).map {
                        Movie(it.tmdbId, it.title, null, it.posterPath, null, null, null, null,null, it.mediaType)
                    }))
                }

                // 2. My Library (star emoji)
                val favorites = withContext(Dispatchers.IO) { AppDatabase.get(applicationContext).movieDao().getFavorites() }
                if (favorites.isNotEmpty()) {
                    rows.add(RowItem("My Library ⭐", favorites.take(15).map {
                        Movie(it.tmdbId, it.title, null, it.posterPath, null, null, null, null, null, it.mediaType)
                    }))
                }

                // 3. Recommended for you
                if (history.size >= 3) {
                    val lastWatched = history.first()
                    try {
                        val recommendations = if (lastWatched.mediaType == "tv") {
                            RetrofitClient.tmdb.getTvRecommendations(lastWatched.tmdbId, key)
                        } else {
                            RetrofitClient.tmdb.getMovieRecommendations(lastWatched.tmdbId, key)
                        }
                        if (recommendations.results.isNotEmpty()) {
                            rows.add(RowItem("Recommended for you", recommendations.results.map { 
                                it.copy(media_type = lastWatched.mediaType) 
                            }))
                        }
                    } catch (e: Exception) { Log.e("MainActivity", "Rec error", e) }
                }

                // Hero and Data Fetching
                val trendingRes = RetrofitClient.tmdb.getTrending(key)
                setupHeroCarousel(trendingRes.results)

                rows.add(RowItem("Only on Flixorent", RetrofitClient.tmdb.getPopular(key).results.shuffled().map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Trending Today 🔥", trendingRes.results))
                rows.add(RowItem("New Releases", RetrofitClient.tmdb.getInTheaters(key).results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Trending Movies", RetrofitClient.tmdb.getTrendingMovies(key).results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Trending Series", RetrofitClient.tmdb.getTrendingTV(key).results.map { it.copy(media_type = "tv") }))
                rows.add(RowItem("Top Movies", RetrofitClient.tmdb.getPopular(key).results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Top Series", RetrofitClient.tmdb.getPopularTV(key).results.map { it.copy(media_type = "tv") }))
                rows.add(RowItem("All Time Movies", RetrofitClient.tmdb.getTopRated(key).results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("All Time Series", RetrofitClient.tmdb.getTopRatedTV(key).results.map { it.copy(media_type = "tv") }))

                // Network Content (Provider Rows)
                rows.add(RowItem("Netflix Originals", RetrofitClient.tmdb.getByProvider(key, "8").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Disney+", RetrofitClient.tmdb.getByProvider(key, "337").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Apple TV+", RetrofitClient.tmdb.getByProvider(key, "350").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("HBO Max", RetrofitClient.tmdb.getByProvider(key, "1899").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Paramount Plus", RetrofitClient.tmdb.getByProvider(key, "531").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Streaming on Hulu", RetrofitClient.tmdb.getByProvider(key, "15").results.map { it.copy(media_type = "movie") }))

                // Genres
                rows.add(RowItem("Action", RetrofitClient.tmdb.getByGenre(key, "28").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Adventure", RetrofitClient.tmdb.getByGenre(key, "12").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Animation", RetrofitClient.tmdb.getByGenre(key, "16").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Comedy", RetrofitClient.tmdb.getByGenre(key, "35").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Crime", RetrofitClient.tmdb.getByGenre(key, "80").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Documentaries", RetrofitClient.tmdb.getByGenre(key, "99").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Drama", RetrofitClient.tmdb.getByGenre(key, "18").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Fantasy", RetrofitClient.tmdb.getByGenre(key, "14").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Horror", RetrofitClient.tmdb.getByGenre(key, "27").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Musical", RetrofitClient.tmdb.getByGenre(key, "10402").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Mystery", RetrofitClient.tmdb.getByGenre(key, "9648").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Romance", RetrofitClient.tmdb.getByGenre(key, "10749").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Sci-Fi", RetrofitClient.tmdb.getByGenre(key, "878").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Thriller", RetrofitClient.tmdb.getByGenre(key, "531").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Western", RetrofitClient.tmdb.getByGenre(key, "37").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("War", RetrofitClient.tmdb.getByGenre(key, "10752").results.map { it.copy(media_type = "movie") }))

                // TV Specific
                rows.add(RowItem("Reality TV", RetrofitClient.tmdb.getTvByGenre(key, "10764").results.map { it.copy(media_type = "tv") }))
                rows.add(RowItem("Talk Shows", RetrofitClient.tmdb.getTvByGenre(key, "10767").results.map { it.copy(media_type = "tv") }))
                rows.add(RowItem("Soap Operas", RetrofitClient.tmdb.getTvByGenre(key, "10766").results.map { it.copy(media_type = "tv") }))
                rows.add(RowItem("Sci-Fi & Fantasy TV", RetrofitClient.tmdb.getTvByGenre(key, "10765").results.map { it.copy(media_type = "tv") }))
                rows.add(RowItem("Anime Hits", RetrofitClient.tmdb.getAnime(key).results.map { it.copy(media_type = "movie") }))

                // 3. Final Render
                if (!isSearching) {
                    binding.mainRecycler.adapter = MainAdapter(rows, watchedIds) { movie ->
                        showHistoryDeleteDialog(movie)
                    }
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Load error", e)
            } finally {
                hideLoading()
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun setupNavigation() {
        binding.navView.setNavigationItemSelectedListener { item ->
            // DEFAULT: Show the movie list container unless we go to Trakt
            binding.swipeRefresh.visibility = View.VISIBLE
            binding.fragmentContainer.visibility = View.GONE
            binding.layoutUpNext.visibility = View.GONE
            isSearching = false
            binding.searchView.visibility = View.GONE

            when (item.itemId) {
                R.id.nav_home -> {
                    currentViewMode = "HOME"
                    binding.heroViewPager.visibility = View.VISIBLE
                    loadHomeContent()
                }
                R.id.nav_movies -> {
                    currentViewMode = "MOVIES"
                    binding.heroViewPager.visibility = View.GONE
                    loadMoviesOnly()
                }
                R.id.nav_tv -> {
                    currentViewMode = "TV"
                    binding.heroViewPager.visibility = View.GONE
                    loadTvOnly()
                }
                R.id.nav_anime -> {
                    currentViewMode = "ANIME"
                    binding.heroViewPager.visibility = View.GONE
                    loadAnimeOnly()
                }
                R.id.nav_downloads -> {
                    startActivity(Intent(this, DownloadsActivity::class.java))
                }
                R.id.nav_trakt_sync -> {
                    currentViewMode = "TRAKT"
                    binding.heroViewPager.visibility = View.GONE
                    binding.swipeRefresh.visibility = View.GONE
                    openTraktDashboard()
                }
                R.id.nav_history -> {
                    currentViewMode = "HISTORY"
                    binding.heroViewPager.visibility = View.GONE
                    loadHistoryPage()
                }
                R.id.nav_lib_movies -> {
                    currentViewMode = "LIB_MOVIES"
                    binding.heroViewPager.visibility = View.GONE
                    loadLibraryMovies()
                }
                R.id.nav_lib_tv -> {
                    currentViewMode = "LIB_TV"
                    binding.heroViewPager.visibility = View.GONE
                    loadLibraryTv()
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun loadMoviesOnly() {
        lifecycleScope.launch {
            showLoading()
            val rows = mutableListOf<RowItem>()
            val key = Constants.TMDB_KEY
            try {
                rows.add(RowItem("Popular Movies", RetrofitClient.tmdb.getPopular(key).results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Top Rated", RetrofitClient.tmdb.getTopRated(key).results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Action", RetrofitClient.tmdb.getByGenre(key, "28").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Comedy", RetrofitClient.tmdb.getByGenre(key, "35").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Horror", RetrofitClient.tmdb.getByGenre(key, "27").results.map { it.copy(media_type = "movie") }))
                binding.mainRecycler.adapter = MainAdapter(rows, watchedIds)
            } catch (e: Exception) { e.printStackTrace() }
            hideLoading()
        }
    }

    private fun loadTvOnly() {
        lifecycleScope.launch {
            showLoading()
            val rows = mutableListOf<RowItem>()
            val key = Constants.TMDB_KEY
            try {
                rows.add(RowItem("Popular Series", RetrofitClient.tmdb.getPopularTV(key).results.map { it.copy(media_type = "tv") }))
                rows.add(RowItem("Top Rated Series", RetrofitClient.tmdb.getTopRatedTV(key).results.map { it.copy(media_type = "tv") }))
                rows.add(RowItem("Animation", RetrofitClient.tmdb.getTvByGenre(key, "16").results.map { it.copy(media_type = "tv") }))
                rows.add(RowItem("Sci-Fi & Fantasy", RetrofitClient.tmdb.getTvByGenre(key, "10765").results.map { it.copy(media_type = "tv") }))
                binding.mainRecycler.adapter = MainAdapter(rows, watchedIds)
            } catch (e: Exception) { e.printStackTrace() }
            hideLoading()
        }
    }

    private fun loadAnimeOnly() {
        lifecycleScope.launch {
            showLoading()
            val rows = mutableListOf<RowItem>()
            val key = Constants.TMDB_KEY
            try {
                rows.add(RowItem("Anime Hits", RetrofitClient.tmdb.getAnime(key).results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Latest Anime Movies", RetrofitClient.tmdb.getByGenre(key, "16").results.map { it.copy(media_type = "movie") }))
                rows.add(RowItem("Trending Anime TV", RetrofitClient.tmdb.getTvByGenre(key, "16").results.map { it.copy(media_type = "tv") }))
                binding.mainRecycler.adapter = MainAdapter(rows, watchedIds)
            } catch (e: Exception) { e.printStackTrace() }
            hideLoading()
        }
    }

    private fun loadHistoryPage() {
        lifecycleScope.launch {
            showLoading()
            try {
                val history = withContext(Dispatchers.IO) { AppDatabase.get(applicationContext).movieDao().getHistory() }
                val movies = history.map {
                    Movie(it.tmdbId, it.title, null, it.posterPath, null, null, null, null, null, it.mediaType)
                }
                val rows = listOf(RowItem("Watch History", movies))
                binding.mainRecycler.adapter = MainAdapter(rows, watchedIds) { movie ->
                    showHistoryDeleteDialog(movie)
                }
            } catch (e: Exception) { e.printStackTrace() }
            hideLoading()
        }
    }

    private fun loadLibraryMovies() {
        lifecycleScope.launch {
            showLoading()
            try {
                val favorites = withContext(Dispatchers.IO) { AppDatabase.get(applicationContext).movieDao().getFavorites() }
                val movies = favorites.filter { it.mediaType == "movie" }.map {
                    Movie(it.tmdbId, it.title, null, it.posterPath, null, null, null, null, null, "movie")
                }
                val rows = listOf(RowItem("My Movies", movies))
                binding.mainRecycler.adapter = MainAdapter(rows, watchedIds)
            } catch (e: Exception) { e.printStackTrace() }
            hideLoading()
        }
    }

    private fun loadLibraryTv() {
        lifecycleScope.launch {
            showLoading()
            try {
                val favorites = withContext(Dispatchers.IO) { AppDatabase.get(applicationContext).movieDao().getFavorites() }
                val tvShows = favorites.filter { it.mediaType == "tv" }.map {
                    Movie(it.tmdbId, it.title, null, it.posterPath, null, null, null, null, null, "tv")
                }
                val rows = listOf(RowItem("My TV Shows", tvShows))
                binding.mainRecycler.adapter = MainAdapter(rows, watchedIds)
            } catch (e: Exception) { e.printStackTrace() }
            hideLoading()
        }
    }

    private fun setupHeroCarousel(trending: List<Movie>) {
        val topItems = trending.take(5)
        binding.heroViewPager.adapter = HeroAdapter(topItems) { movie, sharedView ->
            val intent = Intent(this, DetailsActivity::class.java).apply {
                putExtra("TMDB_ID", movie.id)
                putExtra("MOVIE_TITLE", movie.title)
                putExtra("POSTER", movie.poster_path)
                putExtra("MEDIA_TYPE", movie.media_type ?: "movie")
            }
            startActivity(intent, ActivityOptionsCompat.makeSceneTransitionAnimation(this, sharedView, "poster_transition").toBundle())
        }
        sliderHandler.removeCallbacks(sliderRunnable)
        sliderHandler.postDelayed(sliderRunnable, 5000)
    }

    private fun search(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(500) // Debounce search
            showLoading()
            try {
                val movieRes = RetrofitClient.tmdb.search(Constants.TMDB_KEY, query)
                val tvRes = RetrofitClient.tmdb.searchTv(Constants.TMDB_KEY, query)
                val rows = mutableListOf<RowItem>()
                if (movieRes.results.isNotEmpty()) rows.add(RowItem("Movies: $query", movieRes.results.map { it.copy(media_type = "movie") }))
                if (tvRes.results.isNotEmpty()) rows.add(RowItem("TV Series: $query", tvRes.results.map { it.copy(media_type = "tv") }))
                binding.mainRecycler.adapter = MainAdapter(rows, watchedIds)
            } catch (e: Exception) { e.printStackTrace() }
            hideLoading()
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrEmpty()) search(query)
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (!newText.isNullOrEmpty()) {
                    isSearching = true
                    binding.heroViewPager.visibility = View.GONE
                    search(newText)
                } else if (newText.isNullOrEmpty() && isSearching) {
                   // Optional: Clear results or show home if empty
                }
                return true
            }
        })

        binding.searchView.setOnCloseListener {
            closeSearch()
            true
        }
    }

    private fun showLoading() {
        binding.shimmerViewContainer.visibility = View.VISIBLE
        binding.shimmerViewContainer.startShimmer()
        // Hide the recycler so the shimmer is the only thing visible
        binding.swipeRefresh.visibility = View.GONE
    }

    private fun hideLoading() {
        binding.shimmerViewContainer.stopShimmer()
        binding.shimmerViewContainer.visibility = View.GONE
        // FORCE the list to show up now that data is ready
        binding.swipeRefresh.visibility = View.VISIBLE
        binding.mainRecycler.visibility = View.VISIBLE
        binding.swipeRefresh.isRefreshing = false
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller?.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun resetUI() {
        // 1. Hide the "Home-only" features
        binding.heroViewPager.visibility = View.GONE

        // 2. Hide the Fragment (Trakt Dashboard)
        binding.fragmentContainer.visibility = View.GONE

        // 3. Show the Recycler container (the list)
        binding.swipeRefresh.visibility = View.VISIBLE
        binding.mainRecycler.visibility = View.VISIBLE

        // 4. Stop any accidental loading spinners
        binding.swipeRefresh.isRefreshing = false
    }

    private fun openTraktDashboard() {
        resetUI()
        // Trakt is a fragment, so we DON'T want the recycler showing
        binding.swipeRefresh.visibility = View.GONE
        binding.fragmentContainer.visibility = View.VISIBLE

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, TraktFragment())
            .commit()
    }

    private fun showHistoryDeleteDialog(movie: Movie) {
        MaterialAlertDialogBuilder(this).setTitle("Remove from History?")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(applicationContext).movieDao().deleteHistory(movie.id)
                    withContext(Dispatchers.Main) { 
                        if (currentViewMode == "HOME") loadHomeContent() else if (currentViewMode == "HISTORY") loadHistoryPage()
                    }
                }
            }.setNegativeButton("Cancel", null).show()
    }
}