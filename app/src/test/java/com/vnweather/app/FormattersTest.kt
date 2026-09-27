package com.vnweather.app

import com.vnweather.app.data.local.SettingsStore
import com.vnweather.app.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormattersTest {

    @Test
    fun `temperature rounds and adds the right unit`() {
        assertEquals("31\u00B0C", Formatters.temperature(31.4, SettingsStore.UNIT_CELSIUS))
        assertEquals("89\u00B0F", Formatters.temperature(88.6, SettingsStore.UNIT_FAHRENHEIT))
    }

    @Test
    fun `wind switches between kmh and ms`() {
        assertEquals("11 km/h", Formatters.wind(11.3, SettingsStore.UNIT_KMH))
        assertEquals("3.1 m/s", Formatters.wind(3.14, SettingsStore.UNIT_MS))
    }

    @Test
    fun `hour label uses 24 hour time`() {
        assertEquals("14:00", Formatters.hourLabel("2026-09-27T14:00", Locale.US))
    }

    @Test
    fun `wind direction maps degrees to eight compass points`() {
        assertEquals(0, Formatters.windDirectionIndex(0))
        assertEquals(2, Formatters.windDirectionIndex(90))
        assertEquals(4, Formatters.windDirectionIndex(180))
        assertEquals(6, Formatters.windDirectionIndex(270))
        assertEquals(0, Formatters.windDirectionIndex(360))
    }
}
