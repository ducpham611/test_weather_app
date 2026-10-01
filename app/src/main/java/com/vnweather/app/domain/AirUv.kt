package com.vnweather.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Air quality from the World Air Quality Index project (aqicn.org / waqi.info).
 *
 * WAQI reports the nearest real monitoring station, not a modelled grid
 * point, so the station name and distance are kept and shown to the user.
 */
@Serializable
data class AirQuality(
    /** US-EPA AQI, 0-500+. */
    val aqi: Int,
    /** WAQI pollutant key: pm25, pm10, o3, no2, so2, co. */
    val dominantPollutant: String? = null,
    val stationName: String,
    val stationLatitude: Double? = null,
    val stationLongitude: Double? = null,
    /** Measurement time with offset, e.g. 2026-10-01T11:00:00+07:00. */
    val measuredAtIso: String? = null,
    /** Originating agencies, WAQI itself excluded. Their ToS requires credit. */
    val sources: List<String> = emptyList(),
    val fetchedAtMillis: Long = 0L,
    /** Which provider produced this reading: SettingsStore.AQI_WAQI or AQI_OPEN_METEO. */
    val provider: String = "waqi"
)

/** UV index forecast from uvindexapi.com (NOAA data, CC BY-SA 4.0). */
@Serializable
data class UvForecast(
    /** IANA zone the hourly times are in, e.g. "Asia/Bangkok". */
    val timezone: String,
    val hourly: List<UvHour>,
    /** The API's own "now" value, used when the hourly list has no match. */
    val nowValue: Double? = null,
    val todayMax: Double? = null,
    val todayMaxTime: String? = null,
    val fetchedAtMillis: Long = 0L
)

@Serializable
data class UvHour(
    /** yyyy-MM-dd */
    val date: String,
    /** HH:mm */
    val time: String,
    val uv: Double
)

/** Outcome of one of the optional (non-forecast) blocks. */
sealed class ExtraState<out T> {
    object Loading : ExtraState<Nothing>()
    data class Ready<T>(val data: T, val stale: Boolean = false) : ExtraState<T>()
    data class Failed(val reason: ExtraError) : ExtraState<Nothing>()
}

enum class ExtraError { NO_TOKEN, INVALID_TOKEN, NO_DATA, NETWORK }

class ExtraException(val reason: ExtraError, message: String) : Exception(message)

/** US EPA AQI bands. Bounds are inclusive upper limits. */
enum class AqiCategory(val upTo: Int) {
    GOOD(50),
    MODERATE(100),
    SENSITIVE(150),
    UNHEALTHY(200),
    VERY_UNHEALTHY(300),
    HAZARDOUS(Int.MAX_VALUE);

    companion object {
        fun of(aqi: Int): AqiCategory = entries.first { aqi <= it.upTo }
    }
}

/** WHO / WMO UV index bands, applied to the value rounded to a whole number. */
enum class UvCategory(val upTo: Int) {
    LOW(2),
    MODERATE(5),
    HIGH(7),
    VERY_HIGH(10),
    EXTREME(Int.MAX_VALUE);

    companion object {
        fun of(uv: Double): UvCategory {
            val rounded = uv.coerceAtLeast(0.0).roundToInt()
            return entries.first { rounded <= it.upTo }
        }
    }
}

object AirUvParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * WAQI answers HTTP 200 even for errors, with `data` turned into a plain
     * string ("Invalid key", "Unknown station", "Over quota"), and `aqi` can be
     * "-" when a station is offline. A typed DTO would choke on both, so the
     * payload is read as a JSON tree.
     */
    fun parseWaqi(body: String, fetchedAtMillis: Long): AirQuality {
        val root = runCatching { json.parseToJsonElement(body) as JsonObject }.getOrNull()
            ?: throw ExtraException(ExtraError.NO_DATA, "Unreadable WAQI response")
        val status = root.str("status")
        val data = root["data"]

        if (status != "ok" || data !is JsonObject) {
            val message = (data as? JsonPrimitive)?.contentOrNull.orEmpty()
            val reason = if (message.contains("key", ignoreCase = true)) {
                ExtraError.INVALID_TOKEN
            } else if (message.contains("quota", ignoreCase = true)) {
                ExtraError.NETWORK
            } else {
                ExtraError.NO_DATA
            }
            throw ExtraException(reason, "WAQI: ${message.ifBlank { status ?: "error" }}")
        }

        val aqi = (data["aqi"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.roundToInt()
            ?: throw ExtraException(ExtraError.NO_DATA, "WAQI station has no current AQI")

        val city = data["city"] as? JsonObject
        val geo = city?.get("geo") as? JsonArray
        val time = data["time"] as? JsonObject
        val sources = (data["attributions"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.str("name") }
            .filterNot { it.contains("World Air Quality Index", ignoreCase = true) }

        return AirQuality(
            aqi = aqi,
            dominantPollutant = data.str("dominentpol")?.takeIf { it.isNotBlank() },
            stationName = city?.str("name")?.takeIf { it.isNotBlank() } ?: "?",
            stationLatitude = (geo?.getOrNull(0) as? JsonPrimitive)?.doubleOrNull,
            stationLongitude = (geo?.getOrNull(1) as? JsonPrimitive)?.doubleOrNull,
            measuredAtIso = time?.str("iso"),
            sources = sources,
            fetchedAtMillis = fetchedAtMillis
        )
    }

    /** Open-Meteo `us_aqi_*` sub-index fields and the WAQI-style key each maps to. */
    val OPEN_METEO_SUB_INDICES = linkedMapOf(
        "us_aqi_pm2_5" to "pm25",
        "us_aqi_pm10" to "pm10",
        "us_aqi_ozone" to "o3",
        "us_aqi_nitrogen_dioxide" to "no2",
        "us_aqi_sulphur_dioxide" to "so2",
        "us_aqi_carbon_monoxide" to "co"
    )

    /**
     * Open-Meteo Air Quality API (CAMS global model). The overall US AQI is
     * the highest pollutant sub-index, so the dominant pollutant is whichever
     * sub-index is largest.
     */
    fun parseOpenMeteoAir(body: String, fetchedAtMillis: Long): AirQuality {
        val root = runCatching { json.parseToJsonElement(body) as JsonObject }.getOrNull()
            ?: throw ExtraException(ExtraError.NO_DATA, "Unreadable Open-Meteo air quality response")
        if ((root["error"] as? JsonPrimitive)?.contentOrNull == "true") {
            throw ExtraException(ExtraError.NO_DATA, "Open-Meteo: ${root.str("reason") ?: "error"}")
        }
        val current = root["current"] as? JsonObject
            ?: throw ExtraException(ExtraError.NO_DATA, "Open-Meteo: no current air quality")
        fun num(key: String) = (current[key] as? JsonPrimitive)?.doubleOrNull
        val aqi = num("us_aqi")?.roundToInt()
            ?: throw ExtraException(ExtraError.NO_DATA, "Open-Meteo: us_aqi is null here")

        val dominant = OPEN_METEO_SUB_INDICES.entries
            .mapNotNull { (field, key) -> num(field)?.let { key to it } }
            .maxByOrNull { it.second }
            ?.first

        // "2026-10-01T17:00" is local time; append the offset so it reads
        // the same way as a WAQI timestamp.
        val time = current.str("time")
        val offsetSeconds = (root["utc_offset_seconds"] as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 0
        val iso = time?.let { if (it.length == 16) "$it:00${offsetLabel(offsetSeconds)}" else it }

        return AirQuality(
            aqi = aqi,
            dominantPollutant = dominant,
            stationName = "",
            stationLatitude = (root["latitude"] as? JsonPrimitive)?.doubleOrNull,
            stationLongitude = (root["longitude"] as? JsonPrimitive)?.doubleOrNull,
            measuredAtIso = iso,
            sources = emptyList(),
            fetchedAtMillis = fetchedAtMillis,
            provider = "open_meteo"
        )
    }

    internal fun offsetLabel(seconds: Int): String {
        val sign = if (seconds < 0) "-" else "+"
        val abs = kotlin.math.abs(seconds)
        return String.format(java.util.Locale.US, "%s%02d:%02d", sign, abs / 3600, (abs % 3600) / 60)
    }

    fun parseUv(body: String, fetchedAtMillis: Long): UvForecast {
        val root = runCatching { json.parseToJsonElement(body) as JsonObject }.getOrNull()
            ?: throw ExtraException(ExtraError.NO_DATA, "Unreadable UV response")
        if ((root["ok"] as? JsonPrimitive)?.contentOrNull != "true") {
            throw ExtraException(ExtraError.NO_DATA, "UV Index API: ${root["error"] ?: "error"}")
        }
        val zone = (root["timezone"] as? JsonObject)?.str("id") ?: "UTC"
        val hourly = (root["hourly"] as? JsonArray).orEmpty().mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val date = o.str("date") ?: return@mapNotNull null
            val time = o.str("time")?.take(5) ?: return@mapNotNull null
            val uv = (o["uv_index"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            UvHour(date, time, uv)
        }
        val now = (root["now"] as? JsonObject)
        val todayMax = ((root["today"] as? JsonObject)?.get("max") as? JsonObject)
        return UvForecast(
            timezone = zone,
            hourly = hourly,
            nowValue = (now?.get("uv_index") as? JsonPrimitive)?.doubleOrNull,
            todayMax = (todayMax?.get("uv_index") as? JsonPrimitive)?.doubleOrNull,
            todayMaxTime = todayMax?.str("time")?.take(5),
            fetchedAtMillis = fetchedAtMillis
        )
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}

/** Reading the cached UV forecast at a given local date and hour. */
object UvReader {

    /** UV for the hour containing [localDate] [localHour] ("HH"). */
    fun valueAt(forecast: UvForecast, localDate: String, localHour: String): Double? =
        forecast.hourly.firstOrNull { it.date == localDate && it.time.take(2) == localHour }?.uv

    /** Highest hour of [localDate], or null when that day is not in the list. */
    fun peakOn(forecast: UvForecast, localDate: String): UvHour? =
        forecast.hourly.filter { it.date == localDate }.maxByOrNull { it.uv }
}

object Geo {
    /** Great-circle distance in km. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
