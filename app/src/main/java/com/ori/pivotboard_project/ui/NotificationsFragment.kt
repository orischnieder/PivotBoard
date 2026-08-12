package com.ori.pivotboard_project.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.ListenerRegistration
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.activities.PostDetailActivity
import com.ori.pivotboard_project.activities.ProfileActivity
import com.ori.pivotboard_project.adapters.NotificationAdapter
import com.ori.pivotboard_project.databinding.FragmentNotificationsBinding
import com.ori.pivotboard_project.interfaces.NotificationCallback
import com.ori.pivotboard_project.model.AppNotification
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.Constants
import com.ori.pivotboard_project.utilities.DatabaseManager
import com.ori.pivotboard_project.utilities.SignalManager
import com.ori.pivotboard_project.utilities.hide
import com.ori.pivotboard_project.utilities.showEmpty
import com.ori.pivotboard_project.utilities.showError
import com.ori.pivotboard_project.utilities.showLoading

/**
 * Likes, comments and new followers, newest first.
 *
 * Live via a snapshot listener, removed in `onStop` per section 8. Tapping a row marks it
 * read and opens whatever it refers to.
 */
class NotificationsFragment : Fragment(), NotificationCallback {

    private var binding: FragmentNotificationsBinding? = null
    private val notificationAdapter = NotificationAdapter()

    private var registration: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = this.binding ?: return

        notificationAdapter.notificationCallback = this
        binding.notificationsRVItems.layoutManager = LinearLayoutManager(requireContext())
        binding.notificationsRVItems.adapter = notificationAdapter

        binding.notificationsBTNMarkAll.setOnClickListener { markAllRead() }
    }

    override fun onStart() {
        super.onStart()
        listenToNotifications()
    }

    override fun onStop() {
        super.onStop()
        registration?.remove()
        registration = null
    }

    private fun listenToNotifications() {
        // Retry re-enters here, so drop any existing listener rather than orphaning it.
        registration?.remove()
        registration = null

        val uid = AuthManager.getInstance().currentUid()
        if (uid.isEmpty()) {
            showError()
            return
        }
        binding?.notificationsRVItems?.visibility = View.GONE
        binding?.notificationsLAYState?.showLoading()

        registration = DatabaseManager.getInstance().listenToNotifications(
            uid = uid,
            onChange = { notifications ->
                val binding = this.binding ?: return@listenToNotifications

                if (notifications.isEmpty()) {
                    showEmpty()
                } else {
                    notificationAdapter.setData(notifications)
                    binding.notificationsRVItems.visibility = View.VISIBLE
                    binding.notificationsLAYState.hide()
                }
                bindMarkAllButton(notifications)
            },
            onError = {
                if (binding == null) return@listenToNotifications
                // Signing out tears the session down before this listener detaches, so a
                // denial here is expected rather than a real error worth showing.
                if (!AuthManager.getInstance().isLoggedIn()) return@listenToNotifications

                showError()
            }
        )
    }

    private fun bindMarkAllButton(notifications: List<AppNotification>) {
        val binding = this.binding ?: return
        val hasUnread = notifications.any { !it.read }
        // INVISIBLE, not GONE: the header keeps its height so the list below never jumps.
        binding.notificationsBTNMarkAll.visibility =
            if (hasUnread) View.VISIBLE else View.INVISIBLE
    }

    private fun markAllRead() {
        val uid = AuthManager.getInstance().currentUid()
        val unreadIds = notificationAdapter.items.filter { !it.read }.map { it.id }
        if (unreadIds.isEmpty()) return

        DatabaseManager.getInstance().markAllNotificationsRead(uid, unreadIds) { success ->
            if (binding == null) return@markAllNotificationsRead
            // On success the snapshot listener redraws the rows and hides the button.
            if (success) {
                SignalManager.getInstance().toast(R.string.notif_all_read)
            } else {
                SignalManager.getInstance().toast(R.string.notif_error_mark_read)
            }
        }
    }

    override fun onNotificationClicked(notification: AppNotification, position: Int) {
        val uid = AuthManager.getInstance().currentUid()
        if (!notification.read) {
            DatabaseManager.getInstance().markNotificationRead(uid, notification.id)
        }

        when (notification.type) {
            Constants.NOTIFICATION_TYPE.FOLLOW ->
                ProfileActivity.start(requireContext(), notification.fromUid)

            // Like and comment both point at a post; fall back to the sender's profile if
            // the postId is somehow missing.
            else -> if (notification.postId.isNotEmpty()) {
                PostDetailActivity.start(requireContext(), notification.postId)
            } else {
                ProfileActivity.start(requireContext(), notification.fromUid)
            }
        }
    }

    private fun showEmpty() {
        val binding = this.binding ?: return
        binding.notificationsRVItems.visibility = View.GONE
        binding.notificationsLAYState.showEmpty(
            icon = R.drawable.ic_nav_notifications,
            title = R.string.notif_empty_title,
            body = R.string.notif_empty
        )
    }

    private fun showError() {
        val binding = this.binding ?: return
        binding.notificationsRVItems.visibility = View.GONE
        binding.notificationsLAYState.showError(
            body = R.string.notif_error,
            onRetry = { listenToNotifications() }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        notificationAdapter.notificationCallback = null
        binding?.notificationsRVItems?.adapter = null
        binding = null
    }
}
