package com.adeloc.app.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.adeloc.app.R
import com.adeloc.app.data.model.Season

class SeasonAdapter(
    private val seasons: List<Season>,
    private val onSeasonClick: (Season) -> Unit
) : RecyclerView.Adapter<SeasonAdapter.SeasonViewHolder>() {

    private var selectedPosition = 0

    class SeasonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val seasonTitle: TextView = view.findViewById(R.id.seasonTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeasonViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_season_chip, parent, false)
        return SeasonViewHolder(view)
    }

    override fun onBindViewHolder(holder: SeasonViewHolder, position: Int) {
        val season = seasons[position]
        holder.seasonTitle.text = season.name ?: "Season ${season.season_number}"
        
        // Highlight selected season
        if (position == selectedPosition) {
            holder.seasonTitle.setBackgroundResource(R.drawable.chip_selected_bg)
            holder.seasonTitle.setTextColor(Color.WHITE)
        } else {
            holder.seasonTitle.setBackgroundResource(R.drawable.chip_unselected_bg)
            holder.seasonTitle.setTextColor(Color.GRAY)
        }

        holder.itemView.setOnClickListener {
            val oldPos = selectedPosition
            selectedPosition = holder.bindingAdapterPosition
            if (oldPos != -1) notifyItemChanged(oldPos)
            notifyItemChanged(selectedPosition)
            onSeasonClick(season)
        }
    }

    override fun getItemCount() = seasons.size
}