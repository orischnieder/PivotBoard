package com.ori.pivotboard_project.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.databinding.ActivityWatchlistBinding
import com.ori.pivotboard_project.ui.WatchlistFragment
import com.ori.pivotboard_project.utilities.applySystemBarPadding
import com.ori.pivotboard_project.utilities.Constants

/**
 * Hosts [WatchlistFragment] for another trader, reached from their profile. Only their
 * public tickers are shown - see the `watchlist` rule in firestore.rules.
 */
class WatchlistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWatchlistBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityWatchlistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.applySystemBarPadding()

        binding.watchactTBToolbar.setNavigationOnClickListener { finish() }

        val ownerName = intent.getStringExtra(EXTRA_OWNER_NAME).orEmpty()
        if (ownerName.isNotBlank()) {
            binding.watchactTBToolbar.title =
                getString(R.string.watchlist_title_other, ownerName)
        }

        if (savedInstanceState == null) {
            val uid = intent.getStringExtra(Constants.BUNDLE_KEYS.USER_ID).orEmpty()
            supportFragmentManager.beginTransaction()
                .replace(R.id.watchact_FRAME_container, WatchlistFragment.newInstance(uid))
                .commit()
        }
    }

    companion object {
        private const val EXTRA_OWNER_NAME = "EXTRA_OWNER_NAME"

        fun start(context: Context, uid: String, ownerName: String) {
            val intent = Intent(context, WatchlistActivity::class.java)
                .putExtra(Constants.BUNDLE_KEYS.USER_ID, uid)
                .putExtra(EXTRA_OWNER_NAME, ownerName)
            context.startActivity(intent)
        }
    }
}
