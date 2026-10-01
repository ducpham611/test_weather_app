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
}
