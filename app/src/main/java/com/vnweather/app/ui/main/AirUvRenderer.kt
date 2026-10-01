package com.vnweather.app.ui.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import androidx.core.content.ContextCompat
import com.vnweather.app.R
import com.vnweather.app.data.remote.UvIndexApi
import com.vnweather.app.data.remote.WaqiApi
import com.vnweather.app.databinding.ActivityMainBinding
import com.vnweather.app.domain.AirQuality
import com.vnweather.app.domain.AqiCategory
import com.vnweather.app.domain.City
import com.vnweather.app.domain.ExtraError
import com.vnweather.app.domain.ExtraState
import com.vnweather.app.domain.Geo
import com.vnweather.app.domain.UvCategory
import com.vnweather.app.domain.UvForecast
import com.vnweather.app.domain.UvReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Fills the "Air quality" and "UV index" blocks under the daily forecast.
 *
 * Each block shows a big number, a badge in the official category colour
 * with its name (Good / Moderate / ...), and one line of advice explaining
 * what that level means. Both blocks credit their source, as the WAQI terms
 * and uvindexapi.com's CC BY-SA licence require.
 */
class AirUvRenderer(
    private val binding: ActivityMainBinding,
    private val openSettings: () -> Unit
) {

    private val context: Context get() = binding.root.context

    init {
        binding.textUvSource.setText(R.string.uv_source)
        binding.textUvSource.setOnClickListener { openUrl(UvIndexApi.SITE_URL) }
        binding.textAqiSource.setOnClickListener { openUrl(WaqiApi.SITE_URL) }
    }

    fun renderAir(state: ExtraState<AirQuality>, city: City) {
        binding.aqiBlock.visibility = View.VISIBLE
        binding.aqiBlock.setOnClickListener(null)
        binding.aqiBlock.isClickable = false
        binding.textAqiSource.setText(R.string.aqi_source)

        when (state) {
            is ExtraState.Loading -> message(binding.textAqiMessage, binding.aqiContent, R.string.extra_loading)
            is ExtraState.Failed -> {
                message(
                    binding.textAqiMessage, binding.aqiContent,
                    when (state.reason) {
                        ExtraError.NO_TOKEN -> R.string.aqi_no_token
                        ExtraError.INVALID_TOKEN -> R.string.aqi_invalid_token
                        ExtraError.NO_DATA -> R.string.aqi_no_data
                        ExtraError.NETWORK -> R.string.extra_unavailable
                    }
                )
                if (state.reason == ExtraError.NO_TOKEN || state.reason == ExtraError.INVALID_TOKEN) {
                    binding.aqiBlock.setOnClickListener { openSettings() }
                }
            }
            is ExtraState.Ready -> {
                val air = state.data
                binding.textAqiMessage.visibility = View.GONE
                binding.aqiContent.visibility = View.VISIBLE

                val category = AqiCategory.of(air.aqi)
                binding.textAqiValue.text = air.aqi.toString()
                badge(binding.textAqiBadge, aqiLabel(category), aqiColor(category), darkText = category in DARK_TEXT_AQI)
                binding.textAqiAdvice.setText(aqiAdvice(category))

                val lines = mutableListOf<String>()
                air.dominantPollutant?.let {
                    lines += context.getString(R.string.aqi_pollutant_format, pollutantName(it))
                }
                val distance = if (air.stationLatitude != null && air.stationLongitude != null) {
                    Geo.distanceKm(city.latitude, city.longitude, air.stationLatitude, air.stationLongitude)
                } else null
                lines += if (distance != null && distance < MAX_PLAUSIBLE_KM) {
                    context.getString(R.string.aqi_station_format, air.stationName, distance.roundToInt().coerceAtLeast(1))
                } else {
                    context.getString(R.string.aqi_station_plain, air.stationName)
                }
                measuredLabel(air.measuredAtIso)?.let {
                    lines += context.getString(R.string.aqi_measured_format, it)
                }
                if (state.stale) lines += context.getString(R.string.extra_stale)
                binding.textAqiDetail.text = lines.joinToString("\n")

                air.sources.firstOrNull()?.let {
                    binding.textAqiSource.text = context.getString(R.string.aqi_source_format, it)
                }
            }
        }
    }

    fun renderUv(state: ExtraState<UvForecast>) {
        binding.uvBlock.visibility = View.VISIBLE
        when (state) {
            is ExtraState.Loading -> message(binding.textUvMessage, binding.uvContent, R.string.extra_loading)
            is ExtraState.Failed -> message(
                binding.textUvMessage, binding.uvContent,
                if (state.reason == ExtraError.NO_DATA) R.string.uv_no_data else R.string.extra_unavailable
            )
            is ExtraState.Ready -> {
                val uv = state.data
                val zone = TimeZone.getTimeZone(uv.timezone)
                val now = Date()
                val today = format("yyyy-MM-dd", zone, now)
                // Pick the current hour from the cached hourly list, so the
                // value stays right between refreshes and offline.
                val value = UvReader.valueAt(uv, today, format("HH", zone, now)) ?: uv.nowValue
                if (value == null) {
                    message(binding.textUvMessage, binding.uvContent, R.string.uv_no_data)
                    return
                }
                binding.textUvMessage.visibility = View.GONE
                binding.uvContent.visibility = View.VISIBLE

                val category = UvCategory.of(value)
                binding.textUvValue.text = uvText(value)
                badge(binding.textUvBadge, uvLabel(category), uvColor(category), darkText = category == UvCategory.MODERATE)
                binding.textUvAdvice.setText(uvAdvice(category))

                val lines = mutableListOf<String>()
                val peak = UvReader.peakOn(uv, today)
                val peakValue = peak?.uv ?: uv.todayMax
                val peakTime = peak?.time ?: uv.todayMaxTime
                if (peakValue != null && peakTime != null && peakValue > 0) {
                    lines += context.getString(R.string.uv_peak_format, uvText(peakValue), peakTime)
                }
                if (state.stale) lines += context.getString(R.string.extra_stale)
                binding.textUvDetail.text = lines.joinToString("\n")
                binding.textUvDetail.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun message(text: android.widget.TextView, content: View, res: Int) {
        text.setText(res)
        text.visibility = View.VISIBLE
        content.visibility = View.GONE
    }

    private fun badge(view: android.widget.TextView, label: Int, color: Int, darkText: Boolean) {
        view.setText(label)
        view.setTextColor(
            ContextCompat.getColor(context, if (darkText) R.color.badge_text_dark else R.color.badge_text_light)
        )
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14 * context.resources.displayMetrics.density
            setColor(ContextCompat.getColor(context, color))
        }
    }

    /** "11:00" for today in the station's own zone, "30/09 11:00" otherwise. */
    private fun measuredLabel(iso: String?): String? {
        if (iso.isNullOrBlank() || iso.length < 19) return null
        val offset = iso.substring(19).ifBlank { "Z" }
        val zone = TimeZone.getTimeZone(if (offset == "Z") "UTC" else "GMT$offset")
        val parsed = runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = zone }
                .parse(iso.substring(0, 19))
        }.getOrNull() ?: return null
        val sameDay = format("yyyyMMdd", zone, parsed) == format("yyyyMMdd", zone, Date())
        return format(if (sameDay) "HH:mm" else "dd/MM HH:mm", zone, parsed)
    }

    private fun format(pattern: String, zone: TimeZone, date: Date): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = zone }.format(date)

    private fun uvText(value: Double): String =
        if (value >= 10) value.roundToInt().toString() else String.format(Locale.US, "%.1f", value)

    private fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // No browser installed; nothing useful to do.
        }
    }

    private fun pollutantName(key: String): String = when (key.lowercase(Locale.US)) {
        "pm25" -> "PM2.5"
        "pm10" -> "PM10"
        "o3" -> "O₃ (ozone)"
        "no2" -> "NO₂"
        "so2" -> "SO₂"
        "co" -> "CO"
        else -> key.uppercase(Locale.US)
    }

    private fun aqiLabel(c: AqiCategory) = when (c) {
        AqiCategory.GOOD -> R.string.aqi_good
        AqiCategory.MODERATE -> R.string.aqi_moderate
        AqiCategory.SENSITIVE -> R.string.aqi_sensitive
        AqiCategory.UNHEALTHY -> R.string.aqi_unhealthy
        AqiCategory.VERY_UNHEALTHY -> R.string.aqi_very_unhealthy
        AqiCategory.HAZARDOUS -> R.string.aqi_hazardous
    }

    private fun aqiAdvice(c: AqiCategory) = when (c) {
        AqiCategory.GOOD -> R.string.aqi_advice_good
        AqiCategory.MODERATE -> R.string.aqi_advice_moderate
        AqiCategory.SENSITIVE -> R.string.aqi_advice_sensitive
        AqiCategory.UNHEALTHY -> R.string.aqi_advice_unhealthy
        AqiCategory.VERY_UNHEALTHY -> R.string.aqi_advice_very_unhealthy
        AqiCategory.HAZARDOUS -> R.string.aqi_advice_hazardous
    }

    private fun aqiColor(c: AqiCategory) = when (c) {
        AqiCategory.GOOD -> R.color.aqi_good
        AqiCategory.MODERATE -> R.color.aqi_moderate
        AqiCategory.SENSITIVE -> R.color.aqi_sensitive
        AqiCategory.UNHEALTHY -> R.color.aqi_unhealthy
        AqiCategory.VERY_UNHEALTHY -> R.color.aqi_very_unhealthy
        AqiCategory.HAZARDOUS -> R.color.aqi_hazardous
    }

    private fun uvLabel(c: UvCategory) = when (c) {
        UvCategory.LOW -> R.string.uv_low
        UvCategory.MODERATE -> R.string.uv_moderate
        UvCategory.HIGH -> R.string.uv_high
        UvCategory.VERY_HIGH -> R.string.uv_very_high
        UvCategory.EXTREME -> R.string.uv_extreme
    }

    private fun uvAdvice(c: UvCategory) = when (c) {
        UvCategory.LOW -> R.string.uv_advice_low
        UvCategory.MODERATE -> R.string.uv_advice_moderate
        UvCategory.HIGH -> R.string.uv_advice_high
        UvCategory.VERY_HIGH -> R.string.uv_advice_very_high
        UvCategory.EXTREME -> R.string.uv_advice_extreme
    }

    private fun uvColor(c: UvCategory) = when (c) {
        UvCategory.LOW -> R.color.uv_low
        UvCategory.MODERATE -> R.color.uv_moderate
        UvCategory.HIGH -> R.color.uv_high
        UvCategory.VERY_HIGH -> R.color.uv_very_high
        UvCategory.EXTREME -> R.color.uv_extreme
    }

    private companion object {
        /** Yellow and orange badges need dark text to stay readable. */
        val DARK_TEXT_AQI = setOf(AqiCategory.MODERATE, AqiCategory.SENSITIVE)

        /** Beyond this the "nearest" station is not really local; hide the distance. */
        const val MAX_PLAUSIBLE_KM = 500.0
    }
}
