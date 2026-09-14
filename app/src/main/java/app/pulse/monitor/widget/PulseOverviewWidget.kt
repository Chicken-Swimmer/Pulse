package app.pulse.monitor.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.pulse.monitor.R
import app.pulse.monitor.data.repository.WebSiteEntryRepository
import app.pulse.monitor.ui.home.MainActivity
import app.pulse.monitor.utils.Constants
import app.pulse.monitor.utils.SharedPrefsManager
import app.pulse.monitor.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

open class PulseOverviewWidget : AppWidgetProvider() {
    protected open val layoutId: Int = R.layout.widget_pulse
    protected open val compact: Boolean = false

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        bind(context, appWidgetManager, appWidgetIds, layoutId, compact)
    }

    companion object {
        fun bind(context: Context, mgr: AppWidgetManager, ids: IntArray, layout: Int, compact: Boolean) {
            val list = runBlocking(Dispatchers.IO) {
                WebSiteEntryRepository(context.applicationContext).getAllEntriesDirect()
            }
            val active = list.count { !it.isPaused }
            val up = list.count { !it.isPaused && it.status in 200..299 }
            val down = list.count { !it.isPaused && it.status != null && it.status !in 200..299 }
            val paused = list.count { it.isPaused }
            val pct = if (active == 0) 100 else (up * 100) / active
            val lastMs = SharedPrefsManager.customPrefs.getLong(Constants.LAST_GLOBAL_CHECK_MS, 0L)
            val lastLabel = context.getString(R.string.last_checked_fmt, Utils.formatRelativeTime(context, lastMs))
            val downNames = list.filter { !it.isPaused && it.status != null && it.status !in 200..299 }.map { it.name }
            val downLine = when {
                downNames.isEmpty() -> null
                downNames.size <= 3 -> downNames.joinToString(", ")
                else -> downNames.take(3).joinToString(", ") + " +" + (downNames.size - 3)
            }
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, layout)
                views.setTextViewText(R.id.widgetPercent, "$pct%")
                if (compact) {
                    views.setTextViewText(R.id.widgetSub, "$up ↑    $down ↓")
                    views.setTextViewText(R.id.widgetChecked, lastLabel)
                } else {
                    views.setTextViewText(
                        R.id.widgetSub,
                        downLine ?: context.getString(R.string.widget_up_of, up, active)
                    )
                    views.setTextViewText(R.id.widgetUp, context.getString(R.string.overview_up, up))
                    views.setTextViewText(R.id.widgetDown, context.getString(R.string.overview_down, down))
                    views.setTextViewText(R.id.widgetPaused, context.getString(R.string.overview_paused, paused))
                    views.setTextViewText(R.id.widgetChecked, lastLabel)
                }
                views.setOnClickPendingIntent(R.id.widgetRoot, open)
                mgr.updateAppWidget(id, views)
            }
        }

        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            data class Spec(val cls: Class<out AppWidgetProvider>, val layout: Int, val compact: Boolean)
            listOf(
                Spec(PulseOverviewWidget::class.java, R.layout.widget_pulse, false),
                Spec(PulseNeutralWidget::class.java, R.layout.widget_pulse_neutral, false),
                Spec(PulseSmallWidget::class.java, R.layout.widget_pulse_small, true),
                Spec(PulseSmallNeutralWidget::class.java, R.layout.widget_pulse_small_neutral, true)
            ).forEach { spec ->
                val ids = mgr.getAppWidgetIds(ComponentName(context, spec.cls))
                if (ids.isNotEmpty()) bind(context, mgr, ids, spec.layout, spec.compact)
            }
        }
    }
}

class PulseNeutralWidget : PulseOverviewWidget() {
    override val layoutId = R.layout.widget_pulse_neutral
}

class PulseSmallWidget : PulseOverviewWidget() {
    override val layoutId = R.layout.widget_pulse_small
    override val compact = true
}

class PulseSmallNeutralWidget : PulseOverviewWidget() {
    override val layoutId = R.layout.widget_pulse_small_neutral
    override val compact = true
}
