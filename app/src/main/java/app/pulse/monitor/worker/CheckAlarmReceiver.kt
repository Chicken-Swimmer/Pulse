package app.pulse.monitor.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CheckAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != CheckAlarmScheduler.ACTION_CHECK &&
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        CheckAlarmScheduler.runNow(context)
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            CheckAlarmScheduler.scheduleNext(context)
        }
    }
}
