package com.ori.pivotboard_project.interfaces

import com.ori.pivotboard_project.model.Comment

/** How a comment row reports user intent back to the screen hosting the list. */
interface CommentCallback {
    fun onCommentAuthorClicked(comment: Comment, position: Int)
}
