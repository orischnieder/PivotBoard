package com.ori.pivotboard_project.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.adapters.PostActionHandler
import com.ori.pivotboard_project.adapters.PostAdapter
import com.ori.pivotboard_project.databinding.ActivityTickerPostsBinding
import com.ori.pivotboard_project.model.Post
import com.ori.pivotboard_project.utilities.applySystemBarPadding
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.Constants
import com.ori.pivotboard_project.utilities.DatabaseManager
import com.ori.pivotboard_project.utilities.hide
import com.ori.pivotboard_project.utilities.showEmpty
import com.ori.pivotboard_project.utilities.showError
import com.ori.pivotboard_project.utilities.showLoading

/**
 * Every setup posted for one ticker - the "tapping a ticker filters the feed to it" half of
 * section 5.6. Reached from a watchlist row or from a ticker chip on any post card.
 */
class TickerPostsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTickerPostsBinding
    private val postAdapter = PostAdapter()

    private val ticker: String
        get() = intent.getStringExtra(Constants.BUNDLE_KEYS.TICKER).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTickerPostsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.applySystemBarPadding()

        binding.tickerTBToolbar.title = getString(R.string.ticker_title, ticker)
        binding.tickerTBToolbar.setNavigationOnClickListener { finish() }

        // Already filtered to this ticker, so the chip should not re-filter by it.
        postAdapter.postCallback = object : PostActionHandler(
            context = this,
            adapter = postAdapter,
            isActive = { !isFinishing && !isDestroyed }
        ) {
            override fun onTickerClicked(post: Post, position: Int) = Unit
        }
        binding.tickerRVPosts.layoutManager = LinearLayoutManager(this)
        binding.tickerRVPosts.adapter = postAdapter

        loadPosts()
    }

    private fun loadPosts() {
        binding.tickerRVPosts.visibility = View.GONE
        binding.tickerLAYState.showLoading()

        DatabaseManager.getInstance().loadPostsByTicker(ticker) { posts, _ ->
            if (isFinishing || isDestroyed) return@loadPostsByTicker

            when {
                posts == null -> showError()
                posts.isEmpty() -> showEmpty()
                else -> attachLikeStates(posts)
            }
        }
    }

    private fun attachLikeStates(posts: List<Post>) {
        val uid = AuthManager.getInstance().currentUid()
        DatabaseManager.getInstance().fetchLikedPostIds(posts.map { it.id }, uid) { likedIds ->
            if (isFinishing || isDestroyed) return@fetchLikedPostIds
            postAdapter.setData(posts, likedIds)
            binding.tickerRVPosts.visibility = View.VISIBLE
            binding.tickerLAYState.hide()
        }
    }

    private fun showEmpty() {
        binding.tickerRVPosts.visibility = View.GONE
        binding.tickerLAYState.showEmpty(
            icon = R.drawable.ic_empty_feed,
            title = R.string.ticker_empty_title,
            body = R.string.ticker_empty
        )
    }

    private fun showError() {
        binding.tickerRVPosts.visibility = View.GONE
        binding.tickerLAYState.showError(
            body = R.string.ticker_error,
            onRetry = { loadPosts() }
        )
    }

    companion object {
        fun start(context: Context, ticker: String) {
            val intent = Intent(context, TickerPostsActivity::class.java)
                .putExtra(Constants.BUNDLE_KEYS.TICKER, ticker)
            context.startActivity(intent)
        }
    }
}
