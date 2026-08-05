package com.ori.pivotboard_project.utilities

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject
import com.ori.pivotboard_project.model.AppNotification
import com.ori.pivotboard_project.model.Comment
import com.ori.pivotboard_project.model.Post
import com.ori.pivotboard_project.model.User
import com.ori.pivotboard_project.model.WatchItem

/**
 * The only class in the app that holds a [FirebaseFirestore] handle.
 *
 * This scaffold step exposes the collection references plus the one write the login flow
 * needs. Feed/post/comment/watchlist/notification queries are added alongside their screens.
 */
class DatabaseManager private constructor(context: Context) {

    private val db: FirebaseFirestore = Firebase.firestore

    val usersRef: CollectionReference = db.collection(Constants.FIRESTORE.USERS)
    val postsRef: CollectionReference = db.collection(Constants.FIRESTORE.POSTS)

    fun userDoc(uid: String): DocumentReference = usersRef.document(uid)

    fun postDoc(postId: String): DocumentReference = postsRef.document(postId)

    fun commentsRef(postId: String): CollectionReference =
        postDoc(postId).collection(Constants.FIRESTORE.COMMENTS)

    fun likesRef(postId: String): CollectionReference =
        postDoc(postId).collection(Constants.FIRESTORE.LIKES)

    fun followingRef(uid: String): CollectionReference =
        userDoc(uid).collection(Constants.FIRESTORE.FOLLOWING)

    fun followersRef(uid: String): CollectionReference =
        userDoc(uid).collection(Constants.FIRESTORE.FOLLOWERS)

    fun watchlistRef(uid: String): CollectionReference =
        userDoc(uid).collection(Constants.FIRESTORE.WATCHLIST)

    fun notificationsRef(uid: String): CollectionReference =
        userDoc(uid).collection(Constants.FIRESTORE.NOTIFICATIONS)

    /**
     * Creates `users/{uid}` on first sign-in, or refreshes the profile fields on later ones.
     *
     * Merged rather than overwritten so a returning user keeps their bio and counters, and
     * `createdAt` is only written when the document does not exist yet.
     */
    fun upsertUserOnSignIn(
        user: User,
        onComplete: (success: Boolean) -> Unit
    ) {
        val doc = userDoc(user.id)
        doc.get()
            .addOnSuccessListener { snapshot ->
                // An existing user only gets their display fields refreshed; counters, bio
                // and createdAt are left alone. A new user gets the full zeroed profile.
                val profile: Map<String, Any> = if (snapshot.exists()) {
                    mapOf(
                        Constants.FIRESTORE.USER_DISPLAY_NAME to user.displayName,
                        Constants.FIRESTORE.USER_USERNAME to user.username,
                        Constants.FIRESTORE.USER_PHOTO_URL to user.photoUrl
                    )
                } else {
                    newProfileFor(user)
                }

                doc.set(profile, SetOptions.merge())
                    .addOnSuccessListener { onComplete(true) }
                    .addOnFailureListener { e ->
                        logFirestoreFailure("upsertUserOnSignIn", e)
                        onComplete(false)
                    }
            }
            .addOnFailureListener { e ->
                logFirestoreFailure("upsertUserOnSignIn (read)", e)
                onComplete(false)
            }
    }

    /**
     * Self-heal for accounts whose profile document is missing.
     *
     * [upsertUserOnSignIn] only runs on an explicit sign-in result. Once a session is
     * persisted the app goes Splash -> Main and never revisits it, so an account whose
     * first upsert failed would stay broken forever. This runs on every launch and only
     * writes when the document is genuinely absent, so the steady-state cost is one read.
     */
    fun ensureUserDocument(user: User, onComplete: ((created: Boolean) -> Unit)? = null) {
        if (user.id.isEmpty()) {
            onComplete?.invoke(false)
            return
        }

        val doc = userDoc(user.id)
        doc.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    onComplete?.invoke(false)
                    return@addOnSuccessListener
                }

                Log.w(TAG, "users/${user.id} missing - recreating it")
                doc.set(newProfileFor(user), SetOptions.merge())
                    .addOnSuccessListener { onComplete?.invoke(true) }
                    .addOnFailureListener { e ->
                        logFirestoreFailure("ensureUserDocument", e)
                        onComplete?.invoke(false)
                    }
            }
            .addOnFailureListener { e ->
                logFirestoreFailure("ensureUserDocument (read)", e)
                onComplete?.invoke(false)
            }
    }

    /** The full first-time profile, counters zeroed. */
    private fun newProfileFor(user: User): Map<String, Any> = mapOf(
        Constants.FIRESTORE.USER_DISPLAY_NAME to user.displayName,
        Constants.FIRESTORE.USER_USERNAME to user.username,
        Constants.FIRESTORE.USER_PHOTO_URL to user.photoUrl,
        Constants.FIRESTORE.USER_BIO to "",
        Constants.FIRESTORE.USER_FOLLOWERS_COUNT to 0L,
        Constants.FIRESTORE.USER_FOLLOWING_COUNT to 0L,
        Constants.FIRESTORE.USER_POSTS_COUNT to 0L,
        Constants.FIRESTORE.USER_CREATED_AT to System.currentTimeMillis()
    )

    /** A single profile. Null result means the document does not exist. */
    fun loadUser(uid: String, onResult: (user: User?, error: Exception?) -> Unit) {
        userDoc(uid).get()
            .addOnSuccessListener { doc ->
                val user = if (doc.exists()) doc.toObject<User>()?.apply { id = doc.id } else null
                onResult(user, null)
            }
            .addOnFailureListener { e ->
                logFirestoreFailure("load user", e)
                onResult(null, e)
            }
    }

    /** Editable profile fields. Merged so counters and createdAt are untouched. */
    fun updateProfile(
        uid: String,
        displayName: String,
        bio: String,
        onResult: (success: Boolean) -> Unit
    ) {
        userDoc(uid).set(
            mapOf(
                Constants.FIRESTORE.USER_DISPLAY_NAME to displayName,
                Constants.FIRESTORE.USER_BIO to bio
            ),
            SetOptions.merge()
        )
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { e ->
                logFirestoreFailure("update profile", e)
                onResult(false)
            }
    }

    /**
     * Profiles for a set of uids, fetched in `whereIn` chunks.
     *
     * Firestore caps `whereIn` at 30 values, so this mirrors the Following feed: query in
     * chunks, merge, and order the result to match [uids] rather than trusting query order.
     */
    fun loadUsersByIds(uids: List<String>, onResult: (users: List<User>) -> Unit) {
        if (uids.isEmpty()) {
            onResult(emptyList())
            return
        }

        val chunkQueries = uids
            .chunked(Constants.FEED.WHERE_IN_CHUNK)
            .map { chunk -> usersRef.whereIn(FieldPath.documentId(), chunk).get() }

        Tasks.whenAllSuccess<QuerySnapshot>(chunkQueries)
            .addOnSuccessListener { snapshots ->
                val byId = snapshots
                    .flatMap { it.documents }
                    .mapNotNull { doc -> doc.toObject<User>()?.apply { id = doc.id } }
                    .associateBy { it.id }
                // Keep the caller's ordering; a uid whose profile is missing is skipped.
                onResult(uids.mapNotNull { byId[it] })
            }
            .addOnFailureListener { e ->
                logFirestoreFailure("load users by ids", e)
                onResult(emptyList())
            }
    }

    // --------------------------------------------------------- Follow graph

    /**
     * The people following [uid], or the people [uid] follows, as full profiles.
     *
     * Two hops on purpose: the subcollections store only edges (the document id is the
     * other user's uid), so the profiles come from a second lookup.
     */
    fun loadFollowList(
        uid: String,
        followers: Boolean,
        onResult: (users: List<User>?, error: Exception?) -> Unit
    ) {
        val edgesRef = if (followers) followersRef(uid) else followingRef(uid)

        edgesRef.get()
            .addOnSuccessListener { snapshot ->
                val ids = snapshot.documents.map { it.id }
                if (ids.isEmpty()) {
                    onResult(emptyList(), null)
                    return@addOnSuccessListener
                }
                loadUsersByIds(ids) { users -> onResult(users, null) }
            }
            .addOnFailureListener { e ->
                logFirestoreFailure(if (followers) "load followers" else "load following", e)
                onResult(null, e)
            }
    }

    fun isFollowing(uid: String, targetUid: String, onResult: (following: Boolean) -> Unit) {
        if (uid.isEmpty() || targetUid.isEmpty()) {
            onResult(false)
            return
        }
        followingRef(uid).document(targetUid).get()
            .addOnSuccessListener { onResult(it.exists()) }
            .addOnFailureListener { onResult(false) }
    }

    /**
     * Follows or unfollows in a single batch: both sides of the edge plus both counters, so
     * the graph and the numbers can never disagree.
     *
     * Counters use set+merge because either user document may predate the counter field.
     * Note this writes `followersCount` on *another* user's document - firestore.rules must
     * allow that narrow case.
     */
    fun toggleFollow(
        uid: String,
        fromName: String,
        targetUid: String,
        shouldFollow: Boolean,
        onResult: (success: Boolean) -> Unit
    ) {
        if (uid.isEmpty() || targetUid.isEmpty() || uid == targetUid) {
            onResult(false)
            return
        }

        val batch = db.batch()
        val followingDoc = followingRef(uid).document(targetUid)
        val followerDoc = followersRef(targetUid).document(uid)
        val delta = if (shouldFollow) 1L else -1L

        if (shouldFollow) {
            val edge = mapOf(Constants.FIRESTORE.USER_CREATED_AT to System.currentTimeMillis())
            batch.set(followingDoc, edge)
            batch.set(followerDoc, edge)
        } else {
            batch.delete(followingDoc)
            batch.delete(followerDoc)
        }

        batch.set(
            userDoc(uid),
            mapOf(Constants.FIRESTORE.USER_FOLLOWING_COUNT to FieldValue.increment(delta)),
            SetOptions.merge()
        )
        batch.set(
            userDoc(targetUid),
            mapOf(Constants.FIRESTORE.USER_FOLLOWERS_COUNT to FieldValue.increment(delta)),
            SetOptions.merge()
        )

        batch.commit()
            .addOnSuccessListener {
                if (shouldFollow) {
                    addNotification(
                        toUid = targetUid,
                        type = Constants.NOTIFICATION_TYPE.FOLLOW,
                        fromUid = uid,
                        fromName = fromName
                    )
                }
                onResult(true)
            }
            .addOnFailureListener { e ->
                logFirestoreFailure("toggle follow", e)
                onResult(false)
            }
    }

    // ---------------------------------------------------------------- Posts

    /**
     * Every post by one author, newest first.
     *
     * Sorted client-side on purpose: `whereEqualTo` plus `orderBy` on a different field
     * would require a composite index, and this avoids asking for console setup.
     */
    fun loadUserPosts(uid: String, onResult: (posts: List<Post>?, error: Exception?) -> Unit) {
        postsRef
            .whereEqualTo(Constants.FIRESTORE.POST_AUTHOR_ID, uid)
            .limit(Constants.FEED.PAGE_SIZE)
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.toPosts().sortedByDescending { it.createdAt }, null)
            }
            .addOnFailureListener { e ->
                logFirestoreFailure("load user posts", e)
                onResult(null, e)
            }
    }

    /**
     * Writes a new post and bumps the author's `postsCount` in one batch, so the profile
     * counter can never drift from the number of posts that actually exist.
     *
     * The document id is reserved up front, which is what lets an auto-id write take part
     * in a batch at all.
     */
    fun createPost(post: Post, onResult: (postId: String?, error: Exception?) -> Unit) {
        val newPostRef = postsRef.document()
        post.id = newPostRef.id

        val batch = db.batch()
        batch.set(newPostRef, post)
        // set+merge rather than update: `update` fails outright when the user document does
        // not exist yet, which would reject the whole batch and lose the post.
        batch.set(
            userDoc(post.authorId),
            mapOf(Constants.FIRESTORE.USER_POSTS_COUNT to FieldValue.increment(1)),
            SetOptions.merge()
        )

        batch.commit()
            .addOnSuccessListener { onResult(newPostRef.id, null) }
            .addOnFailureListener { e ->
                logFirestoreFailure("create post", e)
                onResult(null, e)
            }
    }

    /** A single post, for the detail screen. Null result means the post no longer exists. */
    fun loadPost(postId: String, onResult: (post: Post?, error: Exception?) -> Unit) {
        postDoc(postId).get()
            .addOnSuccessListener { doc ->
                val post = if (doc.exists()) doc.toObject<Post>()?.apply { id = doc.id } else null
                onResult(post, null)
            }
            .addOnFailureListener { e ->
                logFirestoreFailure("load post", e)
                onResult(null, e)
            }
    }

    // ------------------------------------------------------------- Comments

    /**
     * Live comments, oldest first. Ordering on a single field inside the subcollection, so
     * no composite index is required.
     *
     * Returns the [ListenerRegistration] so the caller can remove it - see section 8.
     */
    fun listenToComments(
        postId: String,
        onChange: (comments: List<Comment>) -> Unit,
        onError: (error: Exception) -> Unit
    ): ListenerRegistration =
        commentsRef(postId)
            .orderBy(Constants.FIRESTORE.COMMENT_CREATED_AT, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    logFirestoreFailure("comments listener", e)
                    onError(e)
                    return@addSnapshotListener
                }
                val comments = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject<Comment>()?.apply { id = doc.id }
                } ?: emptyList()
                onChange(comments)
            }

    /**
     * Writes a comment and bumps the post's `commentCount` in one batch.
     *
     * `update` on the post is deliberate here: a comment on a post that no longer exists
     * should fail rather than resurrect a phantom document.
     */
    fun addComment(
        postId: String,
        postAuthorId: String,
        comment: Comment,
        onResult: (success: Boolean) -> Unit
    ) {
        val newCommentRef = commentsRef(postId).document()

        val batch = db.batch()
        batch.set(newCommentRef, comment)
        batch.update(
            postDoc(postId),
            Constants.FIRESTORE.POST_COMMENT_COUNT,
            FieldValue.increment(1)
        )

        batch.commit()
            .addOnSuccessListener {
                addNotification(
                    toUid = postAuthorId,
                    type = Constants.NOTIFICATION_TYPE.COMMENT,
                    fromUid = comment.authorId,
                    fromName = comment.authorName,
                    postId = postId
                )
                onResult(true)
            }
            .addOnFailureListener { e ->
                logFirestoreFailure("add comment", e)
                onResult(false)
            }
    }

    // ------------------------------------------------------------ Watchlist

    /**
     * A user's watchlist, newest first.
     *
     * [onlyPublic] is not cosmetic: the security rule allows a non-owner to read only
     * entries where `isPublic == true`, and Firestore rejects an entire `list` query unless
     * every document it could return is readable. Reading someone else's watchlist without
     * this filter fails with PERMISSION_DENIED rather than returning a subset.
     *
     * Sorted client-side so `whereEqualTo` + `orderBy` never needs a composite index.
     */
    fun loadWatchlist(
        uid: String,
        onlyPublic: Boolean,
        onResult: (items: List<WatchItem>?, error: Exception?) -> Unit
    ) {
        val query = if (onlyPublic) {
            watchlistRef(uid).whereEqualTo(Constants.FIRESTORE.WATCH_IS_PUBLIC, true)
        } else {
            watchlistRef(uid)
        }

        query.get()
            .addOnSuccessListener { snapshot ->
                val items = snapshot.documents
                    .mapNotNull { doc -> doc.toObject<WatchItem>()?.apply { id = doc.id } }
                    .sortedByDescending { it.addedAt }
                onResult(items, null)
            }
            .addOnFailureListener { e ->
                logFirestoreFailure("load watchlist", e)
                onResult(null, e)
            }
    }

    /** The ticker is the document id, so adding one twice simply overwrites it. */
    fun addWatchItem(uid: String, ticker: String, onResult: (success: Boolean) -> Unit) {
        val item = WatchItem(
            ticker = ticker,
            isPublic = false,
            addedAt = System.currentTimeMillis()
        )
        watchlistRef(uid).document(ticker).set(item)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { e ->
                logFirestoreFailure("add watch item", e)
                onResult(false)
            }
    }

    fun setWatchItemPublic(
        uid: String,
        ticker: String,
        isPublic: Boolean,
        onResult: (success: Boolean) -> Unit
    ) {
        watchlistRef(uid).document(ticker)
            .set(mapOf(Constants.FIRESTORE.WATCH_IS_PUBLIC to isPublic), SetOptions.merge())
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { e ->
                logFirestoreFailure("toggle watch item visibility", e)
                onResult(false)
            }
    }

    fun removeWatchItem(uid: String, ticker: String, onResult: (success: Boolean) -> Unit) {
        watchlistRef(uid).document(ticker).delete()
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { e ->
                logFirestoreFailure("remove watch item", e)
                onResult(false)
            }
    }

    // ---------------------------------------------------------------- Feed

    // ---------------------------------------------------------------- Search

    /**
     * Posts whose ticker starts with [prefix].
     *
     * Firestore has no full-text search, so this is a range scan: order by ticker and take
     * everything from the prefix up to the same prefix plus a very high code point, which is
     * the standard "starts with" idiom. Ordering on the single field it filters, so no
     * composite index; the newest-first sort is applied client-side afterwards.
     */
    fun searchPostsByTicker(
        prefix: String,
        onResult: (posts: List<Post>?, error: Exception?) -> Unit
    ) {
        val query = prefix.uppercase()
        postsRef
            .orderBy(Constants.FIRESTORE.POST_TICKER)
            .startAt(query)
            .endAt(query + PREFIX_SEARCH_TERMINATOR)
            .limit(Constants.FEED.PAGE_SIZE)
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.toPosts().sortedByDescending { it.createdAt }, null)
            }
            .addOnFailureListener { e ->
                logFirestoreFailure("search by ticker", e)
                onResult(null, e)
            }
    }

    /**
     * Posts carrying [tag]. Tags are stored lowercase without the leading '#', so the query
     * is normalized the same way. This is an exact match - `arrayContains` cannot do
     * prefixes.
     */
    fun searchPostsByTag(
        tag: String,
        onResult: (posts: List<Post>?, error: Exception?) -> Unit
    ) {
        val query = tag.trim().removePrefix("#").lowercase()
        postsRef
            .whereArrayContains(Constants.FIRESTORE.POST_TAGS, query)
            .limit(Constants.FEED.PAGE_SIZE)
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.toPosts().sortedByDescending { it.createdAt }, null)
            }
            .addOnFailureListener { e ->
                logFirestoreFailure("search by tag", e)
                onResult(null, e)
            }
    }

    /** Every post for one ticker, newest first. Sorted client-side, as above. */
    fun loadPostsByTicker(
        ticker: String,
        onResult: (posts: List<Post>?, error: Exception?) -> Unit
    ) {
        postsRef
            .whereEqualTo(Constants.FIRESTORE.POST_TICKER, ticker)
            .limit(Constants.FEED.PAGE_SIZE)
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.toPosts().sortedByDescending { it.createdAt }, null)
            }
            .addOnFailureListener { e ->
                logFirestoreFailure("load posts by ticker", e)
                onResult(null, e)
            }
    }

    /**
     * Discover: the newest posts across the whole app.
     * Ordering on a single field, so no composite index is needed.
     */
    fun loadDiscoverFeed(onResult: (posts: List<Post>?, error: Exception?) -> Unit) {
        postsRef
            .orderBy(Constants.FIRESTORE.POST_CREATED_AT, Query.Direction.DESCENDING)
            .limit(Constants.FEED.PAGE_SIZE)
            .get()
            .addOnSuccessListener { onResult(it.toPosts(), null) }
            .addOnFailureListener { e ->
                logFirestoreFailure("discover feed", e)
                onResult(null, e)
            }
    }

    /** Unpacks the Firestore status code, which the default `toString` tends to bury. */
    private fun logFirestoreFailure(label: String, e: Exception) {
        val firestoreException = e as? FirebaseFirestoreException
        Log.e(
            TAG,
            "$label FAILED" +
                " | code=${firestoreException?.code ?: "(not a FirebaseFirestoreException)"}" +
                " | type=${e.javaClass.name}" +
                " | message=${e.message}" +
                " | cause=${e.cause}",
            e
        )
    }

    /**
     * Following: posts by the accounts [uid] follows.
     *
     * Firestore caps `whereIn` at 30 values, so the following list is queried in chunks and
     * merged here. Sorting is deliberately done client-side: combining `whereIn` with
     * `orderBy` on a different field would require a composite index, and merged chunks
     * would need re-sorting anyway.
     */
    fun loadFollowingFeed(uid: String, onResult: (posts: List<Post>?, error: Exception?) -> Unit) {
        followingRef(uid).get()
            .addOnSuccessListener { snapshot ->
                val followedIds = snapshot.documents.map { it.id }
                if (followedIds.isEmpty()) {
                    onResult(emptyList(), null)
                    return@addOnSuccessListener
                }

                val chunkQueries = followedIds
                    .chunked(Constants.FEED.WHERE_IN_CHUNK)
                    .map { chunk ->
                        postsRef.whereIn(Constants.FIRESTORE.POST_AUTHOR_ID, chunk).get()
                    }

                Tasks.whenAllSuccess<QuerySnapshot>(chunkQueries)
                    .addOnSuccessListener { snapshots ->
                        val merged = snapshots
                            .flatMap { it.toPosts() }
                            .sortedByDescending { it.createdAt }
                            .take(Constants.FEED.PAGE_SIZE.toInt())
                        onResult(merged, null)
                    }
                    .addOnFailureListener { e ->
                        logFirestoreFailure("following feed: post chunks", e)
                        onResult(null, e)
                    }
            }
            .addOnFailureListener { e ->
                logFirestoreFailure("following feed: following list", e)
                onResult(null, e)
            }
    }

    /**
     * Which of [postIds] the given user has already liked.
     *
     * One read per post, because a like is stored as `posts/{id}/likes/{uid}`. Fine at the
     * page sizes this app loads; if the feed ever paginates deeply this is the first thing
     * to revisit.
     */
    fun fetchLikedPostIds(
        postIds: List<String>,
        uid: String,
        onResult: (likedIds: Set<String>) -> Unit
    ) {
        if (postIds.isEmpty() || uid.isEmpty()) {
            onResult(emptySet())
            return
        }

        val gets: List<Task<DocumentSnapshot>> =
            postIds.map { likesRef(it).document(uid).get() }

        Tasks.whenAllComplete(gets)
            .addOnCompleteListener { _ ->
                val liked = mutableSetOf<String>()
                gets.forEachIndexed { index, task ->
                    if (task.isSuccessful && task.result?.exists() == true) {
                        liked.add(postIds[index])
                    }
                }
                onResult(liked)
            }
    }

    /**
     * Adds or removes a like and moves the denormalized counter in the same batch, so the
     * two can never drift apart.
     *
     * A successful like also drops a notification on the author (never on yourself).
     */
    fun toggleLike(
        post: Post,
        uid: String,
        fromName: String,
        shouldLike: Boolean,
        onResult: (success: Boolean) -> Unit
    ) {
        if (uid.isEmpty() || post.id.isEmpty()) {
            onResult(false)
            return
        }

        val batch = db.batch()
        val likeDoc = likesRef(post.id).document(uid)
        val delta = if (shouldLike) 1L else -1L

        if (shouldLike) {
            batch.set(likeDoc, mapOf("liked" to true))
        } else {
            batch.delete(likeDoc)
        }
        batch.update(
            postDoc(post.id),
            Constants.FIRESTORE.POST_LIKE_COUNT,
            FieldValue.increment(delta)
        )

        batch.commit()
            .addOnSuccessListener {
                if (shouldLike && post.authorId != uid) {
                    addNotification(
                        toUid = post.authorId,
                        type = Constants.NOTIFICATION_TYPE.LIKE,
                        fromUid = uid,
                        fromName = fromName,
                        postId = post.id
                    )
                }
                onResult(true)
            }
            .addOnFailureListener { onResult(false) }
    }

    // -------------------------------------------------------- Notifications

    /**
     * Live notifications, newest first. Ordering on a single field, so no composite index.
     * Returns the [ListenerRegistration] so the caller can remove it.
     */
    fun listenToNotifications(
        uid: String,
        onChange: (notifications: List<AppNotification>) -> Unit,
        onError: (error: Exception) -> Unit
    ): ListenerRegistration =
        notificationsRef(uid)
            .orderBy(Constants.FIRESTORE.NOTIF_CREATED_AT, Query.Direction.DESCENDING)
            .limit(Constants.FEED.PAGE_SIZE)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    logFirestoreFailure("notifications listener", e)
                    onError(e)
                    return@addSnapshotListener
                }
                val notifications = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject<AppNotification>()?.apply { id = doc.id }
                } ?: emptyList()
                onChange(notifications)
            }

    /**
     * Live unread count, for the bottom-navigation badge.
     *
     * Filters on `read` only - adding an orderBy on a different field here would require a
     * composite index, and a count needs no ordering.
     */
    fun listenToUnreadCount(
        uid: String,
        onChange: (count: Int) -> Unit
    ): ListenerRegistration =
        notificationsRef(uid)
            .whereEqualTo(Constants.FIRESTORE.NOTIF_READ, false)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    logFirestoreFailure("unread count listener", e)
                    return@addSnapshotListener
                }
                onChange(snapshot?.size() ?: 0)
            }

    fun markNotificationRead(uid: String, notificationId: String) {
        notificationsRef(uid).document(notificationId)
            .update(Constants.FIRESTORE.NOTIF_READ, true)
            .addOnFailureListener { logFirestoreFailure("mark notification read", it) }
    }

    /** One batch, so the badge clears in a single snapshot rather than ticking down. */
    fun markAllNotificationsRead(
        uid: String,
        notificationIds: List<String>,
        onResult: (success: Boolean) -> Unit
    ) {
        if (notificationIds.isEmpty()) {
            onResult(true)
            return
        }

        val batch = db.batch()
        notificationIds.forEach { id ->
            batch.update(
                notificationsRef(uid).document(id),
                Constants.FIRESTORE.NOTIF_READ,
                true
            )
        }

        batch.commit()
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { e ->
                logFirestoreFailure("mark all notifications read", e)
                onResult(false)
            }
    }

    /** Fire-and-forget: a failed notification must never fail the action that caused it. */
    fun addNotification(
        toUid: String,
        type: String,
        fromUid: String,
        fromName: String,
        postId: String = ""
    ) {
        if (toUid.isEmpty() || toUid == fromUid) return

        notificationsRef(toUid).add(
            mapOf(
                Constants.FIRESTORE.NOTIF_TYPE to type,
                Constants.FIRESTORE.NOTIF_FROM_UID to fromUid,
                Constants.FIRESTORE.NOTIF_FROM_NAME to fromName,
                Constants.FIRESTORE.NOTIF_POST_ID to postId,
                Constants.FIRESTORE.NOTIF_CREATED_AT to System.currentTimeMillis(),
                Constants.FIRESTORE.NOTIF_READ to false
            )
        )
    }

    /** Firestore documents carry no id field, so stamp it on from the document itself. */
    private fun QuerySnapshot.toPosts(): List<Post> = documents.mapNotNull { doc ->
        try {
            doc.toObject<Post>()?.apply { id = doc.id }
        } catch (e: RuntimeException) {
            // One malformed document (wrong field type) must not blank the whole feed.
            Log.e(TAG, "skipping post ${doc.id}: could not map to Post", e)
            null
        }
    }

    companion object {
        private const val TAG = "PivotBoardDB"

        /**
         * Private-use code point U+F8FF (invisible in most editors). It sorts after any
         * character a ticker can contain, so appending it to the end of an ordered range
         * turns that range into a "starts with" query - the standard Firestore idiom for
         * prefix search, which the API has no direct operator for.
         */
        private const val PREFIX_SEARCH_TERMINATOR = ""

        @Volatile
        private var instance: DatabaseManager? = null

        fun init(context: Context): DatabaseManager =
            instance ?: synchronized(this) {
                instance ?: DatabaseManager(context).also { instance = it }
            }

        fun getInstance(): DatabaseManager = instance
            ?: throw IllegalStateException("DatabaseManager must be initialized by calling init(context) before use.")
    }
}
