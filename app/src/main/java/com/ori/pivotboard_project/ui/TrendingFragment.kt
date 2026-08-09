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
        binding.trendingBTNRetry.setOnClickListener { load(isRefresh = false) }

        load(isRefresh = false)
    }

    /** Rankings move as people post, so refresh when the tab comes back into view. */
    override fun onResume() {
        super.onResume()
        if (trendingAdapter.items.isNotEmpty()) load(isRefresh = true)
    }

    private fun load(isRefresh: Boolean) {
        val binding = this.binding ?: return
        if (!isRefresh) setState(loading = true)

        DatabaseManager.getInstance().loadTrendingTickers { tickers, _ ->
            if (this.binding == null) return@loadTrendingTickers
            binding.trendingLAYSwipe.isRefreshing = false

            when {
                tickers == null -> showMessage(R.string.trending_error, showRetry = true)
                tickers.isEmpty() -> showMessage(R.string.trending_empty, showRetry = false)
                else -> {
                    trendingAdapter.setData(tickers)
                    setState(content = true)
                }
            }
        }
    }

    override fun onTrendingTickerClicked(item: TrendingTicker, position: Int) {
        TickerPostsActivity.start(requireContext(), item.ticker)
    }

    // -------------------------------------------------------------- States

    private fun showMessage(messageId: Int, showRetry: Boolean) {
        val binding = this.binding ?: return
        setState(message = true)
        binding.trendingLBLMessage.setText(messageId)
        binding.trendingBTNRetry.visibility = if (showRetry) View.VISIBLE else View.GONE
    }

    private fun setState(
        loading: Boolean = false,
        content: Boolean = false,
        message: Boolean = false
    ) {
        val binding = this.binding ?: return
        binding.trendingPRGLoading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.trendingLAYSwipe.visibility = if (content) View.VISIBLE else View.GONE
        binding.trendingLAYMessage.visibility = if (message) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        trendingAdapter.trendingCallback = null
        binding?.trendingRVTickers?.adapter = null
        binding = null
    }
}
