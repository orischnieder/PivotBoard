package com.ori.pivotboard_project.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.databinding.UserItemBinding
import com.ori.pivotboard_project.interfaces.UserCallback
import com.ori.pivotboard_project.model.User
import com.ori.pivotboard_project.utilities.ImageLoader

/** Rows of people - followers, following, and later search results. */
class UserAdapter(
    var items: List<User> = listOf()
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    var userCallback: UserCallback? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = UserItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun setData(users: List<User>) {
        items = users
        notifyDataSetChanged()
    }

    inner class UserViewHolder(private val binding: UserItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.userLAYRoot.setOnClickListener {
                val position = absoluteAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                userCallback?.onUserClicked(items[position], position)
            }
        }

        fun bind(user: User) {
            binding.userLBLName.text = user.displayName.ifBlank { user.username }
            binding.userLBLUsername.text = binding.root.context
                .getString(R.string.profile_username_format, user.username)
            ImageLoader.getInstance().loadImage(user.photoUrl, binding.userIMGAvatar)
        }
    }
}
