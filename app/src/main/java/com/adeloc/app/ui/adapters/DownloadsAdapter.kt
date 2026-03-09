package com.adeloc.app.ui.adapters

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.RecyclerView
import com.adeloc.app.R
import com.adeloc.app.data.db.AppDatabase
import com.adeloc.app.data.db.DownloadedMedia
import com.adeloc.app.databinding.ItemDownloadBinding
import com.adeloc.app.ui.PlayerActivity
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.Gravity
import android.widget.TextView

class DownloadsAdapter(
    private var downloads: List<DownloadedMedia>,
    private val context: Context
) : RecyclerView.Adapter<DownloadsAdapter.ViewHolder>() {

    private val progressMap = mutableMapOf<Int, Int>()

    fun updateList(newList: List<DownloadedMedia>) {
        downloads = newList
        notifyDataSetChanged()
    }

    fun updateProgress(downloadId: Int, progress: Int) {
        val position = downloads.indexOfFirst { it.downloadId == downloadId }
        if (position != -1) {
            progressMap[downloadId] = progress
            notifyItemChanged(position, "progress_payload")
        }
    }

    fun updateStatus(downloadId: Int, status: Int) {
        val position = downloads.indexOfFirst { it.downloadId == downloadId }
        if (position != -1) {
            val newList = downloads.toMutableList()
            newList[position] = newList[position].copy(downloadStatus = status)
            downloads = newList
            notifyItemChanged(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val media = downloads[position]
        holder.bind(media)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            for (payload in payloads) {
                if (payload == "progress_payload") {
                    val progress = progressMap[downloads[position].downloadId] ?: 0
                    holder.updateProgressBar(progress)
                }
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount() = downloads.size

    inner class ViewHolder(private val binding: ItemDownloadBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(media: DownloadedMedia) {
            binding.tvTitle.text = media.title
            Glide.with(context)
                .load("https://image.tmdb.org/t/p/w500${media.posterPath}")
                .placeholder(android.R.color.darker_gray)
                .into(binding.ivPoster)

            binding.progressDownload.progress = progressMap[media.downloadId] ?: 0

            if (media.downloadStatus == 0) {
                binding.progressDownload.visibility = View.VISIBLE
            } else {
                binding.progressDownload.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                playMedia(media)
            }

            binding.root.setOnLongClickListener {
                showDeleteDialog(media)
                true
            }
        }

        fun updateProgressBar(progress: Int) {
            binding.progressDownload.progress = progress
            binding.progressDownload.visibility = if (progress >= 100) View.GONE else View.VISIBLE
        }

        private fun playMedia(media: DownloadedMedia) {
            val file = File(media.filePath)
            if (file.exists() || media.filePath.startsWith("content://")) {
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    putExtra("MOVIE_TITLE", media.title)
                    putExtra("TMDB_ID", media.tmdbId)
                    val uriString = if (media.filePath.startsWith("content://")) {
                        media.filePath
                    } else {
                        Uri.fromFile(file).toString()
                    }
                    putExtra("url", uriString)
                    putExtra("IS_OFFLINE", true)
                }
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
            }
        }

        private fun showDeleteDialog(media: DownloadedMedia) {
            MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog)
                .setTitle("Delete Download")
                .setMessage("Are you sure you want to permanently delete '${media.title}'?")
                .setIcon(android.R.drawable.ic_menu_delete)
                .setPositiveButton("Delete") { _, _ ->
                    deleteDownload(media)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun deleteDownload(media: DownloadedMedia) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (media.filePath.startsWith("content://")) {
                        val uri = Uri.parse(media.filePath)
                        DocumentFile.fromSingleUri(context, uri)?.delete()
                    } else {
                        val file = File(media.filePath)
                        if (file.exists()) file.delete()
                    }

                    AppDatabase.get(context).downloadsDao().delete(media)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
