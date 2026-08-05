package com.ori.pivotboard_project.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.databinding.ActivityMainBinding
import com.ori.pivotboard_project.ui.CreatePostFragment
import com.ori.pivotboard_project.ui.FeedFragment
import com.ori.pivotboard_project.ui.NotificationsFragment
import com.ori.pivotboard_project.ui.ProfileFragment
import com.ori.pivotboard_project.model.User
import com.ori.pivotboard_project.ui.WatchlistFragment
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.DatabaseManager
import com.ori.pivotboard_project.utilities.SignalManager

/**
 * The app shell: a BottomNavigationView over a FrameLayout that fragments are swapped into.
 * Navigation is manual (no Jetpack Navigation Component), per the course convention.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.mainTBToolbar)
        initBottomNavigation()
        healMissingProfile()

        // Only on a cold start - on rotation the FragmentManager restores the current tab.
        //
        // Committed directly rather than via `selectedItemId`: BottomNavigationView already
        // checks its first menu item on inflation, so assigning that same id dispatches to
        // the *reselected* listener and the fragment would never be added.
        if (savedInstanceState == null) {
            showFragment(FeedFragment())
        }
    }

    /**
     * Recreates `users/{uid}` if it went missing - see [DatabaseManager.ensureUserDocument].
     * Silent when nothing is wrong, which is the normal case.
     */
    private fun healMissingProfile() {
        val firebaseUser = AuthManager.getInstance().currentUser() ?: return

        val user = User(
            id = firebaseUser.uid,
            displayName = firebaseUser.displayName.orEmpty(),
            username = firebaseUser.email?.substringBefore('@').orEmpty()
                .ifBlank { "trader_${firebaseUser.uid.take(6)}" },
            photoUrl = firebaseUser.photoUrl?.toString().orEmpty()
        )

        DatabaseManager.getInstance().ensureUserDocument(user) { created ->
            if (created) SignalManager.getInstance().toast(R.string.profile_restored)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.main_MNU_logout -> {
            confirmLogout()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    /** Signing out is destructive enough to be worth a confirmation step. */
    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.logout_confirm_yes) { _, _ -> logout() }
            .show()
    }

    private fun logout() {
        AuthManager.getInstance().logout {
            SignalManager.getInstance().toast(R.string.logout_done)
            // Clear the back stack so the hardware back button cannot re-enter the app shell.
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun initBottomNavigation() {
        binding.mainNAVBottom.setOnItemSelectedListener { item ->
            val fragment = fragmentFor(item.itemId) ?: return@setOnItemSelectedListener false
            showFragment(fragment)
            true
        }
        // Re-selecting the current tab should not rebuild it.
        binding.mainNAVBottom.setOnItemReselectedListener { }
    }

    private fun fragmentFor(itemId: Int): Fragment? = when (itemId) {
        R.id.nav_feed -> FeedFragment()
        R.id.nav_create -> CreatePostFragment()
        R.id.nav_watchlist -> WatchlistFragment()
        R.id.nav_notifications -> NotificationsFragment()
        R.id.nav_profile -> ProfileFragment()
        else -> null
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_FRAME_container, fragment)
            .commit()
    }
}
