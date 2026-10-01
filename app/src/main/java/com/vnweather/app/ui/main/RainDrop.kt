package com.vnweather.app.ui.main

import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Sets how full the rain-chance drop beside a percentage looks.
 *
 * The chance is rounded to the nearest quarter, so the drop shows one of
 * five clear steps: empty, 1/4, 1/2, 3/4 or full (e.g. 1% is empty, 90% is
 * full). At 12-20dp finer steps would not be readable; the exact number is
 * right next to it anyway.
 */
object RainDrop {

    // The drop shape spans y = 2.2 .. 20.6 of its 24-unit icon, so the fill
    // is mapped onto that span rather than the whole icon height.
    private const val DROP_TOP = 2.2f / 24f
    private const val DROP_BOTTOM = 20.6f / 24f

    fun quarters(percent: Int): Int = (percent.coerceIn(0, 100) / 25f).roundToInt()

    /** Drawable level (0..10000) for a chance, used by the clip layer. */
    fun level(percent: Int): Int {
        val q = quarters(percent)
        if (q == 0) return 0
        if (q == 4) return 10_000
        val filled = (1f - DROP_BOTTOM) + (q / 4f) * (DROP_BOTTOM - DROP_TOP)
        return (filled * 10_000).roundToInt()
    }

    fun apply(view: TextView, percent: Int) {
        val level = level(percent)
        view.compoundDrawablesRelative.forEach { it?.level = level }
        view.compoundDrawables.forEach { it?.level = level }
    }
}
