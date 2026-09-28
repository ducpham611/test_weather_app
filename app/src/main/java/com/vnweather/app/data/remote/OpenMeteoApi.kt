package com.vnweather.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo forecast API, pinned to the ECMWF IFS HRES model.
 *
 * HRES is a global 9 km model, updated every 6 hours. We request 7 days,
 * which stays well inside the run's horizon and keeps the payload small.
 * Hourly steps are native for the first 90 hours, which covers the whole
 * 3-day hourly view at full resolution.
 *
 * We request only the variables the UI actually draws, so the payload stays
 * small (~20-40 KB) and JSON parsing stays fast on old CPUs.
 */
interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("models") models: String = MODEL_ECMWF_IFS,
        @Query("current") current: String = CURRENT_FIELDS,
        @Query("hourly") hourly: String = HOURLY_FIELDS,
        @Query("daily") daily: String = DAILY_FIELDS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("precipitation_unit") precipitationUnit: String = "mm"
    ): ForecastResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
        const val MODEL_ECMWF_IFS = "ecmwf_ifs"

        const val CURRENT_FIELDS =
            "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation," +
                "weather_code,wind_speed_10m,wind_direction_10m,is_day"

        const val HOURLY_FIELDS =
            "temperature_2m,precipitation_probability,precipitation,weather_code," +
                "wind_speed_10m,is_day"

        const val DAILY_FIELDS =
            "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
                "precipitation_probability_max,sunrise,sunset"
    }
}

/** Open-Meteo geocoding, used for the city / district search. */
interface GeocodingApi {

    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 20,
        @Query("language") language: String = "vi",
        @Query("format") format: String = "json"
    ): GeocodingResponse

    companion object {
        const val BASE_URL = "https://geocoding-api.open-meteo.com/"
    }
}
