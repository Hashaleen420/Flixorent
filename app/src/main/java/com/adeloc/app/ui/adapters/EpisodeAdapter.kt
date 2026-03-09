package com.adeloc.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.adeloc.app.R
import com.adeloc.app.data.model.Episode
import com.adeloc.app.utils.Constants
import com.bumptech.glide.Glide

class EpisodeAdapter(
    private val episodes: List<Episode>,
    private val onEpisodeClick: (Episode) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

    class EpisodeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val still: ImageView = view.findViewById(R.id.episodeStill)
        val title: TextView = view.findViewById(R.id.episodeTitle)
        val meta: TextView = view.findViewById(R.id.episodeMeta)
        val overview: TextView = view.findViewById(R.id.episodeOverview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_episode, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val ep = episodes[position]
        holder.title.text = "E${ep.episode_number}: ${ep.name}"
        holder.meta.text = "${ep.air_date ?: ""} • ${ep.runtime ?: "?"}m"
        holder.overview.text = ep.overview ?: "No overview available."

        Glide.with(holder.itemView.context)
            .load(Constants.IMG_URL + ep.still_path)
            .placeholder(android.R.color.darker_gray)
            .into(holder.still)

        holder.itemView.setOnClickListener { onEpisodeClick(ep) }
    }

    override fun getItemCount() = episodes.size
}