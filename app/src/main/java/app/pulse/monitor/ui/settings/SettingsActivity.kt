package app.pulse.monitor.ui.settings

import android.content.DialogInterface
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.pulse.monitor.BuildConfig
import app.pulse.monitor.R
import app.pulse.monitor.data.db.CheckHistory
import app.pulse.monitor.data.db.WebSiteEntry
import app.pulse.monitor.data.repository.WebSiteEntryRepository
import app.pulse.monitor.databinding.ActivitySettingsBinding
import app.pulse.monitor.utils.Constants
import app.pulse.monitor.utils.Constants.MONITORING_INTERVAL
import app.pulse.monitor.utils.Constants.MONITORING_STATUS_CODES

import app.pulse.monitor.utils.Constants.THEME_MODE
import app.pulse.monitor.utils.Interval.nameList
import app.pulse.monitor.utils.Interval.valueList
import app.pulse.monitor.utils.SharedPrefsManager
import app.pulse.monitor.utils.SharedPrefsManager.set
import app.pulse.monitor.utils.QuietHours
import app.pulse.monitor.utils.Utils
import app.pulse.monitor.utils.Utils.getMonitorTime
import app.pulse.monitor.utils.Utils.isCustomRom
import app.pulse.monitor.utils.Utils.openAutoStartScreen
import app.pulse.monitor.utils.Utils.startWorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.net.toUri

class SettingsActivity : AppCompatActivity() {

    private lateinit var repository: WebSiteEntryRepository

    private val createBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch { exportDataToUri(uri) }
        }
    }

    private val openBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch { importDataFromUri(uri) }
        }
    }

    private lateinit var activitySettingsBinding: ActivitySettingsBinding
    private lateinit var btnMonitorInterval: LinearLayout
    private lateinit var btnMonitorStatusCodes: LinearLayout
    private lateinit var dividerEnableAutoStart: View
    private lateinit var layoutEnableAutoStart: LinearLayout
    private lateinit var btnEnableAutoStart: android.view.View
    private lateinit var txtIntervalDetails: TextView
    private lateinit var txtStatusCodesDetails: TextView
    private lateinit var statusCodesList: Map<Int, String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activitySettingsBinding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(activitySettingsBinding.root)
        setSupportActionBar(activitySettingsBinding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activitySettingsBinding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        supportActionBar?.title = getString(R.string.settings)
        // Wire up new Telegram Support Group row
        btnMonitorInterval = activitySettingsBinding.btnMonitorInterval
        btnMonitorStatusCodes = activitySettingsBinding.btnMonitorStatusCodes
        layoutEnableAutoStart = activitySettingsBinding.layoutEnableAutoStart
        dividerEnableAutoStart = activitySettingsBinding.dividerEnableAutoStart
        btnEnableAutoStart = activitySettingsBinding.btnEnableAutoStart
        txtIntervalDetails = activitySettingsBinding.txtIntervalDetails
        txtStatusCodesDetails = activitySettingsBinding.txtStatusCodesDetails

        // Init repository
        repository = WebSiteEntryRepository(applicationContext)

        // Wire export/import/share actions
        activitySettingsBinding.btnExportData.setOnClickListener {
            createBackupLauncher.launch(getSuggestedBackupFileName())
        }
        activitySettingsBinding.btnShareData.setOnClickListener {
            lifecycleScope.launch { shareDataJson() }
        }
        activitySettingsBinding.btnImportData.setOnClickListener {
            AlertDialog.Builder(this, R.style.PulseDialog)
                .setTitle(R.string.import_data)
                .setMessage(R.string.import_replace_warning)
                .setPositiveButton(R.string.ok) { _, _ ->
                    openBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        btnMonitorInterval.setOnClickListener { showIntervalChooseDialog() }
        activitySettingsBinding.btnNotifyRecovery.setOnClickListener { toggleNotifyRecovery() }
        updateNotifyRecoveryOnUi()

        statusCodesList = Utils.statusCodesList.toSortedMap()
        btnMonitorStatusCodes.setOnClickListener { showStatusCodeChooseDialog() }

        layoutEnableAutoStart.visibility = if (isCustomRom()) View.VISIBLE else View.GONE
        dividerEnableAutoStart.visibility = if (isCustomRom()) View.VISIBLE else View.GONE
        btnEnableAutoStart.setOnClickListener { openAutoStartScreen(this) }

        updateIntervalTimeOnUi()

        updateStatusCodesOnUi()

        setupQuietHoursUi()
        setupExtraSettings()
        setupAppVersionDisplay()
    }

    private fun setupExtraSettings() {
        val timeoutMs = SharedPrefsManager.customPrefs.getInt(Constants.REQUEST_TIMEOUT_MS, Constants.DEFAULT_TIMEOUT_MS)
        activitySettingsBinding.txtTimeoutDetails.text = getString(R.string.timeout_sec, timeoutMs / 1000)
        activitySettingsBinding.btnRequestTimeout.setOnClickListener {
            val opts = arrayOf("5 s", "10 s", "20 s")
            val vals = intArrayOf(5000, 10000, 20000)
            val cur = SharedPrefsManager.customPrefs.getInt(Constants.REQUEST_TIMEOUT_MS, Constants.DEFAULT_TIMEOUT_MS)
            AlertDialog.Builder(this, R.style.PulseDialog)
                .setTitle(R.string.request_timeout)
                .setSingleChoiceItems(opts, vals.indexOf(cur).coerceAtLeast(0)) { d, which ->
                    SharedPrefsManager.customPrefs[Constants.REQUEST_TIMEOUT_MS] = vals[which]
                    activitySettingsBinding.txtTimeoutDetails.text = getString(R.string.timeout_sec, vals[which] / 1000)
                    d.dismiss()
                }
                .show()
        }

        val swSound = activitySettingsBinding.switchNotifySound
        swSound.isChecked = SharedPrefsManager.customPrefs.getBoolean(Constants.NOTIFY_SOUND, true)
        swSound.setOnCheckedChangeListener { _, on ->
            SharedPrefsManager.customPrefs[Constants.NOTIFY_SOUND] = on
        }
        val swVib = activitySettingsBinding.switchNotifyVibrate
        swVib.isChecked = SharedPrefsManager.customPrefs.getBoolean(Constants.NOTIFY_VIBRATE, true)
        swVib.setOnCheckedChangeListener { _, on ->
            SharedPrefsManager.customPrefs[Constants.NOTIFY_VIBRATE] = on
        }

        activitySettingsBinding.btnTestNotification.setOnClickListener {
            Utils.showNotification(
                this,
                getString(R.string.test_notification_title),
                getString(R.string.test_notification_body),
                notifyId = 9002,
                bypassQuietHours = true
            )
            Toast.makeText(this, R.string.test_notification_body, Toast.LENGTH_SHORT).show()
        }

        fun sortLabel(): String {
            val mode = SharedPrefsManager.customPrefs.getInt(Constants.SORT_MODE, Constants.SORT_PRIORITY)
            return if (mode == Constants.SORT_MANUAL) getString(R.string.sort_manual) else getString(R.string.sort_priority)
        }
        activitySettingsBinding.txtSortDetails.text = sortLabel()
        activitySettingsBinding.btnSortMode.setOnClickListener {
            val opts = arrayOf(getString(R.string.sort_priority), getString(R.string.sort_manual))
            val cur = SharedPrefsManager.customPrefs.getInt(Constants.SORT_MODE, Constants.SORT_PRIORITY)
            AlertDialog.Builder(this, R.style.PulseDialog)
                .setTitle(R.string.sort_mode)
                .setSingleChoiceItems(opts, cur) { d, which ->
                    SharedPrefsManager.customPrefs[Constants.SORT_MODE] = which
                    activitySettingsBinding.txtSortDetails.text = sortLabel()
                    d.dismiss()
                }
                .show()
        }

        activitySettingsBinding.txtMorningTime.text = app.pulse.monitor.utils.PauseUntil.labelHour()
        activitySettingsBinding.btnMorningTime.setOnClickListener {
            android.app.TimePickerDialog(
                this,
                { _, hour, minute ->
                    app.pulse.monitor.utils.PauseUntil.setMorningTime(hour, minute)
                    activitySettingsBinding.txtMorningTime.text = app.pulse.monitor.utils.PauseUntil.labelHour()
                },
                app.pulse.monitor.utils.PauseUntil.morningHour(),
                app.pulse.monitor.utils.PauseUntil.morningMinute(),
                true
            ).show()
        }

        activitySettingsBinding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this, R.style.PulseDialog)
                .setTitle(R.string.clear_history)
                .setMessage(R.string.clear_history_confirm)
                .setPositiveButton(R.string.delete) { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { repository.clearAllHistory() }
                        Toast.makeText(this@SettingsActivity, R.string.clear_history_done, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun setupQuietHoursUi() {
        val sw = activitySettingsBinding.switchQuietHours
        sw.isChecked = SharedPrefsManager.customPrefs.getBoolean(Constants.QUIET_HOURS_ENABLED, false)
        updateQuietHoursLabel()
        sw.setOnCheckedChangeListener { _, checked ->
            SharedPrefsManager.customPrefs[Constants.QUIET_HOURS_ENABLED] = checked
            updateQuietHoursLabel()
            if (checked) showQuietHoursPicker()
        }
        activitySettingsBinding.rowQuietHours.setOnClickListener { showQuietHoursPicker() }
    }

    private fun updateQuietHoursLabel() {
        val on = SharedPrefsManager.customPrefs.getBoolean(Constants.QUIET_HOURS_ENABLED, false)
        activitySettingsBinding.txtQuietHoursDetails.text =
            if (on) getString(R.string.quiet_hours_on, QuietHours.label())
            else getString(R.string.quiet_hours_off)
    }

    private fun showQuietHoursPicker() {
        val hours = (0..23).map { "%02d:00".format(it) }.toTypedArray()
        val start = SharedPrefsManager.customPrefs.getInt(Constants.QUIET_HOURS_START, 22)
        AlertDialog.Builder(this, R.style.PulseDialog)
            .setTitle(R.string.quiet_hours_start)
            .setSingleChoiceItems(hours, start) { d, which ->
                SharedPrefsManager.customPrefs[Constants.QUIET_HOURS_START] = which
                d.dismiss()
                val end = SharedPrefsManager.customPrefs.getInt(Constants.QUIET_HOURS_END, 7)
                AlertDialog.Builder(this, R.style.PulseDialog)
                    .setTitle(R.string.quiet_hours_end)
                    .setSingleChoiceItems(hours, end) { d2, which2 ->
                        SharedPrefsManager.customPrefs[Constants.QUIET_HOURS_END] = which2
                        SharedPrefsManager.customPrefs[Constants.QUIET_HOURS_ENABLED] = true
                        activitySettingsBinding.switchQuietHours.isChecked = true
                        updateQuietHoursLabel()
                        d2.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showIntervalChooseDialog() {
        val current = SharedPrefsManager.customPrefs.getInt(MONITORING_INTERVAL, 60)
        val checkedItem = valueList.indexOf(current)
        AlertDialog.Builder(this, R.style.PulseDialog)
            .setTitle(getString(R.string.choose_interval))
            .setSingleChoiceItems(nameList, checkedItem) { dialog: DialogInterface, which: Int ->
                SharedPrefsManager.customPrefs[MONITORING_INTERVAL] = valueList[which]
                startWorkManager(this, true)
                updateIntervalTimeOnUi()
                dialog.dismiss()
            }
            .setNeutralButton(getString(R.string.custom_minutes)) { _, _ -> showCustomIntervalDialog() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showCustomIntervalDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.custom_minutes_hint)
            val current = SharedPrefsManager.customPrefs.getInt(MONITORING_INTERVAL, 60)
            setText(current.toString())
            setSelection(text.length)
        }
        val pad = (24 * resources.displayMetrics.density).toInt()
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this, R.style.PulseDialog)
            .setTitle(getString(R.string.custom_minutes))
            .setMessage(getString(R.string.custom_minutes_note))
            .setView(wrap)
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                val minutes = input.text?.toString()?.trim()?.toIntOrNull() ?: 0
                if (minutes < 15) {
                    Utils.showToast(this, getString(R.string.custom_minutes_min))
                    return@setPositiveButton
                }
                SharedPrefsManager.customPrefs[MONITORING_INTERVAL] = minutes
                startWorkManager(this, true)
                updateIntervalTimeOnUi()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updateIntervalTimeOnUi() {
        txtIntervalDetails.text = getMonitorTime()
    }

    private fun toggleNotifyRecovery() {
        val next = !SharedPrefsManager.customPrefs.getBoolean(Constants.NOTIFY_ON_RECOVERY, true)
        SharedPrefsManager.customPrefs[Constants.NOTIFY_ON_RECOVERY] = next
        updateNotifyRecoveryOnUi()
    }

    private fun updateNotifyRecoveryOnUi() {
        val on = SharedPrefsManager.customPrefs.getBoolean(Constants.NOTIFY_ON_RECOVERY, true)
        activitySettingsBinding.txtNotifyRecoveryDetails.text =
            getString(if (on) R.string.notify_on_recovery_on else R.string.notify_on_recovery_off)
    }

    private fun showStatusCodeChooseDialog() {

        val alertBuilder = AlertDialog.Builder(this, R.style.PulseDialog)
        alertBuilder.setTitle(getString(R.string.choose_status_codes))


        val selectedStatusCodes = Utils.monitorStatusCodes() ?: statusCodesList.keys.map { "$it" }.toSet()
        val checkedItems = statusCodesList.map { selectedStatusCodes.contains("${it.key}") }.toBooleanArray()

        alertBuilder.setMultiChoiceItems(
            statusCodesList.map { "${it.key}: ${it.value}" }.toTypedArray(),
            checkedItems
        ) { dialog: DialogInterface, which: Int, isChecked: Boolean ->
            checkedItems[which] = isChecked
        }
        alertBuilder.setPositiveButton(getString(R.string.ok)) { dialog, which ->
            val finalSelectedItems = ArrayList<Int>()
            statusCodesList.keys.forEachIndexed { index, i ->
                if (checkedItems[index]) {
                    finalSelectedItems.add(i)
                }
            }

            val finalSet: Set<String> = finalSelectedItems.sorted().map { "$it" }.toSet()
            SharedPrefsManager.customPrefs[MONITORING_STATUS_CODES] = finalSet
        }
        alertBuilder.setNegativeButton(getString(R.string.cancel), null)
        alertBuilder.setOnDismissListener {
            updateStatusCodesOnUi()
        }
        val dialog = alertBuilder.create()
        dialog.show()
    }

    private fun updateStatusCodesOnUi() {
        val all = statusCodesList.keys.map { it.toString() }.toSet()
        val list = Utils.monitorStatusCodes()
        txtStatusCodesDetails.text = when {
            list == null || list.size == all.size -> getString(R.string.status_codes_all)
            list.isEmpty() -> getString(R.string.no_status_codes_monitored)
            else -> {
                val missing = (all - list).mapNotNull { it.toIntOrNull() }.sorted()
                val selected = list.mapNotNull { it.toIntOrNull() }.sorted()
                when {
                    missing.size in 1..3 -> getString(R.string.status_codes_except, missing.joinToString(", "))
                    selected.size <= 3 -> selected.joinToString(", ")
                    selected.all { it in 200..299 } && selected.size >= 8 -> "2xx"
                    else -> getString(R.string.status_codes_count, selected.size)
                }
            }
        }
    }

    /**
     * Setup app version display with actual version information
     */
    private fun setupAppVersionDisplay() {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }



            val versionName = packageInfo.versionName
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            val clean = (versionName ?: "1.0").substringBefore("-")
            activitySettingsBinding.toolbar.subtitle = getString(R.string.version_short, clean)
            activitySettingsBinding.txtVersionFooter.text = getString(R.string.version_short, clean)
        } catch (e: PackageManager.NameNotFoundException) {
            // Fallback if package info cannot be retrieved
            activitySettingsBinding.toolbar.subtitle = getString(R.string.version_unknown)
            activitySettingsBinding.txtVersionFooter.text = getString(R.string.version_unknown)
        }
    }

    // region Backup/Restore
    private suspend fun buildBackupJson(): JSONObject {
        val entries = repository.getAllEntriesDirect()
        val history = repository.getAllHistoryDirect()
        val json = JSONObject()
        json.put("versionName", BuildConfig.VERSION_NAME)
        json.put("versionCode", BuildConfig.VERSION_CODE)
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("name", e.name)
                put("url", e.url)
                put("status", e.status)
                put("isPaused", e.isPaused)
                put("updatedAt", e.updatedAt)
                put("itemPosition", e.itemPosition)
                put("consecutiveFailures", e.consecutiveFailures)
                put("isAlertingDown", e.isAlertingDown)
                put("downSince", e.downSince)
                put("lastLatencyMs", e.lastLatencyMs)
                put("lastCheckedAt", e.lastCheckedAt)
            })
        }
        json.put("entries", arr)
        val hist = JSONArray()
        history.forEach { h ->
            hist.put(JSONObject().apply {
                put("websiteId", h.websiteId)
                put("checkedAt", h.checkedAt)
                put("statusCode", h.statusCode)
                put("result", h.result)
                put("latencyMs", h.latencyMs)
                put("message", h.message)
            })
        }
        json.put("history", hist)
        json.put("prefs", JSONObject().apply {
            put("MONITORING_INTERVAL", SharedPrefsManager.customPrefs.getInt(MONITORING_INTERVAL, 60))
            SharedPrefsManager.customPrefs.getStringSet(MONITORING_STATUS_CODES, null)?.let {
                put("MONITORING_STATUS_CODES", JSONArray(it.sorted()))
            }
            put("THEME_MODE", SharedPrefsManager.customPrefs.getInt(THEME_MODE, -1))
        })
        return json
    }

    private fun parseBackupJson(json: JSONObject): Pair<List<WebSiteEntry>, List<CheckHistory>> {
        val entries = mutableListOf<WebSiteEntry>()
        val entriesJson = json.optJSONArray("entries")
        if (entriesJson != null) {
            for (i in 0 until entriesJson.length()) {
                val o = entriesJson.getJSONObject(i)
                entries.add(
                    WebSiteEntry(
                        id = o.optLong("id").takeIf { it != 0L },
                        name = o.getString("name"),
                        url = o.getString("url"),
                        status = if (o.isNull("status")) null else o.getInt("status"),
                        isPaused = o.optBoolean("isPaused", false),
                        updatedAt = if (o.isNull("updatedAt")) null else o.getString("updatedAt"),
                        itemPosition = if (o.isNull("itemPosition")) null else o.getInt("itemPosition"),
                        consecutiveFailures = o.optInt("consecutiveFailures", 0),
                        isAlertingDown = o.optBoolean("isAlertingDown", false),
                        downSince = if (!o.has("downSince") || o.isNull("downSince")) null else o.optLong("downSince"),
                        lastLatencyMs = if (!o.has("lastLatencyMs") || o.isNull("lastLatencyMs")) null else o.optLong("lastLatencyMs"),
                        lastCheckedAt = o.optLong("lastCheckedAt", 0L)
                    )
                )
            }
        }
        val history = mutableListOf<CheckHistory>()
        val histJson = json.optJSONArray("history")
        if (histJson != null) {
            for (i in 0 until histJson.length()) {
                val o = histJson.getJSONObject(i)
                history.add(
                    CheckHistory(
                        websiteId = o.optLong("websiteId"),
                        checkedAt = o.optLong("checkedAt"),
                        statusCode = if (!o.has("statusCode") || o.isNull("statusCode")) null else o.optInt("statusCode"),
                        result = o.optString("result", CheckHistory.SKIPPED),
                        latencyMs = if (!o.has("latencyMs") || o.isNull("latencyMs")) null else o.optLong("latencyMs"),
                        message = if (!o.has("message") || o.isNull("message")) null else o.optString("message")
                    )
                )
            }
        }
        return entries to history
    }

    private fun getSuggestedBackupFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
        val timestamp = sdf.format(Date())
        return "website_monitor_backup_${BuildConfig.VERSION_NAME}_$timestamp.json"
    }

    private suspend fun exportDataToUri(uri: Uri) {
        try {
            val json = buildBackupJson()

            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri)?.use { os ->
                    os.writer().use { it.write(json.toString(2)) }
                }
            }
            Toast.makeText(this, R.string.backup_export_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.backup_export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun importDataFromUri(uri: Uri) {
        try {
            val text = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "{}"
            }
            val json = JSONObject(text)

            // Import entries
            val parsed = parseBackupJson(json)
            repository.replaceAllEntries(parsed.first, parsed.second)

            // Import prefs
            val prefs = json.optJSONObject("prefs")
            prefs?.let {
                SharedPrefsManager.customPrefs[MONITORING_INTERVAL] = it.optInt("MONITORING_INTERVAL", 60)
                if (it.has("MONITORING_STATUS_CODES")) {
                    val arr = it.optJSONArray("MONITORING_STATUS_CODES")
                    val set = mutableSetOf<String>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) set.add(arr.getString(i))
                    }
                    SharedPrefsManager.customPrefs[MONITORING_STATUS_CODES] = set
                }
                val themeMode = it.optInt("THEME_MODE", -1)
                if (themeMode != -1) {
                    SharedPrefsManager.customPrefs[THEME_MODE] = themeMode
                    Utils.applyThemeMode(themeMode)
                }

            }

            Toast.makeText(this, R.string.backup_import_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.backup_import_failed, Toast.LENGTH_SHORT).show()
        }
    }



    private suspend fun shareDataJson() {
        try {
            val json = buildBackupJson()

            // Write to cache file and share with SAF URI
            val name = getSuggestedBackupFileName()
            val cacheFile = java.io.File(cacheDir, name)
            withContext(Dispatchers.IO) { cacheFile.writeText(json.toString(2)) }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${BuildConfig.APPLICATION_ID}.provider",
                cacheFile
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, getString(R.string.share_data)))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()

        }
    }

    // endregion
}

