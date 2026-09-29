package com.vnweather.app.domain

/**
 * Tomorrow.io ships its own condition codes (1000 = clear, 4001 = rain,
 * 8000 = thunderstorm, ...). Rather than duplicate every icon and Vietnamese
 * label, translate them into the nearest WMO code and reuse
 * [WeatherCodeMapper] unchanged.
 */
object TomorrowCodeMapper {

    fun toWmo(code: Int?): Int = when (code) {
        1000 -> 0            // Clear
        1100 -> 1            // Mostly clear
        1101 -> 2            // Partly cloudy
        1102, 1001 -> 3      // Mostly cloudy / cloudy
        2000, 2100 -> 45     // Fog / light fog
        4000 -> 51           // Drizzle
        4200 -> 61           // Light rain
        4001 -> 63           // Rain
        4201 -> 65           // Heavy rain
        6000 -> 56           // Freezing drizzle
        6200, 6001 -> 66     // Freezing rain
        6201 -> 67           // Heavy freezing rain
        5100 -> 71           // Light snow
        5000 -> 73           // Snow
        5101 -> 75           // Heavy snow
        5001, 7000, 7101, 7102 -> 77 // Flurries, ice pellets
        8000 -> 95           // Thunderstorm
        else -> -1           // Unknown; WeatherCodeMapper shows its fallback
    }
}
