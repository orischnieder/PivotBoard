package com.ori.pivotboard_project.model

import com.google.firebase.firestore.PropertyName

/**
 * Firestore document `users/{uid}/watchlist/{ticker}`.
 * The document id is the ticker itself, which keeps the watchlist naturally de-duplicated.
 *
 * [isPublic] is annotated on purpose. Firestore maps properties from their JavaBean getters
 * and strips an `is` prefix, so a Kotlin `var isPublic` would otherwise be stored as the
 * field `public`. That name has to match exactly, because the security rule reads
 * `resource.data.isPublic` and the public-watchlist query filters on the same key.
 */
data class WatchItem(
    var id: String = "",
    var ticker: String = "",
    @get:PropertyName("isPublic")
    @set:PropertyName("isPublic")
    var isPublic: Boolean = false,
    var addedAt: Long = 0L
)
