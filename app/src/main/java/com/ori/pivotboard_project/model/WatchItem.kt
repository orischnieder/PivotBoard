package com.ori.pivotboard_project.model

/**
 * Firestore document `users/{uid}/watchlist/{ticker}`.
 * The document id is the ticker itself, which keeps the watchlist naturally de-duplicated.
 */
data class WatchItem(
    var id: String = "",
    var ticker: String = "",
    var isPublic: Boolean = false,
    var addedAt: Long = 0L
)
