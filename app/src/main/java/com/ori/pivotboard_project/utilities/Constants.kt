package com.ori.pivotboard_project.utilities

/**
 * Single source of truth for every string key used against Firestore, Storage and
 * SharedPreferences. Nothing outside this object should spell a collection or field name.
 */
object Constants {

    object FIRESTORE {
        // Collections
        const val USERS = "users"
        const val POSTS = "posts"
        const val COMMENTS = "comments"
        const val LIKES = "likes"
        const val FOLLOWING = "following"
        const val FOLLOWERS = "followers"
        const val WATCHLIST = "watchlist"
        const val NOTIFICATIONS = "notifications"

        // users/{uid}
        const val USER_DISPLAY_NAME = "displayName"
        const val USER_USERNAME = "username"
        const val USER_PHOTO_URL = "photoUrl"
        const val USER_BIO = "bio"
        const val USER_FOLLOWERS_COUNT = "followersCount"
        const val USER_FOLLOWING_COUNT = "followingCount"
        const val USER_POSTS_COUNT = "postsCount"
        const val USER_CREATED_AT = "createdAt"

        // posts/{postId}
        const val POST_AUTHOR_ID = "authorId"
        const val POST_AUTHOR_NAME = "authorName"
        const val POST_AUTHOR_PHOTO_URL = "authorPhotoUrl"
        const val POST_TICKER = "ticker"
        const val POST_SETUP_TYPE = "setupType"
        const val POST_IMAGE_URL = "imageUrl"
        const val POST_NOTES = "notes"
        const val POST_TAGS = "tags"
        const val POST_CREATED_AT = "createdAt"
        const val POST_LIKE_COUNT = "likeCount"
        const val POST_COMMENT_COUNT = "commentCount"

        // posts/{postId}/comments/{commentId}
        const val COMMENT_AUTHOR_ID = "authorId"
        const val COMMENT_AUTHOR_NAME = "authorName"
        const val COMMENT_TEXT = "text"
        const val COMMENT_CREATED_AT = "createdAt"

        // users/{uid}/watchlist/{ticker}
        const val WATCH_TICKER = "ticker"
        const val WATCH_IS_PUBLIC = "isPublic"
        const val WATCH_ADDED_AT = "addedAt"

        // users/{uid}/notifications/{notifId}
        const val NOTIF_TYPE = "type"
        const val NOTIF_FROM_UID = "fromUid"
        const val NOTIF_FROM_NAME = "fromName"
        const val NOTIF_POST_ID = "postId"
        const val NOTIF_CREATED_AT = "createdAt"
        const val NOTIF_READ = "read"
    }

    object NOTIFICATION_TYPE {
        const val LIKE = "like"
        const val COMMENT = "comment"
        const val FOLLOW = "follow"
    }

    object STORAGE {
        const val IMAGES_DIR = "images"
    }

    object SP {
        const val FILE_NAME = "PIVOT_BOARD_PREFS"
    }

    object SP_KEYS {
        const val ONBOARDING_SEEN = "ONBOARDING_SEEN"
        const val LAST_FEED_TAB = "LAST_FEED_TAB"
    }

    object BUNDLE_KEYS {
        const val POST_ID = "POST_ID"
        const val USER_ID = "USER_ID"
        const val TICKER = "TICKER"
        const val LIST_MODE = "LIST_MODE"
    }

    object FEED {
        const val PAGE_SIZE = 50L

        /** Firestore caps `whereIn` at 30 values, so following lists are queried in chunks. */
        const val WHERE_IN_CHUNK = 10
    }

    object UI {
        const val SPLASH_FALLBACK_DELAY_MS = 1500L
        const val TICKER_MAX_LENGTH = 6
        const val NOTES_MAX_LENGTH = 1000
    }
}
