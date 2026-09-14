package app.pulse.monitor.worker

import android.content.Context
import androidx.work.*
import app.pulse.monitor.utils.Constants.TAG_WORK_MANAGER
import app.pulse.monitor.utils.Utils
import java.util.concurrent.TimeUnit

object WorkManagerScheduler {

    fun refreshPeriodicWork(context: Context, replace: Boolean = false) {
        // No network constraint: WorkManager's CONNECTED often means
        // "validated internet", which blocks LAN / Tailscale. SyncWorker
        // itself skips (without marking down) when the phone has no link.

        // Android PeriodicWorkRequest minimum is 15 minutes.
        // Flex 5 min: run in the last 5 minutes of each period instead of
        // anywhere in the whole interval (default flex == interval).
        val intervalMin = Utils.getMonitorInterval().coerceAtLeast(15)
        val flexMin = 1L.coerceAtMost(intervalMin)
        val refreshCpnWork = PeriodicWorkRequest
            .Builder(
                SyncWorker::class.java,
                intervalMin, TimeUnit.MINUTES,
                flexMin, TimeUnit.MINUTES
            )
            .addTag(TAG_WORK_MANAGER)
            .build()

        // UPDATE keeps the existing next-run when the app just re-asserts the job.
        // REPLACE when the user changes the interval so the new period starts now.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TAG_WORK_MANAGER,
            if (replace) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.UPDATE,
            refreshCpnWork
        )
    }
}