package com.ori.pivotboard_project.adapters

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.activities.PostDetailActivity
import com.ori.pivotboard_project.activities.ProfileActivity
import com.ori.pivotboard_project.activities.TickerPostsActivity
import com.ori.pivotboard_project.interfaces.PostCallback
import com.ori.pivotboard_project.model.Post
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.DatabaseManager
import com.ori.pivotboard_project.utilities.SignalManager

/**
 * The behaviour behind a post card, in one place.
 *
 * [PostAdapter] is hosted by the feed, a profile, the ticker-filtered list and search. Each
 * of those used to carry its own copy of the like handling; every one of them would now
 * need a copy of the delete flow too. They share this instead and override only where they
 * genuinely differ - a profile does not reopen the author it is already showing, and the
 * ticker screen does not re-filter by the ticker it is already filtered to.
 *
 * [isActive] lets a host say whether its views are still alive, so an async result never
 * touches a destroyed fragment or activity.
 */
open class PostActionHandler(
    protected val context: Context,
    protected val adapter: PostAdapter,
    protected val isActive: () -> Boolean,
    private val onPostDeleted: ((Post) -> Unit)? = null
) : PostCallback {

    override fun onPostClicked(post: Post, position: Int) =
        PostDetailActivity.start(context, post.id)

    /** The comment box lives on the detail screen, so both routes land in the same place. */
    override fun onCommentClicked(post: Post, position: Int) =
        PostDetailActivity.start(context, post.id)

    override fun onAuthorClicked(post: Post, position: Int) =
        ProfileActivity.start(context, post.authorId)

    override fun onTickerClicked(post: Post, position: Int) =
        TickerPostsActivity.start(context, post.ticker)

    /** Optimistic: flip the card now, roll back if the write fails. */
    override fun onLikeClicked(post: Post, position: Int) {
        val uid = AuthManager.getInstance().currentUid()
        if (uid.isEmpty()) return

        val wasLiked = adapter.likedPostIds.contains(post.id)
        applyLikeLocally(post, position, !wasLiked)

        DatabaseManager.getInstance().toggleLike(
            post = post,
            uid = uid,
            fromName = AuthManager.getInstance().currentUser()?.displayName.orEmpty(),
            shouldLike = !wasLiked
        ) { success ->
            if (!isActive()) return@toggleLike
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

    /** Destructive and irreversible, so it always goes through a confirmation first. */
    override fun onDeletePostClicked(post: Post, position: Int) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.post_delete_title)
            .setMessage(R.string.post_delete_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.post_delete_confirm) { _, _ ->
                deletePost(post, position)
            }
            .show()
    }

    private fun deletePost(post: Post, position: Int) {
        DatabaseManager.getInstance().deletePost(post) { success ->
            if (!isActive()) return@deletePost

            if (success) {
                adapter.removeItem(position)
                SignalManager.getInstance().toast(R.string.post_deleted)
                onPostDeleted?.invoke(post)
            } else {
                SignalManager.getInstance().toast(R.string.post_delete_failed)
            }
        }
    }
}
