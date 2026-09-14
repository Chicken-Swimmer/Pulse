package app.pulse.monitor.utils

object Accent {
    const val TEAL = 0
    const val BLUE = 1
    const val AMBER = 2
    const val RED = 3

    fun mode(): Int = SharedPrefsManager.customPrefs.getInt(Constants.ACCENT_MODE, TEAL)

    fun setMode(v: Int) {
        SharedPrefsManager.customPrefs.edit().putInt(Constants.ACCENT_MODE, v).apply()
    }

    fun color(): Int = when (mode()) {
        BLUE -> 0xFF38BDF8.toInt()
        AMBER -> 0xFFFBBF24.toInt()
        RED -> 0xFFF87171.toInt()
        else -> 0xFF2DD4BF.toInt()
    }

    fun colorSoft(): Int = when (mode()) {
        BLUE -> 0xFF7DD3FC.toInt()
        AMBER -> 0xFFFCD34D.toInt()
        RED -> 0xFFFCA5A5.toInt()
        else -> 0xFF5EEAD4.toInt()
    }
}
