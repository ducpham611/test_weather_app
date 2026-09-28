package com.vnweather.app.util

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.vnweather.app.domain.City
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.util.Locale

/**
 * Turns a GPS fix into a readable place name so the header shows
 * "Quận Hoàn Kiếm" instead of the generic "My location".
 *
 * Two sources, in order:
 *  1. The platform [Geocoder]. Free and offline-ish, but it needs a backend
 *     service that plenty of cheap / de-Googled Vietnamese phones do not have,
 *     in which case it throws or returns nothing.
 *  2. BigDataCloud's key-less reverse endpoint as a network fallback.
 *
 * If both fail we keep the generic label rather than blocking the forecast.
 */
class ReverseGeocoder(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun describe(
        latitude: Double,
        longitude: Double,
        locale: Locale,
        fallbackLabel: String
    ): City = withContext(Dispatchers.IO) {
        val place = fromPlatform(latitude, longitude, locale)
            ?: fromNetwork(latitude, longitude, locale)

        City(
            id = CURRENT_LOCATION_ID,
            name = place?.name?.takeIf { it.isNotBlank() } ?: fallbackLabel,
            admin2 = place?.district,
            admin1 = place?.province,
            country = place?.country,
            latitude = latitude,
            longitude = longitude,
            isCurrentLocation = true
        )
    }

    @Suppress("DEPRECATION") // The listener API only exists from API 33.
    private fun fromPlatform(latitude: Double, longitude: Double, locale: Locale): Place? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            val address = Geocoder(context, locale)
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?: return null

            // Vietnam maps as: locality = city/town, subAdminArea = district,
            // adminArea = province. Prefer the most specific non-blank one.
            val district = address.subAdminArea ?: address.subLocality
            val name = listOfNotNull(address.locality, district, address.adminArea)
                .firstOrNull { it.isNotBlank() } ?: return null

            Place(
                name = name,
                district = district?.takeIf { it != name },
                province = address.adminArea?.takeIf { it != name },
                country = address.countryName
            )
        }.onFailure { Log.w(TAG, "Platform geocoder failed", it) }.getOrNull()
    }

    private fun fromNetwork(latitude: Double, longitude: Double, locale: Locale): Place? =
        runCatching {
            val url = "https://api.bigdatacloud.net/data/reverse-geocode-client" +
                "?latitude=$latitude&longitude=$longitude&localityLanguage=${locale.language}"
            val request = Request.Builder().url(url).build()

            NetworkModule.rawClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                val obj = json.parseToJsonElement(body) as? JsonObject ?: return null

                fun field(key: String): String? =
                    obj[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

                val city = field("city") ?: field("locality")
                val province = field("principalSubdivision")
                val name = city ?: province ?: return null

                Place(
                    name = name,
                    district = field("locality")?.takeIf { it != name },
                    province = province?.takeIf { it != name },
                    country = field("countryName")
                )
            }
        }.onFailure { Log.w(TAG, "Network reverse geocode failed", it) }.getOrNull()

    private data class Place(
        val name: String,
        val district: String?,
        val province: String?,
        val country: String?
    )

    private companion object {
        const val TAG = "ReverseGeocoder"
        const val CURRENT_LOCATION_ID = "current-location"
    }
}
