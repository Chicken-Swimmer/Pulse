package app.pulse.monitor.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.pulse.monitor.utils.Constants
import app.pulse.monitor.utils.PauseUntil
import app.pulse.monitor.utils.SharedPrefsManager
import app.pulse.monitor.utils.Utils
import java.util.concurrent.TimeUnit

object CheckAlarmScheduler {

    const val ACTION_CHECK = "app.pulse.monitor.ACTION_ALARM_CHECK"
    private const val REQUEST_CODE = 4101
    private const val NEXT_WORK = "PulseNextCheck"

    fun scheduleNext(context: Context) {
        val intervalMs = Utils.getMonitorInterval().coerceAtLeast(15L) * 60_000L
        val last = SharedPrefsManager.customPrefs.getLong(Constants.LAST_GLOBAL_CHECK_MS, 0L)
        val now = System.currentTimeMillis()
        val pauseUntil = PauseUntil.untilMs().takeIf { PauseUntil.isActive() }
        val target = when {
            pauseUntil != null -> pauseUntil
            last > 0L -> last + intervalMs
            else -> now + intervalMs
        }
        val triggerAt = maxOf(now + 30_000L, target)
        val delayMs = (triggerAt - now).coerceAtLeast(30_000L)

        scheduleAlarm(context, triggerAt)
        scheduleDelayedWork(context, delayMs)
    }

    fun runNow(context: Context) {
        val work = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            NEXT_WORK,
            ExistingWorkPolicy.REPLACE,
            work
        )
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pendingIntent(context))
        WorkManager.getInstance(context).cancelUniqueWork(NEXT_WORK)
    }

    private fun scheduleDelayedWork(context: Context, delayMs: Long) {
        val work = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            NEXT_WORK,
            ExistingWorkPolicy.REPLACE,
            work
        )
    }

    private fun scheduleAlarm(context: Context, triggerAt: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pendingIntent(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CheckAlarmReceiver::class.java).setAction(ACTION_CHECK)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
