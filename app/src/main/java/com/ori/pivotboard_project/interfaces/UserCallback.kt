package com.ori.pivotboard_project.interfaces

import com.ori.pivotboard_project.model.User

/** How a user row reports user intent back to the screen hosting the list. */
interface UserCallback {
    fun onUserClicked(user: User, position: Int)
}
