package com.adeloc.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.adeloc.app.data.db.AppDatabase
import com.github.se_bastiaan.torrentstream.StreamStatus
import com.github.se_bastiaan.torrentstream.Torrent
import com.github.se_bastiaan.torrentstream.TorrentOptions
import com.github.se_bastiaan.torrentstream.TorrentStream
import com.github.se_bastiaan.torrentstream.listeners.TorrentListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder

class TorrentService : Service(), TorrentListener {

    private lateinit var torrentStream: TorrentStream
    private var lastUpdateTimestamp: Long = 0
    private var isDownloadMode = false
    private var isCompleted = false
    private var tmdbId = 0
    private var title = ""
    private var savePath: String? = null
    private var stagingDir: File? = null
    private var isStreamingMode = false

    companion object {
        private const val TAG = "TorrentService"
        const val ACTION_START = "ACTION_START"
        const val ACTION_DOWNLOAD = "ACTION_DOWNLOAD"
        const val ACTION_STOP = "ACTION_STOP"
        
        const val EXTRA_MAGNET = "EXTRA_MAGNET"
        const val EXTRA_SAVE_PATH = "EXTRA_SAVE_PATH"
        const val EXTRA_TMDB_ID = "EXTRA_TMDB_ID"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_POSTER = "EXTRA_POSTER"
        const val EXTRA_MEDIA_TYPE = "EXTRA_MEDIA_TYPE"
        const val EXTRA_IS_STREAMING = "EXTRA_IS_STREAMING"
        
        const val BROADCAST_READY = "com.adeloc.app.TORRENT_READY"
        const val BROADCAST_PROGRESS = "com.adeloc.app.TORRENT_PROGRESS"
        const val EXTRA_URL = "EXTRA_URL"
        const val EXTRA_PERCENT = "EXTRA_PERCENT"
        const val EXTRA_DOWNLOAD_SPEED = "EXTRA_DOWNLOAD_SPEED"

        private val FALLBACK_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://tracker.cyberia.is:6969/announce"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_DOWNLOAD -> {
                isDownloadMode = intent.action == ACTION_DOWNLOAD
                isStreamingMode = intent.getBooleanExtra(EXTRA_IS_STREAMING, false)
                
                val rawMagnet = intent.getStringExtra(EXTRA_MAGNET) ?: return START_NOT_STICKY
                savePath = intent.getStringExtra(EXTRA_SAVE_PATH)
                tmdbId = intent.getIntExtra(EXTRA_TMDB_ID, 0)
                title = intent.getStringExtra(EXTRA_TITLE) ?: "video.mp4"

                stagingDir = File(applicationContext.externalCacheDir, "staging/$tmdbId")
                stagingDir?.mkdirs()

                val options = TorrentOptions.Builder()
                    .saveLocation(stagingDir)
                    .removeFilesAfterStop(false)
                    .autoDownload(true)
                    .anonymousMode(false)
                    .build()
                    
                torrentStream = TorrentStream.init(options)
                torrentStream.addListener(this)

                startForeground(101, createNotification("Initializing..."))
                torrentStream.startStream(injectTrackers(rawMagnet))
            }
            ACTION_STOP -> {
                stopTorrentStream()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun injectTrackers(magnet: String): String {
        var enhanced = magnet
        try {
            FALLBACK_TRACKERS.forEach { tracker ->
                if (!enhanced.contains(tracker)) {
                    enhanced += "&tr=${URLEncoder.encode(tracker, "UTF-8")}"
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Tracker error", e) }
        return enhanced
    }

    override fun onStreamProgress(torrent: Torrent?, status: StreamStatus?) {
        if (status == null) return
        val progress = if (isDownloadMode) status.progress.toInt() else status.bufferProgress.toInt()
        
        val now = System.currentTimeMillis()
        if (now - lastUpdateTimestamp > 1500) {
            lastUpdateTimestamp = now
            val speedKb = status.downloadSpeed / 1024
            val speedText = if (speedKb > 1024) String.format("%.2f MB/s", speedKb / 1024f) else "${speedKb} KB/s"

            sendBroadcast(Intent(BROADCAST_PROGRESS).apply {
                setPackage(packageName)
                putExtra(EXTRA_TMDB_ID, tmdbId)
                putExtra(EXTRA_PERCENT, progress)
                putExtra(EXTRA_DOWNLOAD_SPEED, speedText)
            })

            updateNotification(if (isDownloadMode) "Downloading: $progress% ($speedText)" else "Buffering: $progress%")
        }

        if (isDownloadMode && status.progress >= 100 && !isCompleted) {
            isCompleted = true
            processFinalStaging()
        }
    }

    private fun processFinalStaging() {
        val root = stagingDir ?: return
        val finalDestDir = savePath?.let { File(it) } ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                stopTorrentStream()
                delay(1000)

                val largestFile = findLargestVideoFile(root)
                if (largestFile != null && largestFile.exists()) {
                    finalDestDir.mkdirs()
                    val targetFile = File(finalDestDir, title)

                    val success = if (largestFile.renameTo(targetFile)) {
                        true
                    } else {
                        largestFile.copyTo(targetFile, overwrite = true)
                        largestFile.delete()
                    }

                    if (success) {
                        val dao = AppDatabase.get(applicationContext).downloadsDao()
                        val existing = dao.getDownloadByTmdb(tmdbId)
                        if (existing != null) {
                            dao.insert(existing.copy(downloadStatus = 1, filePath = targetFile.absolutePath))
                        }
                        withContext(Dispatchers.Main) { updateNotification("Download Complete: $title") }
                    }
                }
                root.deleteRecursively()
                withContext(Dispatchers.Main) { stopSelf() }
            } catch (e: Exception) {
                Log.e(TAG, "Final move failed", e)
                withContext(Dispatchers.Main) { stopSelf() }
            }
        }
    }

    private fun findLargestVideoFile(directory: File): File? {
        var largest: File? = null
        var maxSize = 0L
        val videoExtensions = listOf("mp4", "mkv", "avi", "mov", "m4v", "part")

        directory.walkTopDown().forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (videoExtensions.contains(ext)) {
                    if (file.length() > maxSize) {
                        maxSize = file.length()
                        largest = file
                    }
                }
            }
        }
        return largest
    }

    private fun stopTorrentStream() {
        if (::torrentStream.isInitialized) {
            torrentStream.removeListener(this)
            torrentStream.stopStream()
        }
    }

    private fun createNotification(text: String): Notification {
        val channelId = "torrent_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(if (isDownloadMode) title else "Flixtorrent Engine")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(101, createNotification(text))
    }

    override fun onStreamPrepared(torrent: Torrent?) {}
    override fun onStreamStarted(torrent: Torrent?) {}
    override fun onStreamReady(torrent: Torrent) {
        if (!isDownloadMode) {
            sendBroadcast(Intent(BROADCAST_READY).apply {
                setPackage(packageName)
                putExtra(EXTRA_URL, Uri.fromFile(torrent.videoFile).toString())
            })
        }
    }

    override fun onStreamError(torrent: Torrent?, e: Exception?) {
        if (isDownloadMode) {
            CoroutineScope(Dispatchers.IO).launch {
                val dao = AppDatabase.get(applicationContext).downloadsDao()
                val existing = dao.getDownloadByTmdb(tmdbId)
                if (existing != null) dao.insert(existing.copy(downloadStatus = 2))
            }
        }
        stopSelf()
    }

    override fun onStreamStopped() {}

    override fun onDestroy() {
        stopTorrentStream()
        if (isStreamingMode) {
            stagingDir?.deleteRecursively()
            Log.d(TAG, "Streaming cleanup: Staging folder deleted.")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
