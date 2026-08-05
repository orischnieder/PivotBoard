package com.ori.pivotboard_project.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.activities.PostDetailActivity
import com.ori.pivotboard_project.adapters.PostAdapter
import com.ori.pivotboard_project.databinding.FragmentPostListBinding
import com.ori.pivotboard_project.interfaces.PostCallback
import com.ori.pivotboard_project.model.Post
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.DatabaseManager
import com.ori.pivotboard_project.utilities.SignalManager

/**
 * One page of the feed. Both tabs use this same fragment - only [mode] differs, which keeps
 * the list, its states and the like handling in a single place.
 */
class PostListFragment : Fragment(), PostCallback {

    enum class Mode { FOLLOWING, DISCOVER }

    private var binding: FragmentPostListBinding? = null
    private val adapter = PostAdapter()

    private val mode: Mode
        get() = Mode.valueOf(arguments?.getString(ARG_MODE) ?: Mode.DISCOVER.name)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPostListBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = this.binding ?: return

        adapter.postCallback = this
        binding.postlistRVPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.postlistRVPosts.adapter = adapter

        binding.postlistLAYSwipe.setOnRefreshListener { loadFeed(isRefresh = true) }
        binding.postlistBTNRetry.setOnClickListener { loadFeed(isRefresh = false) }

        loadFeed(isRefresh = false)
    }

    /** Reloads on return so likes or posts made elsewhere in the app show up. */
    override fun onResume() {
        super.onResume()
        if (adapter.items.isNotEmpty()) loadFeed(isRefresh = true)
    }

    private fun loadFeed(isRefresh: Boolean) {
        val binding = this.binding ?: return
        if (!isRefresh) showLoading()

        val database = DatabaseManager.getInstance()
        val onLoaded: (List<Post>?, Exception?) -> Unit = { posts, error ->
            // The fragment may have been torn down while the query was in flight.
            if (this.binding != null) {
                binding.postlistLAYSwipe.isRefreshing = false
                if (posts == null) showError() else attachLikeStates(posts)
            }
        }

        when (mode) {
            Mode.DISCOVER -> database.loadDiscoverFeed(onLoaded)
            Mode.FOLLOWING -> database.loadFollowingFeed(AuthManager.getInstance().currentUid(), onLoaded)
        }
    }

    /** A like lives in a subcollection, so it is resolved after the posts themselves. */
    private fun attachLikeStates(posts: List<Post>) {
        val uid = AuthManager.getInstance().currentUid()
        DatabaseManager.getInstance().fetchLikedPostIds(posts.map { it.id }, uid) { likedIds ->
            if (binding == null) return@fetchLikedPostIds
            adapter.setData(posts, likedIds)
            if (posts.isEmpty()) showEmpty() else showContent()
        }
    }

    // ------------------------------------------------------------- States

    private fun showLoading() = setState(loading = true)

    private fun showContent() = setState(content = true)

    private fun showError() = setState(error = true)

    private fun showEmpty() {
        val binding = this.binding ?: return
        setState(empty = true)

        val isFollowing = mode == Mode.FOLLOWING
        binding.postlistLBLEmptyTitle.setText(
            if (isFollowing) R.string.feed_empty_following_title else R.string.feed_empty_discover_title
        )
        binding.postlistLBLEmptyBody.setText(
            if (isFollowing) R.string.feed_empty_following_body else R.string.feed_empty_discover_body
        )
    }

    private fun setState(
        loading: Boolean = false,
        content: Boolean = false,
        empty: Boolean = false,
        error: Boolean = false
    ) {
        val binding = this.binding ?: return
        binding.postlistPRGLoading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.postlistLAYSwipe.visibility = if (content) View.VISIBLE else View.GONE
        binding.postlistLAYEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.postlistLAYError.visibility = if (error) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------ Callbacks

    /**
     * Optimistic: the card flips immediately and is rolled back if the write fails, so the
     * feed never feels like it is waiting on the network.
     */
    override fun onLikeClicked(post: Post, position: Int) {
        val uid = AuthManager.getInstance().currentUid()
        if (uid.isEmpty()) return

        val wasLiked = adapter.likedPostIds.contains(post.id)
        val shouldLike = !wasLiked

        applyLikeLocally(post, position, shouldLike)

        DatabaseManager.getInstance().toggleLike(
            post = post,
            uid = uid,
            fromName = AuthManager.getInstance().currentUser()?.displayName.orEmpty(),
            shouldLike = shouldLike
        ) { success ->
            if (binding == null) return@toggleLike
            if (!success) {
                applyLikeLocally(post, position, wasLiked)
                SignalManager.getInstance().toast(R.string.error_like_failed)
            }
        }
    }

    private fun applyLikeLocally(post: Post, position: Int, isLiked: Boolean) {
        adapter.likedPostIds =
            if (isLiked) adapter.likedPostIds + post.id else adapter.likedPostIds - post.id
        post.likeCount = (post.likeCount + if (isLiked) 1 else -1).coerceAtLeast(0)
        adapter.notifyItemChanged(position)
    }

    override fun onPostClicked(post: Post, position: Int) = openDetail(post)

    /** The comment button opens the same screen - the comment box lives there. */
    override fun onCommentClicked(post: Post, position: Int) = openDetail(post)

    private fun openDetail(post: Post) {
        PostDetailActivity.start(requireContext(), post.id)
    }

    // Profile and ticker search are not built yet (sections 5.5 - 5.6).
    override fun onAuthorClicked(post: Post, position: Int) = Unit

    override fun onTickerClicked(post: Post, position: Int) = Unit

    override fun onDestroyView() {
        super.onDestroyView()
        adapter.postCallback = null
        binding?.postlistRVPosts?.adapter = null
        binding = null
    }

    companion object {
        private const val ARG_MODE = "ARG_MODE"

        fun newInstance(mode: Mode): PostListFragment = PostListFragment().apply {
            arguments = Bundle().apply { putString(ARG_MODE, mode.name) }
        }
    }
}
