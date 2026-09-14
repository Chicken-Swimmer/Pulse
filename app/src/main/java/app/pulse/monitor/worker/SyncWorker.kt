package app.pulse.monitor.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.pulse.monitor.R
import app.pulse.monitor.data.repository.WebSiteEntryRepository
import app.pulse.monitor.utils.NetworkUtils
import app.pulse.monitor.utils.Print
import app.pulse.monitor.utils.Utils
import app.pulse.monitor.utils.Utils.getStringNotWorking
import app.pulse.monitor.utils.Utils.joinToStringDescription

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Always run the check. Skipping while the UI is open froze
        // "last checked" for anyone who left Pulse on screen.

        val applicationContext: Context = applicationContext
        val repository = WebSiteEntryRepository(applicationContext)

        Print.log("Fetching Data from Remote hosts...")
        return try {
            if (app.pulse.monitor.utils.PauseUntil.isActive()) {
                Print.log("Skipping remote checks: paused until morning")
                return Result.success()
            }
            if (NetworkUtils.shouldSkipRemoteCheck(applicationContext)) {
                Print.log("Skipping remote checks: airplane mode or no network")
                return Result.success()
            }
            val results = repository.checkWebSiteStatus()
            app.pulse.monitor.widget.PulseOverviewWidget.refresh(applicationContext)
            if (NetworkUtils.shouldSkipRemoteCheck(applicationContext)) {
                Print.log("Suppressing alerts: no usable link after check")
                return Result.success()
            }
            val newlyDown = results.filter { it.notifyDown }
            val newlyUp = results.filter { it.notifyUp }

            if (newlyDown.size == 1) {
                val entry = newlyDown.first()
                Utils.showNotification(
                    applicationContext,
                    entry.name,
                    Utils.downNotificationMessage(applicationContext, entry.url, entry.downSince),
                    websiteId = entry.id
                )
            } else if (newlyDown.size > 1) {
                Utils.showNotification(
                    applicationContext,
                    Utils.groupedDownTitle(applicationContext, newlyDown),
                    newlyDown.joinToStringDescription(),
                    notifyId = 9101
                )
            }

            if (newlyUp.size == 1) {
                val entry = newlyUp.first()
                val duration = entry.downSince?.let { Utils.formatDuration(applicationContext, it) }
                val body = if (duration != null) {
                    applicationContext.getString(R.string.site_back_up_after, entry.url, duration)
                } else {
                    applicationContext.getString(R.string.site_back_up, entry.url)
                }
                Utils.showNotification(applicationContext, entry.name, body)
            } else if (newlyUp.size > 1) {
                Utils.showNotification(
                    applicationContext,
                    Utils.groupedUpTitle(applicationContext, newlyUp),
                    newlyUp.joinToStringDescription(),
                    notifyId = 9102
                )
            }
            Result.success()
        } catch (e: Throwable) {
            e.printStackTrace()
            Print.log("Error fetching data : $e")
            Result.failure()
        } finally {
            CheckAlarmScheduler.scheduleNext(applicationContext)
        }
    }
}
