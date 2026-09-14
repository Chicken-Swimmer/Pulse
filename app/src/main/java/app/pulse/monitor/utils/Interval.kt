package app.pulse.monitor.utils

object Interval {
    // Minutes. WorkManager will not run more often than 15 min in the background.
    val valueList = arrayOf(
        15, 20, 30, 45, 60,
        60 * 2, 60 * 3, 60 * 4, 60 * 6,
        60 * 12, 60 * 24, 60 * 24 * 7
    )
    val nameList = arrayOf(
        "Every 15 minutes",
        "Every 20 minutes",
        "Every 30 minutes",
        "Every 45 minutes",
        "Every hour",
        "Every 2 hours",
        "Every 3 hours",
        "Every 4 hours",
        "Every 6 hours",
        "Every 12 hours",
        "Once a day",
        "Once a week"
    )

    fun labelForMinutes(minutes: Int): String {
        val idx = valueList.indexOf(minutes)
        if (idx >= 0) return nameList[idx]
        return when {
            minutes < 60 -> "Every $minutes minutes"
            minutes % 60 == 0 && minutes < 60 * 24 -> {
                val h = minutes / 60
                if (h == 1) "Every hour" else "Every $h hours"
            }
            minutes % (60 * 24) == 0 -> {
                val d = minutes / (60 * 24)
                if (d == 1) "Once a day" else "Every $d days"
            }
            else -> "Every $minutes minutes"
        }
    }
}
