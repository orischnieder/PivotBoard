package com.ori.pivotboard_project.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.adapters.PostActionHandler
import com.ori.pivotboard_project.adapters.PostAdapter
import com.ori.pivotboard_project.databinding.FragmentPostListBinding
import com.ori.pivotboard_project.model.Post
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.DatabaseManager

/**
 * One page of the feed. Both tabs use this same fragment - only [mode] differs, which keeps
 * the list and its states in a single place. Card behaviour lives in [PostActionHandler].
 */
class PostListFragment : Fragment() {

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

        adapter.postCallback = PostActionHandler(
            context = requireContext(),
            adapter = adapter,
            isActive = { this.binding != null }
        )
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
