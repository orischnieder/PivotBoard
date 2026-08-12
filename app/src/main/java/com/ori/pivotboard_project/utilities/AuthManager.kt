package com.ori.pivotboard_project.utilities

import android.content.Context
import android.content.Intent
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.ori.pivotboard_project.R
import java.lang.ref.WeakReference

/**
 * Everything auth-related lives here so no screen touches FirebaseAuth directly.
 * The one exception the design allows is `LoginActivity`, which must own the FirebaseUI
 * ActivityResult launcher - it still gets its Intent from [buildSignInIntent].
 */
class AuthManager private constructor(context: Context) {

    private val contextRef = WeakReference(context)

    /** The FirebaseUI sign-in Intent: Email + Google, branded with the app logo and theme. */
    fun buildSignInIntent(): Intent {
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )

        return AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setLogo(R.drawable.logo_pivotboard)
            .setAvailableProviders(providers)
            .setTheme(R.style.Theme_PivotBoard)
            .build()
    }

    fun currentUser(): FirebaseUser? = FirebaseAuth.getInstance().currentUser

    fun currentUid(): String = currentUser()?.uid.orEmpty()

    fun isLoggedIn(): Boolean = FirebaseAuth.getInstance().currentUser != null

    /** Signs out of Firebase *and* clears the cached Google credential. */
    fun logout(onComplete: (() -> Unit)? = null) {
        val context = contextRef.get()
        if (context == null) {
            FirebaseAuth.getInstance().signOut()
            onComplete?.invoke()
            return
        }
        AuthUI.getInstance().signOut(context)
            .addOnCompleteListener { onComplete?.invoke() }
    }

    companion object {
        @Volatile
        private var instance: AuthManager? = null

        fun init(context: Context): AuthManager =
            instance ?: synchronized(this) {
                instance ?: AuthManager(context).also { instance = it }
            }

        fun getInstance(): AuthManager = instance
            ?: throw IllegalStateException("AuthManager must be initialized by calling init(context) before use.")
    }
}
