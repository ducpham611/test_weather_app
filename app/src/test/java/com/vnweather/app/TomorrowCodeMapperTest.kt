package com.vnweather.app

import com.vnweather.app.domain.TomorrowCodeMapper
import com.vnweather.app.domain.WeatherCodeMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TomorrowCodeMapperTest {

    @Test
    fun `clear sky maps to WMO 0`() {
        assertEquals(0, TomorrowCodeMapper.toWmo(1000))
    }

    @Test
    fun `thunderstorm maps to WMO 95`() {
        assertEquals(95, TomorrowCodeMapper.toWmo(8000))
    }

    @Test
    fun `heavy rain keeps a rain icon`() {
        val wmo = TomorrowCodeMapper.toWmo(4201)
        assertEquals(65, wmo)
        assertEquals(R.drawable.ic_weather_rain, WeatherCodeMapper.iconRes(wmo, true))
    }

    @Test
    fun `unknown codes fall back instead of pretending to be clear`() {
        assertNotEquals(0, TomorrowCodeMapper.toWmo(null))
        assertEquals(R.string.wmo_unknown, WeatherCodeMapper.descriptionRes(TomorrowCodeMapper.toWmo(123456)))
    }
}
