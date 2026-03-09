package com.adeloc.app.ui

import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.adeloc.app.R
import com.adeloc.app.data.api.*
import com.adeloc.app.data.db.AppDatabase
import com.adeloc.app.data.db.DownloadedMedia
import com.adeloc.app.data.db.WatchEntity
import com.adeloc.app.data.db.WatchHistoryItem
import com.adeloc.app.data.manager.TraktTokenManager
import com.adeloc.app.data.model.StreamSource
import com.adeloc.app.data.scraper.WebScraper
import com.adeloc.app.databinding.ActivityPlayerBinding
import com.adeloc.app.service.TorrentService
import com.adeloc.app.utils.Constants
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration
import com.tonyodev.fetch2.NetworkType
import com.tonyodev.fetch2.Priority
import com.tonyodev.fetch2.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var player: ExoPlayer

    private var castContext: CastContext? = null
    private lateinit var castSessionManagerListener: SessionManagerListener<CastSession>

    private var tmdbId = 0
    private var movieTitle = ""
    private var posterPath = ""
    private var currentSeason = 1
    private var currentEpisode = 1
    private var isTvShow = false
    private var isFilePlaying = false
    private var passedMediaType: String = "movie"

    private var foundSources: List<StreamSource> = emptyList()
    private var currentSource: StreamSource? = null
    private var isTrackerRunning = false
    private var isTemporaryStreaming = false
    
    private lateinit var traktTokenManager: TraktTokenManager
    private val TRAKT_CLIENT_ID = "4678a15d7ee291184d83d3b031101b8eb916aecf6ee5680d7b6145bbd0aadee0"
    private var isScrobblingStarted = false
    private var scrobbleJob: Job? = null
    
    private var isMarkedWatched = false

    private val hideOverlayHandler = Handler(Looper.getMainLooper())
    private val hideOverlayRunnable = Runnable {
        binding.gestureControlsOverlay.visibility = View.GONE
        binding.layoutVolumeControl.visibility = View.GONE
        binding.layoutBrightnessControl.visibility = View.GONE
        binding.layoutSeekControl.visibility = View.GONE
    }
    private var initialX = 0f
    private var initialY = 0f
    private var initialVolume = 0
    private var initialBrightness = 0f
    private var initialPosition = 0L
    private var targetSeekPosition = 0L
    private var isControlActive = false
    private var gestureMode = "none"

    private val torrentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.adeloc.app.TORRENT_READY" -> {
                    val rawUrl = intent.getStringExtra(TorrentService.EXTRA_URL)
                    if (rawUrl != null) {
                        try {
                            val decodedUrl = URLDecoder.decode(rawUrl, "UTF-8")
                            initializePlayer(decodedUrl)
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
                "com.adeloc.app.TORRENT_PROGRESS" -> {
                    val percent = intent.getIntExtra(TorrentService.EXTRA_PERCENT, 0)
                    binding.torrentPercentText.text = "$percent%"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()
        
        traktTokenManager = TraktTokenManager(this)

        tmdbId = intent.getIntExtra("TMDB_ID", intent.getIntExtra("tmdbId", 0))
        movieTitle = intent.getStringExtra("MOVIE_TITLE") ?: ""
        posterPath = intent.getStringExtra("POSTER") ?: ""
        passedMediaType = intent.getStringExtra("MEDIA_TYPE") ?: "movie"

        currentSeason = intent.getIntExtra("SEASON", intent.getIntExtra("seasonNumber", 1))
        currentEpisode = intent.getIntExtra("EPISODE", intent.getIntExtra("episodeNumber", 1))
        isTvShow = intent.getBooleanExtra("isTvShow", intent.hasExtra("SEASON") || passedMediaType == "tv")

        setupPlayer()
        setupCast()
        setupUI()
        setupGestureControls()
        registerTorrentReceiver()

        val videoUrl = intent.getStringExtra("url") ?: intent.getStringExtra("videoUrl")

        if (videoUrl != null && (videoUrl.startsWith("content://") || videoUrl.startsWith("file://"))) {
            hideAllLoading()
            initializePlayer(videoUrl)
        } else if (!videoUrl.isNullOrEmpty()) {
            hideAllLoading()
            prepareMedia(videoUrl)
        } else if (tmdbId != 0) {
            startSearchProcess()
        }
    }

    private fun registerTorrentReceiver() {
        val filter = IntentFilter().apply {
            addAction("com.adeloc.app.TORRENT_READY")
            addAction("com.adeloc.app.TORRENT_PROGRESS")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(torrentReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(torrentReceiver, filter)
        }
    }

    private fun showLottieLoading() {
        binding.loadingAnimation.visibility = View.VISIBLE
        binding.loadingBar.visibility = View.GONE
    }

    private fun showStandardLoading() {
        binding.loadingBar.visibility = View.VISIBLE
        binding.loadingAnimation.visibility = View.GONE
    }

    private fun hideAllLoading() {
        binding.loadingAnimation.visibility = View.GONE
        binding.loadingBar.visibility = View.GONE
    }

    private fun initializePlayer(videoUrl: String) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideSystemUI()
        
        isFilePlaying = true
        binding.torrentLoadingLayout.visibility = View.GONE
        binding.topControls.visibility = View.GONE
        binding.infoText.visibility = View.GONE
        binding.playerView.visibility = View.VISIBLE

        val mediaItem = if (videoUrl.startsWith("file://")) {
            val path = Uri.parse(videoUrl).path ?: ""
            MediaItem.fromUri(Uri.fromFile(File(path)))
        } else {
            MediaItem.fromUri(Uri.parse(videoUrl))
        }

        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        startProgressTracker()
        updatePipParams()
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true)
            }
            setPictureInPictureParams(builder.build())
        }
    }

    private fun setupGestureControls() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        binding.playerView.setOnTouchListener { v, event ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.x
                    initialY = event.y
                    initialPosition = player.currentPosition
                    initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    initialBrightness = window.attributes.screenBrightness.let { if (it < 0) 0.5f else it }
                    gestureMode = "none"
                    isControlActive = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - initialX
                    val deltaY = initialY - event.y
                    if (gestureMode == "none") {
                        if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 50) gestureMode = "seek"
                        else if (Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > 50) gestureMode = "volume_brightness"
                        if (gestureMode != "none") {
                            isControlActive = true
                            binding.playerView.hideController()
                        }
                    }
                    if (gestureMode == "seek") {
                        val seekChange = (deltaX / v.width) * 90000
                        targetSeekPosition = (initialPosition + seekChange).toLong().coerceIn(0, player.duration)
                        val diff = (targetSeekPosition - initialPosition) / 1000
                        binding.seekIcon.setImageResource(if (deltaX >= 0) R.drawable.ic_fast_forward else R.drawable.ic_fast_rewind)
                        showSeekOverlay(formatTime(targetSeekPosition), if (diff >= 0) "+$diff" else "$diff")
                    } else if (gestureMode == "volume_brightness") {
                        val percentChange = deltaY / v.height
                        if (initialX < v.width / 2) {
                            val newBrightness = (initialBrightness + percentChange).coerceIn(0.01f, 1.0f)
                            window.attributes = window.attributes.apply { screenBrightness = newBrightness }
                            showBrightnessOverlay((newBrightness * 100).toInt())
                        } else {
                            val newVolume = (initialVolume + (percentChange * maxVolume)).toInt().coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                            showVolumeOverlay((newVolume.toFloat() / maxVolume * 100).toInt())
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isControlActive) {
                        if (gestureMode == "seek" && event.action == MotionEvent.ACTION_UP) player.seekTo(targetSeekPosition)
                        hideOverlayHandler.postDelayed(hideOverlayRunnable, 500)
                    } else if (event.action == MotionEvent.ACTION_UP) {
                        if (binding.playerView.isControllerFullyVisible) binding.playerView.hideController() else binding.playerView.showController()
                    }
                    isControlActive = false
                    gestureMode = "none"
                    true
                }
                else -> false
            }
        }
    }

    private fun showVolumeOverlay(percent: Int) {
        hideOverlayHandler.removeCallbacks(hideOverlayRunnable)
        binding.gestureControlsOverlay.visibility = View.VISIBLE
        binding.layoutVolumeControl.visibility = View.VISIBLE
        binding.layoutBrightnessControl.visibility = View.GONE
        binding.layoutSeekControl.visibility = View.GONE
        binding.volumeText.text = "$percent%"
    }

    private fun showBrightnessOverlay(percent: Int) {
        hideOverlayHandler.removeCallbacks(hideOverlayRunnable)
        binding.gestureControlsOverlay.visibility = View.VISIBLE
        binding.layoutBrightnessControl.visibility = View.VISIBLE
        binding.layoutVolumeControl.visibility = View.GONE
        binding.layoutSeekControl.visibility = View.GONE
        binding.brightnessText.text = "$percent%"
    }

    private fun showSeekOverlay(time: String, diff: String) {
        hideOverlayHandler.removeCallbacks(hideOverlayRunnable)
        binding.gestureControlsOverlay.visibility = View.VISIBLE
        binding.layoutSeekControl.visibility = View.VISIBLE
        binding.layoutVolumeControl.visibility = View.GONE
        binding.layoutBrightnessControl.visibility = View.GONE
        binding.seekText.text = "$time ($diff s)"
    }

    private fun formatTime(timeMs: Long): String {
        val s = timeMs / 1000
        val m = (s / 60) % 60
        val h = s / 3600
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s % 60) else String.format("%02d:%02d", m, s % 60)
    }

    private fun setupCast() {
        try {
            castContext = CastContext.getSharedInstance(this)
            CastButtonFactory.setUpMediaRouteButton(applicationContext, binding.mediaRouteButton)
            castSessionManagerListener = object : SessionManagerListener<CastSession> {
                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    currentSource?.url?.let { castVideoToTv(it, player.currentPosition) }
                }
                override fun onSessionEnded(session: CastSession, error: Int) {}
                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {}
                override fun onSessionStarting(session: CastSession) {}
                override fun onSessionEnding(session: CastSession) {}
                override fun onSessionResuming(session: CastSession, sessionId: String) {}
                override fun onSessionStartFailed(session: CastSession, error: Int) {}
                override fun onSessionResumeFailed(session: CastSession, error: Int) {}
                override fun onSessionSuspended(session: CastSession, reason: Int) {}
            }
        } catch (e: Exception) { binding.mediaRouteButton.visibility = View.GONE }
    }

    override fun onResume() {
        super.onResume()
        castContext?.sessionManager?.addSessionManagerListener(castSessionManagerListener, CastSession::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInPictureInPictureMode) hideSystemUI()
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player.isPlaying) {
            hideOverlayHandler.removeCallbacks(hideOverlayRunnable)
            binding.gestureControlsOverlay.visibility = View.GONE
            val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(true)
            enterPictureInPictureMode(builder.build())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            hideOverlayHandler.removeCallbacks(hideOverlayRunnable)
            binding.gestureControlsOverlay.visibility = View.GONE
            binding.playerView.useController = false
            binding.nextEpisodeBtn.visibility = View.GONE
            binding.topControls.visibility = View.GONE
        } else {
            binding.playerView.useController = true
            if (isTvShow && player.duration > 0 && player.currentPosition > (player.duration * 0.95)) binding.nextEpisodeBtn.visibility = View.VISIBLE
            hideSystemUI()
        }
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) return
        if (player.currentPosition > 0) saveProgress(player.currentPosition, player.duration)
        player.pause()
        castContext?.sessionManager?.removeSessionManagerListener(castSessionManagerListener, CastSession::class.java)
        if (isScrobblingStarted) stopTraktScrobble()
    }

    private fun castVideoToTv(url: String, position: Long) {
        val session = castContext?.sessionManager?.currentCastSession ?: return
        if (!session.isConnected) return
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, movieTitle)
            if (posterPath.isNotEmpty()) addImage(WebImage(Uri.parse("https://image.tmdb.org/t/p/w500$posterPath")))
        }
        val mediaInfo = MediaInfo.Builder(url).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setContentType("video/mp4").setMetadata(metadata).build()
        session.remoteMediaClient?.load(mediaInfo, MediaLoadOptions.Builder().setAutoplay(true).setPlayPosition(position).build())
        if (player.isPlaying) player.pause()
        saveProgress(position, player.duration)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (binding.playerView.isControllerFullyVisible) { if (player.isPlaying) player.pause() else player.play() }
                    else binding.playerView.showController()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> { player.seekBack(); binding.playerView.showController(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { player.seekForward(); binding.playerView.showController(); return true }
                KeyEvent.KEYCODE_BACK -> if (binding.playerView.isControllerFullyVisible) { binding.playerView.hideController(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun startSearchProcess() {
        binding.infoText.text = getString(R.string.searching_sources)
        findAllLinks()
    }

    private fun findAllLinks() {
        lifecycleScope.launch(Dispatchers.IO) {
            val sources = mutableListOf<StreamSource>()
            val trackers = listOf("udp://tracker.opentrackr.org:1337/announce", "udp://open.stealth.si:80/announce", "udp://tracker.torrent.eu.org:451/announce")
            val trackerParams = trackers.joinToString("") { "&tr=$it" }
            try {
                val ids = if (isTvShow) RetrofitClient.tmdb.getTvIds(tmdbId, Constants.TMDB_KEY) else RetrofitClient.tmdb.getIds(tmdbId, Constants.TMDB_KEY)
                ids.imdb_id?.let { imdb ->
                    val torrents = if (isTvShow) RetrofitClient.torrentio.getSeriesStreams("$imdb:$currentSeason:$currentEpisode") else RetrofitClient.torrentio.getStreams(imdb)
                    torrents.streams?.forEach {
                        if (!it.title.contains(".avi", ignoreCase = true)) {
                            val cleanTitle = it.title.replace("\n", " ")
                            val magnet = "magnet:?xt=urn:btih:${it.infoHash}&dn=${Uri.encode(cleanTitle)}$trackerParams"
                            val size = "\\b(\\d+(?:\\.\\d+)?\\s*[GM]B)\\b".toRegex(RegexOption.IGNORE_CASE).find(it.title)?.value ?: "Unknown"
                            val seeds = "👤\\s*(\\d+)".toRegex().find(it.title)?.groupValues?.get(1) ?: "?"
                            val quality = if (cleanTitle.contains("4k", true)) "4K" else if (cleanTitle.contains("1080", true)) "1080p" else "HD"
                            sources.add(StreamSource(cleanTitle, magnet, "Torrentio | $quality | S:$seeds | $size", true))
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            try { sources.addAll(WebScraper.scrapeWeb(if (isTvShow) "$movieTitle S${currentSeason}E${currentEpisode}" else movieTitle)) } catch (e: Exception) { e.printStackTrace() }
            val sorted = sortSources(sources)
            withContext(Dispatchers.Main) { hideAllLoading(); foundSources = sorted; showSourceDialog(sorted) }
        }
    }

    private fun sortSources(sources: List<StreamSource>): List<StreamSource> {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val preferredLangs = prefs.getStringSet("pref_languages", setOf("en")) ?: setOf("en")
        return sources.sortedWith(compareByDescending<StreamSource> { s -> preferredLangs.any { l -> s.name.contains(getLanguageName(l), true) || s.name.contains("[$l]", true) } }.thenByDescending { if (it.quality.contains("4K")) 3 else if (it.quality.contains("1080")) 2 else 0 }.thenByDescending { try { it.quality.substringAfter("S:").substringBefore("|").trim().toInt() } catch(e: Exception) { 0 } })
    }

    private fun getLanguageName(code: String) = when(code) { "en" -> "English"; "es" -> "Spanish"; "fr" -> "French"; "de" -> "German"; "it" -> "Italian"; "pt" -> "Portuguese"; "hi" -> "Hindi"; "ru" -> "Russian"; "ja" -> "Japanese"; "ko" -> "Korean"; "zh" -> "Chinese"; "ar" -> "Arabic"; else -> "" }

    private fun setupUI() {
        showStandardLoading()
        binding.infoText.text = getString(R.string.checking_details)
        binding.playerView.visibility = View.GONE
        binding.downloadBtn.setOnClickListener { if (foundSources.isNotEmpty()) showSourceDialog(foundSources) }
        binding.nextEpisodeBtn.setOnClickListener { handleNextEpisodeClick() }
        binding.playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) return@ControllerVisibilityListener
            if (!isFilePlaying) { binding.topControls.visibility = visibility; binding.infoText.visibility = visibility }
            else { binding.topControls.visibility = View.GONE; binding.infoText.visibility = View.GONE }
        })
    }

    private fun handleNextEpisodeClick() {
        binding.nextEpisodeBtn.visibility = View.GONE
        showLottieLoading()
        binding.infoText.visibility = View.VISIBLE
        binding.infoText.text = "Checking for next episode..."
        lifecycleScope.launch {
            try {
                val tvDetails = RetrofitClient.tmdb.getTvDetails(tmdbId, Constants.TMDB_KEY)
                val curS = tvDetails.seasons.find { it.season_number == currentSeason }
                if (curS != null) {
                    if (currentEpisode < curS.episode_count) currentEpisode++
                    else {
                        if (tvDetails.seasons.any { it.season_number == currentSeason + 1 }) { currentSeason++; currentEpisode = 1 }
                        else { hideAllLoading(); binding.infoText.text = "End of series."; return@launch }
                    }
                    binding.infoText.text = "Loading S$currentSeason E$currentEpisode..."
                    loadNextEpisodeStream(tmdbId, currentSeason, currentEpisode)
                }
            } catch (e: Exception) { currentEpisode++; loadNextEpisodeStream(tmdbId, currentSeason, currentEpisode) }
        }
    }

    private fun loadNextEpisodeStream(tmdbId: Int, season: Int, episode: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val sources = mutableListOf<StreamSource>()
            try {
                val ids = RetrofitClient.tmdb.getTvIds(tmdbId, Constants.TMDB_KEY)
                ids.imdb_id?.let { imdb ->
                    RetrofitClient.torrentio.getSeriesStreams("$imdb:$season:$episode").streams?.forEach {
                        if (!it.title.contains(".avi", true)) {
                            val clean = it.title.replace("\n", " ")
                            val magnet = "magnet:?xt=urn:btih:${it.infoHash}&dn=${Uri.encode(clean)}"
                            val size = "\\b(\\d+(?:\\.\\d+)?\\s*[GM]B)\\b".toRegex(RegexOption.IGNORE_CASE).find(it.title)?.value ?: "Unknown"
                            val seeds = "👤\\s*(\\d+)".toRegex().find(it.title)?.groupValues?.get(1) ?: "?"
                            val qual = if (clean.contains("4k", true)) "4K" else if (clean.contains("1080", true)) "1080p" else "HD"
                            sources.add(StreamSource(clean, magnet, "Torrentio | $qual | S:$seeds | $size", true))
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            try { sources.addAll(WebScraper.scrapeWeb("$movieTitle S${String.format("%02d", season)}E${String.format("%02d", episode)}")) } catch (e: Exception) { e.printStackTrace() }
            val sorted = sortSources(sources)
            withContext(Dispatchers.Main) {
                if (sorted.isNotEmpty()) {
                    currentSource = sorted[0]
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val url = DebridResolver.resolveStream(this@PlayerActivity, sorted[0].url)
                            withContext(Dispatchers.Main) {
                                if (url != null) { hideAllLoading(); binding.infoText.visibility = View.GONE; if (isScrobblingStarted) stopTraktScrobble(); prepareMedia(url) }
                                else { hideAllLoading(); binding.infoText.text = "Failed to resolve link." }
                            }
                        } catch (e: Exception) { withContext(Dispatchers.Main) { hideAllLoading(); binding.infoText.text = "Error: ${e.message}" } }
                    }
                } else { hideAllLoading(); binding.infoText.text = "No sources found." }
            }
        }
    }

    private fun showSourceDialog(sources: List<StreamSource>) {
        if (sources.isEmpty()) { binding.infoText.text = "No links found."; return }
        val dialogView = layoutInflater.inflate(R.layout.dialog_list, null)
        val recycler = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerSources)
        val title = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val cancel = dialogView.findViewById<Button>(R.id.btnCancel)
        title.text = "Select Source (${sources.size} found)"
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recycler.adapter = com.adeloc.app.ui.adapters.SourceAdapter(sources) { s -> dialog.dismiss(); currentSource = s; showActionDialog(s) }
        cancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showActionDialog(source: StreamSource) {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val active = prefs.getString("active_provider", "RD")
        val pName = when(active) { "AD" -> "AllDebrid"; "PM" -> "Premiumize"; "TB" -> "TorBox"; else -> "Real-Debrid" }
        val options = arrayOf("Play In-App ($pName)", "Play External ($pName)", "Play External (No Provider)", "Download (In-App)", "Download (External App)", "Play In-App (Slow)")
        AlertDialog.Builder(this).setTitle(source.name).setItems(options) { _, i ->
            when (i) {
                0 -> resolvePremiumLink(source, true)
                1 -> resolvePremiumLink(source, false)
                2 -> playExternalDirect(source)
                3 -> resolveDownloadLink(source)
                4 -> startExternalDownload(source)
                5 -> startTorrentStream(source)
            }
        }.show()
    }

    private fun resolvePremiumLink(source: StreamSource, playInternally: Boolean) {
        showLottieLoading()
        binding.infoText.visibility = View.VISIBLE
        binding.infoText.text = getString(R.string.resolving_link)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = DebridResolver.resolveStream(this@PlayerActivity, source.url)
                withContext(Dispatchers.Main) {
                    hideAllLoading()
                    binding.infoText.visibility = View.GONE
                    if (url != null) { if (playInternally) checkPositionAndPlay(url) else playExternalUrl(url) }
                    else Toast.makeText(this@PlayerActivity, "Please login in Settings", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { hideAllLoading(); Toast.makeText(this@PlayerActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() } }
        }
    }

    private fun resolveDownloadLink(source: StreamSource) {
        showLottieLoading()
        binding.infoText.visibility = View.VISIBLE
        binding.infoText.text = "Initializing download..."
        lifecycleScope.launch(Dispatchers.IO) {
            var url: String? = if (source.url.startsWith("http")) source.url else null
            if (url == null) {
                try { url = DebridResolver.resolveStream(this@PlayerActivity, source.url) } catch (e: Exception) {}
            }
            withContext(Dispatchers.Main) {
                hideAllLoading()
                binding.infoText.visibility = View.GONE
                if (url != null && url.startsWith("http")) {
                    Toast.makeText(this@PlayerActivity, "Starting Download...", Toast.LENGTH_SHORT).show()
                    startInAppDownload(url)
                } else {
                    MaterialAlertDialogBuilder(this@PlayerActivity)
                        .setTitle("Download Unavailable")
                        .setMessage("In-App downloading requires a Real-Debrid, AllDebrid, or Premiumize account. Free users can stream or use an external downloader.")
                        .setPositiveButton("Settings") { _, _ -> startActivity(Intent(this@PlayerActivity, SettingsActivity::class.java)) }
                        .setNegativeButton("External App") { _, _ -> startExternalDownload(source) }
                        .show()
                }
            }
        }
    }

    private fun startInAppDownload(url: String) {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("download_uri", null) ?: return Toast.makeText(this, "Set download location in Settings", Toast.LENGTH_LONG).show().let {}
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val basePath = getPathFromUri(Uri.parse(uriStr)) ?: getExternalFilesDir(null)?.absolutePath
                val targetDir = File(basePath + (if (isTvShow) "/Series/$movieTitle" else "/Movies")).apply { if (!exists()) mkdirs() }
                val fileName = if (isTvShow) "$movieTitle S${String.format("%02d", currentSeason)}E${String.format("%02d", currentEpisode)}.mp4" else "$movieTitle.mp4"
                val destFile = File(targetDir, fileName)
                val fetch = Fetch.getInstance(FetchConfiguration.Builder(this@PlayerActivity).setDownloadConcurrentLimit(3).build())
                val req = Request(url, destFile.absolutePath).apply { priority = Priority.HIGH; networkType = NetworkType.ALL }
                fetch.enqueue(req, { r -> lifecycleScope.launch(Dispatchers.IO) { saveInitialDownloadToDb(fileName, destFile.absolutePath, r.id); withContext(Dispatchers.Main) { Toast.makeText(this@PlayerActivity, "Download Started", Toast.LENGTH_SHORT).show() } } }, { e -> Log.e("FetchError", e.name) })
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private suspend fun saveInitialDownloadToDb(title: String, path: String, id: Int) {
        AppDatabase.get(applicationContext).downloadsDao().insert(DownloadedMedia(tmdbId = tmdbId, title = title, posterPath = posterPath, backdropPath = "", filePath = path, mediaType = if (isTvShow) "tv" else "movie", downloadStatus = 0, downloadId = id))
    }

    private fun getPathFromUri(uri: Uri): String? {
        if (DocumentsContract.isTreeUri(uri)) {
            val id = DocumentsContract.getTreeDocumentId(uri)
            if (id.startsWith("primary:")) return Environment.getExternalStorageDirectory().toString() + "/" + id.split(":")[1]
        }
        return null
    }

    private fun checkPositionAndPlay(url: String) {
        lifecycleScope.launch {
            val h = AppDatabase.get(applicationContext).movieDao().getProgress(tmdbId)
            if (h != null && h.position > 10000 && h.position < (h.duration * 0.98)) showResumeDialog(h, url) else prepareMedia(url)
        }
    }


    private fun showResumeDialog(h: WatchEntity, url: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Resume Watching?")
            .setMessage("Continue watching '${h.title}' from ${(h.position * 100) / h.duration}%?")
            .setPositiveButton("Resume") { _, _ ->
                prepareMedia(url, h.position)
            }
            .setNegativeButton("Start Over") { _, _ ->
                prepareMedia(url)
            }
            .setNeutralButton("Cancel", null) // Nice to have a dismiss option
            .show()
    }

    private fun prepareMedia(url: String, startPosition: Long = 0L) {
        if (url.startsWith("magnet")) {
            isTemporaryStreaming = true
            binding.torrentLoadingLayout.visibility = View.VISIBLE
            binding.torrentPercentText.text = "0%"
            val i = Intent(this, TorrentService::class.java).apply { action = TorrentService.ACTION_START; putExtra(TorrentService.EXTRA_MAGNET, url); putExtra(TorrentService.EXTRA_IS_STREAMING, true); putExtra(TorrentService.EXTRA_TMDB_ID, tmdbId) }
            ContextCompat.startForegroundService(this, i)
        } else { initializePlayer(url); if (startPosition > 0) player.seekTo(startPosition) }
    }

    private fun startProgressTracker() {
        if (isTrackerRunning) return
        isTrackerRunning = true
        lifecycleScope.launch(Dispatchers.Main) {
            while (isFilePlaying) {
                if (player.isPlaying && player.currentPosition > 1000) {
                    saveProgress(player.currentPosition, player.duration)
                    if (!isMarkedWatched && player.duration > 0 && (player.currentPosition.toFloat() / player.duration.toFloat()) >= 0.90f) markAsWatched()
                }
                if (isTvShow && player.duration > 0) binding.nextEpisodeBtn.visibility = if (player.currentPosition > (player.duration * 0.95)) View.VISIBLE else View.GONE
                delay(2000)
            }
            isTrackerRunning = false
        }
    }

    private fun markAsWatched() {
        isMarkedWatched = true
        lifecycleScope.launch(Dispatchers.IO) { AppDatabase.get(applicationContext).watchHistoryDao().insert(WatchHistoryItem(tmdbId = tmdbId, title = movieTitle, isWatched = true, dateWatched = System.currentTimeMillis())) }
    }

    private fun saveProgress(pos: Long, dur: Long) {
        val url = intent.getStringExtra("url") ?: intent.getStringExtra("videoUrl")
        if (url != null && (url.startsWith("content://") || url.startsWith("file://"))) return
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.get(applicationContext).movieDao()
            val ex = dao.getProgress(tmdbId)
            dao.insert(WatchEntity(tmdbId, movieTitle, posterPath, System.currentTimeMillis(), pos, dur, currentSource?.url ?: "", "", ex?.isFavorite ?: false, ex?.mediaType ?: passedMediaType))
        }
    }

    private fun playExternalDirect(s: StreamSource) {
        try { startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { if (s.url.startsWith("magnet:")) setData(Uri.parse(s.url)) else setDataAndType(Uri.parse(s.url), "video/*"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Open with...")) }
        catch (e: Exception) { Toast.makeText(this, "No apps found", Toast.LENGTH_SHORT).show() }
    }

    private fun playExternalUrl(url: String) {
        try { startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(url), "video/*"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Select Player")) }
        catch (e: Exception) { Toast.makeText(this, "No players found", Toast.LENGTH_SHORT).show() }
    }

    private fun startExternalDownload(s: StreamSource) {
        try { startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { setData(Uri.parse(s.url)); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Download with...")) }
        catch (e: Exception) { Toast.makeText(this, "No download managers found", Toast.LENGTH_SHORT).show() }
    }

    private fun startTorrentStream(s: StreamSource) = prepareMedia(s.url)

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).setRenderersFactory(androidx.media3.exoplayer.DefaultRenderersFactory(this).setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this))).setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(15000, 50000, 2500, 5000).build()).build()
        binding.playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && player.playWhenReady) {
                    startTraktScrobble()
                } else if (state == Player.STATE_ENDED) {
                    stopTraktScrobble(100f)
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startTraktScrobble() else stopTraktScrobble()
            }
            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED || e.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE || e.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
                    val pos = player.currentPosition
                    showStandardLoading(); Toast.makeText(this@PlayerActivity, "Buffering torrent...", Toast.LENGTH_SHORT).show()
                    binding.playerView.postDelayed({ player.prepare(); player.seekTo(pos); player.playWhenReady = true; hideAllLoading() }, 2000)
                } else Toast.makeText(this@PlayerActivity, "Player Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        })
    }


    private fun startTraktScrobble() {
        val token = traktTokenManager.getAccessToken()
        if (token.isNullOrEmpty() || tmdbId == 0) return

        val currentPos = player.currentPosition
        val totalDuration = player.duration
        val progress = if (totalDuration > 0L) (currentPos.toFloat() / totalDuration.toFloat()) * 100f else 0f

        val isTv = isTvShow
        val tId = tmdbId
        val season = currentSeason
        val episode = currentEpisode

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val payload = HashMap<String, Any>()
                if (isTv) {
                    payload["show"] = mapOf("ids" to mapOf("tmdb" to tId))
                    payload["episode"] = mapOf("season" to season, "number" to episode)
                } else {
                    payload["movie"] = mapOf("ids" to mapOf("tmdb" to tId))
                }
                payload["progress"] = progress

                val response = RetrofitClient.trakt.startScrobble(
                    auth = "Bearer $token",
                    apiKey = TRAKT_CLIENT_ID,
                    request = payload
                )

                if (response.isSuccessful) {
                    Log.d("Trakt", "Scrobble Started: ${response.code()}")
                    isScrobblingStarted = true
                    startScrobbleUpdateLoop()
                } else {
                    val errorMsg = response.errorBody()?.string()
                    Log.e("Trakt", "Scrobble Failed: $errorMsg")
                }
            } catch (e: Exception) {
                Log.e("Trakt", "Network Error: ${e.message}")
            }
        }
    }

    private fun startScrobbleUpdateLoop() {
        scrobbleJob?.cancel()
        scrobbleJob = lifecycleScope.launch(Dispatchers.Main) {
            while (isScrobblingStarted) {
                delay(60000)

                if (!player.isPlaying) continue
                val token = traktTokenManager.getAccessToken() ?: break
                val dur = player.duration
                if (dur <= 0L) continue

                val currentPos = player.currentPosition
                val progress = (currentPos.toFloat() / dur.toFloat()) * 100f
                val isTv = isTvShow
                val tId = tmdbId
                val season = currentSeason
                val episode = currentEpisode

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val payload = HashMap<String, Any>()
                        if (isTv) {
                            payload["show"] = mapOf("ids" to mapOf("tmdb" to tId))
                            payload["episode"] = mapOf("season" to season, "number" to episode)
                        } else {
                            payload["movie"] = mapOf("ids" to mapOf("tmdb" to tId))
                        }
                        payload["progress"] = progress

                        val response = RetrofitClient.trakt.startScrobble(
                            auth = "Bearer $token",
                            apiKey = TRAKT_CLIENT_ID,
                            request = payload
                        )
                        Log.d("Trakt", "Update Response: ${response.code()}")
                    } catch (e: Exception) { Log.e("Trakt", "Update Error: ${e.message}") }
                }
            }
        }
    }

    private fun stopTraktScrobble(finalP: Float? = null) {
        if (!isScrobblingStarted) return
        val token = traktTokenManager.getAccessToken() ?: return

        val dur = player.duration
        val p = finalP ?: if (dur > 0) (player.currentPosition.toFloat() / dur.toFloat()) * 100f else 0f
        val isTv = isTvShow
        val tId = tmdbId
        val season = currentSeason
        val episode = currentEpisode

        isScrobblingStarted = false
        scrobbleJob?.cancel()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val payload = HashMap<String, Any>()
                if (isTv) {
                    payload["show"] = mapOf("ids" to mapOf("tmdb" to tId))
                    payload["episode"] = mapOf("season" to season, "number" to episode)
                } else {
                    payload["movie"] = mapOf("ids" to mapOf("tmdb" to tId))
                }
                payload["progress"] = p

                val response = RetrofitClient.trakt.stopScrobble(
                    auth = "Bearer $token",
                    apiKey = TRAKT_CLIENT_ID,
                    request = payload
                )
                Log.d("Trakt", "Stop Response: ${response.code()}")
            } catch (e: Exception) { Log.e("Trakt", "Stop Error: ${e.message}") }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isScrobblingStarted) stopTraktScrobble()
        player.release()
        if (isTemporaryStreaming) stopService(Intent(this, TorrentService::class.java).apply { action = TorrentService.ACTION_STOP })
        try { unregisterReceiver(torrentReceiver) } catch (e: Exception) {}
        isFilePlaying = false
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c?.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        c?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
