package app.pulse.monitor.ui.detail

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.view.ViewGroup
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import app.pulse.monitor.R
import app.pulse.monitor.data.db.CheckHistory
import app.pulse.monitor.data.db.DbHelper
import app.pulse.monitor.data.db.WebSiteEntry
import app.pulse.monitor.data.repository.WebSiteEntryRepository
import app.pulse.monitor.databinding.ActivitySiteDetailBinding
import app.pulse.monitor.ui.createentry.CreateEntryActivity
import app.pulse.monitor.utils.Constants
import app.pulse.monitor.utils.NetworkUtils
import app.pulse.monitor.utils.SharedPrefsManager
import app.pulse.monitor.utils.SharedPrefsManager.set
import app.pulse.monitor.utils.Utils
import app.pulse.monitor.utils.Utils.getParcelableExtraCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SiteDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySiteDetailBinding
    private lateinit var repo: WebSiteEntryRepository
    private var entry: WebSiteEntry? = null
    private var rangeHours = 24 * 7

    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val updated = result.data?.getParcelableExtraCompat<WebSiteEntry>(Constants.INTENT_OBJECT)
            ?: return@registerForActivityResult
        entry = updated
        repo.updateWebSiteEntry(updated)
        bindHeader(updated)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySiteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setupInsets()
        repo = WebSiteEntryRepository(applicationContext)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        entry = intent.getParcelableExtraCompat(Constants.INTENT_OBJECT)
        if (entry == null) {
            finish()
            return
        }
        bindHeader(entry!!)
        selectRange(binding.chip7d, 24 * 7)
        binding.chip24h.setOnClickListener { selectRange(binding.chip24h, 24) }
        binding.chip7d.setOnClickListener { selectRange(binding.chip7d, 24 * 7) }
        binding.chip30d.setOnClickListener { selectRange(binding.chip30d, 24 * 30) }

        binding.btnRefresh.setOnClickListener { refreshNow() }
        binding.btnVisit.setOnClickListener { entry?.url?.let { Utils.openUrl(this, it) } }
        binding.btnPause.setOnClickListener { togglePause() }
        binding.btnResetUptime.setOnClickListener { confirmReset() }
        binding.btnEdit.setOnClickListener {
            val i = Intent(this, CreateEntryActivity::class.java)
            i.putExtra(Constants.INTENT_OBJECT, entry)
            editLauncher.launch(i)
        }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        observeEntry()
    }

    override fun onResume() {
        super.onResume()
        loadCharts()
    }

    private fun observeEntry() {
        val id = entry?.id ?: return
        repo.getWebSiteEntryById(id).observe(this) { updated ->
            if (updated != null) {
                entry = updated
                bindHeader(updated)
            }
        }
    }

    private fun bindHeader(e: WebSiteEntry) {
        binding.toolbar.title = e.name
        binding.txtUrl.text = e.url
        val up = !e.isPaused && e.status in 200..299
        val down = !e.isPaused && e.status != null && e.status !in 200..299
        val last = getString(R.string.last_checked_fmt, Utils.formatRelativeTime(this, e.lastCheckedAt))
        binding.txtLastChecked.text = if (down && e.downSince != null) {
            last + "\n" + getString(R.string.down_for_fmt, Utils.formatDuration(this, e.downSince!!))
        } else last
        val tlsMs = SharedPrefsManager.customPrefs.getLong("cert_exp_${e.url}", 0L)
        binding.txtTls.text = if (tlsMs > 0L) {
            val fmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.ENGLISH)
            getString(R.string.tls_expires, fmt.format(java.util.Date(tlsMs)))
        } else {
            getString(R.string.tls_unknown)
        }
        val color = ContextCompat.getColor(
            this, when {
                e.isPaused -> R.color.status_unknown
                up -> R.color.chart_up
                down -> R.color.chart_down
                else -> R.color.status_unknown
            }
        )
        binding.statusCore.backgroundTintList = ColorStateList.valueOf(color)
        binding.statusRing.backgroundTintList = ColorStateList.valueOf(color)
        binding.txtState.text = when {
            e.isPaused -> getString(R.string.state_paused)
            up -> getString(R.string.state_up)
            down -> getString(R.string.state_down)
            else -> getString(R.string.state_unknown)
        }
        binding.txtState.setTextColor(color)
        binding.btnPause.text = if (e.isPaused) getString(R.string.resume) else getString(R.string.pause)
    }

    private fun setupInsets() {
        // Root uses fitsSystemWindows; keep content below status/cutout.
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            if (view.paddingTop < bars.top) {
                view.setPadding(view.paddingLeft, bars.top, view.paddingRight, view.paddingBottom)
            }
            insets
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this, R.style.PulseDialog)
            .setTitle(R.string.reset_uptime)
            .setMessage(R.string.reset_uptime_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val id = entry?.id ?: return@setPositiveButton
                SharedPrefsManager.customPrefs[Constants.uptimeResetKey(id)] = System.currentTimeMillis()
                loadCharts()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun selectRange(selected: View, hours: Int) {
        rangeHours = hours
        listOf(binding.chip24h, binding.chip7d, binding.chip30d).forEach {
            it.alpha = if (it == selected) 1f else 0.55f
        }
        loadCharts()
    }

    private fun loadCharts() {
        val id = entry?.id ?: return
        lifecycleScope.launch {
            val rangeStart = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(rangeHours.toLong())
            val resetAt = SharedPrefsManager.customPrefs.getLong(Constants.uptimeResetKey(id), 0L)
            val since = maxOf(rangeStart, resetAt)
            val rows = withContext(Dispatchers.IO) {
                DbHelper.getInstance(applicationContext)?.checkHistoryDao()
                    ?.getSince(id, since) ?: emptyList()
            }
            renderCharts(rows)
        }
    }

    private fun renderCharts(rows: List<CheckHistory>) {
        val counted = rows.filter { it.result != CheckHistory.SKIPPED }
        val upCount = counted.count { it.result == CheckHistory.UP }
        val downCount = counted.count { it.result == CheckHistory.DOWN }
        val pct = if (counted.isEmpty()) null else upCount * 100.0 / counted.size
        binding.txtUptimePct.text = if (pct == null) getString(R.string.uptime_no_data)
        else getString(R.string.uptime_pct, kotlin.math.round(pct).toInt())
        val outages = countOutages(counted)
        binding.txtUptimeMeta.text = getString(R.string.uptime_meta, downCount, outages)

        val bucketCount = when {
            rangeHours <= 24 -> 24
            rangeHours <= 24 * 7 -> 28
            else -> 30
        }
        val bucketMs = TimeUnit.HOURS.toMillis(rangeHours.toLong()) / bucketCount
        val start = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(rangeHours.toLong())
        val slots = MutableList<Boolean?>(bucketCount) { null }
        val latency = MutableList<Long?>(bucketCount) { null }
        rows.forEach { row ->
            val idx = ((row.checkedAt - start) / bucketMs).toInt().coerceIn(0, bucketCount - 1)
            when (row.result) {
                CheckHistory.DOWN -> slots[idx] = false
                CheckHistory.UP -> if (slots[idx] != false) slots[idx] = true
            }
            row.latencyMs?.let { latency[idx] = it }
        }
        binding.uptimeBar.slots = slots
        binding.latencyLine.points = latency
        val avg = latency.filterNotNull().average()
        binding.txtAvgMs.text = if (avg.isNaN()) "—" else "${avg.toInt()} ms"

        binding.recentList.removeAllViews()
        val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
        rows.asReversed().take(12).forEach { row ->
            val tv = TextView(this)
            val code = row.statusCode?.toString() ?: "—"
            val lat = row.latencyMs?.let { " · ${it} ms" } ?: ""
            tv.text = "${fmt.format(Date(row.checkedAt))}   ${row.result} $code$lat"
            tv.setPadding(0, 10, 0, 10)
            val col = when (row.result) {
                CheckHistory.UP -> R.color.chart_up
                CheckHistory.DOWN -> R.color.chart_down
                else -> R.color.chart_muted
            }
            tv.setTextColor(ContextCompat.getColor(this, col))
            binding.recentList.addView(tv)
        }
    }

    private fun countOutages(rows: List<CheckHistory>): Int {
        var n = 0
        var prevUp = true
        rows.forEach {
            val up = it.result == CheckHistory.UP
            if (prevUp && !up && it.result == CheckHistory.DOWN) n++
            if (it.result != CheckHistory.SKIPPED) prevUp = up
        }
        return n
    }

    private fun refreshNow() {
        val e = entry ?: return
        if (NetworkUtils.shouldSkipRemoteCheck(this)) {
            Utils.showToast(this, getString(R.string.skipped_offline_toast))
            return
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { repo.getWebsiteStatus(e) }
            loadCharts()
        }
    }

    private fun confirmDelete() {
        val e = entry ?: return
        AlertDialog.Builder(this, R.style.PulseDialog)
            .setTitle(R.string.delete_website_title)
            .setMessage(getString(R.string.delete_website_desc) + "\n\n${e.name}\n${e.url}")
            .setPositiveButton(R.string.delete) { _, _ ->
                repo.deleteWebSiteEntry(e)
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun togglePause() {
        val e = entry ?: return
        val id = e.id
        if (e.isPaused) {
            e.isPaused = false
            if (id != null) app.pulse.monitor.utils.SitePause.clearTemp(id)
            repo.updateWebSiteEntry(e)
            bindHeader(e)
            return
        }
        val options = arrayOf(
            getString(R.string.pause_1h),
            getString(R.string.pause_until_morning),
            getString(R.string.monitor_paused),
            getString(R.string.maintenance_window)
        )
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (id != null) app.pulse.monitor.utils.SitePause.pauseForMinutes(id, 60)
                        e.isPaused = true
                        repo.updateWebSiteEntry(e)
                        bindHeader(e)
                    }
                    1 -> {
                        if (id != null) app.pulse.monitor.utils.SitePause.pauseUntil(id, app.pulse.monitor.utils.PauseUntil.nextMorningMs())
                        e.isPaused = true
                        repo.updateWebSiteEntry(e)
                        bindHeader(e)
                    }
                    2 -> {
                        e.isPaused = true
                        repo.updateWebSiteEntry(e)
                        bindHeader(e)
                    }
                    3 -> if (id != null) pickMaintenanceWindow(id)
                }
            }
            .show()
    }

    private fun pickMaintenanceWindow(id: Long) {
        val hours = (0..23).map { "%02d:00".format(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.quiet_hours_start)
            .setItems(hours) { _, start ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.quiet_hours_end)
                    .setItems(hours) { _, end ->
                        app.pulse.monitor.utils.SitePause.setWindow(id, start * 60, end * 60, true)
                        Utils.showToast(this, getString(R.string.maintenance_window))
                    }
                    .show()
            }
            .show()
    }
}
