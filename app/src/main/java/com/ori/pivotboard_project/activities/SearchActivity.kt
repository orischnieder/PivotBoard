package com.ori.pivotboard_project.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.adapters.PostAdapter
import com.ori.pivotboard_project.databinding.ActivitySearchBinding
import com.ori.pivotboard_project.interfaces.PostCallback
import com.ori.pivotboard_project.model.Post
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.DatabaseManager
import com.ori.pivotboard_project.utilities.SignalManager

/**
 * Search setups by ticker or tag.
 *
 * Firestore offers no full-text search, so the two modes work differently and the UI says
 * so: ticker is a prefix match ("NV" finds NVDA), tag is an exact match on the stored
 * lowercase tag.
 */
class SearchActivity : AppCompatActivity(), PostCallback {

    private enum class Mode { TICKER, TAG }

    private lateinit var binding: ActivitySearchBinding
    private val postAdapter = PostAdapter()

    private var mode = Mode.TICKER
    private var lastQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.searchTBToolbar.setNavigationOnClickListener { finish() }

        postAdapter.postCallback = this
        binding.searchRVResults.layoutManager = LinearLayoutManager(this)
        binding.searchRVResults.adapter = postAdapter

        initFilters()
        initQueryField()
    }

    private fun initFilters() {
        binding.searchLAYFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            mode = if (checkedIds.firstOrNull() == R.id.search_CHIP_tag) Mode.TAG else Mode.TICKER
            binding.searchLAYQuery.hint = getString(
                if (mode == Mode.TAG) R.string.search_hint_tag else R.string.search_hint_ticker
            )
            // Re-run so switching mode reinterprets what is already typed.
            if (lastQuery.isNotEmpty()) runSearch()
        }
    }

    private fun initQueryField() {
        binding.searchEDTQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch()
                true
            } else {
                false
            }
        }
    }

    private fun runSearch() {
        val query = binding.searchEDTQuery.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) {
            showMessage(getString(R.string.search_idle))
            return
        }

        lastQuery = query
        hideKeyboard()
        binding.searchPRGLoading.visibility = View.VISIBLE
        binding.searchLBLMessage.visibility = View.GONE
        binding.searchRVResults.visibility = View.GONE

        val onResult: (List<Post>?, Exception?) -> Unit = { posts, _ ->
            if (!isFinishing && !isDestroyed) {
                binding.searchPRGLoading.visibility = View.GONE
                when {
                    posts == null -> showMessage(getString(R.string.search_error))
                    posts.isEmpty() ->
                        showMessage(getString(R.string.search_no_results, query))

                    else -> attachLikeStates(posts)
                }
            }
        }

        when (mode) {
            Mode.TICKER -> DatabaseManager.getInstance().searchPostsByTicker(query, onResult)
            Mode.TAG -> DatabaseManager.getInstance().searchPostsByTag(query, onResult)
        }
    }

    private fun attachLikeStates(posts: List<Post>) {
        val uid = AuthManager.getInstance().currentUid()
        DatabaseManager.getInstance().fetchLikedPostIds(posts.map { it.id }, uid) { likedIds ->
            if (isFinishing || isDestroyed) return@fetchLikedPostIds
            postAdapter.setData(posts, likedIds)
            binding.searchRVResults.visibility = View.VISIBLE
            binding.searchLBLMessage.visibility = View.GONE
        }
    }

    private fun showMessage(message: String) {
        binding.searchRVResults.visibility = View.GONE
        binding.searchLBLMessage.visibility = View.VISIBLE
        binding.searchLBLMessage.text = message
    }

    private fun hideKeyboard() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(binding.searchEDTQuery.windowToken, 0)
    }

    // ----------------------------------------------------------- Post cards

    override fun onPostClicked(post: Post, position: Int) =
        PostDetailActivity.start(this, post.id)

    override fun onCommentClicked(post: Post, position: Int) =
        PostDetailActivity.start(this, post.id)

    override fun onAuthorClicked(post: Post, position: Int) =
        ProfileActivity.start(this, post.authorId)

    override fun onTickerClicked(post: Post, position: Int) =
        TickerPostsActivity.start(this, post.ticker)

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
        fun start(context: Context) {
            context.startActivity(Intent(context, SearchActivity::class.java))
        }
    }
}
