package com.adeloc.app.ui.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.adeloc.app.data.model.TraktUpNextItem
import com.adeloc.app.databinding.ItemUpNextBinding
import com.adeloc.app.ui.DetailsActivity
import com.adeloc.app.ui.PlayerActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class UpNextAdapter(
    private val items: List<TraktUpNextItem>
) : RecyclerView.Adapter<UpNextAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemUpNextBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TraktUpNextItem) {
            val title = if (item.type == "episode") item.show?.title else item.movie?.title
            val subtitle = if (item.type == "episode") {
                "S${item.episode?.season}:E${item.episode?.number} - ${item.episode?.title}"
            } else {
                "Movie"
            }

            binding.tvTitle.text = title
            binding.tvEpisodeInfo.text = subtitle

            Glide.with(binding.imgBackdrop.context)
                .load(item.finalImageUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.imgBackdrop)

            binding.root.setOnClickListener {
                val context = it.context
                val intent = Intent(context, DetailsActivity::class.java).apply {
                    val tmdbId = if (item.type == "episode") item.show?.ids?.tmdb else item.movie?.ids?.tmdb
                    putExtra("TMDB_ID", tmdbId)
                    putExtra("MEDIA_TYPE", if (item.type == "episode") "tv" else "movie")
                    putExtra("MOVIE_TITLE", title)
                }
                context.startActivity(intent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUpNextBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}