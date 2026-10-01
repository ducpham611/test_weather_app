package com.vnweather.app.data

import android.content.Context
import com.vnweather.app.data.local.SettingsStore
import com.vnweather.app.data.remote.UvIndexApi
import com.vnweather.app.data.remote.WaqiApi
import com.vnweather.app.domain.AirQuality
import com.vnweather.app.domain.AirUvParser
import com.vnweather.app.domain.City
import com.vnweather.app.domain.ExtraError
import com.vnweather.app.domain.ExtraException
import com.vnweather.app.domain.ExtraState
import com.vnweather.app.domain.UvForecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Air quality (WAQI) and UV (uvindexapi.com) for the AQI and UV blocks.
 *
 * Kept apart from [WeatherRepository] on purpose: either source can fail, or
 * have no token, without touching the forecast. Each result is cached per
 * location so the blocks still show something offline, marked as stale.
 */
class AirUvRepository(
    context: Context,
    private val waqiApi: WaqiApi,
    private val uvApi: UvIndexApi,
    private val settings: SettingsStore
) {

    private val dir = File(context.applicationContext.cacheDir, "air_uv").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun airQuality(city: City, forceRefresh: Boolean): ExtraState<AirQuality> =
        withContext(Dispatchers.IO) {
            val token = settings.waqiToken
            if (token.isBlank()) return@withContext ExtraState.Failed(ExtraError.NO_TOKEN)
            load(
                file = fileFor("aqi", city),
                serializer = AirQuality.serializer(),
                maxAgeMinutes = AQI_MAX_AGE_MIN,
                forceRefresh = forceRefresh,
                fetchedAt = { it.fetchedAtMillis }
            ) { now ->
                val body = waqiApi.feed(WaqiApi.feedUrl(city.latitude, city.longitude, token)).string()
                AirUvParser.parseWaqi(body, now)
            }
        }

    suspend fun uv(city: City, forceRefresh: Boolean): ExtraState<UvForecast> =
        withContext(Dispatchers.IO) {
            load(
                file = fileFor("uv", city),
                serializer = UvForecast.serializer(),
                maxAgeMinutes = UV_MAX_AGE_MIN,
                forceRefresh = forceRefresh,
                fetchedAt = { it.fetchedAtMillis }
            ) { now ->
                AirUvParser.parseUv(uvApi.forecast(city.latitude, city.longitude).string(), now)
            }
        }

    /** Cached copy without any network, for an instant first paint. */
    fun cachedAir(city: City): AirQuality? = read(fileFor("aqi", city), AirQuality.serializer())

    fun cachedUv(city: City): UvForecast? = read(fileFor("uv", city), UvForecast.serializer())

    private suspend fun <T> load(
        file: File,
        serializer: KSerializer<T>,
        maxAgeMinutes: Long,
        forceRefresh: Boolean,
        fetchedAt: (T) -> Long,
        fetch: suspend (Long) -> T
    ): ExtraState<T> {
        val now = System.currentTimeMillis()
        val cached = read(file, serializer)
        if (cached != null) {
            val age = now - fetchedAt(cached)
            // Pull-to-refresh still waits a couple of minutes between calls:
            // neither source changes faster, and both have daily quotas.
            val minAge = if (forceRefresh) MIN_REFRESH_MILLIS else TimeUnit.MINUTES.toMillis(maxAgeMinutes)
            if (age in 0 until minAge) return ExtraState.Ready(cached)
        }
        return try {
            val fresh = fetch(now)
            runCatching { file.writeText(json.encodeToString(serializer, fresh)) }
            ExtraState.Ready(fresh)
        } catch (e: Exception) {
            lastError = "${file.name}: ${e.javaClass.simpleName}: ${e.message}"
            val reason = when {
                e is ExtraException -> e.reason
                e is HttpException && (e.code() == 401 || e.code() == 403) -> ExtraError.INVALID_TOKEN
                else -> ExtraError.NETWORK
            }
            // A bad token must say so, even if an older reading exists.
            if (cached != null && reason != ExtraError.INVALID_TOKEN) {
                ExtraState.Ready(cached, stale = true)
            } else {
                ExtraState.Failed(reason)
            }
        }
    }

    private fun <T> read(file: File, serializer: KSerializer<T>): T? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull()
    }

    /**
     * Keyed by rounded coordinates, not just the city id: the "current
     * location" entry keeps one id while the GPS fix moves around.
     */
    private fun fileFor(kind: String, city: City): File {
        val key = String.format(Locale.US, "%.2f_%.2f", city.latitude, city.longitude)
        return File(dir, "$kind-$key.json")
    }

    companion object {
        /** Stations publish hourly; 30 min keeps the block close to live. */
        const val AQI_MAX_AGE_MIN = 30L

        /** NOAA UV is only republished once a day; the hourly list covers days ahead. */
        const val UV_MAX_AGE_MIN = 180L

        private const val MIN_REFRESH_MILLIS = 2 * 60 * 1000L

        /** Last failure text, for the "last error" dialog. */
        @Volatile
        var lastError: String? = null
            private set
    }
}
