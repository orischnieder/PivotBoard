package com.ori.pivotboard_project.activities

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.adapters.CommentAdapter
import com.ori.pivotboard_project.databinding.ActivityPostDetailBinding
import com.ori.pivotboard_project.interfaces.CommentCallback
import com.ori.pivotboard_project.model.Comment
import com.ori.pivotboard_project.model.Post
import com.ori.pivotboard_project.utilities.applySystemBarPadding
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.Constants
import com.ori.pivotboard_project.utilities.DatabaseManager
import com.ori.pivotboard_project.utilities.ImageLoader
import com.ori.pivotboard_project.utilities.SignalManager
import androidx.core.content.ContextCompat
import com.ori.pivotboard_project.utilities.TimeFormatter
import com.ori.pivotboard_project.utilities.hide
import com.ori.pivotboard_project.utilities.showError
import com.ori.pivotboard_project.utilities.showLoading

/**
 * Section 5.4 - the full post with its likes and comments.
 *
 * A drill-down screen rather than a bottom-nav tab, so it is its own Activity; the tab
 * fragments stay owned by [MainActivity].
 *
 * Comments use a snapshot listener so a new comment appears without a manual refresh. The
 * registration is removed in `onStop` to avoid leaking it.
 */
class PostDetailActivity : AppCompatActivity(), CommentCallback {

    private lateinit var binding: ActivityPostDetailBinding
    private val commentAdapter = CommentAdapter()

    private var post: Post? = null
    private var isLiked = false
    private var isDeleting = false
    private var commentsRegistration: ListenerRegistration? = null

    private val postId: String
        get() = intent.getStringExtra(Constants.BUNDLE_KEYS.POST_ID).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // applyIme: the comment box is pinned to the bottom, so it has to ride above the
        // keyboard rather than sit under it.
        binding.root.applySystemBarPadding(applyIme = true)

        binding.detailTBToolbar.setNavigationOnClickListener { finish() }
        binding.detailTBToolbar.inflateMenu(R.menu.post_detail_menu)
        binding.detailTBToolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.detail_MNU_delete) {
                confirmDelete()
                true
            } else {
                false
            }
        }

        commentAdapter.commentCallback = this
        binding.detailRVComments.layoutManager = LinearLayoutManager(this)
        binding.detailRVComments.adapter = commentAdapter

        binding.detailBTNLike.setOnClickListener { toggleLike() }
        binding.detailBTNSend.setOnClickListener { sendComment() }

        val openAuthor = View.OnClickListener {
            post?.let { ProfileActivity.start(this, it.authorId) }
        }
        binding.detailIMGAvatar.setOnClickListener(openAuthor)
        binding.detailLBLAuthor.setOnClickListener(openAuthor)

        if (postId.isEmpty()) {
            showError()
            return
        }
        loadPost()
    }

    /** Attached here rather than in onCreate so onStop can cleanly detach it. */
    override fun onStart() {
        super.onStart()
        if (postId.isNotEmpty()) listenToComments()
    }

    override fun onStop() {
        super.onStop()
        commentsRegistration?.remove()
        commentsRegistration = null
    }

    // ------------------------------------------------------------- Loading

    private fun loadPost() {
        setLoading(true)
        DatabaseManager.getInstance().loadPost(postId) { loaded, _ ->
            if (isFinishing || isDestroyed) return@loadPost

            if (loaded == null) {
                showError()
                return@loadPost
            }
            post = loaded
            // The delete action only exists for the author.
            binding.detailTBToolbar.menu.findItem(R.id.detail_MNU_delete)?.isVisible =
                loaded.authorId == AuthManager.getInstance().currentUid()
            bindPost(loaded)
            loadLikeState()
            showContent()
        }
    }

    private fun loadLikeState() {
        val uid = AuthManager.getInstance().currentUid()
        DatabaseManager.getInstance().fetchLikedPostIds(listOf(postId), uid) { likedIds ->
            if (isFinishing || isDestroyed) return@fetchLikedPostIds
            isLiked = likedIds.contains(postId)
            bindLikeState()
        }
    }

    private fun listenToComments() {
        commentsRegistration = DatabaseManager.getInstance().listenToComments(
            postId = postId,
            onChange = { comments ->
                if (isFinishing || isDestroyed) return@listenToComments
                commentAdapter.setData(comments)
                binding.detailLBLNoComments.visibility =
                    if (comments.isEmpty()) View.VISIBLE else View.GONE
                // The listener is the freshest source, so trust it over the stored counter.
                bindCommentCount(comments.size)
            },
            onError = {
                if (isFinishing || isDestroyed) return@listenToComments
                SignalManager.getInstance().toast(R.string.detail_error_comments)
            }
        )
    }

    // ------------------------------------------------------------- Binding

    private fun bindPost(post: Post) {
        val imageLoader = ImageLoader.getInstance()

        binding.detailLBLAuthor.text = post.authorName
        binding.detailLBLTime.text = TimeFormatter.relative(post.createdAt)
        binding.detailLBLTicker.text = post.ticker
        binding.detailLBLSetup.text = post.setupType
        binding.detailLBLNotes.text = post.notes
        binding.detailLBLNotes.visibility = if (post.notes.isBlank()) View.GONE else View.VISIBLE

        imageLoader.loadImage(post.authorPhotoUrl, binding.detailIMGAvatar)
        imageLoader.loadImage(post.imageUrl, binding.detailIMGChart)

        binding.detailLBLTags.apply {
            text = post.tags.joinToString(" ") { "#$it" }
            visibility = if (post.tags.isEmpty()) View.GONE else View.VISIBLE
        }

        bindCommentCount(post.commentCount.toInt())
        bindLikeState()
    }

    private fun bindLikeState() {
        val likeCount = post?.likeCount ?: 0
        binding.detailBTNLike.text = likeCount.toString()
        binding.detailBTNLike.setIconResource(
            if (isLiked) R.drawable.ic_like_filled else R.drawable.ic_like
        )
        // Matches the feed card: liked hearts carry the brand red.
        val tint = if (isLiked) {
            ContextCompat.getColor(this, R.color.like_red)
        } else {
            MaterialColors.getColor(
                binding.detailBTNLike,
                com.google.android.material.R.attr.colorOnSurfaceVariant
            )
        }
        binding.detailBTNLike.iconTint = ColorStateList.valueOf(tint)
    }

    private fun bindCommentCount(count: Int) {
        binding.detailLBLCommentCount.text =
            resources.getQuantityString(R.plurals.detail_comment_count, count, count)
    }

    // ------------------------------------------------------------- Actions

    /** Optimistic, mirroring the feed: flip immediately, roll back if the write fails. */
    private fun toggleLike() {
        val post = this.post ?: return
        val uid = AuthManager.getInstance().currentUid()
        if (uid.isEmpty()) return

        val wasLiked = isLiked
        applyLikeLocally(!wasLiked)

        DatabaseManager.getInstance().toggleLike(
            post = post,
            uid = uid,
            fromName = AuthManager.getInstance().currentUser()?.displayName.orEmpty(),
            shouldLike = !wasLiked
        ) { success ->
            if (isFinishing || isDestroyed) return@toggleLike
            if (!success) {
                applyLikeLocally(wasLiked)
                SignalManager.getInstance().toast(R.string.error_like_failed)
            }
        }
    }

    private fun applyLikeLocally(liked: Boolean) {
        val post = this.post ?: return
        isLiked = liked
        post.likeCount = (post.likeCount + if (liked) 1 else -1).coerceAtLeast(0)
        bindLikeState()
    }

    private fun sendComment() {
        val post = this.post ?: return
        val uid = AuthManager.getInstance().currentUid()
        if (uid.isEmpty()) {
            SignalManager.getInstance().toast(R.string.create_error_not_signed_in)
            return
        }

        val text = binding.detailEDTComment.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            SignalManager.getInstance().toast(R.string.detail_comment_empty)
            return
        }

        val currentUser = AuthManager.getInstance().currentUser()
        val comment = Comment(
            authorId = uid,
            authorName = currentUser?.displayName?.takeIf { it.isNotBlank() }
                ?: currentUser?.email?.substringBefore('@').orEmpty(),
            text = text,
            createdAt = System.currentTimeMillis()
        )

        binding.detailBTNSend.isEnabled = false
        DatabaseManager.getInstance().addComment(postId, post.authorId, comment) { success ->
            if (isFinishing || isDestroyed) return@addComment
            binding.detailBTNSend.isEnabled = true

            if (success) {
                // The snapshot listener renders the new comment; just clear the field.
                binding.detailEDTComment.text = null
            } else {
                SignalManager.getInstance().toast(R.string.detail_error_comment_failed)
            }
        }
    }

    override fun onCommentAuthorClicked(comment: Comment, position: Int) =
        ProfileActivity.start(this, comment.authorId)

    private fun confirmDelete() {
        if (isDeleting) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.post_delete_title)
            .setMessage(R.string.post_delete_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.post_delete_confirm) { _, _ -> deletePost() }
            .show()
    }

    private fun deletePost() {
        val post = this.post ?: return
        if (isDeleting) return

        // Also hide the action, so the menu cannot re-arm while the delete is running.
        isDeleting = true
        binding.detailTBToolbar.menu.findItem(R.id.detail_MNU_delete)?.isEnabled = false

        DatabaseManager.getInstance().deletePost(post) { success ->
            isDeleting = false
            if (isFinishing || isDestroyed) return@deletePost
            binding.detailTBToolbar.menu.findItem(R.id.detail_MNU_delete)?.isEnabled = true

            if (success) {
                SignalManager.getInstance().toast(R.string.post_deleted)
                // Nothing left to show, so close back to whatever opened this screen.
                finish()
            } else {
                SignalManager.getInstance().toast(R.string.post_delete_failed)
            }
        }
    }

    // --------------------------------------------------------------- States

    private fun setLoading(loading: Boolean) {
        if (loading) binding.detailLAYState.showLoading() else binding.detailLAYState.hide()
    }

    private fun showContent() {
        binding.detailLAYState.hide()
        binding.detailLAYContent.visibility = View.VISIBLE
        binding.detailLAYCompose.visibility = View.VISIBLE
    }

    /**
     * No retry: the usual cause is a deleted post, so re-running the same fetch would just
     * fail again. Backing out is the only sensible move.
     */
    private fun showError() {
        binding.detailLAYContent.visibility = View.GONE
        binding.detailLAYCompose.visibility = View.GONE
        binding.detailLAYState.showError(body = R.string.detail_error_load)
    }

    companion object {
        fun start(context: Context, postId: String) {
            val intent = Intent(context, PostDetailActivity::class.java)
                .putExtra(Constants.BUNDLE_KEYS.POST_ID, postId)
            context.startActivity(intent)
        }
    }
}
