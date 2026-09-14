package app.pulse.monitor.utils

object Constants {
    const val INTENT_OBJECT = "intent_object"
    const val INTENT_CREATE_ENTRY = 1
    const val INTENT_UPDATE_ENTRY = 2
    const val DEFAULT_INTERVAL_MIN = 60

    const val IS_SCHEDULED: String = "is_scheduled"
    const val TAG_GLOBAL: String = "Pulse ##--> "
    const val TAG_WORK_MANAGER: String = "PulseWorkManager"

    const val NOTIFICATION_CHANNEL_ID = "WEB_SITE_MONITOR_CHANNEL_ID"
    const val NOTIFICATION_CHANNEL_NAME = "Pulse"
    const val NOTIFICATION_CHANNEL_DESCRIPTION = "Alerts when a site goes down or comes back up."

    const val IS_ADDED_DEFAULT_DATA: String = "is_added_default_data"
    const val MONITORING_INTERVAL: String = "monitoring_interval"
    const val LAST_GLOBAL_CHECK_MS: String = "last_global_check_ms"
    const val MONITORING_STATUS_CODES: String = "monitoring_status_codes"
    const val RETRY_COUNT: String = "retry_count"
    const val NOTIFY_ON_RECOVERY: String = "notify_on_recovery"
    const val DEFAULT_RETRY_COUNT: Int = 1
    const val HISTORY_RETENTION_DAYS: Int = 30
    const val EXTRA_WEBSITE_ID: String = "extra_website_id"
    const val EXTRA_WEBSITE_NAME: String = "extra_website_name"
    const val IS_AUTO_START_SHOWN : String = "is_auto_start_shown"

    const val IS_DARK_MODE_ENABLED : String = "is_dark_mode_enabled" // Deprecated - use THEME_MODE instead
    const val THEME_MODE : String = "theme_mode"

    // Theme mode values
    const val THEME_MODE_LIGHT = 0
    const val THEME_MODE_DARK = 1
    const val THEME_MODE_SYSTEM = 2

    const val MATERIAL_YOU_ENABLED = "material_you_enabled"
    fun uptimeResetKey(id: Long) = "uptime_reset_$id"

    const val PAUSE_UNTIL_MS = "pause_until_ms"
    const val PAUSE_MORNING_HOUR = "pause_morning_hour"
    const val PAUSE_MORNING_MINUTE = "pause_morning_minute"
    const val ACCENT_MODE = "accent_mode"
    const val RELIABLE_BACKGROUND = "reliable_background"
    const val LAST_LINK_USABLE = "last_link_usable"
    const val CERT_WARN_DAYS = 14
    const val QUIET_HOURS_ENABLED = "quiet_hours_enabled"
    const val QUIET_HOURS_START = "quiet_hours_start"
    const val QUIET_HOURS_END = "quiet_hours_end"

    const val REQUEST_TIMEOUT_MS = "request_timeout_ms"
    const val DEFAULT_TIMEOUT_MS = 10000
    const val NOTIFY_SOUND = "notify_sound"
    const val NOTIFY_VIBRATE = "notify_vibrate"
    const val SORT_MODE = "sort_mode"
    const val SORT_PRIORITY = 0
    const val SORT_MANUAL = 1
}

