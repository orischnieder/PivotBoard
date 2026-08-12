package com.ori.pivotboard_project.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.activities.PostDetailActivity
import com.ori.pivotboard_project.activities.TickerPostsActivity
import com.ori.pivotboard_project.activities.UserListActivity
import com.ori.pivotboard_project.activities.WatchlistActivity
import com.ori.pivotboard_project.adapters.PostActionHandler
import com.ori.pivotboard_project.adapters.PostAdapter
import com.ori.pivotboard_project.databinding.DialogEditProfileBinding
import com.ori.pivotboard_project.databinding.FragmentProfileBinding
import com.ori.pivotboard_project.model.Post
import com.ori.pivotboard_project.model.User
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.Constants
import com.ori.pivotboard_project.utilities.DatabaseManager
import com.ori.pivotboard_project.utilities.ImageLoader
import com.ori.pivotboard_project.utilities.SignalManager
import com.ori.pivotboard_project.utilities.StorageManager
import com.ori.pivotboard_project.utilities.hide
import com.ori.pivotboard_project.utilities.showError
import com.ori.pivotboard_project.utilities.showLoading

/**
 * Section 5.5 - a user profile with counts, follow state and that user's posts.
 *
 * The same fragment serves the Profile tab (your own page) and [ProfileActivity] (someone
 * else's), which keeps one implementation of the header and the post list. With no uid
 * argument it falls back to the signed-in user.
 */
class ProfileFragment : Fragment() {

    private var binding: FragmentProfileBinding? = null
    private val postAdapter = PostAdapter()

    private var profileUser: User? = null
    private var isFollowing = false
    private var isUploadingPhoto = false

    private val pickPhotoLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // A null uri just means the picker was dismissed.
            if (uri != null) uploadProfilePhoto(uri)
        }

    private val targetUid: String
        get() = arguments?.getString(ARG_UID).takeUnless { it.isNullOrEmpty() }
            ?: AuthManager.getInstance().currentUid()

    private val isOwnProfile: Boolean
        get() = targetUid == AuthManager.getInstance().currentUid()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = this.binding ?: return

        // Already on this author's page, so the author targets should not reopen it.
        // Deleting a post here also has to move the visible posts counter.
        postAdapter.postCallback = object : PostActionHandler(
            context = requireContext(),
            adapter = postAdapter,
            isActive = { this@ProfileFragment.binding != null },
            onPostDeleted = { onOwnPostDeleted() }
        ) {
            override fun onAuthorClicked(post: Post, position: Int) = Unit
        }
        binding.profileRVPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.profileRVPosts.adapter = postAdapter

        binding.profileBTNAction.setOnClickListener {
            if (isOwnProfile) showEditDialog() else toggleFollow()
        }

        // Only your own picture is editable.
        binding.profileIMGAvatarEdit.visibility = if (isOwnProfile) View.VISIBLE else View.GONE
        if (isOwnProfile) {
            binding.profileLAYAvatar.setOnClickListener { pickProfilePhoto() }
        }

        binding.profileLAYFollowers.setOnClickListener {
            openFollowList(UserListActivity.Mode.FOLLOWERS)
        }
        binding.profileLAYFollowing.setOnClickListener {
            openFollowList(UserListActivity.Mode.FOLLOWING)
        }

        binding.profileBTNWatchlist.setOnClickListener {
            val user = profileUser ?: return@setOnClickListener
            WatchlistActivity.start(
                context = requireContext(),
                uid = user.id,
                ownerName = user.displayName.ifBlank { user.username }
            )
        }

        loadProfile()
    }

    /** Counts and posts change elsewhere in the app, so refresh when we come back. */
    override fun onResume() {
        super.onResume()
        if (profileUser != null) loadProfile()
    }

    // ------------------------------------------------------------- Loading

    private fun loadProfile() {
        val binding = this.binding ?: return
        val uid = targetUid
        if (uid.isEmpty()) {
            showError()
            return
        }
        if (profileUser == null) binding.profileLAYState.showLoading()

        DatabaseManager.getInstance().loadUser(uid) { user, _ ->
            if (this.binding == null) return@loadUser

            if (user == null) {
                showError()
                return@loadUser
            }
            profileUser = user
            bindProfile(user)
            showContent()

            loadPosts(uid)
            if (!isOwnProfile) loadFollowState(uid)
        }
    }

    private fun loadPosts(uid: String) {
        DatabaseManager.getInstance().loadUserPosts(uid) { posts, _ ->
            val binding = this.binding ?: return@loadUserPosts
            val loaded = posts ?: emptyList()

            attachLikeStates(loaded)
            binding.profileLBLNoPosts.apply {
                visibility = if (loaded.isEmpty()) View.VISIBLE else View.GONE
                setText(
                    if (isOwnProfile) R.string.profile_no_posts_own
                    else R.string.profile_no_posts_other
                )
            }
        }
    }

    private fun attachLikeStates(posts: List<Post>) {
        val uid = AuthManager.getInstance().currentUid()
        DatabaseManager.getInstance().fetchLikedPostIds(posts.map { it.id }, uid) { likedIds ->
            if (binding == null) return@fetchLikedPostIds
            postAdapter.setData(posts, likedIds)
        }
    }

    private fun loadFollowState(uid: String) {
        val currentUid = AuthManager.getInstance().currentUid()
        DatabaseManager.getInstance().isFollowing(currentUid, uid) { following ->
            if (binding == null) return@isFollowing
            isFollowing = following
            bindActionButton()
        }
    }

    private fun openFollowList(mode: UserListActivity.Mode) {
        val user = profileUser ?: return
        UserListActivity.start(requireContext(), user.id, mode)
    }

    // ------------------------------------------------------------- Binding

    private fun bindProfile(user: User) {
        val binding = this.binding ?: return

        binding.profileLBLName.text = user.displayName.ifBlank { user.username }
        binding.profileLBLUsername.text =
            getString(R.string.profile_username_format, user.username)
        binding.profileLBLPostsCount.text = user.postsCount.toString()
        binding.profileLBLFollowersCount.text = user.followersCount.toString()
        binding.profileLBLFollowingCount.text = user.followingCount.toString()

        binding.profileLBLBio.apply {
            text = user.bio
            visibility = if (user.bio.isBlank()) View.GONE else View.VISIBLE
        }

        ImageLoader.getInstance().loadImage(user.photoUrl, binding.profileIMGAvatar)
        bindActionButton()
    }

    private fun bindActionButton() {
        val binding = this.binding ?: return
        binding.profileBTNAction.setText(
            when {
                isOwnProfile -> R.string.profile_action_edit
                isFollowing -> R.string.profile_action_unfollow
                else -> R.string.profile_action_follow
            }
        )
    }

    // ------------------------------------------------------------- Actions

    /** Optimistic, like the feed: flip the button now, roll back if the write fails. */
    private fun toggleFollow() {
        val user = profileUser ?: return
        val currentUid = AuthManager.getInstance().currentUid()
        if (currentUid.isEmpty()) return

        val wasFollowing = isFollowing
        applyFollowLocally(!wasFollowing)

        DatabaseManager.getInstance().toggleFollow(
            uid = currentUid,
            fromName = AuthManager.getInstance().currentUser()?.displayName.orEmpty(),
            targetUid = user.id,
            shouldFollow = !wasFollowing
        ) { success ->
            if (binding == null) return@toggleFollow
            if (!success) {
                applyFollowLocally(wasFollowing)
                SignalManager.getInstance().toast(R.string.profile_error_follow)
            }
        }
    }

    private fun applyFollowLocally(following: Boolean) {
        val binding = this.binding ?: return
        val user = profileUser ?: return

        isFollowing = following
        user.followersCount = (user.followersCount + if (following) 1 else -1).coerceAtLeast(0)
        binding.profileLBLFollowersCount.text = user.followersCount.toString()
        bindActionButton()
    }

    // -------------------------------------------------------- Profile photo

    private fun pickProfilePhoto() {
        if (isUploadingPhoto) return
        pickPhotoLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    /**
     * Upload to Storage, then point the profile at the new url.
     *
     * The previous image is removed only after the new one is safely referenced, so a failed
     * upload never leaves the user without a picture.
     */
    private fun uploadProfilePhoto(imageUri: Uri) {
        val user = profileUser ?: return
        val uid = AuthManager.getInstance().currentUid()
        if (uid.isEmpty() || isUploadingPhoto) return

        val previousPhotoUrl = user.photoUrl
        setPhotoUploading(true)
        SignalManager.getInstance().toast(R.string.profile_photo_uploading)

        StorageManager.getInstance().uploadImage(
            imageUri = imageUri,
            uid = uid,
            onProgress = { percent -> binding?.profilePRGAvatar?.progress = percent }
        ) { downloadUrl ->
            if (binding == null) return@uploadImage

            if (downloadUrl == null) {
                setPhotoUploading(false)
                SignalManager.getInstance().toast(R.string.profile_photo_failed)
                return@uploadImage
            }
            savePhotoUrl(user, downloadUrl, previousPhotoUrl)
        }
    }

    private fun savePhotoUrl(user: User, downloadUrl: String, previousPhotoUrl: String) {
        DatabaseManager.getInstance().updateProfilePhoto(user.id, downloadUrl) { success ->
            val binding = this.binding ?: return@updateProfilePhoto
            setPhotoUploading(false)

            if (!success) {
                SignalManager.getInstance().toast(R.string.profile_photo_failed)
                return@updateProfilePhoto
            }

            user.photoUrl = downloadUrl
            ImageLoader.getInstance().loadImage(downloadUrl, binding.profileIMGAvatar)
            SignalManager.getInstance().toast(R.string.profile_photo_updated)

            // Best effort tidy-up. A Google sign-in avatar is not ours to delete, and
            // deleteImage safely ignores any non-Firebase url.
            if (previousPhotoUrl.isNotBlank() && previousPhotoUrl != downloadUrl) {
                StorageManager.getInstance().deleteImage(previousPhotoUrl)
            }
        }
    }

    private fun setPhotoUploading(uploading: Boolean) {
        val binding = this.binding ?: return
        isUploadingPhoto = uploading
        binding.profilePRGAvatar.progress = 0
        binding.profilePRGAvatar.visibility = if (uploading) View.VISIBLE else View.GONE
        binding.profileLAYAvatar.isEnabled = !uploading
    }

    private fun showEditDialog() {
        val user = profileUser ?: return
        val dialogBinding = DialogEditProfileBinding.inflate(layoutInflater)
        dialogBinding.editEDTName.setText(user.displayName)
        dialogBinding.editEDTBio.setText(user.bio)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_edit_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.profile_action_save) { _, _ ->
                saveProfile(
                    displayName = dialogBinding.editEDTName.text?.toString()?.trim().orEmpty(),
                    bio = dialogBinding.editEDTBio.text?.toString()?.trim().orEmpty()
                )
            }
            .show()
    }

    private fun saveProfile(displayName: String, bio: String) {
        val user = profileUser ?: return

        DatabaseManager.getInstance().updateProfile(user.id, displayName, bio) { success ->
            if (binding == null) return@updateProfile

            if (success) {
                user.displayName = displayName
                user.bio = bio
                bindProfile(user)
                SignalManager.getInstance().toast(R.string.profile_saved)
            } else {
                SignalManager.getInstance().toast(R.string.profile_error_save)
            }
        }
    }

    /** Keeps the header counter honest without waiting for a reload. */
    private fun onOwnPostDeleted() {
        val binding = this.binding ?: return
        val user = profileUser ?: return

        user.postsCount = (user.postsCount - 1).coerceAtLeast(0)
        binding.profileLBLPostsCount.text = user.postsCount.toString()

        if (postAdapter.items.isEmpty()) {
            binding.profileLBLNoPosts.visibility = View.VISIBLE
            binding.profileLBLNoPosts.setText(
                if (isOwnProfile) R.string.profile_no_posts_own
                else R.string.profile_no_posts_other
            )
        }
    }

    // -------------------------------------------------------------- States

    private fun showContent() {
        val binding = this.binding ?: return
        binding.profileLAYState.hide()
        binding.profileLAYContent.visibility = View.VISIBLE
    }

    private fun showError() {
        val binding = this.binding ?: return
        binding.profileLAYContent.visibility = View.GONE
        binding.profileLAYState.showError(
            body = R.string.profile_error_load,
            onRetry = { loadProfile() }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        postAdapter.postCallback = null
        binding?.profileRVPosts?.adapter = null
        binding = null
    }

    companion object {
        private const val ARG_UID = Constants.BUNDLE_KEYS.USER_ID

        /** Pass null for the signed-in user's own profile. */
        fun newInstance(uid: String?): ProfileFragment = ProfileFragment().apply {
            arguments = Bundle().apply { putString(ARG_UID, uid) }
        }
    }
}
