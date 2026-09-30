package com.vnweather.app.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.vnweather.app.R

/**
 * Glassmorphism block background, without blur.
 *
 * Layers, bottom to top:
 *  1. a soft shadow, drawn only outside the block so it never shows through
 *     the see-through glass;
 *  2. a smoky tint that keeps white text readable on pale gradients;
 *  3. a light sheen that fades out from the top-left corner;
 *  4. a 1dp rim, bright at the top-left and faint towards the bottom-right,
 *     like light catching the edge of a pane.
 *
 * XML shapes cannot draw a gradient border, and custom drawables can only be
 * referenced from XML on API 24+, so this is applied in code. Everything is
 * plain Canvas work with shaders built once per size change: no blur, no
 * offscreen layers, nothing per-frame, so it scrolls as cheaply as a flat
 * colour on Android 5.
 */
class GlassDrawable(context: Context) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val radius = 22f * density
    private val rimWidth = 1f * density

    /** Space kept free at the bottom of the bounds for the shadow. */
    val shadowSpace = (4f * density).toInt()

    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.glass_fill)
    }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = rimWidth
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val sheenStart = ContextCompat.getColor(context, R.color.glass_sheen)
    private val rimBright = ContextCompat.getColor(context, R.color.glass_rim_bright)
    private val rimFaint = ContextCompat.getColor(context, R.color.glass_rim_faint)
    private val shadowColor = ContextCompat.getColor(context, R.color.glass_shadow)

    private val card = RectF()
    private val rim = RectF()
    private val shadow = RectF()
    private val cardPath = Path()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        card.set(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            (bounds.bottom - shadowSpace).toFloat()
        )
        rim.set(card)
        rim.inset(rimWidth / 2f, rimWidth / 2f)
        cardPath.reset()
        cardPath.addRoundRect(card, radius, radius, Path.Direction.CW)

        // Sheen fades to nothing a little past the middle of the diagonal.
        sheenPaint.shader = LinearGradient(
            card.left, card.top,
            card.left + card.width() * 0.6f, card.top + card.height() * 0.6f,
            sheenStart, sheenStart and 0x00FFFFFF,
            Shader.TileMode.CLAMP
        )
        rimPaint.shader = LinearGradient(
            card.left, card.top, card.right, card.bottom,
            intArrayOf(rimBright, rimFaint, rimFaint),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        if (card.isEmpty) return

        // 1. Shadow: a few stacked, offset copies, clipped to outside the card.
        canvas.save()
        @Suppress("DEPRECATION")
        canvas.clipPath(cardPath, Region.Op.DIFFERENCE)
        shadowPaint.color = shadowColor
        for (step in 1..3) {
            val dy = step * 1.2f * density
            shadow.set(card.left + step * 0.5f, card.top + dy, card.right - step * 0.5f, card.bottom + dy)
            canvas.drawRoundRect(shadow, radius, radius, shadowPaint)
        }
        canvas.restore()

        // 2-4. Tint, sheen, rim.
        canvas.drawRoundRect(card, radius, radius, tintPaint)
        canvas.drawRoundRect(card, radius, radius, sheenPaint)
        canvas.drawRoundRect(rim, radius, radius, rimPaint)
    }

    override fun setAlpha(alpha: Int) {
        tintPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        tintPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
