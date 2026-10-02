package com.vnweather.app.ui.main

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.vnweather.app.R
import com.vnweather.app.databinding.ItemDailyBinding
import com.vnweather.app.domain.DailyItem
import com.vnweather.app.domain.WeatherCodeMapper
import com.vnweather.app.util.Formatters
import com.vnweather.app.util.LocaleHelper

/**
 * Day rows drawn straight into a LinearLayout instead of a RecyclerView.
 *
 * A RecyclerView nested in the page ScrollView kept its own scroll region, so
 * the third row was cut off and "See more days" expanded into a scrollable
 * box rather than growing the page. With plain child views the section simply
 * gets taller and the whole page scrolls, which is what the design wants. The
 * list is at most 7 rows, so recycling buys nothing here.
 */
class DailyListRenderer(private val container: LinearLayout) {

    private val inflater = LayoutInflater.from(container.context)

    var temperatureUnit: String = "celsius"

    /** Set by MainActivity for the "Simple" background option only. */
    var simpleStyle: SimpleStyle? = null

    fun render(days: List<DailyItem>) {
        // Reuse rows already inflated; add or trim only the difference.
        while (container.childCount > days.size) {
            container.removeViewAt(container.childCount - 1)
        }
        while (container.childCount < days.size) {
            val binding = ItemDailyBinding.inflate(inflater, container, false)
            binding.root.tag = binding
            simpleStyle?.restyle(binding.root)
            container.addView(binding.root)
        }

        days.forEachIndexed { index, item ->
            val binding = container.getChildAt(index).tag as ItemDailyBinding
            bind(binding, item, isToday = index == 0)
            alignColumns(binding)
        }

        container.requestLayout()
    }

    private fun bind(binding: ItemDailyBinding, item: DailyItem, isToday: Boolean) {
        val context = binding.root.context
        val locale = LocaleHelper.currentLocale()

        binding.textDay.text = if (isToday) {
            context.getString(R.string.today)
        } else {
            Formatters.dayLabel(item.dateIso, locale)
        }

        binding.imageIcon.setImageResource(WeatherCodeMapper.iconRes(item.weatherCode, true))
        binding.textCondition.setText(WeatherCodeMapper.descriptionRes(item.weatherCode))
        binding.textTempMax.text = Formatters.temperatureShort(item.tempMax)
        binding.textTempMin.text = Formatters.temperatureShort(item.tempMin)

        val pop = item.precipitationProbabilityMax
        if (pop != null && pop > 0) {
            binding.textRain.text = context.getString(R.string.percent_format, pop)
            RainDrop.apply(binding.textRain, pop)
            binding.textRain.visibility = View.VISIBLE
        } else {
            binding.textRain.visibility = View.INVISIBLE
        }
    }

    /**
     * Lays out the rain column so that
     *  - every drop sits at the same x (a straight vertical line), with the
     *    number right after it, and
     *  - the gap from a two-digit rain value ("46%") to the low temperature
     *    equals the gap between the low and high temperatures.
     *
     * Measured with the real text paints, so it stays right with any font,
     * font size setting or language. "100%" still fits: if the column needs
     * more room, it takes it from the blank space in front of the low
     * temperature (which is right-aligned), so the low / high columns and the
     * header above them do not move.
     */
    private fun alignColumns(binding: ItemDailyBinding) {
        val density = binding.root.resources.displayMetrics.density
        val rain = binding.textRain
        val minView = binding.textTempMin
        val maxView = binding.textTempMax

        val minColumn = MIN_COLUMN_DP * density
        val maxColumn = MAX_COLUMN_DP * density
        val minText = minView.paint.measureText(REF_TEMP)
        val maxText = maxView.paint.measureText(REF_TEMP)
        val gap = (maxColumn - maxText).coerceAtLeast(4 * density)

        val drop = rain.compoundDrawablesRelative[0]?.intrinsicWidth
            ?: rain.compoundDrawables[0]?.intrinsicWidth ?: 0
        val lead = rain.paddingStart + drop + rain.compoundDrawablePadding
        val twoDigits = lead + rain.paint.measureText(REF_RAIN_2)
        val threeDigits = lead + rain.paint.measureText(REF_RAIN_3) + rain.paddingEnd

        // Rain column ends where the low-temperature column starts, so a
        // two-digit value ends (rainWidth - twoDigits) + (minColumn - minText)
        // before the low temperature's text. Solve for the wanted gap.
        var minWidth = minColumn
        var rainWidth = twoDigits + gap - (minColumn - minText)
        if (rainWidth < threeDigits) {
            minWidth = (minColumn - (threeDigits - rainWidth)).coerceAtLeast(minText)
            rainWidth = threeDigits
        }

        val width = kotlin.math.ceil(rainWidth).toInt()
        if (rain.layoutParams.width != width) {
            rain.layoutParams = rain.layoutParams.apply { this.width = width }
        }
        minView.minWidth = minWidth.toInt()
    }

    private companion object {
        /** Must match android:minWidth of textTempMin / textTempMax (and the header). */
        const val MIN_COLUMN_DP = 40
        const val MAX_COLUMN_DP = 44

        const val REF_TEMP = "00°"
        const val REF_RAIN_2 = "00%"
        const val REF_RAIN_3 = "100%"
    }
}
