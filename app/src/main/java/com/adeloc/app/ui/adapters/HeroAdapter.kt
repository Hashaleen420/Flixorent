package com.adeloc.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.adeloc.app.R
import com.adeloc.app.data.model.Movie
import com.adeloc.app.utils.Constants
import com.bumptech.glide.Glide

class HeroAdapter(
    private val items: List<Movie>,
    private val onClick: (Movie, View) -> Unit
) : RecyclerView.Adapter<HeroAdapter.HeroViewHolder>() {

    class HeroViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.heroImage)
        val title: TextView = view.findViewById(R.id.heroTitle)
        val rating: TextView = view.findViewById(R.id.heroRating)
        val description: TextView = view.findViewById(R.id.heroDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeroViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hero, parent, false)
        return HeroViewHolder(view)
    }

    override fun onBindViewHolder(holder: HeroViewHolder, position: Int) {
        val movie = items[position]
        holder.title.text = movie.title
        holder.rating.text = "⭐ TMDB: ${movie.rating ?: 0.0}"
        holder.description.text = movie.overview

        Glide.with(holder.itemView.context)
            .load(Constants.IMG_URL + (movie.backdrop_path ?: movie.poster_path))
            .placeholder(android.R.color.darker_gray)
            .into(holder.image)

        holder.itemView.setOnClickListener { onClick(movie, holder.image) }
    }

    override fun getItemCount() = items.size
}