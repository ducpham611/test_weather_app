package com.vnweather.app

import com.vnweather.app.data.remote.ForecastResponse
import com.vnweather.app.data.toDomain
import com.vnweather.app.domain.City
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses a trimmed but real-shaped ECMWF IFS HRES response. */
class ForecastParsingTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val sample = """
    {
      "latitude": 21.0,
      "longitude": 105.875,
      "timezone": "Asia/Bangkok",
      "utc_offset_seconds": 25200,
      "current": {
        "time": "2026-09-27T10:00",
        "temperature_2m": 31.4,
        "relative_humidity_2m": 74,
        "apparent_temperature": 38.2,
        "precipitation": 0.0,
        "weather_code": 80,
        "wind_speed_10m": 11.3,
        "wind_direction_10m": 135,
        "is_day": 1
      },
      "hourly": {
        "time": ["2026-09-27T10:00", "2026-09-27T11:00", "2026-09-27T12:00"],
        "temperature_2m": [31.4, 32.0, 32.6],
        "precipitation_probability": [20, 35, null],
        "precipitation": [0.0, 0.2, 1.1],
        "weather_code": [80, 80, 95],
        "wind_speed_10m": [11.3, 12.0, 13.4],
        "is_day": [1, 1, 1]
      },
      "daily": {
        "time": ["2026-09-27", "2026-09-28", "2026-09-29"],
        "weather_code": [80, 95, 3],
        "temperature_2m_max": [33.1, 32.4, 31.0],
        "temperature_2m_min": [25.2, 25.0, 24.6],
        "precipitation_sum": [4.2, 18.0, 0.4],
        "precipitation_probability_max": [60, 85, 20],
        "sunrise": ["2026-09-27T05:43", "2026-09-28T05:43", "2026-09-29T05:44"],
        "sunset": ["2026-09-27T17:45", "2026-09-28T17:44", "2026-09-29T17:43"]
      }
    }
    """.trimIndent()

    @Test
    fun `parses current hourly and daily blocks`() {
        val dto = json.decodeFromString(ForecastResponse.serializer(), sample)
        val forecast = dto.toDomain(City.HANOI, fetchedAtMillis = 1_000L)

        assertEquals(31.4, forecast.current.temperature, 0.01)
        assertEquals(80, forecast.current.weatherCode)
        assertTrue(forecast.current.isDay)

        assertEquals(3, forecast.hourly.size)
        assertEquals(35, forecast.hourly[1].precipitationProbability)
        assertEquals(null, forecast.hourly[2].precipitationProbability)

        assertEquals(3, forecast.daily.size)
        assertEquals(33.1, forecast.daily[0].tempMax, 0.01)
        assertEquals("2026-09-29", forecast.daily[2].dateIso)
    }

    /**
     * Regression test. Open-Meteo pads the daily arrays to the requested
     * number of days and fills the tail with nulls once the model run ends.
     * This used to throw and surface as "Something went wrong".
     */
    @Test
    fun `null padded days are parsed and skipped`() {
        val padded = """
        {
          "timezone": "Asia/Bangkok",
          "daily": {
            "time": ["2026-09-28", "2026-09-29", "2026-10-12"],
            "weather_code": [53, 3, null],
            "temperature_2m_max": [33.6, 33.9, null],
            "temperature_2m_min": [26.4, 26.7, null],
            "precipitation_sum": [1.5, 0.0, null],
            "precipitation_probability_max": [49, 12, null],
            "sunrise": ["2026-09-28T05:46", "2026-09-29T05:46", "2026-10-12T05:50"],
            "sunset": ["2026-09-28T17:47", "2026-09-29T17:46", "2026-10-12T17:34"]
          },
          "hourly": {
            "time": ["2026-09-28T00:00", "2026-09-28T01:00"],
            "temperature_2m": [27.1, null],
            "precipitation_probability": [0, null],
            "precipitation": [0.0, null],
            "weather_code": [0, null],
            "wind_speed_10m": [12.0, null],
            "is_day": [0, null]
          }
        }
        """.trimIndent()

        val dto = json.decodeFromString(ForecastResponse.serializer(), padded)
        val forecast = dto.toDomain(City.HANOI, fetchedAtMillis = 0L)

        assertEquals(2, forecast.daily.size)
        assertEquals("2026-09-29", forecast.daily[1].dateIso)
        assertEquals(1, forecast.hourly.size)
    }

    @Test
    fun `missing blocks do not crash the mapper`() {
        val dto = json.decodeFromString(ForecastResponse.serializer(), """{"timezone":"auto"}""")
        val forecast = dto.toDomain(City.HANOI, fetchedAtMillis = 0L)
        assertTrue(forecast.hourly.isEmpty())
        assertTrue(forecast.daily.isEmpty())
    }
}
