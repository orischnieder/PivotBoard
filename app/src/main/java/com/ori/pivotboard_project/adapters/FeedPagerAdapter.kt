package com.ori.pivotboard_project.adapters

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.ori.pivotboard_project.ui.PostListFragment

/** Two feed pages: Following first, then Discover. */
class FeedPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = MODES.size

    override fun createFragment(position: Int): Fragment =
        PostListFragment.newInstance(MODES[position])

    companion object {
        val MODES = listOf(PostListFragment.Mode.FOLLOWING, PostListFragment.Mode.DISCOVER)
    }
}
