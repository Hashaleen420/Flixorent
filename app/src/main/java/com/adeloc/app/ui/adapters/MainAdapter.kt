package com.adeloc.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adeloc.app.R
import com.adeloc.app.data.model.Movie
import com.adeloc.app.ui.RowItem

class MainAdapter(
    private var rows: List<RowItem>,
    private var watchedIds: Set<Int> = emptySet(),
    private val onLongClick: ((Movie) -> Unit)? = null
) : RecyclerView.Adapter<MainAdapter.MainViewHolder>() {

    // THE SECRET SAUCE FOR SPEED: A shared view pool
    private val viewPool = RecyclerView.RecycledViewPool()

    class MainViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.categoryTitle)
        val childRecycler: RecyclerView = view.findViewById(R.id.childRecycler)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return MainViewHolder(view)
    }

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val row = rows[position]
        holder.title.text = row.title

        // Only set the LayoutManager if it hasn't been set yet
        if (holder.childRecycler.layoutManager == null) {
            holder.childRecycler.layoutManager = LinearLayoutManager(
                holder.itemView.context,
                LinearLayoutManager.HORIZONTAL,
                false
            )
        }

        // Optimize scrolling performance
        holder.childRecycler.setRecycledViewPool(viewPool)
        

        // Use context to get the string so it matches MainActivity exactly
        val continueTitle = holder.itemView.context.getString(R.string.row_continue)
        val isHistoryRow = row.title == continueTitle

        holder.childRecycler.adapter = MovieAdapter(row.movies, watchedIds, if (isHistoryRow) onLongClick else null)
    }

    override fun getItemCount() = rows.size

    fun updateData(newRows: List<RowItem>) {
        // Ensure we are working with a fresh, distinct list
        this.rows = ArrayList(newRows)
        notifyDataSetChanged()
    }

    fun updateWatchedIds(newIds: Set<Int>) {
        this.watchedIds = newIds
        notifyDataSetChanged()
    }
}