package com.vnweather.app

import com.vnweather.app.data.local.ForecastCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class CacheFreshnessTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `data from ten minutes ago is fresh for a 45 minute rule`() {
        val fetched = now - TimeUnit.MINUTES.toMillis(10)
        assertTrue(ForecastCache.isFresh(fetched, 45, now))
    }

    @Test
    fun `data from two hours ago is stale`() {
        val fetched = now - TimeUnit.HOURS.toMillis(2)
        assertFalse(ForecastCache.isFresh(fetched, 45, now))
    }

    @Test
    fun `a timestamp in the future is treated as stale`() {
        assertFalse(ForecastCache.isFresh(now + 10_000, 45, now))
    }
}
