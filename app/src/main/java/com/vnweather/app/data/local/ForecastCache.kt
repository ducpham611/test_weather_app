package com.vnweather.app.data.local

import android.content.Context
import com.vnweather.app.domain.Forecast
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Last successful forecast per city, stored as one small JSON file each.
 *
 * Files (not SharedPreferences) because a full forecast is ~20-60 KB and we
 * do not want that sitting in memory for the whole process lifetime.
 */
class ForecastCache(context: Context) {

    private val dir = File(context.applicationContext.cacheDir, "forecasts").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun read(cityId: String, modelId: String): Forecast? {
        val file = fileFor(cityId, modelId)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(Forecast.serializer(), file.readText()) }
            .getOrNull()
            ?.copy(fromCache = true)
    }

    fun write(forecast: Forecast, modelId: String) {
        runCatching {
            fileFor(forecast.city.id, modelId)
                .writeText(json.encodeToString(Forecast.serializer(), forecast))
        }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    // Keyed by model as well as city: switching models must refetch instead of
    // showing the other model's numbers.
    private fun fileFor(cityId: String, modelId: String) =
        File(dir, "${sanitize(cityId)}-${sanitize(modelId)}.json")

    private fun sanitize(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        /**
         * Freshness rule. HRES only publishes a new run every 6 hours, so
         * refreshing more often than this just burns battery and data.
         */
        fun isFresh(fetchedAtMillis: Long, maxAgeMinutes: Int, nowMillis: Long): Boolean {
            val age = nowMillis - fetchedAtMillis
            return age >= 0 && age < TimeUnit.MINUTES.toMillis(maxAgeMinutes.toLong())
        }
    }
}
