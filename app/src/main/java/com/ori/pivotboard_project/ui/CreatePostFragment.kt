package com.ori.pivotboard_project.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.databinding.FragmentCreatePostBinding
import com.ori.pivotboard_project.model.Post
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.Constants
import com.ori.pivotboard_project.utilities.DataManager
import com.ori.pivotboard_project.utilities.DatabaseManager
import com.ori.pivotboard_project.utilities.ImageLoader
import com.ori.pivotboard_project.utilities.SignalManager
import com.ori.pivotboard_project.utilities.StorageManager

/**
 * Section 5.3 - compose a new setup post.
 *
 * Publishing is two steps: upload the chart to Storage, then write the post document with
 * the resulting download url. The form stays locked for the whole sequence so a double tap
 * cannot produce two posts.
 */
class CreatePostFragment : Fragment() {

    private var binding: FragmentCreatePostBinding? = null

    private var selectedImageUri: Uri? = null
    private var isPublishing = false

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // A null uri simply means the user backed out of the picker.
            if (uri != null) showSelectedImage(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCreatePostBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = this.binding ?: return

        initSetupSpinner()

        binding.createBTNPickImage.setOnClickListener {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.createBTNPublish.setOnClickListener { publish() }

        // The picked image is not part of any view's saved state, so it has to be restored
        // by hand or a rotation would silently clear the chart preview.
        savedInstanceState?.getString(STATE_IMAGE_URI)
            ?.takeIf { it.isNotEmpty() }
            ?.let { showSelectedImage(it.toUri()) }

        // Clearing the error as soon as the user edits is friendlier than leaving it up.
        binding.createEDTTicker.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.createLAYTicker.error = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_IMAGE_URI, selectedImageUri?.toString().orEmpty())
    }

    private fun initSetupSpinner() {
        val binding = this.binding ?: return
        binding.createSPNSetup.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            DataManager.setupTypes
        )
        binding.createSPNSetup.setSelection(
            DataManager.setupTypes.indexOf(DataManager.defaultSetupType).coerceAtLeast(0)
        )
    }

    private fun showSelectedImage(uri: Uri) {
        val binding = this.binding ?: return
        selectedImageUri = uri

        binding.createIMGChart.visibility = View.VISIBLE
        binding.createLBLChartEmpty.visibility = View.GONE
        binding.createBTNPickImage.setText(R.string.create_action_change_image)
        ImageLoader.getInstance().loadImage(uri, binding.createIMGChart)
    }

    // ------------------------------------------------------------ Publishing

    private fun publish() {
        val binding = this.binding ?: return
        if (isPublishing) return

        val uid = AuthManager.getInstance().currentUid()
        if (uid.isEmpty()) {
            SignalManager.getInstance().toast(R.string.create_error_not_signed_in)
            return
        }

        val ticker = binding.createEDTTicker.text?.toString()?.trim()?.uppercase().orEmpty()
        if (!validate(ticker)) return

        val imageUri = selectedImageUri ?: return

        setPublishing(true)
        uploadChartThenPost(imageUri, uid, ticker)
    }

    private fun validate(ticker: String): Boolean {
        val binding = this.binding ?: return false

        val tickerError = when {
            ticker.isEmpty() -> getString(R.string.create_error_ticker_required)
            !ticker.matches(TICKER_PATTERN) -> getString(R.string.create_error_ticker_invalid)
            else -> null
        }
        binding.createLAYTicker.error = tickerError
        if (tickerError != null) {
            binding.createEDTTicker.requestFocus()
            return false
        }

        if (selectedImageUri == null) {
            SignalManager.getInstance().toast(R.string.create_error_chart_required)
            return false
        }
        return true
    }

    private fun uploadChartThenPost(imageUri: Uri, uid: String, ticker: String) {
        StorageManager.getInstance().uploadImage(
            imageUri = imageUri,
            uid = uid,
            onProgress = { percent -> showProgress(percent) }
        ) { downloadUrl ->
            if (binding == null) return@uploadImage

            if (downloadUrl == null) {
                setPublishing(false)
                SignalManager.getInstance().toast(R.string.create_error_upload_failed)
                return@uploadImage
            }
            writePost(uid, ticker, downloadUrl)
        }
    }

    private fun writePost(uid: String, ticker: String, imageUrl: String) {
        val binding = this.binding ?: return
        showPublishingStatus()

        val currentUser = AuthManager.getInstance().currentUser()
        val post = Post(
            authorId = uid,
            authorName = authorNameFor(),
            authorPhotoUrl = currentUser?.photoUrl?.toString().orEmpty(),
            ticker = ticker,
            setupType = binding.createSPNSetup.selectedItem?.toString()
                ?: DataManager.defaultSetupType,
            imageUrl = imageUrl,
            notes = binding.createEDTNotes.text?.toString()?.trim().orEmpty(),
            tags = parseTags(binding.createEDTTags.text?.toString()),
            createdAt = System.currentTimeMillis()
        )

        DatabaseManager.getInstance().createPost(post) { postId, _ ->
            if (this.binding == null) return@createPost
            setPublishing(false)

            if (postId == null) {
                SignalManager.getInstance().toast(R.string.create_error_publish_failed)
            } else {
                SignalManager.getInstance().toast(R.string.create_published)
                SignalManager.getInstance().vibrate()
                clearForm()
            }
        }
    }

    /**
     * Denormalized onto the post so the feed can render a card without a second lookup.
     * Falls back to the email local part, then to a generic label, so a card is never
     * headed by an empty name.
     */
    private fun authorNameFor(): String {
        val currentUser = AuthManager.getInstance().currentUser() ?: return DEFAULT_AUTHOR_NAME
        return currentUser.displayName
            ?.takeIf { it.isNotBlank() }
            ?: currentUser.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: DEFAULT_AUTHOR_NAME
    }

    /** "earnings, #gap ,, Gap" -> ["earnings", "gap"] */
    private fun parseTags(raw: String?): List<String> =
        raw.orEmpty()
            .split(',')
            .map { it.trim().removePrefix("#").lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()

    // ----------------------------------------------------------- Form state

    private fun setPublishing(publishing: Boolean) {
        val binding = this.binding ?: return
        isPublishing = publishing

        binding.createBTNPublish.isEnabled = !publishing
        binding.createBTNPickImage.isEnabled = !publishing
        binding.createEDTTicker.isEnabled = !publishing
        binding.createEDTNotes.isEnabled = !publishing
        binding.createEDTTags.isEnabled = !publishing
        binding.createSPNSetup.isEnabled = !publishing

        val visibility = if (publishing) View.VISIBLE else View.GONE
        binding.createPRGUpload.visibility = visibility
        binding.createLBLStatus.visibility = visibility
    }

    private fun showProgress(percent: Int) {
        val binding = this.binding ?: return
        binding.createPRGUpload.isIndeterminate = false
        binding.createPRGUpload.progress = percent
        binding.createLBLStatus.text = getString(R.string.create_status_uploading, percent)
    }

    /** The Firestore write has no progress to report, so the bar goes indeterminate. */
    private fun showPublishingStatus() {
        val binding = this.binding ?: return
        binding.createPRGUpload.isIndeterminate = true
        binding.createLBLStatus.setText(R.string.create_status_publishing)
    }

    private fun clearForm() {
        val binding = this.binding ?: return

        binding.createEDTTicker.text = null
        binding.createEDTNotes.text = null
        binding.createEDTTags.text = null
        binding.createLAYTicker.error = null

        selectedImageUri = null
        binding.createIMGChart.setImageDrawable(null)
        binding.createIMGChart.visibility = View.GONE
        binding.createLBLChartEmpty.visibility = View.VISIBLE
        binding.createBTNPickImage.setText(R.string.create_action_pick_image)

        initSetupSpinner()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        private const val STATE_IMAGE_URI = "STATE_IMAGE_URI"
        private const val DEFAULT_AUTHOR_NAME = "Trader"
        private val TICKER_PATTERN = Regex("^[A-Z]{1,${Constants.UI.TICKER_MAX_LENGTH}}$")
    }
}
