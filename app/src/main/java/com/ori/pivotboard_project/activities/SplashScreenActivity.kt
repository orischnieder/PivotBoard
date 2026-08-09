package com.ori.pivotboard_project.activities

import android.animation.Animator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.ori.pivotboard_project.databinding.ActivitySplashScreenBinding
import com.ori.pivotboard_project.utilities.applySystemBarPadding
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.Constants

/**
 * Launcher screen. Plays the branded animation, then routes to Login or Main depending on
 * whether a Firebase session already exists.
 */
class SplashScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashScreenBinding

    /** Guards against routing twice if both the animation end and the fallback fire. */
    private var hasRouted = false

    private val fallbackHandler = Handler(Looper.getMainLooper())
    private val fallbackRunnable = Runnable { routeOnwards() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.applySystemBarPadding()

        startSplash()
    }

    /**
     * Plays the Lottie animation when one has been supplied; otherwise the static logo stays
     * on screen for a fixed beat. Either way the fallback timer guarantees we move on.
     */
    private fun startSplash() {
        val lottie = binding.splashLOTTIEAnimation
        if (lottie.composition != null || lottie.visibility == android.view.View.VISIBLE) {
            lottie.addAnimatorListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) = routeOnwards()
                override fun onAnimationCancel(animation: Animator) = routeOnwards()
                override fun onAnimationRepeat(animation: Animator) {}
            })
            lottie.playAnimation()
        }

        fallbackHandler.postDelayed(fallbackRunnable, Constants.UI.SPLASH_FALLBACK_DELAY_MS)
    }

    private fun routeOnwards() {
        if (hasRouted) return
        hasRouted = true
        fallbackHandler.removeCallbacks(fallbackRunnable)

        val destination = if (AuthManager.getInstance().isLoggedIn()) {
            MainActivity::class.java
        } else {
            LoginActivity::class.java
        }
        startActivity(Intent(this, destination))
        finish()
    }

    override fun onDestroy() {
        fallbackHandler.removeCallbacks(fallbackRunnable)
        super.onDestroy()
    }
}
