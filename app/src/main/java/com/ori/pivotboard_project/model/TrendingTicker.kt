package com.ori.pivotboard_project.model

/**
 * One row of the Trending tab.
 *
 * Unlike the other models this is never stored or read back - it is computed from the posts
 * of the last week, so it needs no defaulted fields or no-arg construction path.
 */
data class TrendingTicker(
    val ticker: String,
    val postCount: Int,
    val authorCount: Int
)
