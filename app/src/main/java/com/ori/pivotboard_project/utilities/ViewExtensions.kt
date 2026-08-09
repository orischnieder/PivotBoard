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
 * [applyIme] is only for a screen whose input is **anchored to the bottom of the root** -
 * PostDetailActivity's comment bar is the one case. There, `enableEdgeToEdge()` defeats
 * `android:windowSoftInputMode="adjustResize"` and the bar ends up under the keyboard, so
 * the ime inset has to be consumed here instead.
 *
 * Do NOT enable it for a screen whose inputs live inside a scrolling container. That
 * container already keeps its field visible, and padding the root as well shrinks the
 * content viewport twice - on a form-heavy screen it collapses to nothing.
 *
 * The display cutout is included so a notch cannot clip content in landscape.
 */
fun View.applySystemBarPadding(
    applyTop: Boolean = true,
    applyBottom: Boolean = true,
    applyIme: Boolean = false
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val bars = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val imeBottom =
            if (applyIme) windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0

        // The keyboard already covers the navigation bar, so it replaces that inset rather
        // than stacking with it. With the keyboard hidden this is exactly the old behaviour.
        val bottom = when {
            imeBottom > 0 -> imeBottom
            applyBottom -> bars.bottom
            else -> 0
        }

        view.updatePadding(
            left = bars.left,
            top = if (applyTop) bars.top else 0,
            right = bars.right,
            bottom = bottom
        )
        windowInsets
    }
}
