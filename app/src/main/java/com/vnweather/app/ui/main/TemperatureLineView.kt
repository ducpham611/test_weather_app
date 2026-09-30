package com.vnweather.app.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.vnweather.app.R

/**
 * One cell's slice of the temperature line under the hourly strip.
 *
 * Each hour draws a dot at its own temperature plus two half-segments: from
 * the left edge (at the midpoint between the previous hour and this one) to
 * the dot, and from the dot to the right edge (midpoint with the next hour).
 * Neighbouring cells meet at exactly the same height, so the strip reads as
 * one continuous line even though every cell is a separate RecyclerView item,
 * and it scrolls with the hours for free. Plain Canvas calls only, so it
 * works back to Android 5.
 */
class TemperatureLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.temp_line)
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.on_glass_primary)
    }

    private val dotRadius = 3.5f * density

    private var previous: Double? = null
    private var current = 0.0
    private var next: Double? = null
    private var min = 0.0
    private var max = 0.0

    fun setColors(line: Int, dot: Int) {
        linePaint.color = line
        dotPaint.color = dot
        invalidate()
    }

    fun setTemperatures(previous: Double?, current: Double, next: Double?, min: Double, max: Double) {
        this.previous = previous
        this.current = current
        this.next = next
        this.min = min
        this.max = max
        invalidate()
    }

    /** Warmer is higher. A flat day draws a flat line through the middle. */
    private fun yFor(temperature: Double): Float {
        val top = dotRadius + linePaint.strokeWidth
        val bottom = height - top
        val range = max - min
        if (range < 0.01) return (top + bottom) / 2f
        val fraction = ((max - temperature) / range).toFloat().coerceIn(0f, 1f)
        return top + fraction * (bottom - top)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = yFor(current)

        previous?.let {
            canvas.drawLine(0f, yFor((it + current) / 2.0), cx, cy, linePaint)
        }
        next?.let {
            canvas.drawLine(cx, cy, width.toFloat(), yFor((current + it) / 2.0), linePaint)
        }
        canvas.drawCircle(cx, cy, dotRadius, dotPaint)
    }
}
