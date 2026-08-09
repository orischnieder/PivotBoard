package com.ori.pivotboard_project.adapters

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.ui.PostListFragment
import com.ori.pivotboard_project.ui.TrendingFragment

/**
 * The three feed pages. Trending shows tickers rather than posts, so the adapter maps each
 * page to its own fragment type instead of assuming one.
 */
class FeedPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    enum class Page(@StringRes val titleRes: Int) {
        FOLLOWING(R.string.feed_tab_following),
        DISCOVER(R.string.feed_tab_discover),
        TRENDING(R.string.feed_tab_trending)
    }

    override fun getItemCount(): Int = PAGES.size

    override fun createFragment(position: Int): Fragment = when (PAGES[position]) {
        Page.FOLLOWING -> PostListFragment.newInstance(PostListFragment.Mode.FOLLOWING)
        Page.DISCOVER -> PostListFragment.newInstance(PostListFragment.Mode.DISCOVER)
        Page.TRENDING -> TrendingFragment()
    }

    companion object {
        val PAGES: List<Page> = Page.entries
    }
}
