package com.ori.pivotboard_project.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.activities.TickerPostsActivity
import com.ori.pivotboard_project.adapters.WatchlistAdapter
import com.ori.pivotboard_project.databinding.FragmentWatchlistBinding
import com.ori.pivotboard_project.interfaces.WatchCallback
import com.ori.pivotboard_project.model.WatchItem
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.Constants
import com.ori.pivotboard_project.utilities.DatabaseManager
import com.ori.pivotboard_project.utilities.SignalManager

/**
 * Section 5.6 - the watchlist.
 *
 * Serves two cases from one implementation, like [ProfileFragment]: your own list (add,
 * remove, toggle public/private) and another trader's, which shows only their public
 * tickers and hides every control.
 */
class WatchlistFragment : Fragment(), WatchCallback {

    private var binding: FragmentWatchlistBinding? = null
    private val watchlistAdapter = WatchlistAdapter()

    private val targetUid: String
        get() = arguments?.getString(ARG_UID).takeUnless { it.isNullOrEmpty() }
            ?: AuthManager.getInstance().currentUid()

    private val isOwnWatchlist: Boolean
        get() = targetUid == AuthManager.getInstance().currentUid()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentWatchlistBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = this.binding ?: return

        watchlistAdapter.watchCallback = this
        binding.watchlistRVItems.layoutManager = LinearLayoutManager(requireContext())
        binding.watchlistRVItems.adapter = watchlistAdapter

        // Only your own list is editable.
        binding.watchlistLAYAdd.visibility = if (isOwnWatchlist) View.VISIBLE else View.GONE

        binding.watchlistBTNAdd.setOnClickListener { addTicker() }
        binding.watchlistBTNRetry.setOnClickListener { loadWatchlist(isRefresh = false) }
        binding.watchlistLAYSwipe.setOnRefreshListener { loadWatchlist(isRefresh = true) }

        loadWatchlist(isRefresh = false)
    }

    // ------------------------------------------------------------- Loading

    private fun loadWatchlist(isRefresh: Boolean) {
        val binding = this.binding ?: return
        if (!isRefresh) setState(loading = true)

        DatabaseManager.getInstance().loadWatchlist(
            uid = targetUid,
            onlyPublic = !isOwnWatchlist
        ) { items, _ ->
            if (this.binding == null) return@loadWatchlist
            binding.watchlistLAYSwipe.isRefreshing = false

            when {
                items == null -> setState(error = true)
                items.isEmpty() -> showEmpty()
                else -> {
                    watchlistAdapter.setData(items, isOwnWatchlist)
                    setState(content = true)
                }
            }
        }
    }

    private fun addTicker() {
        val binding = this.binding ?: return

        val ticker = binding.watchlistEDTTicker.text?.toString()?.trim()?.uppercase().orEmpty()
        if (!ticker.matches(TICKER_PATTERN)) {
            binding.watchlistLAYTicker.error = getString(R.string.create_error_ticker_invalid)
            return
        }
        binding.watchlistLAYTicker.error = null
        binding.watchlistBTNAdd.isEnabled = false

        DatabaseManager.getInstance().addWatchItem(targetUid, ticker) { success ->
            val currentBinding = this.binding ?: return@addWatchItem
            currentBinding.watchlistBTNAdd.isEnabled = true

            if (success) {
                currentBinding.watchlistEDTTicker.text = null
                loadWatchlist(isRefresh = true)
            } else {
                SignalManager.getInstance().toast(R.string.watchlist_error_add)
            }
        }
    }

    // ------------------------------------------------------------ Callbacks

    /** Tapping a ticker shows every setup posted for it. */
    override fun onWatchItemClicked(item: WatchItem, position: Int) {
        TickerPostsActivity.start(requireContext(), item.ticker)
    }

    override fun onWatchItemVisibilityChanged(item: WatchItem, position: Int, isPublic: Boolean) {
        item.isPublic = isPublic

        DatabaseManager.getInstance()
            .setWatchItemPublic(targetUid, item.ticker, isPublic) { success ->
                if (binding == null) return@setWatchItemPublic

                if (success) {
                    SignalManager.getInstance().toast(
                        getString(
                            if (isPublic) R.string.watchlist_now_public
                            else R.string.watchlist_now_private,
                            item.ticker
                        )
                    )
                } else {
                    // Roll the row back so the switch never lies about what is stored.
                    item.isPublic = !isPublic
                    watchlistAdapter.notifyItemChanged(position)
                    SignalManager.getInstance().toast(R.string.watchlist_error_update)
                }
            }
    }

    override fun onWatchItemRemoveClicked(item: WatchItem, position: Int) {
        DatabaseManager.getInstance().removeWatchItem(targetUid, item.ticker) { success ->
            if (binding == null) return@removeWatchItem

            if (success) {
                SignalManager.getInstance()
                    .toast(getString(R.string.watchlist_removed, item.ticker))
                loadWatchlist(isRefresh = true)
            } else {
                SignalManager.getInstance().toast(R.string.watchlist_error_remove)
            }
        }
    }

    // -------------------------------------------------------------- States

    private fun showEmpty() {
        val binding = this.binding ?: return
        setState(empty = true)
        binding.watchlistLBLEmpty.setText(
            if (isOwnWatchlist) R.string.watchlist_empty_own else R.string.watchlist_empty_other
        )
    }

    private fun setState(
        loading: Boolean = false,
        content: Boolean = false,
        empty: Boolean = false,
        error: Boolean = false
    ) {
        val binding = this.binding ?: return
        binding.watchlistPRGLoading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.watchlistLAYSwipe.visibility = if (content) View.VISIBLE else View.GONE
        binding.watchlistLAYEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.watchlistLAYError.visibility = if (error) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        watchlistAdapter.watchCallback = null
        binding?.watchlistRVItems?.adapter = null
        binding = null
    }

    companion object {
        private const val ARG_UID = Constants.BUNDLE_KEYS.USER_ID
        private val TICKER_PATTERN = Regex("^[A-Z]{1,${Constants.UI.TICKER_MAX_LENGTH}}$")

        /** Pass null for the signed-in user's own watchlist. */
        fun newInstance(uid: String?): WatchlistFragment = WatchlistFragment().apply {
            arguments = Bundle().apply { putString(ARG_UID, uid) }
        }
    }
}
