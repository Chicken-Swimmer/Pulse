package app.pulse.monitor.utils

import java.util.Calendar

object PauseUntil {

    fun isActive(): Boolean {
        val until = SharedPrefsManager.customPrefs.getLong(Constants.PAUSE_UNTIL_MS, 0L)
        if (until <= 0L) return false
        if (System.currentTimeMillis() >= until) {
            SharedPrefsManager.customPrefs.edit().putLong(Constants.PAUSE_UNTIL_MS, 0L).apply()
            return false
        }
        return true
    }

    fun untilMs(): Long = SharedPrefsManager.customPrefs.getLong(Constants.PAUSE_UNTIL_MS, 0L)

    fun clear() {
        SharedPrefsManager.customPrefs.edit().putLong(Constants.PAUSE_UNTIL_MS, 0L).apply()
    }

    fun pauseUntilMorning() {
        SharedPrefsManager.customPrefs.edit()
            .putLong(Constants.PAUSE_UNTIL_MS, nextMorningMs())
            .apply()
    }

    fun morningHour(): Int {
        val stored = SharedPrefsManager.customPrefs.getInt(Constants.PAUSE_MORNING_HOUR, -1)
        if (stored in 0..23) return stored
        return if (SharedPrefsManager.customPrefs.getBoolean(Constants.QUIET_HOURS_ENABLED, false)) {
            SharedPrefsManager.customPrefs.getInt(Constants.QUIET_HOURS_END, 7)
        } else 7
    }

    fun morningMinute(): Int {
        return SharedPrefsManager.customPrefs.getInt(Constants.PAUSE_MORNING_MINUTE, 0).coerceIn(0, 59)
    }

    fun setMorningHour(hour: Int) {
        setMorningTime(hour, morningMinute())
    }

    fun setMorningTime(hour: Int, minute: Int) {
        SharedPrefsManager.customPrefs.edit()
            .putInt(Constants.PAUSE_MORNING_HOUR, hour.coerceIn(0, 23))
            .putInt(Constants.PAUSE_MORNING_MINUTE, minute.coerceIn(0, 59))
            .apply()
    }

    fun nextMorningMs(): Long {
        val hour = morningHour().coerceIn(0, 23)
        val minute = morningMinute()
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val targetMin = hour * 60 + minute
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (nowMin >= targetMin) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        return cal.timeInMillis
    }

    fun labelHour(): String = "%02d:%02d".format(morningHour(), morningMinute())
}
