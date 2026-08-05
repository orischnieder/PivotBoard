package com.ori.pivotboard_project.utilities

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.ori.pivotboard_project.R
import java.lang.ref.WeakReference

/** Thin Glide wrapper so no screen has to touch Glide directly. */
class ImageLoader private constructor(context: Context) {

    private val contextRef = WeakReference(context)

    /**
     * [source] may be a URL string, a drawable res id, a Uri - anything Glide accepts.
     * A blank/absent source still renders [placeHolder] rather than an empty view.
     */
    fun loadImage(
        source: Any?,
        imageView: ImageView,
        placeHolder: Int = R.drawable.unavailable_photo
    ) {
        val context = contextRef.get() ?: return
        val safeSource = if (source is String && source.isBlank()) placeHolder else source

        Glide.with(context)
            .load(safeSource)
            .centerCrop()
            .placeholder(placeHolder)
            .error(placeHolder)
            .into(imageView)
    }

    companion object {
        @Volatile
        private var instance: ImageLoader? = null

        fun init(context: Context): ImageLoader =
            instance ?: synchronized(this) {
                instance ?: ImageLoader(context).also { instance = it }
            }

        fun getInstance(): ImageLoader = instance
            ?: throw IllegalStateException("ImageLoader must be initialized by calling init(context) before use.")
    }
}
