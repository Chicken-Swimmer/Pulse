package app.pulse.monitor.utils

import android.util.Log
import app.pulse.monitor.utils.Constants.TAG_GLOBAL

object Print {
    fun log(msg : String) {
        Log.e(TAG_GLOBAL, msg)
    }
}