package com.ori.pivotboard_project.utilities

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Pads a root view clear of the system bars under `enableEdgeToEdge()`.
 *
 * Replaces the identical listener that used to be pasted into every Activity.
 *
 * [applyBottom] exists because the bottom inset must have exactly one owner. A screen whose
 * bottom-most view handles its own inset - `MainActivity`, where the BottomNavigationView
 * does - has to pass `false` here, or the inset gets applied twice and leaves an empty strip
 * above the navigation bar.
 *
 * The display cutout is included so a notch cannot clip content in landscape.
 */
fun View.applySystemBarPadding(
    applyTop: Boolean = true,
    applyBottom: Boolean = true
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.updatePadding(
            left = insets.left,
            top = if (applyTop) insets.top else 0,
            right = insets.right,
            bottom = if (applyBottom) insets.bottom else 0
        )
        windowInsets
    }
}
