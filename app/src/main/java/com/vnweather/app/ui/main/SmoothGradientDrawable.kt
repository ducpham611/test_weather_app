package com.vnweather.app.ui.main

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Top-to-bottom gradient without the "line" of an XML three-colour gradient.
 *
 * An XML <gradient> with a centerColor blends in two straight sections that
 * meet at 50%. The rate of change jumps there and the eye sees a seam across
 * the middle of the screen. Here the colours follow one smooth curve (a
 * quadratic Bezier forced through the centre colour), sampled into many
 * stops, so there is no corner anywhere. Dithering is on so 8-bit and
 * 6-bit panels do not show stripes. A [wash] colour is drawn on top.
 *
 * The shader is rebuilt only when the size changes; drawing is one rect.
 */
class SmoothGradientDrawable(
    private val colors: IntArray,
    private val wash: Int = Color.TRANSPARENT
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val washPaint = Paint().apply { color = wash }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val stops = FloatArray(STEPS + 1) { it / STEPS.toFloat() }
        val sampled = IntArray(STEPS + 1) { colorAt(stops[it]) }
        paint.shader = LinearGradient(
            0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
            sampled, stops, Shader.TileMode.CLAMP
        )
    }

    private fun colorAt(t: Float): Int {
        if (colors.size == 1) return colors[0]
        if (colors.size == 2) return lerp(colors[0], colors[1], t)
        // Three colours: control point chosen so the curve passes through
        // the centre colour at t = 0.5.
        val a = colors[0]
        val m = colors[1]
        val b = colors[2]
        fun channel(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cm = (m shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            val control = 2f * cm - (ca + cb) / 2f
            val u = 1f - t
            val v = u * u * ca + 2f * u * t * control + t * t * cb
            return v.toInt().coerceIn(0, 255)
        }
        return Color.argb(channel(24), channel(16), channel(8), channel(0))
    }

    private fun lerp(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            return (ca + (cb - ca) * t).toInt().coerceIn(0, 255)
        }
        return Color.argb(ch(24), ch(16), ch(8), ch(0))
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, paint)
        if (Color.alpha(wash) > 0) canvas.drawRect(bounds, washPaint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    private companion object {
        const val STEPS = 24
    }
}
