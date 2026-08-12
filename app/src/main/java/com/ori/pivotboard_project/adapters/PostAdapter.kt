package com.ori.pivotboard_project.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.databinding.PostItemBinding
import com.ori.pivotboard_project.interfaces.PostCallback
import com.ori.pivotboard_project.model.Post
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.ImageLoader
import com.ori.pivotboard_project.utilities.TimeFormatter

/**
 * Feed cards. [likedPostIds] is kept separate from [items] because a like lives in its own
 * subcollection - it is not part of the post document.
 */
class PostAdapter(
    var items: List<Post> = listOf(),
    var likedPostIds: Set<String> = setOf()
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    var postCallback: PostCallback? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = PostItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun setData(posts: List<Post>, likedIds: Set<String>) {
        items = posts
        likedPostIds = likedIds
        notifyDataSetChanged()
    }

    /** Drops a row locally after its post has been deleted server-side. */
    fun removeItem(position: Int) {
        if (position !in items.indices) return
        items = items.toMutableList().apply { removeAt(position) }
        notifyItemRemoved(position)
        // Later holders cache their own positions, so refresh the tail.
        notifyItemRangeChanged(position, items.size - position)
    }

    inner class PostViewHolder(private val binding: PostItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.postCARDRoot.setOnClickListener { notify { post, pos -> onPostClicked(post, pos) } }
            binding.postBTNLike.setOnClickListener { notify { post, pos -> onLikeClicked(post, pos) } }
            binding.postBTNComment.setOnClickListener { notify { post, pos -> onCommentClicked(post, pos) } }
            binding.postIMGAvatar.setOnClickListener { notify { post, pos -> onAuthorClicked(post, pos) } }
            binding.postLBLAuthor.setOnClickListener { notify { post, pos -> onAuthorClicked(post, pos) } }
            binding.postLBLTicker.setOnClickListener { notify { post, pos -> onTickerClicked(post, pos) } }
            binding.postBTNMenu.setOnClickListener { showOverflowMenu() }
        }

        /**
         * The PopupMenu is built here rather than in the callback: the adapter owns the
         * views, so it owns the anchoring. The callback stays about intent.
         */
        private fun showOverflowMenu() {
            val position = absoluteAdapterPosition
            if (position == RecyclerView.NO_POSITION) return

            PopupMenu(binding.postBTNMenu.context, binding.postBTNMenu).apply {
                menuInflater.inflate(R.menu.post_item_menu, menu)
                setOnMenuItemClickListener { item ->
                    if (item.itemId == R.id.post_MNU_delete) {
                        val current = absoluteAdapterPosition
                        if (current != RecyclerView.NO_POSITION) {
                            postCallback?.onDeletePostClicked(items[current], current)
                        }
                        true
                    } else {
                        false
                    }
                }
            }.show()
        }

        /** Guards every click against a stale position after the list has changed. */
        private inline fun notify(action: PostCallback.(post: Post, position: Int) -> Unit) {
            val position = absoluteAdapterPosition
            if (position == RecyclerView.NO_POSITION) return
            postCallback?.action(items[position], position)
        }

        fun bind(post: Post) {
            val imageLoader = ImageLoader.getInstance()

            binding.postLBLAuthor.text = post.authorName
            binding.postLBLTime.text = TimeFormatter.relative(post.createdAt)
            binding.postLBLTicker.text = post.ticker
            binding.postLBLSetup.text = post.setupType

            imageLoader.loadImage(post.authorPhotoUrl, binding.postIMGAvatar)
            imageLoader.loadImage(post.imageUrl, binding.postIMGChart)

            binding.postLBLNotes.text = post.notes
            binding.postLBLNotes.visibility = if (post.notes.isBlank()) View.GONE else View.VISIBLE

            binding.postBTNLike.text = post.likeCount.toString()
            binding.postBTNComment.text = post.commentCount.toString()

            // Only the author can delete, so only the author sees the overflow.
            val isOwnPost = post.authorId == AuthManager.getInstance().currentUid()
            binding.postBTNMenu.visibility = if (isOwnPost) View.VISIBLE else View.GONE

            bindLikeState(post)
        }

        private fun bindLikeState(post: Post) {
            val isLiked = likedPostIds.contains(post.id)
            binding.postBTNLike.setIconResource(
                if (isLiked) R.drawable.ic_like_filled else R.drawable.ic_like
            )
            // A liked heart is the one place the brand red appears on a card.
            val tint = if (isLiked) {
                ContextCompat.getColor(binding.root.context, R.color.like_red)
            } else {
                MaterialColors.getColor(
                    binding.postBTNLike,
                    com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            }
            binding.postBTNLike.iconTint = ColorStateList.valueOf(tint)
        }
    }
}
