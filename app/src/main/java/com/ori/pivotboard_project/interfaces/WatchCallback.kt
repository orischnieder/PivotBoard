package com.ori.pivotboard_project.interfaces

import com.ori.pivotboard_project.model.WatchItem

/** How a watchlist row reports user intent back to the screen hosting the list. */
interface WatchCallback {
    fun onWatchItemClicked(item: WatchItem, position: Int)
    fun onWatchItemVisibilityChanged(item: WatchItem, position: Int, isPublic: Boolean)
    fun onWatchItemRemoveClicked(item: WatchItem, position: Int)
}
