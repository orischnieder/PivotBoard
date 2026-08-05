package com.ori.pivotboard_project.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.databinding.PostItemBinding
import com.ori.pivotboard_project.interfaces.PostCallback
import com.ori.pivotboard_project.model.Post
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

    inner class PostViewHolder(private val binding: PostItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.postCARDRoot.setOnClickListener { notify { post, pos -> onPostClicked(post, pos) } }
            binding.postBTNLike.setOnClickListener { notify { post, pos -> onLikeClicked(post, pos) } }
            binding.postBTNComment.setOnClickListener { notify { post, pos -> onCommentClicked(post, pos) } }
            binding.postIMGAvatar.setOnClickListener { notify { post, pos -> onAuthorClicked(post, pos) } }
            binding.postLBLAuthor.setOnClickListener { notify { post, pos -> onAuthorClicked(post, pos) } }
            binding.postLBLTicker.setOnClickListener { notify { post, pos -> onTickerClicked(post, pos) } }
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

            bindLikeState(post)
        }

        private fun bindLikeState(post: Post) {
            val isLiked = likedPostIds.contains(post.id)
            binding.postBTNLike.setIconResource(
                if (isLiked) R.drawable.ic_like_filled else R.drawable.ic_like
            )
            val tintAttr =
                if (isLiked) androidx.appcompat.R.attr.colorPrimary
                else com.google.android.material.R.attr.colorOnSurfaceVariant
            val tint = MaterialColors.getColor(binding.postBTNLike, tintAttr)
            binding.postBTNLike.iconTint = ColorStateList.valueOf(tint)
        }
    }
}
