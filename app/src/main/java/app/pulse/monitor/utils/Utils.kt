package app.pulse.monitor.utils

import android.annotation.SuppressLint
import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import com.google.android.material.snackbar.Snackbar
import app.pulse.monitor.MyApplication
import app.pulse.monitor.R
import app.pulse.monitor.data.model.WebSiteStatus
import app.pulse.monitor.ui.home.MainActivity
import app.pulse.monitor.utils.Constants.DEFAULT_INTERVAL_MIN
import app.pulse.monitor.utils.Constants.NOTIFICATION_CHANNEL_DESCRIPTION
import app.pulse.monitor.utils.Constants.NOTIFICATION_CHANNEL_ID
import app.pulse.monitor.utils.Constants.NOTIFICATION_CHANNEL_NAME
import app.pulse.monitor.utils.Interval
import app.pulse.monitor.utils.SharedPrefsManager.get
import app.pulse.monitor.utils.SharedPrefsManager.set
import app.pulse.monitor.worker.CheckAlarmScheduler
import app.pulse.monitor.worker.WorkManagerScheduler
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.*

object Utils {

    val lineEnd = System.lineSeparator()
    var totalAmountEntry = 0

    fun currentDateTime(): String {
        return DateFormat.getDateTimeInstance().format(Date())
    }

    fun formatRelativeTime(context: Context, epochMs: Long): String {
        if (epochMs <= 0L) return context.getString(R.string.never_checked)
        val mins = ((System.currentTimeMillis() - epochMs) / 60_000L).coerceAtLeast(0)
        return when {
            mins < 1L -> context.getString(R.string.checked_just_now)
            mins < 60L -> context.getString(R.string.checked_minutes_ago, mins)
            mins < 60L * 24L -> context.getString(R.string.checked_hours_ago, mins / 60L)
            else -> context.getString(R.string.checked_days_ago, mins / (60L * 24L))
        }
    }

    fun formatDuration(context: Context, startMs: Long): String {
        val mins = ((System.currentTimeMillis() - startMs) / 60_000L).coerceAtLeast(1)
        return when {
            mins < 60L -> context.getString(R.string.duration_minutes, mins)
            mins < 60L * 24L -> {
                val h = mins / 60L
                val rem = mins % 60L
                if (rem == 0L) context.getString(R.string.duration_hours, h)
                else context.getString(R.string.duration_hours_minutes, h, rem)
            }
            else -> context.getString(R.string.duration_days, mins / (60L * 24L))
        }
    }

    fun downNotificationMessage(context: Context, url: String, downSince: Long?): String {
        val base = context.getStringNotWorking(url)
        if (downSince == null || downSince <= 0L) return base
        return context.getString(R.string.not_working_for, url, formatDuration(context, downSince))
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        notifyId: Int? = null,
        bypassQuietHours: Boolean = false,
        websiteId: Long? = null
    ) {
        if (!bypassQuietHours && QuietHours.isActive()) {
            Print.log("Quiet hours active, skipping notification")
            return
        }
        if (!NotificationPermissionHelper.hasNotificationPermission(context)) {
            Print.log("Notification permission not granted, skipping notification")
            return
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = NOTIFICATION_CHANNEL_DESCRIPTION
                enableVibration(true)
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (websiteId != null && websiteId > 0L) {
                putExtra(Constants.EXTRA_WEBSITE_ID, websiteId)
            }
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT
        val requestCode = websiteId?.toInt()?.and(0x7fffffff) ?: 0
        val pi = PendingIntent.getActivity(context, requestCode, intent, pendingFlags)

        val id = notifyId ?: (title + message).hashCode()
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_watch)
            .setColor(0xFF0F766E.toInt())
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message).setBigContentTitle(title))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup("pulse_watch")
            .setContentIntent(pi)

        val sound = SharedPrefsManager.customPrefs.getBoolean(Constants.NOTIFY_SOUND, true)
        val vibrate = SharedPrefsManager.customPrefs.getBoolean(Constants.NOTIFY_VIBRATE, true)
        if (!sound) builder.setSilent(true)
        if (vibrate) builder.setVibrate(longArrayOf(0, 180, 80, 180))
        else builder.setVibrate(longArrayOf(0))

        nm.notify(id, builder.build())

        val summary = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_watch)
            .setColor(0xFF0F766E.toInt())
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(title)
            .setStyle(NotificationCompat.InboxStyle().setSummaryText(context.getString(R.string.app_name)))
            .setGroup("pulse_watch")
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
        nm.notify(9001, summary.build())
    }

    fun isValidUrl(url: String) : Boolean{
        try {
            URL(url).toURI()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun startWorkManager(context: Context, isForce : Boolean = false) {
        // Always re-assert the periodic job. OEM killers can drop WorkManager
        // while IS_SCHEDULED stays true, after which nothing was re-queued.
        SharedPrefsManager.customPrefs.set(Constants.IS_SCHEDULED, true)
        WorkManagerScheduler.refreshPeriodicWork(context, replace = isForce)
        CheckAlarmScheduler.scheduleNext(context)
    }

    fun getMonitorInterval() : Long {
        return (SharedPrefsManager.customPrefs[Constants.MONITORING_INTERVAL, DEFAULT_INTERVAL_MIN] ?: DEFAULT_INTERVAL_MIN).toLong()
    }

    fun getMonitorTime() : String {
        val interval = getMonitorInterval().toInt().coerceAtLeast(1)
        return Interval.labelForMinutes(interval)
    }

    fun isCustomRom(): Boolean {
        return listOf("xiaomi", "oppo", "vivo", "letv", "honor")
            .contains(
                Build.MANUFACTURER.lowercase(Locale.ROOT)
            )
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun openAutoStartScreen(context: Context) {
        try {
            val intent = Intent()
            // https://stackoverflow.com/questions/39366231/how-to-check-miui-autostart-permission-programmatically
            when(Build.MANUFACTURER.lowercase(Locale.ROOT)) {
                "xiaomi" -> intent.component= ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                "oppo" -> intent.component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                "vivo" -> intent.component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                "letv" -> intent.component = ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
                "honor" -> intent.component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            }

            val list = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (list.size > 0) {
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, context.getString(R.string.something_went_wrong), Toast.LENGTH_LONG).show()
        }

    }

    fun showAutoStartEnableDialog(context: Context) {
        if (isCustomRom() && !SharedPrefsManager.customPrefs.getBoolean(Constants.IS_AUTO_START_SHOWN, false)) {
            val alertBuilder = AlertDialog.Builder(context)
            alertBuilder.setTitle(context.getString(R.string.enable_auto_start))
            alertBuilder.setMessage(context.getString(R.string.message_auto_start_reason))
            alertBuilder.setPositiveButton(context.getString(R.string.ok)) { dialog, _ ->
                SharedPrefsManager.customPrefs[Constants.IS_AUTO_START_SHOWN] = true
                openAutoStartScreen(context)
                dialog.dismiss()
            }
            alertBuilder.setNegativeButton(context.getString(R.string.cancel), null)
            val dialog = alertBuilder.create()
            dialog.setCancelable(false)
            dialog.show()
        }
    }

    object RequestCode {
        const val RC_IGNORE_BATTERY_OPTIMIZATION = 101
    }

    @SuppressLint("BatteryLife")
    fun Activity.askToRunBackground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = this.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(packageName)
            if (!isIgnoringBatteryOptimizations) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Print.log("Failed to open battery optimization settings: ${e.message}")
                    // Fallback to general battery optimization settings
                    try {
                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(fallbackIntent)
                    } catch (e2: Exception) {
                        Print.log("Failed to open fallback battery optimization settings: ${e2.message}")
                    }
                }
            }
        }
    }

    fun openUrl(context: Context, url: String) {
        try {
            val intents = Intent(Intent.ACTION_VIEW)
            intents.data = Uri.parse(url)
            intents.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intents)
        } catch (e: Exception) {
            Print.log(e.toString())
            Toast.makeText(context, context.getString(R.string.no_apps_found), Toast.LENGTH_LONG)
                .show()
        }
    }

    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun showSnackBar(view: View, message: String) {
        Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
    }

    val statusCodesList get() = hashMapOf<Int, String>().apply {
        this[HttpURLConnection.HTTP_OK] = "Success"
        this[HttpURLConnection.HTTP_CREATED] = "Created" // 201
        this[HttpURLConnection.HTTP_ACCEPTED] = "Accepted" // 202
        this[HttpURLConnection.HTTP_NOT_AUTHORITATIVE] = "Non-Authoritative Information" // 203
        this[HttpURLConnection.HTTP_NO_CONTENT] = "No Content" // 204
        this[HttpURLConnection.HTTP_RESET] = "Reset Content" // 205
        this[HttpURLConnection.HTTP_PARTIAL] = "Partial Content" // 206
        this[HttpURLConnection.HTTP_MULT_CHOICE] = "Multiple Choices" // 300
        this[HttpURLConnection.HTTP_MOVED_PERM] = "Moved Permanently" // 301
        this[HttpURLConnection.HTTP_MOVED_TEMP] = "Temporary Redirect" // 302
        this[HttpURLConnection.HTTP_SEE_OTHER] = "See Other" // 303
        this[HttpURLConnection.HTTP_NOT_MODIFIED] = "Not Modified" // 304
        this[HttpURLConnection.HTTP_USE_PROXY] = "Use Proxy" // 305
        this[HttpURLConnection.HTTP_BAD_REQUEST] = "Bad Request" // 400
        this[HttpURLConnection.HTTP_UNAUTHORIZED] = "Unauthorized" // 401
        this[HttpURLConnection.HTTP_PAYMENT_REQUIRED] = "Payment Required" // 402
        this[HttpURLConnection.HTTP_FORBIDDEN] = "Forbidden" // 403
        this[HttpURLConnection.HTTP_NOT_FOUND] = "Not Found" // 404
        this[HttpURLConnection.HTTP_BAD_METHOD] = "Method Not Allowed" // 405
        this[HttpURLConnection.HTTP_NOT_ACCEPTABLE] = "Not Acceptable" // 406
        this[HttpURLConnection.HTTP_PROXY_AUTH] = "Proxy Authentication Required" // 407
        this[HttpURLConnection.HTTP_CLIENT_TIMEOUT] = "Request Time-Out" // 408
        this[HttpURLConnection.HTTP_CONFLICT] = "Conflict" // 409
        this[HttpURLConnection.HTTP_GONE] = "Gone" // 410
        this[HttpURLConnection.HTTP_LENGTH_REQUIRED] = "Length Required" // 411
        this[HttpURLConnection.HTTP_PRECON_FAILED] = "Precondition Failed" // 412
        this[HttpURLConnection.HTTP_ENTITY_TOO_LARGE] = "Request Entity Too Large" // 413
        this[HttpURLConnection.HTTP_REQ_TOO_LONG] = "Request-URI Too Large" // 414
        this[HttpURLConnection.HTTP_UNSUPPORTED_TYPE] = "Unsupported Media Type" // 415
        this[HttpURLConnection.HTTP_INTERNAL_ERROR] = "Internal Server Error" // 500
        this[HttpURLConnection.HTTP_NOT_IMPLEMENTED] = "Not Implemented" // 501
        this[HttpURLConnection.HTTP_BAD_GATEWAY] = "Bad Gateway" // 502
        this[HttpURLConnection.HTTP_UNAVAILABLE] = "Service Unavailable" // 503
        this[HttpURLConnection.HTTP_GATEWAY_TIMEOUT] = "Gateway Timeout" // 504
        this[HttpURLConnection.HTTP_VERSION] = "HTTP Version Not Supported" // 505
    };

    fun getStatusMessage(code: Int?): String {
        return if (statusCodesList.contains(code)) statusCodesList[code] ?: "" else "Unknown"
    }

    private fun isServerRelatedFail(status: Int): Boolean {
        return status >= 500
    }

    fun monitorStatusCodes(): List<String>? {
        return SharedPrefsManager.customPrefs.getStringSet(Constants.MONITORING_STATUS_CODES, null)?.sorted()
    }

    private fun isMonitorStatusCode(status: Int): Boolean {
        val trackedStatusCodes = monitorStatusCodes()
        return trackedStatusCodes == null || trackedStatusCodes.contains("$status")
    }

    fun mayNotifyStatusFailure(status: Int): Boolean {
        return status != HttpURLConnection.HTTP_OK && isMonitorStatusCode(status)
    }

    fun Context.getStringNotWorking(url: String): String {
        return this.getString(R.string.not_working, url)
    }

    fun List<WebSiteStatus>.joinToStringDescription(): String {
        return this.joinToString(lineEnd) { status ->
            status.name
        }
    }

    fun List<WebSiteStatus>.joinedNames(): String {
        return this.joinToString(", ") { it.name }
    }

    fun groupedDownTitle(context: Context, items: List<WebSiteStatus>): String {
        return if (items.size >= 3) context.getString(R.string.all_monitors_down)
        else context.getString(R.string.n_sites_down, items.size, items.joinedNames())
    }

    fun groupedUpTitle(context: Context, items: List<WebSiteStatus>): String {
        return if (items.size >= 3) context.getString(R.string.all_monitors_up)
        else context.getString(R.string.n_sites_up, items.size, items.joinedNames())
    }

    fun resumeApp() {
        MyApplication.ActivityVisibility.resumeApp()
    }

    fun pauseApp() {
        MyApplication.ActivityVisibility.pauseApp()
    }

    fun appIsVisible(): Boolean {
        return MyApplication.ActivityVisibility.appIsVisible
    }

    fun String.removeUrlProto(): String = this.replace(Regex("""^http[s]?://"""), "")

    fun applyThemeMode(themeMode: Int = Constants.THEME_MODE_DARK) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }

    /**
     * Get current theme mode, migrating from old dark mode setting if needed
     */
    fun getCurrentThemeMode(): Int {
        val currentThemeMode = SharedPrefsManager.customPrefs.getInt(Constants.THEME_MODE, -1)

        // If new theme mode is not set, migrate from old dark mode setting
        if (currentThemeMode == -1) {
            val isDarkModeEnabled = SharedPrefsManager.customPrefs.getBoolean(Constants.IS_DARK_MODE_ENABLED, false)
            val migratedThemeMode = Constants.THEME_MODE_DARK
            SharedPrefsManager.customPrefs[Constants.THEME_MODE] = migratedThemeMode
            return migratedThemeMode
        }

        return currentThemeMode
    }

    /**
     * Extension function to handle deprecated getParcelableExtra for Android 33+
     */
    @Suppress("DEPRECATION")
    inline fun <reified T : android.os.Parcelable> Intent.getParcelableExtraCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            getParcelableExtra(key)
        }
    }

    /**
     * Open app settings for permission management
     */
    fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${activity.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings if specific app settings can't be opened
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
            activity.startActivity(fallbackIntent)
        }
    }
}