package com.ori.pivotboard_project.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.databinding.WatchItemBinding
import com.ori.pivotboard_project.interfaces.WatchCallback
import com.ori.pivotboard_project.model.WatchItem
import com.ori.pivotboard_project.utilities.TimeFormatter

/**
 * Watchlist rows.
 *
 * [isEditable] is false when viewing someone else's list, which hides the visibility switch
 * and the remove button - the security rules would reject those writes anyway.
 */
class WatchlistAdapter(
    var items: List<WatchItem> = listOf(),
    var isEditable: Boolean = true
) : RecyclerView.Adapter<WatchlistAdapter.WatchViewHolder>() {

    var watchCallback: WatchCallback? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WatchViewHolder {
        val binding = WatchItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return WatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WatchViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun setData(watchItems: List<WatchItem>, editable: Boolean) {
        items = watchItems
        isEditable = editable
        notifyDataSetChanged()
    }

    inner class WatchViewHolder(private val binding: WatchItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.watchCARDRoot.setOnClickListener {
                val position = absoluteAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                watchCallback?.onWatchItemClicked(items[position], position)
            }

            binding.watchBTNRemove.setOnClickListener {
                val position = absoluteAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                watchCallback?.onWatchItemRemoveClicked(items[position], position)
            }

            binding.watchSWPublic.setOnCheckedChangeListener { button, isChecked ->
                // Only react to real taps: bind() sets the state programmatically, which
                // would otherwise fire this listener and write straight back to Firestore.
                if (!button.isPressed) return@setOnCheckedChangeListener
                val position = absoluteAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
                watchCallback?.onWatchItemVisibilityChanged(items[position], position, isChecked)
            }
        }

        fun bind(item: WatchItem) {
            binding.watchLBLTicker.text = item.ticker
            binding.watchLBLAdded.text = binding.root.context.getString(
                R.string.watchlist_added_format,
                TimeFormatter.relative(item.addedAt)
            )

            binding.watchSWPublic.isChecked = item.isPublic
            binding.watchSWPublic.visibility = if (isEditable) View.VISIBLE else View.GONE
            binding.watchBTNRemove.visibility = if (isEditable) View.VISIBLE else View.GONE
        }
    }
}
