package com.ori.pivotboard_project.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.activities.TickerPostsActivity
import com.ori.pivotboard_project.adapters.TrendingAdapter
import com.ori.pivotboard_project.databinding.FragmentTrendingBinding
import com.ori.pivotboard_project.interfaces.TrendingCallback
import com.ori.pivotboard_project.model.TrendingTicker
import com.ori.pivotboard_project.utilities.DatabaseManager
import com.ori.pivotboard_project.utilities.hide
import com.ori.pivotboard_project.utilities.showEmpty
import com.ori.pivotboard_project.utilities.showError
import com.ori.pivotboard_project.utilities.showLoading

/**
 * Section 7 bonus - the most-posted tickers of the last week, ranked.
 *
 * Tapping a row reuses [TickerPostsActivity], so the whole feature is one query plus a list.
 */
class TrendingFragment : Fragment(), TrendingCallback {

    private var binding: FragmentTrendingBinding? = null
    private val trendingAdapter = TrendingAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTrendingBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = this.binding ?: return

        trendingAdapter.trendingCallback = this
        binding.trendingRVTickers.layoutManager = LinearLayoutManager(requireContext())
        binding.trendingRVTickers.adapter = trendingAdapter

        binding.trendingLAYSwipe.setOnRefreshListener { load(isRefresh = true) }

        load(isRefresh = false)
    }

    /** Rankings move as people post, so refresh when the tab comes back into view. */
    override fun onResume() {
        super.onResume()
        if (trendingAdapter.items.isNotEmpty()) load(isRefresh = true)
    }

    private fun load(isRefresh: Boolean) {
        val binding = this.binding ?: return
        if (!isRefresh) showLoading()

        DatabaseManager.getInstance().loadTrendingTickers { tickers, _ ->
            if (this.binding == null) return@loadTrendingTickers
            binding.trendingLAYSwipe.isRefreshing = false

            when {
                tickers == null -> showError()
                tickers.isEmpty() -> showEmpty()
                else -> {
                    trendingAdapter.setData(tickers)
                    showContent()
                }
            }
        }
    }

    override fun onTrendingTickerClicked(item: TrendingTicker, position: Int) {
        TickerPostsActivity.start(requireContext(), item.ticker)
    }

    // -------------------------------------------------------------- States

    private fun showLoading() {
        val binding = this.binding ?: return
        binding.trendingLAYSwipe.visibility = View.GONE
        binding.trendingLAYState.showLoading()
    }

    private fun showContent() {
        val binding = this.binding ?: return
        binding.trendingLAYSwipe.visibility = View.VISIBLE
        binding.trendingLAYState.hide()
    }

    private fun showEmpty() {
        val binding = this.binding ?: return
        binding.trendingLAYSwipe.visibility = View.GONE
        binding.trendingLAYState.showEmpty(
            icon = R.drawable.ic_empty_feed,
            title = R.string.trending_empty_title,
            body = R.string.trending_empty
        )
    }

    private fun showError() {
        val binding = this.binding ?: return
        binding.trendingLAYSwipe.visibility = View.GONE
        binding.trendingLAYState.showError(
            body = R.string.trending_error,
            onRetry = { load(isRefresh = false) }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        trendingAdapter.trendingCallback = null
        binding?.trendingRVTickers?.adapter = null
        binding = null
    }
}
