package app.pulse.monitor.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.pulse.monitor.worker.SyncWorker

object NetworkRestore {

    fun register(context: Context) {
        val app = context.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val had = NetworkUtils.hasUsableLink(app)
        SharedPrefsManager.customPrefs.edit().putBoolean(Constants.LAST_LINK_USABLE, had).apply()
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val now = NetworkUtils.hasUsableLink(app)
                    val was = SharedPrefsManager.customPrefs.getBoolean(Constants.LAST_LINK_USABLE, false)
                    SharedPrefsManager.customPrefs.edit().putBoolean(Constants.LAST_LINK_USABLE, now).apply()
                    if (!was && now && !PauseUntil.isActive()) {
                        val work = OneTimeWorkRequestBuilder<SyncWorker>().build()
                        WorkManager.getInstance(app).enqueueUniqueWork(
                            "pulse_net_restore",
                            ExistingWorkPolicy.KEEP,
                            work
                        )
                    }
                }

                override fun onLost(network: Network) {
                    SharedPrefsManager.customPrefs.edit()
                        .putBoolean(Constants.LAST_LINK_USABLE, NetworkUtils.hasUsableLink(app))
                        .apply()
                }
            })
        } catch (_: Exception) {
        }
    }
}
