package com.vnweather.app.domain

import kotlinx.serialization.Serializable

/** A place the user can look at: a city, a district or a GPS fix. */
@Serializable
data class City(
    val id: String,
    val name: String,
    val admin1: String? = null,   // province / city level 1, e.g. "Hà Nội"
    val admin2: String? = null,   // district level, e.g. "Quận Hoàn Kiếm"
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
    val timezone: String? = null,
    val isCurrentLocation: Boolean = false
) {
    /** "Quận 1, Hồ Chí Minh" - what we show under the search field. */
    val subtitle: String
        get() = listOfNotNull(admin2, admin1, country)
            .distinct()
            .filter { it.isNotBlank() && it != name }
            .joinToString(", ")

    companion object {
        /** Fallback shown on first launch before the user picks anything. */
        val HANOI = City(
            id = "default-hanoi",
            name = "Hà Nội",
            admin1 = "Hà Nội",
            country = "Việt Nam",
            latitude = 21.0285,
            longitude = 105.8542,
            timezone = "Asia/Bangkok"
        )

        fun fromLocation(latitude: Double, longitude: Double, label: String) = City(
            id = "current-location",
            name = label,
            latitude = latitude,
            longitude = longitude,
            isCurrentLocation = true
        )
    }
}

@Serializable
data class CurrentWeather(
    val timeIso: String,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val precipitation: Double,
    val weatherCode: Int,
    val windSpeed: Double,
    val windDirection: Int,
    val isDay: Boolean
)

@Serializable
data class HourlyItem(
    val timeIso: String,
    val temperature: Double,
    val precipitationProbability: Int?,
    val precipitation: Double,
    val weatherCode: Int,
    val windSpeed: Double,
    val isDay: Boolean
)

@Serializable
data class DailyItem(
    val dateIso: String,
    val weatherCode: Int,
    val tempMax: Double,
    val tempMin: Double,
    val precipitationSum: Double,
    val precipitationProbabilityMax: Int?,
    val sunriseIso: String?,
    val sunsetIso: String?
)

/**
 * One complete forecast for one city.
 *
 * [fetchedAtMillis] drives both the "Cập nhật lúc HH:mm" label and the
 * cache freshness rule.
 */
@Serializable
data class Forecast(
    val city: City,
    val timezone: String,
    val current: CurrentWeather,
    val hourly: List<HourlyItem>,
    val daily: List<DailyItem>,
    val fetchedAtMillis: Long,
    val fromCache: Boolean = false
)

/** UI state for the main screen. */
sealed class UiState {
    object Loading : UiState()
    data class Success(val forecast: Forecast) : UiState()
    data class Error(val type: ErrorType, val staleForecast: Forecast? = null) : UiState()
}

enum class ErrorType {
    NO_NETWORK,
    DNS_ERROR,
    TLS_ERROR,
    TIMEOUT,
    API_ERROR,
    MISSING_API_KEY,
    LOCATION_UNAVAILABLE,
    UNKNOWN
}
