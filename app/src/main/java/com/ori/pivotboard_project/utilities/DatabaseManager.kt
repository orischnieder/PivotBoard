package com.ori.pivotboard_project.utilities

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject
import com.ori.pivotboard_project.model.Post
import com.ori.pivotboard_project.model.User

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

    // ---------------------------------------------------------------- Posts

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

    // ---------------------------------------------------------------- Feed

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
