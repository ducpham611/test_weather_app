package com.vnweather.app.ui.main

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.vnweather.app.R

/**
 * The "Simple (light / dark)" background option, in the style of the Rain
 * weather app: a solid background, solid blue-grey cards, dark text in the
 * light theme and light text in the dark theme.
 *
 * Layouts are written for the see-through glass look (white text with a
 * shadow). Rather than duplicating every layout, this walks a view tree once
 * and swaps each glass colour for its Simple counterpart. MainActivity
 * recreates itself when switching to or from Simple, so nothing ever has to
 * be switched back.
 */
class SimpleStyle(context: Context) {

    private val glassPrimary = ContextCompat.getColor(context, R.color.on_glass_primary)
    private val glassSecondary = ContextCompat.getColor(context, R.color.on_glass_secondary)
    private val glassRain = ContextCompat.getColor(context, R.color.rain_on_glass)

    val primary = ContextCompat.getColor(context, R.color.simple_text_primary)
    val secondary = ContextCompat.getColor(context, R.color.simple_text_secondary)
    val rain = ContextCompat.getColor(context, R.color.simple_rain)
    val line = ContextCompat.getColor(context, R.color.simple_line)

    /** Recolours every TextView under [root] and drops the glass text shadow. */
    fun restyle(root: View) {
        when (root) {
            is TextView -> restyleText(root)
            is TemperatureLineView -> root.setColors(line, primary)
            is ViewGroup -> for (i in 0 until root.childCount) restyle(root.getChildAt(i))
        }
    }

    private fun restyleText(view: TextView) {
        val newColor = when (view.currentTextColor) {
            glassPrimary -> primary
            glassSecondary -> secondary
            glassRain -> rain
            else -> return
        }
        view.setTextColor(newColor)
        view.setShadowLayer(0f, 0f, 0f, 0)
        // Icons beside the text (detail row icons, rain droplet) follow the
        // text colour. TextViewCompat covers API 21-22 via AppCompatTextView.
        if (view.compoundDrawablesRelative.any { it != null } ||
            view.compoundDrawables.any { it != null }
        ) {
            TextViewCompat.setCompoundDrawableTintList(view, ColorStateList.valueOf(newColor))
        }
    }
}
