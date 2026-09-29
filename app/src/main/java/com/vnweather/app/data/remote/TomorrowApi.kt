package com.vnweather.app.data.remote

import com.vnweather.app.data.local.SettingsStore
import com.vnweather.app.domain.City
import com.vnweather.app.domain.CurrentWeather
import com.vnweather.app.domain.DailyItem
import com.vnweather.app.domain.Forecast
import com.vnweather.app.domain.HourlyItem
import com.vnweather.app.domain.TomorrowCodeMapper
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Tomorrow.io v4 forecast, offered as an alternative provider.
 *
 * Limits worth knowing, straight from their docs: hourly runs 120 hours but
 * the daily timeline stops at 5 days, so the daily list is shorter than with
 * Open-Meteo. It also needs an API key, which the user enters in Settings
 * because we cannot ship one safely inside an APK.
 */
interface TomorrowApi {

    @GET("v4/weather/forecast")
    suspend fun getForecast(
        @Query("location") location: String,
        @Query("apikey") apiKey: String,
        @Query("timesteps") timesteps: List<String> = listOf("1h", "1d"),
        // Always metric; the mapper converts to whatever the user picked so we
        // never have to reason about two unit systems at once.
        @Query("units") units: String = "metric"
    ): TomorrowResponse

    companion object {
        const val BASE_URL = "https://api.tomorrow.io/"

        /** Their daily timeline ends here, unlike Open-Meteo's 7 days. */
        const val DAILY_DAYS = 5

        const val HOURS = 48
    }
}

@Serializable
data class TomorrowResponse(val timelines: TomorrowTimelines? = null)

@Serializable
data class TomorrowTimelines(
    val hourly: List<TomorrowStep> = emptyList(),
    val daily: List<TomorrowStep> = emptyList()
)

@Serializable
data class TomorrowStep(
    val time: String = "",
    val values: TomorrowValues = TomorrowValues()
)

/**
 * One flat bag for both timelines. Tomorrow.io suffixes daily fields
 * (`temperatureMax`, `windSpeedAvg`, ...) while hourly fields are bare, so
 * declaring both here keeps a single DTO.
 */
@Serializable
data class TomorrowValues(
    val temperature: Double? = null,
    val temperatureApparent: Double? = null,
    val temperatureMax: Double? = null,
    val temperatureMin: Double? = null,
    val temperatureApparentAvg: Double? = null,
    val humidity: Double? = null,
    val humidityAvg: Double? = null,
    val precipitationProbability: Double? = null,
    val precipitationProbabilityAvg: Double? = null,
    val precipitationProbabilityMax: Double? = null,
    val rainIntensity: Double? = null,
    val rainIntensityAvg: Double? = null,
    val rainAccumulation: Double? = null,
    val rainAccumulationSum: Double? = null,
    val weatherCode: Int? = null,
    val weatherCodeMax: Int? = null,
    val windSpeed: Double? = null,
    val windSpeedAvg: Double? = null,
    val windDirection: Double? = null,
    val windDirectionAvg: Double? = null,
    val sunriseTime: String? = null,
    val sunsetTime: String? = null
)

/**
 * Maps a Tomorrow.io payload onto the same domain objects Open-Meteo produces,
 * so nothing above the repository knows which provider is selected.
 *
 * Two conversions matter:
 *  - Timestamps come back in UTC ("...T15:00:00Z"); the rest of the app works
 *    with local wall-clock strings, so they are re-formatted in the city's
 *    own time zone.
 *  - Values arrive metric (°C, m/s, mm) and are converted to the user's
 *    chosen units here.
 */
internal fun TomorrowResponse.toDomain(
    city: City,
    fetchedAtMillis: Long,
    temperatureUnit: String,
    windUnit: String
): Forecast {
    val zone = runCatching { TimeZone.getTimeZone(city.timezone ?: "") }
        .getOrNull()
        ?.takeIf { !city.timezone.isNullOrBlank() }
        ?: TimeZone.getDefault()

    fun temp(value: Double?): Double? = value?.let {
        if (temperatureUnit == SettingsStore.UNIT_FAHRENHEIT) it * 9 / 5 + 32 else it
    }

    fun wind(value: Double?): Double = (value ?: 0.0).let {
        // Metric response is m/s.
        if (windUnit == SettingsStore.UNIT_MS) it else it * 3.6
    }

    val hourlySteps = timelines?.hourly.orEmpty()
    val dailySteps = timelines?.daily.orEmpty()

    val hourly = hourlySteps.mapNotNull { step ->
        val v = step.values
        val temperature = temp(v.temperature) ?: return@mapNotNull null
        val localIso = TomorrowTime.toLocalMinute(step.time, zone) ?: return@mapNotNull null
        HourlyItem(
            timeIso = localIso,
            temperature = temperature,
            precipitationProbability = v.precipitationProbability?.roundToInt(),
            precipitation = v.rainIntensity ?: v.rainAccumulation ?: 0.0,
            weatherCode = TomorrowCodeMapper.toWmo(v.weatherCode),
            windSpeed = wind(v.windSpeed),
            isDay = TomorrowTime.isDaylight(step.time, zone)
        )
    }

    val daily = dailySteps.mapNotNull { step ->
        val v = step.values
        val max = temp(v.temperatureMax) ?: return@mapNotNull null
        val min = temp(v.temperatureMin) ?: return@mapNotNull null
        val dateIso = TomorrowTime.toLocalDate(step.time, zone) ?: return@mapNotNull null
        DailyItem(
            dateIso = dateIso,
            weatherCode = TomorrowCodeMapper.toWmo(v.weatherCodeMax ?: v.weatherCode),
            tempMax = max,
            tempMin = min,
            precipitationSum = v.rainAccumulationSum ?: 0.0,
            precipitationProbabilityMax =
                (v.precipitationProbabilityMax ?: v.precipitationProbabilityAvg)?.roundToInt(),
            sunriseIso = TomorrowTime.toLocalMinute(v.sunriseTime, zone),
            sunsetIso = TomorrowTime.toLocalMinute(v.sunsetTime, zone)
        )
    }

    // Tomorrow.io's forecast endpoint has no "current" block, so the nearest
    // hourly step stands in for it. One request instead of two keeps the free
    // tier's call budget usable.
    val nowStep = hourlySteps.firstOrNull()
    val nv = nowStep?.values
    val current = CurrentWeather(
        timeIso = hourly.firstOrNull()?.timeIso.orEmpty(),
        temperature = temp(nv?.temperature) ?: 0.0,
        apparentTemperature = temp(nv?.temperatureApparent ?: nv?.temperature) ?: 0.0,
        humidity = (nv?.humidity ?: 0.0).roundToInt(),
        precipitation = nv?.rainIntensity ?: 0.0,
        weatherCode = TomorrowCodeMapper.toWmo(nv?.weatherCode),
        windSpeed = wind(nv?.windSpeed),
        windDirection = (nv?.windDirection ?: 0.0).roundToInt(),
        isDay = nowStep?.let { TomorrowTime.isDaylight(it.time, zone) } ?: true
    )

    return Forecast(
        city = city,
        timezone = zone.id,
        current = current,
        hourly = hourly,
        daily = daily,
        fetchedAtMillis = fetchedAtMillis
    )
}

/** UTC ISO-8601 in, local wall-clock strings out. */
internal object TomorrowTime {

    private const val UTC_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    private fun parseUtc(value: String?): Date? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            SimpleDateFormat(UTC_PATTERN, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(value)
        }.getOrNull()
    }

    fun toLocalMinute(value: String?, zone: TimeZone): String? {
        val date = parseUtc(value) ?: return null
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            .apply { timeZone = zone }
            .format(date)
    }

    fun toLocalDate(value: String?, zone: TimeZone): String? {
        val date = parseUtc(value) ?: return null
        return SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = zone }
            .format(date)
    }

    /** No isDay field in the payload, so fall back to the local hour. */
    fun isDaylight(value: String?, zone: TimeZone): Boolean {
        val date = parseUtc(value) ?: return true
        val hour = SimpleDateFormat("HH", Locale.US)
            .apply { timeZone = zone }
            .format(date)
            .toIntOrNull() ?: 12
        return hour in 6..17
    }
}
