package com.vnweather.app

import com.vnweather.app.ui.main.RainDrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RainDropTest {

    @Test
    fun roundsToNearestQuarter() {
        assertEquals(0, RainDrop.quarters(1))
        assertEquals(0, RainDrop.quarters(12))
        assertEquals(1, RainDrop.quarters(13))
        assertEquals(2, RainDrop.quarters(50))
        assertEquals(3, RainDrop.quarters(70))
        assertEquals(4, RainDrop.quarters(90))
        assertEquals(4, RainDrop.quarters(100))
    }

    @Test
    fun emptyAndFullLevels() {
        assertEquals(0, RainDrop.level(1))
        assertEquals(10_000, RainDrop.level(90))
    }

    @Test
    fun levelsRiseWithEachStep() {
        val levels = listOf(0, 25, 50, 75, 100).map { RainDrop.level(it) }
        assertTrue(levels.zipWithNext().all { (a, b) -> b > a })
    }
}
