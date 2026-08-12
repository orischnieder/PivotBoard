package com.ori.pivotboard_project.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.adapters.UserAdapter
import com.ori.pivotboard_project.databinding.ActivityUserListBinding
import com.ori.pivotboard_project.interfaces.UserCallback
import com.ori.pivotboard_project.model.User
import com.ori.pivotboard_project.utilities.applySystemBarPadding
import com.ori.pivotboard_project.utilities.Constants
import com.ori.pivotboard_project.utilities.DatabaseManager
import com.ori.pivotboard_project.utilities.hide
import com.ori.pivotboard_project.utilities.showEmpty
import com.ori.pivotboard_project.utilities.showError
import com.ori.pivotboard_project.utilities.showLoading

/**
 * A browsable follower / following list, reached by tapping either count on a profile.
 *
 * The spec only called for counters; a number you cannot open is a dead end, so the same
 * screen serves both directions via [Mode].
 */
class UserListActivity : AppCompatActivity(), UserCallback {

    enum class Mode { FOLLOWERS, FOLLOWING }

    private lateinit var binding: ActivityUserListBinding
    private val userAdapter = UserAdapter()

    private val mode: Mode
        get() = Mode.valueOf(
            intent.getStringExtra(Constants.BUNDLE_KEYS.LIST_MODE) ?: Mode.FOLLOWERS.name
        )

    private val targetUid: String
        get() = intent.getStringExtra(Constants.BUNDLE_KEYS.USER_ID).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityUserListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.applySystemBarPadding()

        binding.userlistTBToolbar.setTitle(
            if (mode == Mode.FOLLOWERS) R.string.profile_label_followers
            else R.string.profile_label_following
        )
        binding.userlistTBToolbar.setNavigationOnClickListener { finish() }

        userAdapter.userCallback = this
        binding.userlistRVUsers.layoutManager = LinearLayoutManager(this)
        binding.userlistRVUsers.adapter = userAdapter

        loadUsers()
    }

    private fun loadUsers() {
        if (targetUid.isEmpty()) {
            showError()
            return
        }
        binding.userlistRVUsers.visibility = View.GONE
        binding.userlistLAYState.showLoading()

        DatabaseManager.getInstance().loadFollowList(
            uid = targetUid,
            followers = mode == Mode.FOLLOWERS
        ) { users, _ ->
            if (isFinishing || isDestroyed) return@loadFollowList

            when {
                users == null -> showError()
                users.isEmpty() -> showEmpty()
                else -> {
                    userAdapter.setData(users)
                    binding.userlistRVUsers.visibility = View.VISIBLE
                    binding.userlistLAYState.hide()
                }
            }
        }
    }

    private fun showEmpty() {
        binding.userlistRVUsers.visibility = View.GONE
        binding.userlistLAYState.showEmpty(
            icon = R.drawable.ic_state_people,
            title = if (mode == Mode.FOLLOWERS) R.string.userlist_empty_followers
            else R.string.userlist_empty_following
        )
    }

    private fun showError() {
        binding.userlistRVUsers.visibility = View.GONE
        binding.userlistLAYState.showError(
            body = R.string.userlist_error,
            onRetry = { loadUsers() }
        )
    }

    override fun onUserClicked(user: User, position: Int) =
        ProfileActivity.start(this, user.id)

    companion object {
        fun start(context: Context, uid: String, mode: Mode) {
            val intent = Intent(context, UserListActivity::class.java)
                .putExtra(Constants.BUNDLE_KEYS.USER_ID, uid)
                .putExtra(Constants.BUNDLE_KEYS.LIST_MODE, mode.name)
            context.startActivity(intent)
        }
    }
}
