package com.vnweather.app

import com.vnweather.app.domain.AirUvParser
import com.vnweather.app.domain.AqiCategory
import com.vnweather.app.domain.ExtraError
import com.vnweather.app.domain.ExtraException
import com.vnweather.app.domain.Geo
import com.vnweather.app.domain.UvCategory
import com.vnweather.app.domain.UvReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class AirUvTest {

    @Test
    fun aqiBandsFollowUsEpa() {
        assertEquals(AqiCategory.GOOD, AqiCategory.of(0))
        assertEquals(AqiCategory.GOOD, AqiCategory.of(50))
        assertEquals(AqiCategory.MODERATE, AqiCategory.of(51))
        assertEquals(AqiCategory.MODERATE, AqiCategory.of(100))
        assertEquals(AqiCategory.SENSITIVE, AqiCategory.of(101))
        assertEquals(AqiCategory.UNHEALTHY, AqiCategory.of(185))
        assertEquals(AqiCategory.VERY_UNHEALTHY, AqiCategory.of(300))
        assertEquals(AqiCategory.HAZARDOUS, AqiCategory.of(301))
        assertEquals(AqiCategory.HAZARDOUS, AqiCategory.of(999))
    }

    @Test
    fun uvBandsUseRoundedValue() {
        assertEquals(UvCategory.LOW, UvCategory.of(0.0))
        assertEquals(UvCategory.LOW, UvCategory.of(2.4))
        assertEquals(UvCategory.MODERATE, UvCategory.of(2.5))
        assertEquals(UvCategory.MODERATE, UvCategory.of(5.0))
        assertEquals(UvCategory.HIGH, UvCategory.of(6.8))
        assertEquals(UvCategory.VERY_HIGH, UvCategory.of(9.1))
        assertEquals(UvCategory.VERY_HIGH, UvCategory.of(10.4))
        assertEquals(UvCategory.EXTREME, UvCategory.of(10.5))
        assertEquals(UvCategory.LOW, UvCategory.of(-1.0))
    }

    @Test
    fun parsesWaqiFeed() {
        val body = """
            {"status":"ok","data":{"aqi":93,"idx":1,"attributions":[
              {"url":"x","name":"Hanoi Environment Agency"},
              {"url":"https://waqi.info/","name":"World Air Quality Index Project"}],
             "city":{"geo":[21.02,105.85],"name":"Hà Nội - Hoàn Kiếm"},
             "dominentpol":"pm25","iaqi":{"pm25":{"v":93}},
             "time":{"s":"2026-10-01 11:00:00","tz":"+07:00","iso":"2026-10-01T11:00:00+07:00"}}}
        """.trimIndent()
        val air = AirUvParser.parseWaqi(body, 42L)
        assertEquals(93, air.aqi)
        assertEquals("pm25", air.dominantPollutant)
        assertEquals("Hà Nội - Hoàn Kiếm", air.stationName)
        assertEquals(21.02, air.stationLatitude!!, 1e-9)
        assertEquals(listOf("Hanoi Environment Agency"), air.sources)
        assertEquals("2026-10-01T11:00:00+07:00", air.measuredAtIso)
        assertEquals(42L, air.fetchedAtMillis)
    }

    @Test
    fun waqiErrorsAreClassified() {
        expect(ExtraError.INVALID_TOKEN, """{"status":"error","data":"Invalid key"}""")
        expect(ExtraError.NO_DATA, """{"status":"error","data":"Unknown station"}""")
        expect(ExtraError.NETWORK, """{"status":"error","data":"Over quota"}""")
        expect(ExtraError.NO_DATA, """{"status":"ok","data":{"aqi":"-","city":{"name":"X"}}}""")
        expect(ExtraError.NO_DATA, "<html>")
    }

    private fun expect(reason: ExtraError, body: String) {
        try {
            AirUvParser.parseWaqi(body, 0L)
            fail("expected $reason")
        } catch (e: ExtraException) {
            assertEquals(reason, e.reason)
        }
    }

    @Test
    fun parsesUvAndReadsCurrentHourAndPeak() {
        val body = """
            {"ok":true,"timezone":{"id":"Asia/Bangkok"},
             "now":{"date":"2026-10-01","time":"10:00:00","uv_index":6.8},
             "today":{"date":"2026-10-01","max":{"time":"12:00:00","uv_index":9.1}},
             "hourly":[
               {"date":"2026-10-01","time":"10:00:00","uv_index":6.8},
               {"date":"2026-10-01","time":"11:00:00","uv_index":8.4},
               {"date":"2026-10-01","time":"12:00:00","uv_index":9.1},
               {"date":"2026-10-02","time":"11:00:00","uv_index":7.1}]}
        """.trimIndent()
        val uv = AirUvParser.parseUv(body, 1L)
        assertEquals("Asia/Bangkok", uv.timezone)
        assertEquals(4, uv.hourly.size)
        assertEquals(8.4, UvReader.valueAt(uv, "2026-10-01", "11")!!, 1e-9)
        assertNull(UvReader.valueAt(uv, "2026-10-03", "11"))
        val peak = UvReader.peakOn(uv, "2026-10-01")!!
        assertEquals(9.1, peak.uv, 1e-9)
        assertEquals("12:00", peak.time)
        assertEquals(7.1, UvReader.peakOn(uv, "2026-10-02")!!.uv, 1e-9)
        assertEquals(6.8, uv.nowValue!!, 1e-9)
    }

    @Test
    fun distanceIsSane() {
        // Hoan Kiem to Noi Bai airport is roughly 21 km.
        val km = Geo.distanceKm(21.0285, 105.8542, 21.2187, 105.8042)
        assertEquals(21.7, km, 1.5)
    }
}

class OpenMeteoAirTest {

    @org.junit.Test
    fun parsesUsAqiAndDominantPollutant() {
        val body = """
            {"latitude":21.0,"longitude":105.9,"utc_offset_seconds":25200,"timezone":"Asia/Bangkok",
             "current":{"time":"2026-10-01T17:00","interval":3600,"us_aqi":174,"us_aqi_pm2_5":95,
             "us_aqi_pm10":32,"us_aqi_ozone":174,"us_aqi_nitrogen_dioxide":10,
             "us_aqi_sulphur_dioxide":14,"us_aqi_carbon_monoxide":4}}
        """.trimIndent()
        val air = com.vnweather.app.domain.AirUvParser.parseOpenMeteoAir(body, 5L)
        org.junit.Assert.assertEquals(174, air.aqi)
        org.junit.Assert.assertEquals("o3", air.dominantPollutant)
        org.junit.Assert.assertEquals("open_meteo", air.provider)
        org.junit.Assert.assertEquals("2026-10-01T17:00:00+07:00", air.measuredAtIso)
    }

    @org.junit.Test
    fun nullAqiOrErrorIsNoData() {
        for (body in listOf(
            """{"current":{"time":"2026-10-01T17:00","us_aqi":null}}""",
            """{"error":true,"reason":"bad"}"""
        )) {
            try {
                com.vnweather.app.domain.AirUvParser.parseOpenMeteoAir(body, 0L)
                org.junit.Assert.fail()
            } catch (e: com.vnweather.app.domain.ExtraException) {
                org.junit.Assert.assertEquals(com.vnweather.app.domain.ExtraError.NO_DATA, e.reason)
            }
        }
    }

    @org.junit.Test
    fun offsetLabel() {
        org.junit.Assert.assertEquals("+07:00", com.vnweather.app.domain.AirUvParser.offsetLabel(25200))
        org.junit.Assert.assertEquals("-03:30", com.vnweather.app.domain.AirUvParser.offsetLabel(-12600))
    }
}
