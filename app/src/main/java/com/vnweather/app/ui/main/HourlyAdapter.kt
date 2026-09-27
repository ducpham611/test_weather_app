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
 * Horizontal strip of hourly entries. The list is pre-trimmed to the next
 * 72 hours (3 full days), which is inside the ECMWF IFS HRES native hourly
 * window, so every cell is real model output rather than interpolation.
 */
class HourlyAdapter : ListAdapter<HourlyItem, HourlyAdapter.VH>(DIFF) {

    var temperatureUnit: String = "celsius"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemHourlyBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), position == 0)
    }

    inner class VH(private val binding: ItemHourlyBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HourlyItem, isFirst: Boolean) {
            val context = binding.root.context
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
