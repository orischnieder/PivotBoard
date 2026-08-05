package com.ori.pivotboard_project.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayoutMediator
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.adapters.FeedPagerAdapter
import com.ori.pivotboard_project.databinding.FragmentFeedBinding
import com.ori.pivotboard_project.utilities.Constants
import com.ori.pivotboard_project.utilities.SharedPreferencesManager

/** Feed host: Following / Discover tabs over a ViewPager2. */
class FeedFragment : Fragment() {

    private var binding: FragmentFeedBinding? = null
    private var mediator: TabLayoutMediator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = this.binding ?: return

        binding.feedVPPager.adapter = FeedPagerAdapter(this)

        mediator = TabLayoutMediator(binding.feedTABTabs, binding.feedVPPager) { tab, position ->
            tab.setText(
                when (FeedPagerAdapter.MODES[position]) {
                    PostListFragment.Mode.FOLLOWING -> R.string.feed_tab_following
                    PostListFragment.Mode.DISCOVER -> R.string.feed_tab_discover
                }
            )
        }.also { it.attach() }

        restoreLastTab()
    }

    /** Reopening the Feed lands on whichever tab was last used. */
    private fun restoreLastTab() {
        val binding = this.binding ?: return
        val lastTab = SharedPreferencesManager.getInstance()
            .getInt(Constants.SP_KEYS.LAST_FEED_TAB, 0)
            .coerceIn(0, FeedPagerAdapter.MODES.lastIndex)
        binding.feedVPPager.setCurrentItem(lastTab, false)
    }

    override fun onPause() {
        super.onPause()
        binding?.let {
            SharedPreferencesManager.getInstance()
                .putInt(Constants.SP_KEYS.LAST_FEED_TAB, it.feedVPPager.currentItem)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediator?.detach()
        mediator = null
        binding?.feedVPPager?.adapter = null
        binding = null
    }
}
