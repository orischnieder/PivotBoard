package com.ori.pivotboard_project.utilities

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.color.MaterialColors
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.databinding.ViewStateBinding

/**
 * Renders the shared loading / empty / error surface (`view_state.xml`).
 *
 * Every list screen used to hand-roll its own placeholder, which is why an empty list and a
 * failed request ended up looking identical. Routing all of them through here means the
 * distinction is made once: an empty state is a neutral glyph and no action, an error is an
 * error-tinted glyph with a Retry button.
 *
 * Include the layout with its own id and the generated binding property is this type:
 * `binding.feedLAYState.showEmpty(...)`.
 */

/** Spinner only, optionally with a line of text under it so the screen is not just a dot. */
fun ViewStateBinding.showLoading(@StringRes label: Int? = null) {
    reset()
    root.visibility = View.VISIBLE
    statePRGLoading.visibility = View.VISIBLE
    label?.let {
        stateLBLBody.setText(it)
        stateLBLBody.visibility = View.VISIBLE
    }
}

/** Nothing to show, but nothing went wrong. Neutral glyph, no action. */
fun ViewStateBinding.showEmpty(
    @DrawableRes icon: Int,
    title: CharSequence,
    body: CharSequence? = null
) {
    reset()
    root.visibility = View.VISIBLE

    stateIMGIcon.setImageResource(icon)
    stateIMGIcon.imageTintList = null
    stateIMGIcon.setColorFilter(
        MaterialColors.getColor(stateIMGIcon, com.google.android.material.R.attr.colorOnSurfaceVariant)
    )
    stateIMGIcon.alpha = EMPTY_ICON_ALPHA
    stateIMGIcon.visibility = View.VISIBLE

    stateLBLTitle.text = title
    stateLBLTitle.visibility = View.VISIBLE

    body?.let {
        stateLBLBody.text = it
        stateLBLBody.visibility = View.VISIBLE
    }
}

fun ViewStateBinding.showEmpty(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes body: Int? = null
) = showEmpty(
    icon = icon,
    title = root.context.getText(title),
    body = body?.let { root.context.getText(it) }
)

/**
 * Something failed. Error-tinted glyph, and a Retry when the caller can actually retry -
 * a screen with no recovery path passes null rather than showing a button that does nothing.
 */
fun ViewStateBinding.showError(
    body: CharSequence,
    @StringRes title: Int = R.string.state_error_title,
    onRetry: (() -> Unit)? = null
) {
    reset()
    root.visibility = View.VISIBLE

    stateIMGIcon.setImageResource(R.drawable.ic_state_error)
    stateIMGIcon.imageTintList = null
    stateIMGIcon.setColorFilter(
        MaterialColors.getColor(stateIMGIcon, androidx.appcompat.R.attr.colorError)
    )
    stateIMGIcon.alpha = ERROR_ICON_ALPHA
    stateIMGIcon.visibility = View.VISIBLE

    stateLBLTitle.setText(title)
    stateLBLTitle.visibility = View.VISIBLE

    stateLBLBody.text = body
    stateLBLBody.visibility = View.VISIBLE

    onRetry?.let { retry ->
        stateBTNAction.setOnClickListener { retry() }
        stateBTNAction.visibility = View.VISIBLE
    }
}

fun ViewStateBinding.showError(
    @StringRes body: Int,
    @StringRes title: Int = R.string.state_error_title,
    onRetry: (() -> Unit)? = null
) = showError(body = root.context.getText(body), title = title, onRetry = onRetry)

/** Hides the whole surface, for when real content takes over. */
fun ViewStateBinding.hide() {
    root.visibility = View.GONE
}

/** Clears anything a previous state left behind, so states cannot bleed into each other. */
private fun ViewStateBinding.reset() {
    statePRGLoading.visibility = View.GONE
    stateIMGIcon.visibility = View.GONE
    stateLBLTitle.visibility = View.GONE
    stateLBLBody.visibility = View.GONE
    stateBTNAction.visibility = View.GONE
    stateBTNAction.setOnClickListener(null)
}

private const val EMPTY_ICON_ALPHA = 0.4f
private const val ERROR_ICON_ALPHA = 0.9f
