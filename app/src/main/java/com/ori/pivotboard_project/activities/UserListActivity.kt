package com.ori.pivotboard_project.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.adapters.UserAdapter
import com.ori.pivotboard_project.databinding.ActivityUserListBinding
import com.ori.pivotboard_project.interfaces.UserCallback
import com.ori.pivotboard_project.model.User
import com.ori.pivotboard_project.utilities.Constants
import com.ori.pivotboard_project.utilities.DatabaseManager

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

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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
            showMessage(R.string.userlist_error)
            return
        }
        binding.userlistPRGLoading.visibility = View.VISIBLE

        DatabaseManager.getInstance().loadFollowList(
            uid = targetUid,
            followers = mode == Mode.FOLLOWERS
        ) { users, _ ->
            if (isFinishing || isDestroyed) return@loadFollowList
            binding.userlistPRGLoading.visibility = View.GONE

            when {
                users == null -> showMessage(R.string.userlist_error)
                users.isEmpty() -> showMessage(
                    if (mode == Mode.FOLLOWERS) R.string.userlist_empty_followers
                    else R.string.userlist_empty_following
                )

                else -> {
                    userAdapter.setData(users)
                    binding.userlistRVUsers.visibility = View.VISIBLE
                    binding.userlistLBLMessage.visibility = View.GONE
                }
            }
        }
    }

    private fun showMessage(messageId: Int) {
        binding.userlistRVUsers.visibility = View.GONE
        binding.userlistLBLMessage.visibility = View.VISIBLE
        binding.userlistLBLMessage.setText(messageId)
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
