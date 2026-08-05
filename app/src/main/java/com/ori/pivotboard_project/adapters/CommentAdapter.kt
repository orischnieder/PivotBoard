package com.ori.pivotboard_project.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ori.pivotboard_project.databinding.CommentItemBinding
import com.ori.pivotboard_project.interfaces.CommentCallback
import com.ori.pivotboard_project.model.Comment
import com.ori.pivotboard_project.utilities.TimeFormatter

/** Comments on the post detail screen, oldest first. */
class CommentAdapter(
    var items: List<Comment> = listOf()
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    var commentCallback: CommentCallback? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = CommentItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun setData(comments: List<Comment>) {
        items = comments
        notifyDataSetChanged()
    }

    inner class CommentViewHolder(private val binding: CommentItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.commentLBLAuthor.setOnClickListener {
                val position = absoluteAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                commentCallback?.onCommentAuthorClicked(items[position], position)
            }
        }

        fun bind(comment: Comment) {
            binding.commentLBLAuthor.text = comment.authorName
            binding.commentLBLText.text = comment.text
            binding.commentLBLTime.text = TimeFormatter.relative(comment.createdAt)
        }
    }
}
