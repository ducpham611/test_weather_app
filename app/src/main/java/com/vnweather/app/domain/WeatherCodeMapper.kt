package com.vnweather.app.domain

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.vnweather.app.R

/**
 * Maps WMO weather interpretation codes (what Open-Meteo returns) to an icon
 * and a translated label. Labels live in strings.xml / values-vi so the
 * Vietnamese text is a normal translation, not hard-coded here.
 */
object WeatherCodeMapper {

    @StringRes
    fun descriptionRes(code: Int): Int = when (code) {
        0 -> R.string.wmo_0
        1 -> R.string.wmo_1
        2 -> R.string.wmo_2
        3 -> R.string.wmo_3
        45, 48 -> R.string.wmo_fog
        51 -> R.string.wmo_drizzle_light
        53 -> R.string.wmo_drizzle_moderate
        55 -> R.string.wmo_drizzle_dense
        56, 57 -> R.string.wmo_freezing_drizzle
        61 -> R.string.wmo_rain_slight
        63 -> R.string.wmo_rain_moderate
        65 -> R.string.wmo_rain_heavy
        66, 67 -> R.string.wmo_freezing_rain
        71, 73, 75, 77 -> R.string.wmo_snow
        80 -> R.string.wmo_showers_slight
        81 -> R.string.wmo_showers_moderate
        82 -> R.string.wmo_showers_violent
        85, 86 -> R.string.wmo_snow_showers
        95 -> R.string.wmo_thunderstorm
        96, 99 -> R.string.wmo_thunderstorm_hail
        else -> R.string.wmo_unknown
    }

    @DrawableRes
    fun iconRes(code: Int, isDay: Boolean): Int = when (code) {
        0 -> if (isDay) R.drawable.ic_weather_sunny else R.drawable.ic_weather_night
        1, 2 -> if (isDay) R.drawable.ic_weather_partly_cloudy else R.drawable.ic_weather_night_cloudy
        3 -> R.drawable.ic_weather_cloudy
        45, 48 -> R.drawable.ic_weather_fog
        51, 53, 55, 56, 57 -> R.drawable.ic_weather_drizzle
        61, 63, 65, 66, 67 -> R.drawable.ic_weather_rain
        71, 73, 75, 77, 85, 86 -> R.drawable.ic_weather_snow
        80, 81, 82 -> R.drawable.ic_weather_showers
        95, 96, 99 -> R.drawable.ic_weather_thunder
        else -> R.drawable.ic_weather_cloudy
    }

    /** True when the code means meaningful rain, used for the rain hint. */
    fun isRainy(code: Int): Boolean =
        code in 51..67 || code in 80..82 || code in 95..99
}
