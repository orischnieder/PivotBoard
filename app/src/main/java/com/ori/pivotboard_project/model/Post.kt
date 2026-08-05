package com.ori.pivotboard_project.model

/** Firestore document `posts/{postId}`. See [User] for the field conventions. */
data class Post(
    var id: String = "",
    var authorId: String = "",
    var authorName: String = "",
    var authorPhotoUrl: String = "",
    var ticker: String = "",
    var setupType: String = "",
    var imageUrl: String = "",
    var notes: String = "",
    var tags: List<String> = listOf(),
    var createdAt: Long = 0L,
    var likeCount: Long = 0,
    var commentCount: Long = 0
)
