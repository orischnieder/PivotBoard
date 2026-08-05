package com.ori.pivotboard_project.interfaces

import com.ori.pivotboard_project.model.Post

/** How a post card reports user intent back to the fragment hosting the list. */
interface PostCallback {
    fun onPostClicked(post: Post, position: Int)
    fun onLikeClicked(post: Post, position: Int)
    fun onCommentClicked(post: Post, position: Int)
    fun onAuthorClicked(post: Post, position: Int)
    fun onTickerClicked(post: Post, position: Int)
}
