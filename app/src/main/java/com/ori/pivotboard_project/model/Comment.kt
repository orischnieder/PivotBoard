package com.ori.pivotboard_project.model

/** Firestore document `posts/{postId}/comments/{commentId}`. */
data class Comment(
    var id: String = "",
    var authorId: String = "",
    var authorName: String = "",
    var text: String = "",
    var createdAt: Long = 0L
)
