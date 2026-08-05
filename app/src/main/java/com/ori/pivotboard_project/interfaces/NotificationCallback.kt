package com.ori.pivotboard_project.interfaces

import com.ori.pivotboard_project.model.AppNotification

/** How a notification row reports user intent back to the screen hosting the list. */
interface NotificationCallback {
    fun onNotificationClicked(notification: AppNotification, position: Int)
}
