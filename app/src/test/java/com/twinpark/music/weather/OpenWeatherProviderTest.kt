package com.twinpark.music.weather

import com.twinpark.music.domain.GeoPoint
import com.twinpark.music.domain.LocationSource
import com.twinpark.music.domain.WeatherProvider
import com.twinpark.music.domain.WeatherReport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenWeatherParserTest {
    private fun parse(code: Int, extra: String = "") = OpenWeatherParser.parse(
        """{"weather":[{"id":$code}],"main":{"humidity":88},"clouds":{"all":40},"wind":{"speed":2.0}$extra}""", 1_000L,
    )!!

    @Test
    fun mistIsNeitherRainNorSnow() {
        val r = parse(701)
        assertFalse(r.raining)
        assertFalse(r.snowing)
        assertEquals(40, r.cloudsPercent)
        assertEquals(88, r.humidityPercent)
        assertEquals(7, r.windKmh)
    }

    @Test
    fun rainCodesRain() {
        assertTrue(parse(500).raining)
        assertTrue(parse(300).raining)
        assertTrue(parse(211).raining)
    }

    @Test
    fun snowCodesSnow() {
        val r = parse(601)
        assertTrue(r.snowing)
        assertFalse(r.raining)
    }

    @Test
    fun sleetCountsAsSnowOnly() {
        val r = parse(612, ""","rain":{"1h":1.2}""")
        assertTrue(r.snowing)
        assertFalse(r.raining)
    }

    @Test
    fun precipitationAmountWithoutRainCodeStillRains() {
        assertTrue(parse(803, ""","rain":{"1h":0.4}""").raining)
    }

    @Test
    fun precipitationSumsRainAndSnow() {
        assertEquals(1.5f, parse(615, ""","rain":{"1h":1.0},"snow":{"1h":0.5}""").precipitationMm, 1e-4f)
    }

    @Test
    fun missingOptionalFieldsDefaultToZero() {
        val r = OpenWeatherParser.parse("""{"weather":[{"id":800}],"main":{}}""", 0L)!!
        assertEquals(0, r.cloudsPercent)
        assertEquals(0, r.windKmh)
        assertEquals(0f, r.precipitationMm, 0f)
    }

    @Test
    fun brokenResponseIsNull() {
        assertNull(OpenWeatherParser.parse("not json", 0L))
        assertNull(OpenWeatherParser.parse("""{"cod":401,"message":"Invalid API key"}""", 0L))
    }
}

class WeatherUpdaterTest {
    private val here = LocationSource { GeoPoint(1.0, 2.0) }
    private val report = WeatherReport(raining = true, snowing = false, cloudsPercent = 1, humidityPercent = 2, windKmh = 3, precipitationMm = 0f, fetchedAtMs = 0L)

    @Test
    fun passesReportToCallbackAndAsksForTheLocation() = runBlocking {
        var asked: GeoPoint? = null
        val provider = object : WeatherProvider {
            override suspend fun current(at: GeoPoint): WeatherReport? = report.also { asked = at }
        }
        val received = mutableListOf<WeatherReport>()
        assertTrue(WeatherUpdater(provider, here, received::add).refreshOnce())
        assertEquals(listOf(report), received)
        assertEquals(GeoPoint(1.0, 2.0), asked)
    }

    @Test
    fun failureKeepsPreviousValue() = runBlocking {
        val provider = object : WeatherProvider {
            override suspend fun current(at: GeoPoint): WeatherReport? = null
        }
        val received = mutableListOf<WeatherReport>()
        assertFalse(WeatherUpdater(provider, here, received::add).refreshOnce())
        assertTrue(received.isEmpty())
    }
}
