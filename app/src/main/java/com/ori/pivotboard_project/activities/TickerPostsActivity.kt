package com.ori.pivotboard_project.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.adapters.PostAdapter
import com.ori.pivotboard_project.databinding.ActivityTickerPostsBinding
import com.ori.pivotboard_project.interfaces.PostCallback
import com.ori.pivotboard_project.model.Post
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.Constants
import com.ori.pivotboard_project.utilities.DatabaseManager
import com.ori.pivotboard_project.utilities.SignalManager

/**
 * Every setup posted for one ticker - the "tapping a ticker filters the feed to it" half of
 * section 5.6. Reached from a watchlist row or from a ticker chip on any post card.
 */
class TickerPostsActivity : AppCompatActivity(), PostCallback {

    private lateinit var binding: ActivityTickerPostsBinding
    private val postAdapter = PostAdapter()

    private val ticker: String
        get() = intent.getStringExtra(Constants.BUNDLE_KEYS.TICKER).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTickerPostsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.tickerTBToolbar.title = getString(R.string.ticker_title, ticker)
        binding.tickerTBToolbar.setNavigationOnClickListener { finish() }

        postAdapter.postCallback = this
        binding.tickerRVPosts.layoutManager = LinearLayoutManager(this)
        binding.tickerRVPosts.adapter = postAdapter

        loadPosts()
    }

    private fun loadPosts() {
        binding.tickerPRGLoading.visibility = View.VISIBLE

        DatabaseManager.getInstance().loadPostsByTicker(ticker) { posts, _ ->
            if (isFinishing || isDestroyed) return@loadPostsByTicker
            binding.tickerPRGLoading.visibility = View.GONE

            when {
                posts == null -> showMessage(R.string.ticker_error)
                posts.isEmpty() -> showMessage(R.string.ticker_empty)
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
            binding.tickerLBLMessage.visibility = View.GONE
        }
    }

    private fun showMessage(messageId: Int) {
        binding.tickerRVPosts.visibility = View.GONE
        binding.tickerLBLMessage.visibility = View.VISIBLE
        binding.tickerLBLMessage.setText(messageId)
    }

    // ----------------------------------------------------------- Post cards

    override fun onPostClicked(post: Post, position: Int) =
        PostDetailActivity.start(this, post.id)

    override fun onCommentClicked(post: Post, position: Int) =
        PostDetailActivity.start(this, post.id)

    override fun onAuthorClicked(post: Post, position: Int) =
        ProfileActivity.start(this, post.authorId)

    /** Already filtered to this ticker, so tapping the chip again does nothing. */
    override fun onTickerClicked(post: Post, position: Int) = Unit

    override fun onLikeClicked(post: Post, position: Int) {
        val uid = AuthManager.getInstance().currentUid()
        if (uid.isEmpty()) return

        val wasLiked = postAdapter.likedPostIds.contains(post.id)
        applyLikeLocally(post, position, !wasLiked)

        DatabaseManager.getInstance().toggleLike(
            post = post,
            uid = uid,
            fromName = AuthManager.getInstance().currentUser()?.displayName.orEmpty(),
            shouldLike = !wasLiked
        ) { success ->
            if (isFinishing || isDestroyed) return@toggleLike
            if (!success) {
                applyLikeLocally(post, position, wasLiked)
                SignalManager.getInstance().toast(R.string.error_like_failed)
            }
        }
    }

    private fun applyLikeLocally(post: Post, position: Int, isLiked: Boolean) {
        postAdapter.likedPostIds =
            if (isLiked) postAdapter.likedPostIds + post.id else postAdapter.likedPostIds - post.id
        post.likeCount = (post.likeCount + if (isLiked) 1 else -1).coerceAtLeast(0)
        postAdapter.notifyItemChanged(position)
    }

    companion object {
        fun start(context: Context, ticker: String) {
            val intent = Intent(context, TickerPostsActivity::class.java)
                .putExtra(Constants.BUNDLE_KEYS.TICKER, ticker)
            context.startActivity(intent)
        }
    }
}
