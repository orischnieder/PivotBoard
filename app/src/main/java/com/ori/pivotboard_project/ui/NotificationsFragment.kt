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
        val uid = AuthManager.getInstance().currentUid()
        if (uid.isEmpty()) {
            showMessage(R.string.notif_error)
            return
        }
        binding?.notificationsPRGLoading?.visibility = View.VISIBLE

        registration = DatabaseManager.getInstance().listenToNotifications(
            uid = uid,
            onChange = { notifications ->
                val binding = this.binding ?: return@listenToNotifications
                binding.notificationsPRGLoading.visibility = View.GONE

                if (notifications.isEmpty()) {
                    showMessage(R.string.notif_empty)
                } else {
                    notificationAdapter.setData(notifications)
                    binding.notificationsRVItems.visibility = View.VISIBLE
                    binding.notificationsLBLMessage.visibility = View.GONE
                }
                bindMarkAllButton(notifications)
            },
            onError = {
                if (binding == null) return@listenToNotifications
                // Signing out tears the session down before this listener detaches, so a
                // denial here is expected rather than a real error worth showing.
                if (!AuthManager.getInstance().isLoggedIn()) return@listenToNotifications

                binding?.notificationsPRGLoading?.visibility = View.GONE
                showMessage(R.string.notif_error)
            }
        )
    }

    private fun bindMarkAllButton(notifications: List<AppNotification>) {
        val binding = this.binding ?: return
        val hasUnread = notifications.any { !it.read }
        binding.notificationsBTNMarkAll.visibility = if (hasUnread) View.VISIBLE else View.GONE
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

    private fun showMessage(messageId: Int) {
        val binding = this.binding ?: return
        binding.notificationsRVItems.visibility = View.GONE
        binding.notificationsLBLMessage.visibility = View.VISIBLE
        binding.notificationsLBLMessage.setText(messageId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        notificationAdapter.notificationCallback = null
        binding?.notificationsRVItems?.adapter = null
        binding = null
    }
}
