package app.pulse.monitor.ui.createentry

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem
import androidx.core.view.WindowCompat
import app.pulse.monitor.R
import app.pulse.monitor.data.db.WebSiteEntry
import app.pulse.monitor.databinding.ActivityCreateEntryBinding
import app.pulse.monitor.utils.Constants
import app.pulse.monitor.utils.Utils
import app.pulse.monitor.utils.Utils.getParcelableExtraCompat

class CreateEntryActivity : AppCompatActivity() {

    var webSiteEntry: WebSiteEntry? = null
    private lateinit var activityCreateEntryBinding: ActivityCreateEntryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        activityCreateEntryBinding = ActivityCreateEntryBinding.inflate(layoutInflater)

        setContentView(activityCreateEntryBinding.root)

        setSupportActionBar(activityCreateEntryBinding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // Allow the home-as-up to be clickable and finish the Activity
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_ab_back_material)

        // Ensure navigation icon is set
        activityCreateEntryBinding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)

        activityCreateEntryBinding.toolbar.setNavigationOnClickListener { finish() }


        //Prepopulate existing title and content from intent
        val intent = intent
        if (intent != null && intent.hasExtra(Constants.INTENT_OBJECT)) {
            webSiteEntry = intent.getParcelableExtraCompat(Constants.INTENT_OBJECT)
            webSiteEntry?.let { prePopulateData(it) }
        }

        title = if (webSiteEntry != null) getString(R.string.update_entry) else getString(R.string.create_entry)

        // Clear errors when user starts typing
        activityCreateEntryBinding.editName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                activityCreateEntryBinding.inputName.error = null
            }
        }

        activityCreateEntryBinding.editUrl.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                activityCreateEntryBinding.inputUrl.error = null
                // Auto-add https:// prefix when user starts typing
                val currentText = activityCreateEntryBinding.editUrl.text?.toString() ?: ""
                if (currentText.isEmpty()) {
                    activityCreateEntryBinding.editUrl.setText("https://")
                    activityCreateEntryBinding.editUrl.setSelection(activityCreateEntryBinding.editUrl.text?.length ?: 0)
                }
            } else {
                // When focus is lost, ensure URL has proper protocol
                val url = activityCreateEntryBinding.editUrl.text?.toString()?.trim()
                if (!url.isNullOrEmpty()) {
                    val processedUrl = processUrlWithProtocol(url)
                    val normalizedUrl = processedUrl.trimEnd('/')
                    if (normalizedUrl != url) {
                        activityCreateEntryBinding.editUrl.setText(normalizedUrl)
                    }
                }
            }
        }

        activityCreateEntryBinding.btnSave.setOnClickListener { saveEntry() }
    }

    private fun prePopulateData(todoRecord: WebSiteEntry) {
        activityCreateEntryBinding.editName.setText(todoRecord.name)
        activityCreateEntryBinding.editUrl.setText(todoRecord.url)
        activityCreateEntryBinding.btnSave.text = getString(R.string.update)
    }


    /**
     * Sends the updated information back to calling Activity
     * */
    private fun saveEntry() {
        if (validateFields()) {
            val rawUrl = activityCreateEntryBinding.editUrl.text.toString().trim()
            val processedUrl = processUrlWithProtocol(rawUrl).trimEnd('/')
            val name = activityCreateEntryBinding.editName.text.toString().trim()

            // copy() keeps pause/status/history fields; a fresh WebSiteEntry wiped them.
            val existing = webSiteEntry
            val todo = if (existing != null) {
                existing.copy(name = name, url = processedUrl)
            } else {
                WebSiteEntry(
                    name = name,
                    url = processedUrl,
                    itemPosition = Utils.totalAmountEntry
                )
            }
            val intent = Intent()
            intent.putExtra(Constants.INTENT_OBJECT, todo)
            setResult(RESULT_OK, intent)
            finish()
        }
    }

    /**
     * Validation of EditText with improved user feedback
     * */
    private fun validateFields(): Boolean {
        var firstInvalidField: android.view.View? = null

        // Clear previous errors
        activityCreateEntryBinding.inputName.error = null
        activityCreateEntryBinding.inputUrl.error = null

        // Validate name
        val name = activityCreateEntryBinding.editName.text?.toString()?.trim()
        if (name.isNullOrEmpty()) {
            activityCreateEntryBinding.inputName.error = getString(R.string.enter_valid_name)
            if (firstInvalidField == null) firstInvalidField = activityCreateEntryBinding.editName
        }

        // Validate URL (accept localhost/IP with optional port by normalizing first)
        val urlInput = activityCreateEntryBinding.editUrl.text?.toString()?.trim()
        val urlForValidation = urlInput?.let { processUrlWithProtocol(it).trimEnd('/') }
        if (urlForValidation.isNullOrEmpty() || !Utils.isValidUrl(urlForValidation)) {
            activityCreateEntryBinding.inputUrl.error = getString(R.string.enter_valid_url)
            if (firstInvalidField == null) firstInvalidField = activityCreateEntryBinding.editUrl
        }

        firstInvalidField?.requestFocus()
        return firstInvalidField == null
    }

    /**
     * Process URL to ensure it has a proper protocol
     * Smart detection to avoid adding prefix if URL already contains a protocol
     */
    private fun processUrlWithProtocol(url: String): String {
        val trimmedUrl = url.trim()

        // If URL is empty or just the default prefix, return as is
        if (trimmedUrl.isEmpty() || trimmedUrl == "https://") {
            return trimmedUrl
        }

        // Check if URL already has a protocol
        val protocolPattern = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")
        if (protocolPattern.matches(trimmedUrl)) {
            return trimmedUrl // URL already has protocol
        }

        // No protocol present: default to https for all cases (user must type ftp:// explicitly)
        return "https://$trimmedUrl"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

}