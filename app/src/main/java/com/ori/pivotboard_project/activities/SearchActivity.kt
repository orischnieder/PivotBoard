package com.ori.pivotboard_project.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.ori.pivotboard_project.R
import com.ori.pivotboard_project.adapters.PostActionHandler
import com.ori.pivotboard_project.adapters.PostAdapter
import com.ori.pivotboard_project.databinding.ActivitySearchBinding
import com.ori.pivotboard_project.model.Post
import com.ori.pivotboard_project.utilities.applySystemBarPadding
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.DatabaseManager

/**
 * Search setups by ticker or tag.
 *
 * Firestore offers no full-text search, so the two modes work differently and the UI says
 * so: ticker is a prefix match ("NV" finds NVDA), tag is an exact match on the stored
 * lowercase tag.
 */
class SearchActivity : AppCompatActivity() {

    private enum class Mode { TICKER, TAG }

    private lateinit var binding: ActivitySearchBinding
    private val postAdapter = PostAdapter()

    /**
     * Derived from the chip rather than stored, so it can never disagree with what the user
     * sees after the ChipGroup restores its own state on rotation.
     */
    private val mode: Mode
        get() = if (binding.searchCHIPTag.isChecked) Mode.TAG else Mode.TICKER

    private var lastQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.applySystemBarPadding(applyIme = true)

        binding.searchTBToolbar.setNavigationOnClickListener { finish() }

        postAdapter.postCallback = PostActionHandler(
            context = this,
            adapter = postAdapter,
            isActive = { !isFinishing && !isDestroyed }
        )
        binding.searchRVResults.layoutManager = LinearLayoutManager(this)
        binding.searchRVResults.adapter = postAdapter

        initFilters()
        initQueryField()
    }

    private fun initFilters() {
        binding.searchLAYFilters.setOnCheckedStateChangeListener { _, _ ->
            binding.searchLAYQuery.hint = getString(
                if (mode == Mode.TAG) R.string.search_hint_tag else R.string.search_hint_ticker
            )
            // Re-run so switching mode reinterprets what is already typed.
            if (lastQuery.isNotEmpty()) runSearch()
        }
    }

    /**
     * Results are not view state, so a rotation would drop them and fall back to the idle
     * prompt. Re-running here - after the views have restored - keeps the screen coherent.
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (binding.searchEDTQuery.text?.toString()?.trim().isNullOrEmpty()) return
        runSearch()
    }

    private fun initQueryField() {
        binding.searchEDTQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch()
                true
            } else {
                false
            }
        }
    }

    private fun runSearch() {
        val query = binding.searchEDTQuery.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) {
            showMessage(getString(R.string.search_idle))
            return
        }

        lastQuery = query
        hideKeyboard()
        binding.searchPRGLoading.visibility = View.VISIBLE
        binding.searchLBLMessage.visibility = View.GONE
        binding.searchRVResults.visibility = View.GONE

        val onResult: (List<Post>?, Exception?) -> Unit = { posts, _ ->
            if (!isFinishing && !isDestroyed) {
                binding.searchPRGLoading.visibility = View.GONE
                when {
                    posts == null -> showMessage(getString(R.string.search_error))
                    posts.isEmpty() ->
                        showMessage(getString(R.string.search_no_results, query))

                    else -> attachLikeStates(posts)
                }
            }
        }

        when (mode) {
            Mode.TICKER -> DatabaseManager.getInstance().searchPostsByTicker(query, onResult)
            Mode.TAG -> DatabaseManager.getInstance().searchPostsByTag(query, onResult)
        }
    }

    private fun attachLikeStates(posts: List<Post>) {
        val uid = AuthManager.getInstance().currentUid()
        DatabaseManager.getInstance().fetchLikedPostIds(posts.map { it.id }, uid) { likedIds ->
            if (isFinishing || isDestroyed) return@fetchLikedPostIds
            postAdapter.setData(posts, likedIds)
            binding.searchRVResults.visibility = View.VISIBLE
            binding.searchLBLMessage.visibility = View.GONE
        }
    }

    private fun showMessage(message: String) {
        binding.searchRVResults.visibility = View.GONE
        binding.searchLBLMessage.visibility = View.VISIBLE
        binding.searchLBLMessage.text = message
    }

    private fun hideKeyboard() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(binding.searchEDTQuery.windowToken, 0)
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, SearchActivity::class.java))
        }
    }
}
