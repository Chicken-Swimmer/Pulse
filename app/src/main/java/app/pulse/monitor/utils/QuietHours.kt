package app.pulse.monitor.utils

import java.util.Calendar

object QuietHours {
    fun isActive(): Boolean {
        if (!SharedPrefsManager.customPrefs.getBoolean(Constants.QUIET_HOURS_ENABLED, false)) return false
        val start = SharedPrefsManager.customPrefs.getInt(Constants.QUIET_HOURS_START, 22)
        val end = SharedPrefsManager.customPrefs.getInt(Constants.QUIET_HOURS_END, 7)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (start == end) false
        else if (start < end) hour in start until end
        else hour >= start || hour < end
    }

    fun label(): String {
        val start = SharedPrefsManager.customPrefs.getInt(Constants.QUIET_HOURS_START, 22)
        val end = SharedPrefsManager.customPrefs.getInt(Constants.QUIET_HOURS_END, 7)
        return "%02d:00 – %02d:00".format(start, end)
    }
}
