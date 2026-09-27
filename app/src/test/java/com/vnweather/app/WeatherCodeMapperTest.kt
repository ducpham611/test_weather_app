package com.vnweather.app

import com.vnweather.app.domain.WeatherCodeMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCodeMapperTest {

    @Test
    fun `clear sky maps to the clear string`() {
        assertEquals(R.string.wmo_0, WeatherCodeMapper.descriptionRes(0))
    }

    @Test
    fun `fog codes share one description`() {
        assertEquals(
            WeatherCodeMapper.descriptionRes(45),
            WeatherCodeMapper.descriptionRes(48)
        )
    }

    @Test
    fun `unknown codes fall back instead of crashing`() {
        assertEquals(R.string.wmo_unknown, WeatherCodeMapper.descriptionRes(1234))
    }

    @Test
    fun `day and night icons differ for a clear sky`() {
        val day = WeatherCodeMapper.iconRes(0, isDay = true)
        val night = WeatherCodeMapper.iconRes(0, isDay = false)
        assertTrue(day != night)
    }

    @Test
    fun `rain detection covers rain showers and storms`() {
        assertTrue(WeatherCodeMapper.isRainy(63))
        assertTrue(WeatherCodeMapper.isRainy(81))
        assertTrue(WeatherCodeMapper.isRainy(95))
        assertFalse(WeatherCodeMapper.isRainy(0))
        assertFalse(WeatherCodeMapper.isRainy(3))
    }
}
