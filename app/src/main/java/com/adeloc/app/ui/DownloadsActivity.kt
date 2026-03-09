package com.adeloc.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.adeloc.app.data.db.AppDatabase
import com.adeloc.app.databinding.ActivityDownloadsBinding
import com.adeloc.app.ui.adapters.DownloadsAdapter
import com.tonyodev.fetch2.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadsBinding
    private lateinit var adapter: DownloadsAdapter
    private var fetch: Fetch? = null

    private val torrentProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.adeloc.app.TORRENT_PROGRESS") {
                val percent = intent.getIntExtra("percent", 0)
                val downloadId = intent.getIntExtra("download_id", -1) // or tmdbId

                // Update the specific item in the adapter
                if (downloadId != -1) {
                    adapter.updateProgress(downloadId, percent)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        observeDownloads()
        setupFetch()
    }

    private fun setupRecyclerView() {
        adapter = DownloadsAdapter(emptyList(), this)
        binding.recyclerDownloads.layoutManager = GridLayoutManager(this, 3)
        binding.recyclerDownloads.adapter = adapter
    }

    private fun observeDownloads() {
        lifecycleScope.launch {
            AppDatabase.get(this@DownloadsActivity).downloadsDao().getAllDownloads().collectLatest { list ->
                if (list.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerDownloads.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerDownloads.visibility = View.VISIBLE
                    adapter.updateList(list)
                }
            }
        }
    }

    private fun setupFetch() {
        val fetchConfiguration = FetchConfiguration.Builder(this)
            .setDownloadConcurrentLimit(3)
            .build()
        fetch = Fetch.getInstance(fetchConfiguration)
    }

    override fun onResume() {
        super.onResume()
        fetch?.addListener(fetchListener)

        val filter = IntentFilter("com.adeloc.app.TORRENT_PROGRESS")
        ContextCompat.registerReceiver(
            this,
            torrentProgressReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        fetch?.removeListener(fetchListener)

        try {
            unregisterReceiver(torrentProgressReceiver)
        } catch (e: Exception) {
            // Already unregistered or not registered
        }
    }

    private val fetchListener = object : AbstractFetchListener() {
        override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
            adapter.updateProgress(download.id, download.progress)
        }

        override fun onCompleted(download: Download) {
            // Mapping Status.COMPLETED to our internal status 1 (Completed)
            adapter.updateStatus(download.id, 1)
        }

        override fun onError(download: Download, error: Error, throwable: Throwable?) {
            adapter.updateStatus(download.id, 2) // 2=Failed
        }
    }
}
