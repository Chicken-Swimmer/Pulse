package app.pulse.monitor.utils

object SitePause {

    private fun p() = SharedPrefsManager.customPrefs

    fun resumeAtKey(id: Long) = "site_pause_until_$id"
    fun winStartKey(id: Long) = "site_maint_start_$id"
    fun winEndKey(id: Long) = "site_maint_end_$id"
    fun winOnKey(id: Long) = "site_maint_on_$id"

    fun pauseUntil(id: Long, epochMs: Long) {
        p().edit().putLong(resumeAtKey(id), epochMs).apply()
    }

    fun pauseForMinutes(id: Long, minutes: Int) {
        pauseUntil(id, System.currentTimeMillis() + minutes * 60_000L)
    }

    fun clearTemp(id: Long) {
        p().edit().putLong(resumeAtKey(id), 0L).apply()
    }

    fun tempUntil(id: Long): Long = p().getLong(resumeAtKey(id), 0L)

    fun isTempPaused(id: Long): Boolean {
        val until = tempUntil(id)
        return until > 0L && System.currentTimeMillis() < until
    }

    fun tempExpired(id: Long): Boolean {
        val until = tempUntil(id)
        return until > 0L && System.currentTimeMillis() >= until
    }

    fun setWindow(id: Long, startMin: Int, endMin: Int, on: Boolean) {
        p().edit()
            .putBoolean(winOnKey(id), on)
            .putInt(winStartKey(id), startMin)
            .putInt(winEndKey(id), endMin)
            .apply()
    }

    fun windowOn(id: Long) = p().getBoolean(winOnKey(id), false)
    fun windowStart(id: Long) = p().getInt(winStartKey(id), 2 * 60)
    fun windowEnd(id: Long) = p().getInt(winEndKey(id), 4 * 60)

    fun inMaintenanceWindow(id: Long): Boolean {
        if (!windowOn(id)) return false
        val start = windowStart(id)
        val end = windowEnd(id)
        val cal = java.util.Calendar.getInstance()
        val now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        return if (start == end) false
        else if (start < end) now in start until end
        else now >= start || now < end
    }

    fun shouldSkipSite(id: Long?): Boolean {
        if (id == null) return false
        return isTempPaused(id) || inMaintenanceWindow(id)
    }
}
