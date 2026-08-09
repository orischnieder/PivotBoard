package com.ori.pivotboard_project.interfaces

import com.ori.pivotboard_project.model.TrendingTicker

/** How a trending row reports user intent back to the screen hosting the list. */
interface TrendingCallback {
    fun onTrendingTickerClicked(item: TrendingTicker, position: Int)
}
