package com.vnweather.app

import com.vnweather.app.domain.WeatherBackgroundMapper
import org.breezyweather.ui.theme.weatherView.WeatherView
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherBackgroundMapperTest {

    @Test
    fun `clear sky animates as clear`() {
        assertEquals(WeatherView.WEATHER_KIND_CLEAR, WeatherBackgroundMapper.weatherKind(0))
    }

    @Test
    fun `every rain flavour animates as rain`() {
        listOf(51, 55, 61, 63, 65, 80, 82).forEach { code ->
            assertEquals(
                "code $code",
                WeatherView.WEATHER_KIND_RAINY,
                WeatherBackgroundMapper.weatherKind(code)
            )
        }
    }

    @Test
    fun `thunder severity picks the stronger animation`() {
        assertEquals(WeatherView.WEATHER_KIND_THUNDER, WeatherBackgroundMapper.weatherKind(95))
        assertEquals(
            WeatherView.WEATHER_KIND_THUNDERSTORM,
            WeatherBackgroundMapper.weatherKind(97)
        )
    }

    @Test
    fun `fog and freezing rain are not treated as plain rain`() {
        assertEquals(WeatherView.WEATHER_KIND_FOG, WeatherBackgroundMapper.weatherKind(45))
        assertEquals(WeatherView.WEATHER_KIND_SLEET, WeatherBackgroundMapper.weatherKind(66))
    }

    @Test
    fun `unknown codes fall back to no animation`() {
        assertEquals(WeatherView.WEATHER_KIND_NULL, WeatherBackgroundMapper.weatherKind(4242))
    }
}
