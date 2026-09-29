package com.vnweather.app.domain

import org.breezyweather.ui.theme.weatherView.WeatherView

/**
 * WMO code -> Breezy Weather animation kind.
 *
 * Their view draws the phenomenon, so codes are grouped by what you would
 * actually see falling from the sky rather than by severity.
 */
object WeatherBackgroundMapper {

    fun weatherKind(code: Int): Int = when (code) {
        0 -> WeatherView.WEATHER_KIND_CLEAR
        1, 2 -> WeatherView.WEATHER_KIND_CLOUD        // partly cloudy
        3 -> WeatherView.WEATHER_KIND_CLOUDY
        45, 48 -> WeatherView.WEATHER_KIND_FOG
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> WeatherView.WEATHER_KIND_RAINY
        56, 57, 66, 67 -> WeatherView.WEATHER_KIND_SLEET
        71, 73, 75, 77, 85, 86 -> WeatherView.WEATHER_KIND_SNOW
        95 -> WeatherView.WEATHER_KIND_THUNDER
        96, 97, 99 -> WeatherView.WEATHER_KIND_THUNDERSTORM
        else -> WeatherView.WEATHER_KIND_NULL
    }
}
