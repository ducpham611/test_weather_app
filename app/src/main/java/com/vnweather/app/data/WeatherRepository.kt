package com.vnweather.app.data

import com.vnweather.app.data.local.ForecastCache
import com.vnweather.app.data.local.SettingsStore
import com.vnweather.app.data.remote.ForecastResponse
import com.vnweather.app.data.remote.GeocodingApi
import com.vnweather.app.data.remote.OpenMeteoApi
import com.vnweather.app.domain.City
import com.vnweather.app.domain.CurrentWeather
import com.vnweather.app.domain.DailyItem
import com.vnweather.app.domain.Forecast
import com.vnweather.app.domain.HourlyItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single source of truth for forecast data.
 *
 * Strategy: serve the cache when it is still fresh, otherwise hit the network
 * and fall back to stale cache on failure so the app is never empty offline.
 */
class WeatherRepository(
    private val api: OpenMeteoApi,
    private val geocodingApi: GeocodingApi,
    private val cache: ForecastCache,
    private val settings: SettingsStore
) {

    suspend fun getForecast(
        city: City,
        forceRefresh: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<Forecast> = withContext(Dispatchers.IO) {
        val model = settings.weatherModel
        val cached = cache.read(city.id, model)

        if (!forceRefresh && cached != null &&
            ForecastCache.isFresh(cached.fetchedAtMillis, settings.refreshMinutes, nowMillis)
        ) {
            return@withContext Result.success(cached)
        }

        try {
            val response = api.getForecast(
                latitude = city.latitude,
                longitude = city.longitude,
                models = model,
                temperatureUnit = settings.temperatureUnit,
                windSpeedUnit = settings.windUnit
            )
            val forecast = response.toDomain(city, nowMillis)
            cache.write(forecast, model)
            Result.success(forecast)
        } catch (e: Exception) {
            // Always propagate the real error. The ViewModel decides whether to
            // keep showing cached data alongside it, so a TLS or DNS problem is
            // never silently swallowed.
            Result.failure(e)
        }
    }

    /** Cached data only; used by the widget and by the offline path. */
    fun getCached(city: City): Forecast? = cache.read(city.id, settings.weatherModel)

    suspend fun searchCities(query: String, language: String): Result<List<City>> =
        withContext(Dispatchers.IO) {
            try {
                val response = geocodingApi.search(name = query, language = language)
                val cities = response.results.orEmpty().map { r ->
                    City(
                        id = "geo-${r.id}",
                        name = r.name,
                        admin1 = r.admin1,
                        admin2 = r.admin2 ?: r.admin3,
                        country = r.country,
                        latitude = r.latitude,
                        longitude = r.longitude,
                        timezone = r.timezone
                    )
                }
                Result.success(cities)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

/**
 * Open-Meteo returns parallel arrays. Zipping them here keeps every other
 * layer working with plain objects.
 */
internal fun ForecastResponse.toDomain(city: City, fetchedAtMillis: Long): Forecast {
    val c = current
    val currentWeather = CurrentWeather(
        timeIso = c?.time.orEmpty(),
        temperature = c?.temperature ?: 0.0,
        apparentTemperature = c?.apparentTemperature ?: 0.0,
        humidity = c?.humidity ?: 0,
        precipitation = c?.precipitation ?: 0.0,
        weatherCode = c?.weatherCode ?: 0,
        windSpeed = c?.windSpeed ?: 0.0,
        windDirection = c?.windDirection ?: 0,
        isDay = (c?.isDay ?: 1) == 1
    )

    // Steps beyond the model's horizon come back as nulls; drop them rather
    // than rendering a row of dashes.
    val h = hourly
    val hourlyItems = if (h == null) emptyList() else h.time.indices.mapNotNull { i ->
        val temp = h.temperature.getOrNull(i) ?: return@mapNotNull null
        HourlyItem(
            timeIso = h.time[i],
            temperature = temp,
            precipitationProbability = h.precipitationProbability.getOrNull(i),
            precipitation = h.precipitation.getOrNull(i) ?: 0.0,
            weatherCode = h.weatherCode.getOrNull(i) ?: 0,
            windSpeed = h.windSpeed.getOrNull(i) ?: 0.0,
            isDay = (h.isDay.getOrNull(i) ?: 1) == 1
        )
    }

    // The last requested day is routinely null for ECMWF IFS: skip it.
    val d = daily
    val dailyItems = if (d == null) emptyList() else d.time.indices.mapNotNull { i ->
        val max = d.tempMax.getOrNull(i) ?: return@mapNotNull null
        val min = d.tempMin.getOrNull(i) ?: return@mapNotNull null
        DailyItem(
            dateIso = d.time[i],
            weatherCode = d.weatherCode.getOrNull(i) ?: 0,
            tempMax = max,
            tempMin = min,
            precipitationSum = d.precipitationSum.getOrNull(i) ?: 0.0,
            precipitationProbabilityMax = d.precipitationProbabilityMax.getOrNull(i),
            sunriseIso = d.sunrise.getOrNull(i),
            sunsetIso = d.sunset.getOrNull(i)
        )
    }

    return Forecast(
        city = city.copy(timezone = timezone),
        timezone = timezone,
        current = currentWeather,
        hourly = hourlyItems,
        daily = dailyItems,
        fetchedAtMillis = fetchedAtMillis
    )
}
