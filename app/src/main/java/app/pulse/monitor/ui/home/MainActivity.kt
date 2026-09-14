package app.pulse.monitor.ui.home

import android.app.Activity
import android.app.Dialog
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.drawable.DrawableCompat
import android.widget.ImageView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.pulse.monitor.R
import app.pulse.monitor.data.db.WebSiteEntry
import app.pulse.monitor.data.model.CustomMonitorData
import app.pulse.monitor.databinding.ActivityMainBinding
import app.pulse.monitor.databinding.CustomRefreshInputBinding
import app.pulse.monitor.ui.createentry.CreateEntryActivity
import app.pulse.monitor.ui.settings.SettingsActivity
import app.pulse.monitor.utils.Constants
import app.pulse.monitor.utils.NetworkUtils
import app.pulse.monitor.utils.PauseUntil
import app.pulse.monitor.utils.SharedPrefsManager
import app.pulse.monitor.utils.Print
import app.pulse.monitor.utils.NotificationPermissionHelper
import app.pulse.monitor.utils.Utils
import app.pulse.monitor.utils.Utils.askToRunBackground
import app.pulse.monitor.utils.Utils.getParcelableExtraCompat
import app.pulse.monitor.utils.Utils.getStringNotWorking
import app.pulse.monitor.utils.Utils.joinToStringDescription
import app.pulse.monitor.presentation.viewmodel.MainViewModel


class MainActivity : AppCompatActivity(), WebSiteEntryAdapter.WebSiteEntryEvents {

    private lateinit var viewModel: MainViewModel
    private lateinit var searchView: SearchView

    private lateinit var webSiteEntryAdapter: WebSiteEntryAdapter
    private lateinit var binding: ActivityMainBinding

    private lateinit var customRefreshInputBinding: CustomRefreshInputBinding
    private lateinit var notificationPermissionHelper: NotificationPermissionHelper

    private lateinit var onEditClickedResultLauncher: ActivityResultLauncher<Intent>

    var handler = Handler(Looper.getMainLooper())

    private var runningCount = 0
    private var customMonitorData: CustomMonitorData = CustomMonitorData()

    private lateinit var itemTouchHelper: ItemTouchHelper
    private var pendingOpenSiteId: Long = -1L

    private val runnableTask: Runnable = Runnable {
        if (runningCount == 0) {
            stopTask()
        } else {
            startUpdateTask(isUpdate = true)
        }
    }

    private val intervalWatchdog: Runnable = object : Runnable {
        override fun run() {
            if (::webSiteEntryAdapter.isInitialized) {
                webSiteEntryAdapter.refreshTimestamps()
            }
            handler.postDelayed(this, 30_000L)
        }
    }

    private fun startUpdateTask(isUpdate: Boolean = true) {
        Print.log("Called on main thread $runningCount")
        binding.layout.layoutForceRefreshInfo.visibility = View.VISIBLE
        if (isUpdate) {
            // If offline, stop custom monitor and show a single notification. Do not spam toasts.
            if (NetworkUtils.shouldSkipRemoteCheck(applicationContext)) {
                handler.postDelayed(runnableTask, customMonitorData.runningDelay)
                binding.layout.txtForceRefreshInfo.text = getString(
                    R.string.custom_monitor_running_info,
                    customMonitorData.runningDelayValue,
                    runningCount.toString()
                )
                return
            }

            handler.postDelayed(runnableTask, customMonitorData.runningDelay)
            viewModel.checkWebSiteStatus()
            binding.layout.txtForceRefreshInfo.text = getString(R.string.custom_monitor_running_info, customMonitorData.runningDelayValue, runningCount.toString())
            runningCount += 1
        } else{
            runningCount = 1
            handler.post(runnableTask)
        }
    }

    private fun stopTask() {
        handler.removeCallbacks(runnableTask)
        runningCount = 0
        customMonitorData = CustomMonitorData()
        binding.layout.layoutForceRefreshInfo.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Switch from launcher (splash) theme to the normal app theme to avoid showing splash background
        setTheme(R.style.AppTheme_NoActionBar)


        // Apply theme mode before setting content view
        Utils.applyThemeMode(Utils.getCurrentThemeMode())

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        customRefreshInputBinding = CustomRefreshInputBinding.inflate(layoutInflater)
        binding = ActivityMainBinding.inflate(layoutInflater)
        customRefreshInputBinding = CustomRefreshInputBinding.inflate(layoutInflater, binding.root, false)

        setContentView(binding.root)
        // Setup modern back pressed handler
        setupBackPressedHandler()


        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""
        binding.toolbar.setNavigationIcon(R.drawable.ic_refresh)
        binding.toolbar.navigationIcon?.setTint(0xFFF0FDFA.toInt())
        binding.toolbar.setNavigationOnClickListener {
            startManualRefresh()
        }

        // Colors are now handled by theme attributes in layout files

        // Handle window insets for edge-to-edge
        setupEdgeToEdge()

        // Edit Website Entry Result Launcher
        onEditClickedResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val currentWebSiteEntry = data?.getParcelableExtraCompat<WebSiteEntry>(Constants.INTENT_OBJECT)!!
                viewModel.updateWebSiteEntry(currentWebSiteEntry)
            }
        }

        // Fab click listener
        val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val webSiteEntry = data?.getParcelableExtraCompat<WebSiteEntry>(Constants.INTENT_OBJECT)!!
                viewModel.saveWebSiteEntry(webSiteEntry)
            }
        }

        binding.fabAdd.setOnClickListener {
            resetSearchView()
            Utils.totalAmountEntry = webSiteEntryAdapter.itemCount
            val intent = Intent(this, CreateEntryActivity::class.java)
            resultLauncher.launch(intent)
        }

        binding.layout.btnStop.setOnClickListener { stopTask() }

        binding.layout.swipeRefresh.setOnRefreshListener {
            startManualRefresh()
        }

        // Setting up RecyclerView
        val thisContext = this
        webSiteEntryAdapter = WebSiteEntryAdapter(this)
        setupInlineSearch()
        setupStatusFilter()
        setupPauseUntil()

        // Set up filter results callback for empty state handling
        webSiteEntryAdapter.onFilterResultsChanged = { isEmpty, query ->
            updateEmptyStateVisibility(query)
        }

        binding.layout.recyclerView.apply {
            layoutManager = LinearLayoutManager(thisContext)
            adapter = webSiteEntryAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 0) {
                        binding.fabAdd.hide()
                    } else if (dy < 0)
                        binding.fabAdd.show()
                }
            })

            // Setting up Drag & Drop Re-Order WebsiteEntry List
            itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
            itemTouchHelper.attachToRecyclerView(this)
        }



        // Setting up ViewModel and LiveData
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        pendingOpenSiteId = intent.getLongExtra(Constants.EXTRA_WEBSITE_ID, -1L)
        viewModel.getWebSiteEntryList().observe(this) {
            webSiteEntryAdapter.setAllTodoItems(it)
            updateOverview(it)
            if (it.isEmpty()) {
                viewModel.addDefaultData()
            }
            if (pendingOpenSiteId > 0L) {
                val target = it.firstOrNull { e -> e.id == pendingOpenSiteId }
                pendingOpenSiteId = -1L
                if (target != null) onItemClicked(target)
            }
        }

        // Show loader only for the item that is currently in HTTP call
        viewModel.getCurrentRefreshingUrl().observe(this) { url ->
            webSiteEntryAdapter.setActiveRefreshingUrl(url)
        }

        // Setting up Custom Monitor Option
        viewModel.getAllWebSiteStatusList().observe(this) { it ->
            /*
              This block gets executed when Custom Monitor option is used,
              plus when pressing the Refresh option, manually.
            */

            if (binding.layout.swipeRefresh.isRefreshing) {
                binding.layout.swipeRefresh.isRefreshing = false
            }
            // clear all item loaders when global status list arrives
            webSiteEntryAdapter.clearRefreshing()

            if (Utils.appIsVisible().not()) {
                /*
                  If Custom Monitor option is used,
                  we only want to send notifications,
                  when App is in Foreground.
                */
                return@observe
            }

            if (it.isNotEmpty() && it.all { s -> s.skippedOffline }) {
                Utils.showToast(applicationContext, getString(R.string.skipped_offline_toast))
                return@observe
            }

            val entriesWithFailedConnection =
                it.filter { s -> s.notifyDown && customMonitorData.showNotification }

            val customMonitorEnabled = runningCount >= 1
            val runningCountText = if (customMonitorEnabled) {
                "#${runningCount - 1} "
            } else {
                ""
            }
            val severalWebsitesNotReachable = Utils.groupedDownTitle(this, entriesWithFailedConnection)
            if (entriesWithFailedConnection.size == 1) {
                val entry = entriesWithFailedConnection.first()
                Utils.showNotification(
                    applicationContext,
                    runningCountText + entry.name,
                    Utils.downNotificationMessage(applicationContext, entry.url, entry.downSince)
                )
            } else if (entriesWithFailedConnection.size > 1) {
                Utils.showNotification(
                    applicationContext,
                    runningCountText + severalWebsitesNotReachable,
                    entriesWithFailedConnection.joinToStringDescription()
                )
            }
        }

        Utils.startWorkManager(this)

        // Initialize notification permission helper with sequential permission flow
        notificationPermissionHelper = NotificationPermissionHelper(this) { isGranted ->
            if (isGranted) {
                Print.log("Notification permission granted")
            } else {
                Print.log("Notification permission denied - notifications will be disabled")
            }
            // Update home banner visibility based on current permission
            updateNotificationPermissionBanner()
            // After notification permission is handled, request battery optimization permission
            requestBatteryOptimizationPermission()
        }

        // Show a non-intrusive banner instead of auto-requesting the permission
        updateNotificationPermissionBanner()
    }

    private fun updateNotificationPermissionBanner() {
        try {
            val hasPermission = NotificationPermissionHelper.hasNotificationPermission(this)
            val banner = binding.layout.layoutNotificationPermissionInfo
            val button = binding.layout.btnEnableNotifications
            if (hasPermission) {
                banner.visibility = View.GONE
            } else {
                banner.visibility = View.VISIBLE
                button.setOnClickListener {
                    notificationPermissionHelper.requestNotificationPermission()
                }
            }
        } catch (_: Throwable) {
            // Safe-guard: never crash UI due to banner logic
        }
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Apply top inset to app bar layout
            binding.appBarLayout.let { appBar ->
                val layoutParams = appBar.layoutParams as ViewGroup.MarginLayoutParams
                layoutParams.topMargin = insets.top
                appBar.layoutParams = layoutParams
            }

            // Apply bottom inset to FAB
            binding.fabAdd.let { fab ->
                val layoutParams = fab.layoutParams as ViewGroup.MarginLayoutParams
                layoutParams.bottomMargin = insets.bottom + resources.getDimensionPixelSize(R.dimen.fab_margin)
                fab.layoutParams = layoutParams
            }

            // Apply side insets to main content
            binding.layout.root.setPadding(
                insets.left,
                binding.layout.root.paddingTop,
                insets.right,
                0 // Bottom inset handled by FAB
            )

            WindowInsetsCompat.CONSUMED
        }
    }

    /**
     * Start the permission flow (deprecated: we now use a banner to request notifications)
     */
    private fun startPermissionRequestFlow() {
        updateNotificationPermissionBanner()
    }

    /**
     * Request battery optimization permission after notification permission is handled
     */
    private fun requestBatteryOptimizationPermission() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isDestroyed) {
                this@MainActivity.askToRunBackground()
            }
        }, 500) // Small delay to prevent dialog overlap
    }

    private fun getThemeColor(attrId: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attrId, typedValue, true)
        return if (typedValue.resourceId != 0) {
            androidx.core.content.ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    private fun setupInlineSearch() {
        searchView = binding.layout.searchViewHome
        searchView.queryHint = getString(R.string.search_here)
        setupSearchViewColors(searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                webSiteEntryAdapter.filter.filter(query)
                return false
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                webSiteEntryAdapter.filter.filter(newText)
                updateEmptyStateVisibility(newText)
                return false
            }
        })
    }

    private fun applyMenuIconTinting(menu: Menu) {

        // Menu icons now use theme attributes (app:iconTint="?attr/colorOnSurface") for automatic theming
        // This method handles only the overflow menu icon which can't be themed through XML
        try {
            val colorOnSurface = getThemeColor(com.google.android.material.R.attr.colorOnSurface)

            // Set overflow menu icon color to match theme
            val toolbar = binding.toolbar
            val overflowIcon = toolbar.overflowIcon
            if (overflowIcon != null) {
                val wrappedIcon = DrawableCompat.wrap(overflowIcon)
                DrawableCompat.setTint(wrappedIcon, colorOnSurface)
                toolbar.overflowIcon = wrappedIcon
            }
        } catch (e: Exception) {
            Print.log("Error applying overflow menu icon tinting: ${e.message}")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        // Apply proper icon tinting based on theme
        applyMenuIconTinting(menu)
        return true
    }

    private fun setupSearchViewColors(searchView: SearchView) {
        try {
            // Apply colors to SearchView components using solid, non-disabled tints
            val colorOnSurface = getThemeColor(com.google.android.material.R.attr.colorOnSurface)

            // Search icon (magnifying glass) inside expanded view
            val searchIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
            androidx.core.widget.ImageViewCompat.setImageTintList(searchIcon, android.content.res.ColorStateList.valueOf(colorOnSurface))

            // Close/clear icon
            val closeIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
            androidx.core.widget.ImageViewCompat.setImageTintList(closeIcon, android.content.res.ColorStateList.valueOf(colorOnSurface))

            // Voice search icon (if available)
            val voiceIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_voice_btn)
            androidx.core.widget.ImageViewCompat.setImageTintList(voiceIcon, android.content.res.ColorStateList.valueOf(colorOnSurface))

            // Go icon (keyboard submit)
            val goIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_go_btn)
            androidx.core.widget.ImageViewCompat.setImageTintList(goIcon, android.content.res.ColorStateList.valueOf(colorOnSurface))

            // Collapsed search button (just in case)
            val collapsedSearchBtn = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_button)
            androidx.core.widget.ImageViewCompat.setImageTintList(collapsedSearchBtn, android.content.res.ColorStateList.valueOf(colorOnSurface))

            // Search text and hint color
            val searchEditText = searchView.findViewById<android.widget.AutoCompleteTextView>(androidx.appcompat.R.id.search_src_text)
            searchEditText?.apply {
                setTextColor(colorOnSurface)
                setHintTextColor(colorOnSurface)
            }

            // Search plate background (make it transparent to blend with toolbar)
            val searchPlate = searchView.findViewById<View>(androidx.appcompat.R.id.search_plate)
            searchPlate?.background = null

        } catch (e: Exception) {
            Print.log("Error setting up search view colors: ${e.message}")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showForceRefreshUI() {
        val dialog = Dialog(this)
        dialog.setCancelable(true)
        if (customRefreshInputBinding.root.parent != null) {
            /*
              Make sure child does not already have parent.
              https://stackoverflow.com/a/52988517/7061105
            */
            (customRefreshInputBinding.root.parent as ViewGroup).removeView(customRefreshInputBinding.root)
        }
        dialog.setContentView(customRefreshInputBinding.root)

        dialog.run {
            // Cancel button functionality
            customRefreshInputBinding.btnCancel.setOnClickListener {
                dialog.dismiss()
            }

            // Save button functionality
            customRefreshInputBinding.btnSave.setOnClickListener {
                if (NetworkUtils.shouldSkipRemoteCheck(applicationContext)) {
                    Utils.showToast(applicationContext, getString(R.string.skipped_offline_toast))
                    return@setOnClickListener
                }

                val durationText = customRefreshInputBinding.editDuration.text?.toString()?.trim()

                if (durationText.isNullOrEmpty()) {
                    customRefreshInputBinding.editDuration.error = getString(R.string.enter_valid_input)
                    return@setOnClickListener
                }

                val duration = try {
                    durationText.toLong()
                } catch (e: NumberFormatException) {
                    customRefreshInputBinding.editDuration.error = getString(R.string.enter_valid_input)
                    return@setOnClickListener
                }

                if (duration <= 0) {
                    customRefreshInputBinding.editDuration.error = getString(R.string.enter_valid_input)
                    return@setOnClickListener
                }

                val durationBy = if (customRefreshInputBinding.rgDurationType.checkedRadioButtonId == R.id.rbDurationMin) 60 * 1000 else 1000

                customMonitorData.apply {
                    val durationType =
                        if (customRefreshInputBinding.rgDurationType.checkedRadioButtonId == customRefreshInputBinding.rbDurationMin.id) {
                            customRefreshInputBinding.rbDurationMin.text
                        } else {
                            customRefreshInputBinding.rbDurationSec.text
                        }
                    runningDelay = duration * durationBy
                    runningDelayValue = "$duration $durationType"
                    showNotification = customRefreshInputBinding.switchShowNotification.isChecked
                }

                startUpdateTask(isUpdate = false)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onDeleteClicked(webSiteEntry: WebSiteEntry) {
        val builder = AlertDialog.Builder(this, R.style.PulseDialog)
        with(builder)
        {
            setTitle(getString(R.string.delete_website_title))
            val message = buildString {
                appendLine(getString(R.string.delete_website_desc))
                appendLine()
                appendLine("${getString(R.string.delete_website_name_label)}: ${webSiteEntry.name}")
                append("${getString(R.string.delete_website_url_label)}: ${webSiteEntry.url}")
            }
            setMessage(message)
            setPositiveButton(getString(R.string.yes)) { _, _ -> viewModel.deleteWebSiteEntry(webSiteEntry) }
            setNegativeButton(getString(R.string.no)) { dialog, _ -> dialog.dismiss() }
            show()
        }
    }

    private fun updateOverview(list: List<WebSiteEntry>) {
        val up = list.count { !it.isPaused && it.status in 200..299 }
        val downCount = list.count { !it.isPaused && it.status != null && it.status !in 200..299 }
        val paused = list.count { it.isPaused }
        val active = list.count { !it.isPaused }
        val pct = if (active == 0) 100 else (up * 100) / active
        binding.layout.healthRing.percent = pct
        binding.layout.txtHealthPercent.text = getString(R.string.health_percent, pct)
        binding.layout.txtOverviewUp.text = getString(R.string.health_up_of, up, active)
        binding.layout.txtOverviewDown.text = getString(R.string.overview_down, downCount)
        binding.layout.txtOverviewPaused.text = getString(R.string.overview_paused, paused)
        app.pulse.monitor.widget.PulseOverviewWidget.refresh(applicationContext)
    }

    override fun onEditClicked(webSiteEntry: WebSiteEntry) {
        resetSearchView()
        val intent = Intent(this, CreateEntryActivity::class.java)
        intent.putExtra(Constants.INTENT_OBJECT, webSiteEntry)
        onEditClickedResultLauncher.launch(intent)
    }

    override fun onHistoryClicked(webSiteEntry: WebSiteEntry) {
        val intent = Intent(this, app.pulse.monitor.ui.history.HistoryActivity::class.java)
        intent.putExtra(Constants.EXTRA_WEBSITE_ID, webSiteEntry.id)
        intent.putExtra(Constants.EXTRA_WEBSITE_NAME, webSiteEntry.name)
        startActivity(intent)
    }

    override fun onRefreshClicked(webSiteEntry: WebSiteEntry) {
        if (NetworkUtils.shouldSkipRemoteCheck(applicationContext)) {
            if (binding.layout.swipeRefresh.isRefreshing)
                binding.layout.swipeRefresh.isRefreshing = false
            Utils.showToast(applicationContext, getString(R.string.skipped_offline_toast))
            return
        }
        // trigger single-item HTTP refresh; adapter shows loader via currentRefreshingUrl
        viewModel.getWebSiteStatus(webSiteEntry)
        Utils.showSnackBar(
            binding.layout.swipeRefresh, String.format(
                getString(R.string.site_refreshing),
                webSiteEntry.url
            )
        )
    }

    override fun onViewClicked(webSiteEntry: WebSiteEntry, adapterPosition: Int) {
        // Long-press is reserved for drag-reorder. Delete lives in the site menu.
    }

    override fun onItemClicked(webSiteEntry: WebSiteEntry) {
        val intent = Intent(this, app.pulse.monitor.ui.detail.SiteDetailActivity::class.java)
        intent.putExtra(Constants.INTENT_OBJECT, webSiteEntry)
        startActivity(intent)
    }

    override fun onPauseClicked(webSiteEntry: WebSiteEntry, adapterPosition: Int) {
        viewModel.updateWebSiteEntry(webSiteEntry.apply {
            isPaused = this.isPaused.not()
        })
        Utils.showSnackBar(
            binding.layout.swipeRefresh, String.format(
                getString(if (webSiteEntry.isPaused) R.string.monitor_paused else R.string.monitor_resumed),
                webSiteEntry.url
            )
        )
    }

    // Handle back press using OnBackPressedDispatcher for modern API compliance
    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::searchView.isInitialized && !searchView.query.isNullOrEmpty()) {
                    searchView.setQuery("", false)
                    binding.layout.emptyStateView.visibility = android.view.View.GONE
                } else {
                    // Allow default back behavior
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun resetSearchView() {
        if (::searchView.isInitialized) {
            searchView.setQuery("", false)
            binding.layout.emptyStateView.visibility = android.view.View.GONE
            return
        }
    }

    /**
     * Update empty state visibility based on search results
     */
    private fun updateEmptyStateVisibility(searchQuery: String?) {
        val isSearching = !searchQuery.isNullOrBlank()
        val hasResults = webSiteEntryAdapter.itemCount > 0

        if (isSearching && !hasResults) {
            // Show empty state when searching but no results found
            binding.layout.emptyStateView.visibility = android.view.View.VISIBLE
            binding.layout.swipeRefresh.visibility = android.view.View.GONE

            // Update empty state message based on search query
            binding.layout.emptyStateTitle.text = getString(R.string.no_websites_found)
            binding.layout.emptyStateMessage.text = getString(R.string.try_different_search_terms)
        } else {
            // Hide empty state when not searching or when results are found
            binding.layout.emptyStateView.visibility = android.view.View.GONE
            binding.layout.swipeRefresh.visibility = android.view.View.VISIBLE
        }
    }

    private fun setupStatusFilter() {
        fun paint() {
            val selected = 0xFF5EEAD4.toInt()
            val idle = 0xFF6B9E98.toInt()
            binding.layout.chipFilterAll.setTextColor(
                if (webSiteEntryAdapter.statusFilter == WebSiteEntryAdapter.StatusFilter.ALL) selected else idle
            )
            binding.layout.chipFilterDown.setTextColor(
                if (webSiteEntryAdapter.statusFilter == WebSiteEntryAdapter.StatusFilter.DOWN) selected else idle
            )
            binding.layout.chipFilterPaused.setTextColor(
                if (webSiteEntryAdapter.statusFilter == WebSiteEntryAdapter.StatusFilter.PAUSED) selected else idle
            )
        }
        binding.layout.chipFilterAll.setOnClickListener {
            webSiteEntryAdapter.statusFilter = WebSiteEntryAdapter.StatusFilter.ALL
            paint()
        }
        binding.layout.chipFilterDown.setOnClickListener {
            webSiteEntryAdapter.statusFilter = WebSiteEntryAdapter.StatusFilter.DOWN
            paint()
        }
        binding.layout.chipFilterPaused.setOnClickListener {
            webSiteEntryAdapter.statusFilter = WebSiteEntryAdapter.StatusFilter.PAUSED
            paint()
        }
        paint()
    }

    private fun startManualRefresh() {
        if (NetworkUtils.shouldSkipRemoteCheck(applicationContext)) {
            binding.layout.swipeRefresh.isRefreshing = false
            Utils.showToast(applicationContext, getString(R.string.skipped_offline_toast))
            return
        }
        binding.layout.swipeRefresh.isRefreshing = true
        viewModel.checkWebSiteStatus()
    }

    private fun setupPauseUntil() {
        binding.layout.btnPauseUntil.setOnClickListener {
            if (PauseUntil.isActive()) {
                PauseUntil.clear()
                Utils.showToast(this, getString(R.string.resumed_toast))
            } else {
                PauseUntil.pauseUntilMorning()
                Utils.showToast(this, getString(R.string.paused_toast, PauseUntil.labelHour()))
            }
            updatePauseUntilUi()
            app.pulse.monitor.worker.CheckAlarmScheduler.scheduleNext(applicationContext)
        }
        updatePauseUntilUi()
    }

    private fun updatePauseUntilUi() {
        binding.layout.btnPauseUntil.text = if (PauseUntil.isActive()) {
            getString(R.string.paused_until_tap, PauseUntil.labelHour())
        } else {
            getString(R.string.pause_until_morning)
        }
    }

    override fun onResume() {
        super.onResume()
        stopTask()
        updatePauseUntilUi()
        handler.removeCallbacks(intervalWatchdog)
        handler.post(intervalWatchdog)
    }

    override fun onPause() {
        handler.removeCallbacks(intervalWatchdog)
        super.onPause()
    }

    private val itemTouchHelperCallback = object: ItemTouchHelper.Callback() {

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            // Available movement directions.
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
            return makeMovementFlags(dragFlags, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            // Notify your adapter that an item is moved from Position X to position Y.
            webSiteEntryAdapter.notifyItemMoved(viewHolder.adapterPosition, target.adapterPosition)
            return true
        }

        override fun isLongPressDragEnabled(): Boolean {
            // true: You want to start dragging on long press.
            // false: You want to handle it yourself.
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            // Hanlde action state changes
            val swiping = actionState == ItemTouchHelper.ACTION_STATE_DRAG
            binding.layout.swipeRefresh.isEnabled = swiping.not()
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)

            // Called by the ItemTouchHelper when the user interaction with an element is over and it also completed its animation.
            // This is a good place to send an update to your backend about changes.

            val entries = (0..recyclerView.childCount).mapNotNull {
                val holder = try { recyclerView.getChildViewHolder(recyclerView.getChildAt(it)) } catch (e: Exception) { return@mapNotNull null }
                val position = holder.adapterPosition
                Print.log("${holder.itemView.tag} holder.adapterPosition: " + holder.adapterPosition)
                Print.log("holder.itemView.tag: " + (holder.itemView.tag as WebSiteEntry).name)
                (holder.itemView.tag as WebSiteEntry) to position
            }.toMap()

            entries.forEach { entryToPosition ->
                val entry = entryToPosition.key
                entry.apply {
                    itemPosition = entryToPosition.value
                }
                viewModel.updateWebSiteEntry(entry)
            }
        }
    }

}