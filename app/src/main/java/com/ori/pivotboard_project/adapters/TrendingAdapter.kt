package com.ori.pivotboard_project.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.databinding.TrendingItemBinding
import com.ori.pivotboard_project.interfaces.TrendingCallback
import com.ori.pivotboard_project.model.TrendingTicker

/** Ranked tickers on the Trending tab. */
class TrendingAdapter(
    var items: List<TrendingTicker> = listOf()
) : RecyclerView.Adapter<TrendingAdapter.TrendingViewHolder>() {

    var trendingCallback: TrendingCallback? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrendingViewHolder {
        val binding = TrendingItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TrendingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrendingViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    fun setData(tickers: List<TrendingTicker>) {
        items = tickers
        notifyDataSetChanged()
    }

    inner class TrendingViewHolder(private val binding: TrendingItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.trendingCARDRoot.setOnClickListener {
                val position = absoluteAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                trendingCallback?.onTrendingTickerClicked(items[position], position)
            }
        }

        fun bind(item: TrendingTicker, position: Int) {
            val resources = binding.root.context.resources

            binding.trendingLBLRank.text = (position + 1).toString()
            binding.trendingLBLTicker.text = item.ticker

            val posts = resources.getQuantityString(
                R.plurals.trending_posts, item.postCount, item.postCount
            )
            val traders = resources.getQuantityString(
                R.plurals.trending_traders, item.authorCount, item.authorCount
            )
            binding.trendingLBLSummary.text =
                binding.root.context.getString(R.string.trending_summary_format, posts, traders)
        }
    }
}
