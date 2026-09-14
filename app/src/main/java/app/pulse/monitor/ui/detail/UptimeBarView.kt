package app.pulse.monitor.ui.detail

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import app.pulse.monitor.R

class UptimeBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var slots: List<Boolean?> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val up = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_up)
    }
    private val down = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_down)
    }
    private val skip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_skip)
    }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (slots.isEmpty()) return
        val gap = 3f * resources.displayMetrics.density
        val n = slots.size
        val w = ((width - gap * (n - 1)) / n).coerceAtLeast(2f)
        val h = height.toFloat()
        slots.forEachIndexed { i, v ->
            val left = i * (w + gap)
            rect.set(left, 0f, left + w, h)
            val paint = when (v) {
                true -> up
                false -> down
                null -> skip
            }
            canvas.drawRoundRect(rect, w / 2f, w / 2f, paint)
        }
    }
}
