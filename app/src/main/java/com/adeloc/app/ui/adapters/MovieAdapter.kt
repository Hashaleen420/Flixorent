package com.adeloc.app.ui.adapters

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.app.ActivityOptionsCompat
import androidx.recyclerview.widget.RecyclerView
import com.adeloc.app.R
import com.adeloc.app.data.model.Movie
import com.adeloc.app.ui.DetailsActivity
import com.adeloc.app.utils.Constants
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class MovieAdapter(
    private var movies: List<Movie>,
    private var watchedIds: Set<Int> = emptySet(),
    private val onLongClick: ((Movie) -> Unit)? = null
) : RecyclerView.Adapter<MovieAdapter.MovieViewHolder>() {

    class MovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val poster: ImageView = view.findViewById(R.id.moviePoster)
        val watchedOverlay: View = view.findViewById(R.id.watchedOverlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_movie, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val movie = movies[position]

        val posterUrl = if (!movie.poster_path.isNullOrEmpty()) {
            Constants.IMG_URL + movie.poster_path
        } else {
            null
        }

        Glide.with(holder.itemView.context)
            .load(posterUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(android.R.color.darker_gray)
            .error(android.R.color.darker_gray)
            .into(holder.poster)

        // Show/hide watched indicator
        holder.watchedOverlay.visibility = if (watchedIds.contains(movie.id)) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, DetailsActivity::class.java).apply {
                putExtra("TMDB_ID", movie.id)
                putExtra("MOVIE_TITLE", movie.title)
                putExtra("POSTER", movie.poster_path)
                putExtra("BACKDROP", movie.backdrop_path)
                putExtra("OVERVIEW", movie.overview)
                putExtra("MEDIA_TYPE", movie.media_type)
            }
            
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                holder.itemView.context as Activity,
                holder.poster,
                "poster_transition"
            )
            holder.itemView.context.startActivity(intent, options.toBundle())
        }

        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(movie)
            true
        }
    }

    fun updateWatchedIds(newIds: Set<Int>) {
        watchedIds = newIds
        notifyDataSetChanged()
    }

    override fun getItemCount() = movies.size
}