package app.pulse.monitor.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager

object NetworkUtils {

    fun isConnected(context: Context): Boolean = hasUsableLink(context)

    fun isAirplaneModeOn(context: Context): Boolean {
        return android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.AIRPLANE_MODE_ON,
            0
        ) != 0
    }

    /**
     * A path the user actually turned on:
     *   Wi-Fi associated, ethernet, or mobile *data* toggle + cellular.
     * Airplane + Wi-Fi still checks. Wi-Fi off + mobile data off = skip,
     * even if Android still lists VPN or a cellular radio.
     */
    fun hasUsableLink(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            return cm.activeNetworkInfo?.isConnected == true
        }
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val ethernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

        if (isAirplaneModeOn(context)) {
            return wifi || ethernet
        }
        if (wifi || ethernet) return true

        val mobileData = isMobileDataEnabled(context)
        val cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        return mobileData && cellular
    }

    fun isMobileDataEnabled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                tm.isDataEnabled
            } else {
                android.provider.Settings.Global.getInt(
                    context.contentResolver,
                    "mobile_data",
                    1
                ) == 1
            }
        } catch (_: Exception) {
            try {
                android.provider.Settings.Global.getInt(
                    context.contentResolver,
                    "mobile_data",
                    1
                ) == 1
            } catch (_: Exception) {
                false
            }
        }
    }

    fun shouldSkipRemoteCheck(context: Context): Boolean {
        return !hasUsableLink(context)
    }
}
