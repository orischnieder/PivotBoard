package com.ori.pivotboard_project.model

/**
 * Firestore document `users/{uid}/notifications/{notifId}`.
 * `type` is one of `Constants.NOTIFICATION_TYPE`; `postId` is empty for follow notifications.
 */
data class AppNotification(
    var id: String = "",
    var type: String = "",
    var fromUid: String = "",
    var fromName: String = "",
    var postId: String = "",
    var createdAt: Long = 0L,
    var read: Boolean = false
)
