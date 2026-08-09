package com.ori.pivotboard_project.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseUser
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.databinding.ActivityLoginBinding
import com.ori.pivotboard_project.model.User
import com.ori.pivotboard_project.utilities.applySystemBarPadding
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.DatabaseManager
import com.ori.pivotboard_project.utilities.SignalManager

/**
 * Hosts the FirebaseUI drop-in sign-in flow.
 *
 * This is the one screen allowed to touch FirebaseUI directly, because the ActivityResult
 * launcher must be registered against an Activity. The Intent itself still comes from
 * [AuthManager.buildSignInIntent].
 *
 * TODO(ori): add google-services.json to app/ before this flow can complete.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val signInLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(FirebaseAuthUIActivityResultContract()) { result ->
            onSignInResult(result)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.applySystemBarPadding()

        binding.loginBTNSignin.setOnClickListener { launchSignIn() }
    }

    private fun launchSignIn() {
        setLoading(true)
        signInLauncher.launch(AuthManager.getInstance().buildSignInIntent())
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode != RESULT_OK) {
            setLoading(false)
            // A null idpResponse means the user backed out rather than hitting an error.
            val messageId =
                if (result.idpResponse == null) R.string.login_cancelled else R.string.login_failed
            SignalManager.getInstance().toast(messageId)
            return
        }

        val firebaseUser = AuthManager.getInstance().currentUser()
        if (firebaseUser == null) {
            setLoading(false)
            SignalManager.getInstance().toast(R.string.login_failed)
            return
        }

        saveProfileAndContinue(firebaseUser)
    }

    /** Creates or refreshes `users/{uid}` before the app shell is shown. */
    private fun saveProfileAndContinue(firebaseUser: FirebaseUser) {
        val user = User(
            id = firebaseUser.uid,
            displayName = firebaseUser.displayName.orEmpty(),
            username = usernameFrom(firebaseUser),
            photoUrl = firebaseUser.photoUrl?.toString().orEmpty()
        )

        DatabaseManager.getInstance().upsertUserOnSignIn(user) { success ->
            setLoading(false)
            // A failed profile write should not strand a signed-in user on the login screen.
            if (!success) SignalManager.getInstance().toast(R.string.login_profile_failed)
            goToMain()
        }
    }

    /** Seeds a handle from the email local part, falling back to a uid prefix. */
    private fun usernameFrom(firebaseUser: FirebaseUser): String {
        val fromEmail = firebaseUser.email?.substringBefore('@').orEmpty()
        return fromEmail.ifBlank { "trader_${firebaseUser.uid.take(6)}" }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.loginBTNSignin.isEnabled = !isLoading
        binding.loginPRGLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}
