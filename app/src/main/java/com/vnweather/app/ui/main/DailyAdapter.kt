package com.vnweather.app.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vnweather.app.R
import com.vnweather.app.databinding.ItemDailyBinding
import com.vnweather.app.domain.DailyItem
import com.vnweather.app.domain.WeatherCodeMapper
import com.vnweather.app.util.Formatters
import com.vnweather.app.util.LocaleHelper

/** Vertical day-by-day list: 3 days by default, up to 15 when expanded. */
class DailyAdapter : ListAdapter<DailyItem, DailyAdapter.VH>(DIFF) {

    var temperatureUnit: String = "celsius"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDailyBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), position == 0)
    }

    inner class VH(private val binding: ItemDailyBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DailyItem, isToday: Boolean) {
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
                binding.textRain.visibility = View.VISIBLE
            } else {
                binding.textRain.visibility = View.GONE
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<DailyItem>() {
            override fun areItemsTheSame(a: DailyItem, b: DailyItem) = a.dateIso == b.dateIso
            override fun areContentsTheSame(a: DailyItem, b: DailyItem) = a == b
        }
    }
}
