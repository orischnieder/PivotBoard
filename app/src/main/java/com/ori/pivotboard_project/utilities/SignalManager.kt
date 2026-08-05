package com.ori.pivotboard_project.utilities

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import java.lang.ref.WeakReference

/** User feedback signals - toasts and haptics - behind one thread-safe singleton. */
class SignalManager private constructor(context: Context) {

    private val contextRef = WeakReference(context)

    fun toast(text: String) {
        contextRef.get()?.let { Toast.makeText(it, text, Toast.LENGTH_SHORT).show() }
    }

    fun toast(stringResId: Int) {
        contextRef.get()?.let { toast(it.getString(stringResId)) }
    }

    fun vibrate(milliseconds: Long = DEFAULT_VIBRATION_MS) {
        val context = contextRef.get() ?: return
        val effect = VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.vibrate(CombinedVibration.createParallel(effect))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(effect)
        }
    }

    companion object {
        private const val DEFAULT_VIBRATION_MS = 100L

        @Volatile
        private var instance: SignalManager? = null

        fun init(context: Context): SignalManager =
            instance ?: synchronized(this) {
                instance ?: SignalManager(context).also { instance = it }
            }

        fun getInstance(): SignalManager = instance
            ?: throw IllegalStateException("SignalManager must be initialized by calling init(context) before use.")
    }
}
