package com.adeloc.app.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.adeloc.app.data.api.RetrofitClient
import com.adeloc.app.data.manager.TraktTokenManager
import com.adeloc.app.data.model.Movie
import com.adeloc.app.databinding.FragmentTraktBinding
import com.adeloc.app.ui.SettingsActivity
import com.adeloc.app.ui.adapters.MovieAdapter
import com.adeloc.app.utils.Constants
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class TraktFragment : Fragment() {

    private var _binding: FragmentTraktBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTraktBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        val tokenManager = TraktTokenManager(requireContext())
        if (tokenManager.getAccessToken() != null) {
            binding.layoutConnected.visibility = View.VISIBLE
            binding.layoutDisconnected.visibility = View.GONE
            loadTraktData()
        } else {
            binding.layoutConnected.visibility = View.GONE
            binding.layoutDisconnected.visibility = View.VISIBLE
            binding.btnConnectTrakt.setOnClickListener {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }
        }
    }

    private fun loadTraktData() {
        binding.progressBar.visibility = View.VISIBLE
        val tokenManager = TraktTokenManager(requireContext())
        val token = tokenManager.getAccessToken() ?: return
        val authHeader = "Bearer $token"
        val apiKey = Constants.TRAKT_CLIENT_ID

        lifecycleScope.launch {
            try {
                val traktApi = RetrofitClient.trakt

                val upNextDef = async { try { traktApi.getUpNext(authHeader, apiKey, "full", 10) } catch(e: Exception) { emptyList() } }
                val calendarDef = async { try { traktApi.getCalendar(authHeader, apiKey, "full") } catch(e: Exception) { emptyList() } }
                val watchlistMoviesDef = async { try { traktApi.getWatchlistMovies(authHeader, apiKey, "full", "added") } catch(e: Exception) { emptyList() } }
                val watchlistShowsDef = async { try { traktApi.getWatchlistShows(authHeader, apiKey, "full", "added") } catch(e: Exception) { emptyList() } }
                val historyDef = async { try { traktApi.getHistory(authHeader, apiKey, "full", 10) } catch(e: Exception) { emptyList() } }

                val rawUpNext = upNextDef.await()
                val rawCalendar = calendarDef.await()
                val rawWatchlistMovies = watchlistMoviesDef.await()
                val rawWatchlistShows = watchlistShowsDef.await()
                val rawHistory = historyDef.await()

                val tmdbKey = Constants.TMDB_KEY

                // 1. Up Next
                if (rawUpNext.isNotEmpty()) {
                    val movies = rawUpNext.mapNotNull { item ->
                        item.show?.let { show ->
                            val poster = fetchTmdbPoster(show.ids.tmdb, "tv", tmdbKey)
                            Movie(show.ids.tmdb ?: 0, show.title ?: "", null, poster, null, null, null, null, null, "tv")
                        }
                    }
                    if (movies.isNotEmpty()) {
                        binding.sectionUpNext.root.visibility = View.VISIBLE
                        binding.sectionUpNext.categoryTitle.text = "Up Next to Watch"
                        binding.sectionUpNext.childRecycler.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                        binding.sectionUpNext.childRecycler.adapter = MovieAdapter(movies)
                    } else binding.sectionUpNext.root.visibility = View.GONE
                } else binding.sectionUpNext.root.visibility = View.GONE

                // 2. Upcoming
                if (rawCalendar.isNotEmpty()) {
                    val movies = rawCalendar.mapNotNull { item ->
                        item.show?.let { show ->
                            val poster = fetchTmdbPoster(show.ids.tmdb, "tv", tmdbKey)
                            Movie(show.ids.tmdb ?: 0, show.title ?: "", null, poster, null, null, null, null, null, "tv")
                        }
                    }
                    if (movies.isNotEmpty()) {
                        binding.sectionUpcoming.root.visibility = View.VISIBLE
                        binding.sectionUpcoming.categoryTitle.text = "Upcoming Schedule"
                        binding.sectionUpcoming.childRecycler.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                        binding.sectionUpcoming.childRecycler.adapter = MovieAdapter(movies)
                    } else binding.sectionUpcoming.root.visibility = View.GONE
                } else binding.sectionUpcoming.root.visibility = View.GONE

                // 3. Watchlist Movies
                if (rawWatchlistMovies.isNotEmpty()) {
                    val movies = rawWatchlistMovies.mapNotNull { item ->
                        item.movie?.let { movie ->
                            val poster = fetchTmdbPoster(movie.ids.tmdb, "movie", tmdbKey)
                            Movie(movie.ids.tmdb ?: 0, movie.title ?: "", null, poster, null, null, null, null, null, "movie")
                        }
                    }
                    if (movies.isNotEmpty()) {
                        binding.sectionWatchlistMovies.root.visibility = View.VISIBLE
                        binding.sectionWatchlistMovies.categoryTitle.text = "My Watchlist (Movies)"
                        binding.sectionWatchlistMovies.childRecycler.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                        binding.sectionWatchlistMovies.childRecycler.adapter = MovieAdapter(movies)
                    } else binding.sectionWatchlistMovies.root.visibility = View.GONE
                } else binding.sectionWatchlistMovies.root.visibility = View.GONE

                // 4. Watchlist Shows
                if (rawWatchlistShows.isNotEmpty()) {
                    val movies = rawWatchlistShows.mapNotNull { item ->
                        item.show?.let { show ->
                            val poster = fetchTmdbPoster(show.ids.tmdb, "tv", tmdbKey)
                            Movie(show.ids.tmdb ?: 0, show.title ?: "", null, poster, null, null, null, null, null, "tv")
                        }
                    }
                    if (movies.isNotEmpty()) {
                        binding.sectionWatchlistShows.root.visibility = View.VISIBLE
                        binding.sectionWatchlistShows.categoryTitle.text = "My Watchlist (Shows)"
                        binding.sectionWatchlistShows.childRecycler.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                        binding.sectionWatchlistShows.childRecycler.adapter = MovieAdapter(movies)
                    } else binding.sectionWatchlistShows.root.visibility = View.GONE
                } else binding.sectionWatchlistShows.root.visibility = View.GONE

                // 5. History
                if (rawHistory.isNotEmpty()) {
                    val movies = rawHistory.mapNotNull { item ->
                        item.movie?.let { movie ->
                            val poster = fetchTmdbPoster(movie.ids.tmdb, "movie", tmdbKey)
                            Movie(movie.ids.tmdb ?: 0, movie.title ?: "", null, poster, null, null, null, null, null, "movie")
                        }
                    }
                    if (movies.isNotEmpty()) {
                        binding.sectionHistory.root.visibility = View.VISIBLE
                        binding.sectionHistory.categoryTitle.text = "Recently Watched"
                        binding.sectionHistory.childRecycler.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                        binding.sectionHistory.childRecycler.adapter = MovieAdapter(movies)
                    } else binding.sectionHistory.root.visibility = View.GONE
                } else binding.sectionHistory.root.visibility = View.GONE

            } catch (e: Exception) {
                Log.e("TraktFragment", "Error loading data", e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun fetchTmdbPoster(tmdbId: Int?, type: String, apiKey: String): String? {
        if (tmdbId == null) return null
        return try {
            if (type == "movie") {
                val details = RetrofitClient.tmdb.getMovieDetails(tmdbId, apiKey)
                // Fix: use poster_path instead of backdrop_path to avoid "zoomed in" effect in portrait containers
                details.poster_path?.let { "/$it" }
            } else {
                val details = RetrofitClient.tmdb.getTvDetails(tmdbId, apiKey)
                // Fix: use poster_path instead of season posters for consistency
                details.poster_path?.let { "/$it" }
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}