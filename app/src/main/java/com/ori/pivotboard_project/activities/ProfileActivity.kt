package com.ori.pivotboard_project.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.databinding.ActivityProfileBinding
import com.ori.pivotboard_project.ui.ProfileFragment
import com.ori.pivotboard_project.utilities.applySystemBarPadding
import com.ori.pivotboard_project.utilities.Constants

/**
 * Hosts [ProfileFragment] for another user, reached by tapping an author anywhere in the
 * app. The Profile tab inside [MainActivity] shows the same fragment for the signed-in user.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.applySystemBarPadding()

        binding.profileactTBToolbar.setNavigationOnClickListener { finish() }

        // On rotation the FragmentManager restores the fragment itself.
        if (savedInstanceState == null) {
            val uid = intent.getStringExtra(Constants.BUNDLE_KEYS.USER_ID).orEmpty()
            supportFragmentManager.beginTransaction()
                .replace(R.id.profileact_FRAME_container, ProfileFragment.newInstance(uid))
                .commit()
        }
    }

    companion object {
        fun start(context: Context, uid: String) {
            val intent = Intent(context, ProfileActivity::class.java)
                .putExtra(Constants.BUNDLE_KEYS.USER_ID, uid)
            context.startActivity(intent)
        }
    }
}
