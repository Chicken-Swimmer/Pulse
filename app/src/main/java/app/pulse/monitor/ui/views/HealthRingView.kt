package app.pulse.monitor.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View

class HealthRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val box = RectF()
    var percent: Int = 100
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val stroke = w * 0.09f
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        val pad = stroke
        box.set(pad, pad, w - pad, h - pad)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sweep = 360f * (percent / 100f)
        track.color = 0xFF16302E.toInt()
        canvas.drawArc(box, -90f, 360f, false, track)
        if (sweep <= 0f) return

        val cx = width / 2f
        val cy = height / 2f
        val t = percent / 100f
        // Low = red, mid = amber, high = bright cyan
        val start: Int
        val end: Int
        when {
            t < 0.5f -> {
                val u = t / 0.5f
                start = lerpColor(0xFF7F1D1D.toInt(), 0xFFB45309.toInt(), u)
                end = lerpColor(0xFFEF4444.toInt(), 0xFFF59E0B.toInt(), u)
            }
            else -> {
                val u = (t - 0.5f) / 0.5f
                start = lerpColor(0xFF0F766E.toInt(), 0xFF14B8A6.toInt(), u)
                end = lerpColor(0xFF2DD4BF.toInt(), 0xFF99F6E4.toInt(), u)
            }
        }
        canvas.save()
        canvas.rotate(-90f, cx, cy)
        arc.shader = SweepGradient(
            cx, cy,
            intArrayOf(start, end, end),
            floatArrayOf(0f, t.coerceAtLeast(0.04f), 1f)
        )
        canvas.drawArc(box, 0f, sweep, false, arc)
        canvas.restore()
        arc.shader = null
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val av = (a shr shift) and 0xFF
            val bv = (b shr shift) and 0xFF
            return (av + ((bv - av) * f)).toInt()
        }
        return 0xFF000000.toInt() or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
