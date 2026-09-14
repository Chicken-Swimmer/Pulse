package app.pulse.monitor.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import app.pulse.monitor.BuildConfig
import app.pulse.monitor.R
import app.pulse.monitor.data.db.CheckHistory
import app.pulse.monitor.data.db.CheckHistoryDao
import app.pulse.monitor.data.db.DbHelper
import app.pulse.monitor.data.db.WebSiteEntry
import app.pulse.monitor.data.db.WebSiteEntryDao
import app.pulse.monitor.data.model.WebSiteStatus
import app.pulse.monitor.utils.Constants
import app.pulse.monitor.utils.NetworkUtils
import app.pulse.monitor.utils.SharedPrefsManager
import app.pulse.monitor.utils.SharedPrefsManager.set
import app.pulse.monitor.utils.Utils
import app.pulse.monitor.utils.Utils.currentDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class WebSiteEntryRepository(private val context: Context) {

    private val webSiteEntryDao: WebSiteEntryDao? by lazy {
        DbHelper.getInstance(context)?.webSiteEntryDao()
    }
    private val checkHistoryDao: CheckHistoryDao? by lazy {
        DbHelper.getInstance(context)?.checkHistoryDao()
    }
    private val allWebSiteEntry: LiveData<List<WebSiteEntry>> = webSiteEntryDao?.getAllWebSiteEntryList()!!

    suspend fun clearAllHistory() {
        checkHistoryDao?.deleteAll()
    }

    fun addDefaultData() = runBlocking {
        this.launch(Dispatchers.IO) {
            webSiteEntryDao?.saveWebSiteEntry(
                WebSiteEntry(name = "F-Droid", url = "https://f-droid.org")
            )
            webSiteEntryDao?.saveWebSiteEntry(
                WebSiteEntry(name = "GitHub", url = "https://github.com")
            )
            webSiteEntryDao?.saveWebSiteEntry(
                WebSiteEntry(name = "HTTP 200 test", url = "https://httpstat.us/200")
            )
            SharedPrefsManager.customPrefs[Constants.IS_ADDED_DEFAULT_DATA] = true
        }
    }

    fun replaceLegacyDemoEntries() = runBlocking {
        this.launch(Dispatchers.IO) {
            webSiteEntryDao?.getAllWebSiteEntryDirectList()?.forEach { e ->
                val url = e.url.lowercase()
                when {
                    url.contains("manimaran96.wordpress.com") ->
                        webSiteEntryDao?.updateWebSiteEntry(
                            e.copy(name = "F-Droid", url = "https://f-droid.org")
                        )
                    e.name == "Example Error Site" && url.contains("httpstat.us") ->
                        webSiteEntryDao?.updateWebSiteEntry(e.copy(name = "HTTP 200 test"))
                }
            }
        }
    }

    fun saveWebSiteEntry(websiteEntry: WebSiteEntry) = runBlocking {
        this.launch(Dispatchers.IO) {
            webSiteEntryDao?.saveWebSiteEntry(websiteEntry)
        }
    }

    fun updateWebSiteEntry(websiteEntry: WebSiteEntry) = runBlocking {
        this.launch(Dispatchers.IO) {
            webSiteEntryDao?.updateWebSiteEntry(websiteEntry)
        }
    }

    fun deleteWebSiteEntry(websiteEntry: WebSiteEntry) {
        runBlocking {
            this.launch(Dispatchers.IO) {
                websiteEntry.id?.let { checkHistoryDao?.deleteForWebsite(it) }
                webSiteEntryDao?.deleteWebSiteEntry(websiteEntry)
            }
        }
    }

    fun getAllWebSiteEntryList(): LiveData<List<WebSiteEntry>> {
        return allWebSiteEntry
    }

    fun getHistory(websiteId: Long): LiveData<List<CheckHistory>>? {
        return checkHistoryDao?.getHistory(websiteId)
    }

    fun getWebSiteEntryById(id: Long): LiveData<WebSiteEntry> {
        return webSiteEntryDao!!.getById(id)
    }

    suspend fun getAllEntriesDirect(): List<WebSiteEntry> {
        return withContext(Dispatchers.IO) {
            webSiteEntryDao?.getAllWebSiteEntryDirectList() ?: emptyList()
        }
    }

    suspend fun getAllHistoryDirect(): List<CheckHistory> {
        return withContext(Dispatchers.IO) {
            checkHistoryDao?.getAll() ?: emptyList()
        }
    }

    suspend fun replaceAllEntries(
        entries: List<WebSiteEntry>,
        history: List<CheckHistory> = emptyList()
    ) {
        withContext(Dispatchers.IO) {
            checkHistoryDao?.deleteAll()
            webSiteEntryDao?.deleteAll()
            if (entries.isNotEmpty()) {
                webSiteEntryDao?.saveWebSiteEntryList(entries)
            }
            if (history.isNotEmpty()) {
                checkHistoryDao?.insertAll(history)
            }
        }
    }

    suspend fun checkWebSiteStatus(onEntryStart: ((WebSiteEntry) -> Unit)? = null): ArrayList<WebSiteStatus> {
        val statusList = ArrayList<WebSiteStatus>()
        pruneHistory()
        webSiteEntryDao?.getAllWebSiteEntryDirectList()?.forEach { entry ->
            val id = entry.id ?: return@forEach
            if (entry.isPaused && app.pulse.monitor.utils.SitePause.tempExpired(id)) {
                app.pulse.monitor.utils.SitePause.clearTemp(id)
                entry.isPaused = false
                webSiteEntryDao?.updateWebSiteEntry(entry)
            }
        }
        if (NetworkUtils.shouldSkipRemoteCheck(context)) {
            withContext(Dispatchers.IO) {
                webSiteEntryDao?.getAllValidWebSiteEntryDirectList()?.forEach { entry ->
                    onEntryStart?.invoke(entry)
                    statusList.add(recordSkipped(entry))
                }
            }
            return statusList
        }
        SharedPrefsManager.customPrefs.edit()
            .putLong(Constants.LAST_GLOBAL_CHECK_MS, System.currentTimeMillis())
            .apply()
        withContext(Dispatchers.IO) {
            webSiteEntryDao?.getAllValidWebSiteEntryDirectList()?.sortedBy { it.itemPosition }?.forEach {
                onEntryStart?.invoke(it)
                statusList.add(getWebsiteStatus(it))
            }
        }
        return statusList
    }

    suspend fun getWebsiteStatus(websiteEntry: WebSiteEntry): WebSiteStatus {
        if (NetworkUtils.shouldSkipRemoteCheck(context)) {
            return recordSkipped(websiteEntry)
        }
        if (app.pulse.monitor.utils.SitePause.shouldSkipSite(websiteEntry.id)) {
            return recordSkipped(websiteEntry)
        }
        return performRemoteCheck(websiteEntry)
    }

    private suspend fun recordSkipped(websiteEntry: WebSiteEntry): WebSiteStatus {
        val id = websiteEntry.id
        if (id != null) {
            checkHistoryDao?.insert(
                CheckHistory(
                    websiteId = id,
                    checkedAt = System.currentTimeMillis(),
                    statusCode = websiteEntry.status,
                    result = CheckHistory.SKIPPED,
                    latencyMs = null,
                    message = "offline"
                )
            )
        }
        return WebSiteStatus(
            name = websiteEntry.name,
            url = websiteEntry.url,
            status = websiteEntry.status ?: 0,
            isSuccessful = websiteEntry.status in 200..299,
            message = "Skipped (no network)",
            skippedOffline = true
        )
    }

    private data class Probe(
        val status: Int,
        val message: String,
        val latency: Long?,
        val skipped: Boolean = false,
        val transportError: Boolean = false
    )

    private fun isHardFailure(status: Int): Boolean {
        return status !in 200..299 && Utils.mayNotifyStatusFailure(status)
    }

    private fun probeUrl(rawUrl: String): Probe {
        var conn: HttpURLConnection? = null
        val started = System.currentTimeMillis()
        return try {
            conn = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("User-Agent", "Pulse/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "*/*")
                val timeout = SharedPrefsManager.customPrefs.getInt(
                    Constants.REQUEST_TIMEOUT_MS,
                    Constants.DEFAULT_TIMEOUT_MS
                )
                connectTimeout = timeout
                readTimeout = timeout + 2000
                requestMethod = "GET"
            }
            conn.connect()
            if (conn is javax.net.ssl.HttpsURLConnection) {
                runCatching {
                    val cert = conn.serverCertificates.firstOrNull() as? java.security.cert.X509Certificate
                    cert?.notAfter?.time?.let { exp ->
                        SharedPrefsManager.customPrefs.edit().putLong("cert_exp_$rawUrl", exp).apply()
                    }
                    val days = cert?.notAfter?.let { ((it.time - System.currentTimeMillis()) / 86400000L).toInt() }
                    if (days != null && days in 0..Constants.CERT_WARN_DAYS) {
                        val key = "cert_warned_${rawUrl}"
                        val last = SharedPrefsManager.customPrefs.getInt(key, -1)
                        if (last != days) {
                            SharedPrefsManager.customPrefs.edit().putInt(key, days).apply()
                            Utils.showNotification(
                                context,
                                context.getString(R.string.cert_expiring_title),
                                context.getString(R.string.cert_expiring_body, rawUrl, days)
                            )
                        }
                    }
                }
            }
            val status = runCatching { conn.responseCode }.getOrDefault(HttpURLConnection.HTTP_UNAVAILABLE)
            val message = runCatching { conn.responseMessage }.getOrNull() ?: ""
            Probe(status, message, System.currentTimeMillis() - started)
        } catch (e: Exception) {
            if (NetworkUtils.shouldSkipRemoteCheck(context)) {
                Probe(0, "offline", null, skipped = true)
            } else {
                Probe(
                    status = HttpURLConnection.HTTP_UNAVAILABLE,
                    message = e.localizedMessage ?: "Please check",
                    latency = System.currentTimeMillis() - started,
                    transportError = true
                )
            }
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private suspend fun performRemoteCheck(websiteEntry: WebSiteEntry): WebSiteStatus = withContext(Dispatchers.IO) {
        var probe = probeUrl(websiteEntry.url)
        if (probe.skipped) return@withContext recordSkipped(websiteEntry)

        // One quiet rematch after a failure so a 2-second blip does not alert.
        if (isHardFailure(probe.status)) {
            kotlinx.coroutines.delay(15_000)
            if (NetworkUtils.shouldSkipRemoteCheck(context)) {
                return@withContext recordSkipped(websiteEntry)
            }
            probe = probeUrl(websiteEntry.url)
            if (probe.skipped) return@withContext recordSkipped(websiteEntry)
        }

        val status = probe.status
        val message = probe.message
        val latency = probe.latency
        val httpOk = status in 200..299
        val countsAsFailure = isHardFailure(status)

        val now = System.currentTimeMillis()
        var notifyDown = false
        var notifyUp = false
        val previousDownSince = websiteEntry.downSince

        if (countsAsFailure) {
            websiteEntry.consecutiveFailures = websiteEntry.consecutiveFailures + 1
            if (websiteEntry.downSince == null) {
                websiteEntry.downSince = now
            }
            if (!websiteEntry.isAlertingDown) {
                websiteEntry.isAlertingDown = true
                notifyDown = true
            }
        } else {
            val wasDown = websiteEntry.isAlertingDown
            websiteEntry.consecutiveFailures = 0
            websiteEntry.downSince = null
            websiteEntry.isAlertingDown = false
            if (wasDown) {
                notifyUp = SharedPrefsManager.customPrefs.getBoolean(Constants.NOTIFY_ON_RECOVERY, true)
            }
        }

        websiteEntry.status = status
        websiteEntry.updatedAt = currentDateTime()
        websiteEntry.lastCheckedAt = now
        websiteEntry.lastLatencyMs = latency
        webSiteEntryDao?.updateWebSiteEntry(websiteEntry)

        websiteEntry.id?.let { id ->
            checkHistoryDao?.insert(
                CheckHistory(
                    websiteId = id,
                    checkedAt = now,
                    statusCode = status,
                    result = if (countsAsFailure) CheckHistory.DOWN else CheckHistory.UP,
                    latencyMs = latency,
                    message = message
                )
            )
        }

        WebSiteStatus(
            name = websiteEntry.name,
            url = websiteEntry.url,
            status = status,
            isSuccessful = httpOk,
            message = message,
            skippedOffline = false,
            latencyMs = latency,
            notifyDown = notifyDown,
            notifyUp = notifyUp,
            downSince = websiteEntry.downSince ?: previousDownSince,
            transportError = probe.transportError,
            id = websiteEntry.id
        )
    }

    private suspend fun pruneHistory() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(Constants.HISTORY_RETENTION_DAYS.toLong())
        withContext(Dispatchers.IO) {
            checkHistoryDao?.deleteOlderThan(cutoff)
        }
    }
}
