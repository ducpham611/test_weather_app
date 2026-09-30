package com.vnweather.app.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vnweather.app.R
import com.vnweather.app.databinding.ItemHourlyBinding
import com.vnweather.app.domain.HourlyItem
import com.vnweather.app.domain.WeatherCodeMapper
import com.vnweather.app.util.Formatters
import com.vnweather.app.util.LocaleHelper

/**
 * Horizontal strip of hourly entries, pre-trimmed to the next 24 hours.
 * That is well inside the ECMWF IFS HRES native hourly window, so every cell
 * is real model output rather than interpolation.
 *
 * Cells size themselves to their content (with a 64dp minimum) so a larger
 * system font widens them instead of wrapping the text.
 */
class HourlyAdapter : ListAdapter<HourlyItem, HourlyAdapter.VH>(DIFF) {

    var temperatureUnit: String = "celsius"

    /** Set by MainActivity for the "Simple" background option only. */
    var simpleStyle: SimpleStyle? = null

    /** Coldest and warmest hour in the list, the temperature line's scale. */
    private var minTemp = 0.0
    private var maxTemp = 0.0

    /**
     * Use this instead of submitList. Each cell's slice of the temperature
     * line depends on its neighbours and on the whole list's range, which
     * DiffUtil cannot see, so every visible cell is rebound after the diff.
     */
    fun submitHours(hours: List<HourlyItem>, onCommitted: () -> Unit) {
        minTemp = hours.minOfOrNull { it.temperature } ?: 0.0
        maxTemp = hours.maxOfOrNull { it.temperature } ?: 0.0
        submitList(hours) {
            notifyItemRangeChanged(0, itemCount)
            onCommitted()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemHourlyBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        simpleStyle?.restyle(binding.root)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), position == 0)
        holder.bindLine(
            previous = if (position > 0) getItem(position - 1).temperature else null,
            current = getItem(position).temperature,
            next = if (position < itemCount - 1) getItem(position + 1).temperature else null
        )
    }

    inner class VH(private val binding: ItemHourlyBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindLine(previous: Double?, current: Double, next: Double?) {
            binding.tempLine.setTemperatures(previous, current, next, minTemp, maxTemp)
        }

        fun bind(item: HourlyItem, isFirst: Boolean) {
            val context = binding.root.context

            // Simple option only: the current hour sits on a highlighted pill.
            if (simpleStyle != null) {
                binding.root.setBackgroundResource(if (isFirst) R.drawable.bg_simple_hour else 0)
            }
            val locale = LocaleHelper.currentLocale()

            binding.textHour.text = if (isFirst) {
                context.getString(R.string.now)
            } else {
                Formatters.hourLabel(item.timeIso, locale)
            }
            binding.imageIcon.setImageResource(
                WeatherCodeMapper.iconRes(item.weatherCode, item.isDay)
            )
            binding.textTemp.text = Formatters.temperature(item.temperature, temperatureUnit)

            val pop = item.precipitationProbability
            if (pop != null && pop > 0) {
                binding.textRain.text = context.getString(R.string.percent_format, pop)
                binding.textRain.visibility = android.view.View.VISIBLE
            } else {
                binding.textRain.visibility = android.view.View.INVISIBLE
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<HourlyItem>() {
            override fun areItemsTheSame(a: HourlyItem, b: HourlyItem) = a.timeIso == b.timeIso
            override fun areContentsTheSame(a: HourlyItem, b: HourlyItem) = a == b
        }
    }
}
