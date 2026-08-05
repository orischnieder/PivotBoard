package com.ori.pivotboard_project.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.databinding.NotificationItemBinding
import com.ori.pivotboard_project.interfaces.NotificationCallback
import com.ori.pivotboard_project.model.AppNotification
import com.ori.pivotboard_project.utilities.Constants
import com.ori.pivotboard_project.utilities.TimeFormatter

/** Like / comment / follow notifications, newest first. */
class NotificationAdapter(
    var items: List<AppNotification> = listOf()
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    var notificationCallback: NotificationCallback? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = NotificationItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun setData(notifications: List<AppNotification>) {
        items = notifications
        notifyDataSetChanged()
    }

    inner class NotificationViewHolder(private val binding: NotificationItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.notifLAYRoot.setOnClickListener {
                val position = absoluteAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                notificationCallback?.onNotificationClicked(items[position], position)
            }
        }

        fun bind(notification: AppNotification) {
            val context = binding.root.context
            val name = notification.fromName.ifBlank {
                context.getString(R.string.notif_someone)
            }

            val textId = when (notification.type) {
                Constants.NOTIFICATION_TYPE.LIKE -> R.string.notif_like
                Constants.NOTIFICATION_TYPE.COMMENT -> R.string.notif_comment
                Constants.NOTIFICATION_TYPE.FOLLOW -> R.string.notif_follow
                else -> R.string.notif_unknown
            }
            binding.notifLBLText.text = context.getString(textId, name)

            binding.notifIMGType.setImageResource(
                when (notification.type) {
                    Constants.NOTIFICATION_TYPE.LIKE -> R.drawable.ic_like_filled
                    Constants.NOTIFICATION_TYPE.COMMENT -> R.drawable.ic_comment
                    Constants.NOTIFICATION_TYPE.FOLLOW -> R.drawable.ic_nav_profile
                    else -> R.drawable.ic_nav_notifications
                }
            )

            binding.notifLBLTime.text = TimeFormatter.relative(notification.createdAt)
            binding.notifLAYUnread.visibility =
                if (notification.read) View.INVISIBLE else View.VISIBLE
        }
    }
}
