package com.vnweather.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for the Open-Meteo forecast response.
 * Open-Meteo returns parallel arrays (one array per variable), not a list of
 * objects, so the mapping to domain models happens in the repository.
 */
@Serializable
data class ForecastResponse(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timezone: String = "auto",
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val current: CurrentDto? = null,
    val hourly: HourlyDto? = null,
    val daily: DailyDto? = null
)

@Serializable
data class CurrentDto(
    val time: String = "",
    @SerialName("temperature_2m") val temperature: Double = 0.0,
    @SerialName("relative_humidity_2m") val humidity: Int = 0,
    @SerialName("apparent_temperature") val apparentTemperature: Double = 0.0,
    val precipitation: Double = 0.0,
    @SerialName("weather_code") val weatherCode: Int = 0,
    @SerialName("wind_speed_10m") val windSpeed: Double = 0.0,
    @SerialName("wind_direction_10m") val windDirection: Int = 0,
    @SerialName("is_day") val isDay: Int = 1
)

@Serializable
data class HourlyDto(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
    val precipitation: List<Double> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double> = emptyList(),
    @SerialName("is_day") val isDay: List<Int> = emptyList()
)

@Serializable
data class DailyDto(
    val time: List<String> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int> = emptyList(),
    @SerialName("temperature_2m_max") val tempMax: List<Double> = emptyList(),
    @SerialName("temperature_2m_min") val tempMin: List<Double> = emptyList(),
    @SerialName("precipitation_sum") val precipitationSum: List<Double> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int?> = emptyList(),
    val sunrise: List<String> = emptyList(),
    val sunset: List<String> = emptyList()
)

@Serializable
data class GeocodingResponse(
    val results: List<GeocodingResult>? = null
)

@Serializable
data class GeocodingResult(
    val id: Long = 0,
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val admin1: String? = null,
    val admin2: String? = null,
    val admin3: String? = null,
    val timezone: String? = null
)
