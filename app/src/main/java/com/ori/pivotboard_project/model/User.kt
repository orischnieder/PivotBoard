package com.ori.pivotboard_project.model

/**
 * Firestore document `users/{uid}`.
 * Every field is a `var` with a default so `toObject<User>()` can construct it reflectively.
 * `id` is not stored in the document - assign it from `document.id` after loading.
 */
data class User(
    var id: String = "",
    var displayName: String = "",
    var username: String = "",
    var photoUrl: String = "",
    var bio: String = "",
    var followersCount: Long = 0,
    var followingCount: Long = 0,
    var postsCount: Long = 0,
    var createdAt: Long = 0L
)
