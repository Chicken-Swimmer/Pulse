package app.pulse.monitor.ui.detail

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import app.pulse.monitor.R

class LatencyLineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var points: List<Long?> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_up)
        strokeWidth = 3f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_grid)
        strokeWidth = resources.displayMetrics.density
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val w = width.toFloat()
        for (i in 1..3) {
            val y = h * i / 4f
            canvas.drawLine(0f, y, w, y, grid)
        }
        val vals = points.mapNotNull { it?.toFloat() }
        if (vals.isEmpty()) return
        val max = (vals.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val step = if (points.size <= 1) w else w / (points.size - 1)
        path.reset()
        var started = false
        var lastX = 0f
        var lastY = 0f
        points.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val x = i * step
            val y = h - (v / max) * (h * 0.85f) - h * 0.08f
            lastX = x
            lastY = y
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
        if (vals.size == 1) {
            canvas.drawCircle(lastX, lastY, 4f * resources.displayMetrics.density, line)
        } else {
            canvas.drawPath(path, line)
        }
    }
}
