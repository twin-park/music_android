package com.twinpark.music.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherSituationTest {
    private fun report(raining: Boolean = false, snowing: Boolean = false, clouds: Int = 0, mm: Float = 0f, at: Long = 1_000) =
        WeatherReport(raining, snowing, clouds, 50, 5, mm, at)

    @Test
    fun `날씨 값을 날씨 칸으로 바꾼다`() {
        assertEquals("snow", WeatherSituation.kindOf(report(snowing = true), false))
        assertEquals("rain", WeatherSituation.kindOf(report(raining = true, mm = 1f), false))
        assertEquals("storm", WeatherSituation.kindOf(report(raining = true, mm = 8f), false))
        assertEquals("cloudy", WeatherSituation.kindOf(report(clouds = 90), false))
        assertEquals("clear", WeatherSituation.kindOf(report(clouds = 10), false))
    }

    @Test
    fun `와이퍼가 켜져 있으면 값이 없어도 비다`() {
        assertEquals("rain", WeatherSituation.kindOf(null, true))
        assertEquals("rain", WeatherSituation.kindOf(report(clouds = 10), true))
        assertNull(WeatherSituation.kindOf(null, false))
    }

    @Test
    fun `바뀔 때만 보낸다`() {
        val w = WeatherSituation()
        assertEquals("""{"weather":{"kind":"clear"}}""", w.update(report(), false, 2_000))
        assertNull(w.update(report(), false, 3_000))
        assertEquals("""{"weather":{"kind":"rain"}}""", w.update(report(raining = true), false, 4_000))
    }

    @Test
    fun `오래된 값은 모르는 것으로 본다`() {
        val w = WeatherSituation()
        assertNull(w.update(report(at = 0), false, WeatherReport.MAX_AGE_MS + 1))
    }

    @Test
    fun `사람이 고른 칸과 못 받은 경우`() {
        val w = WeatherSituation()
        assertNull(w.update(report(), false, 2_000, setOf("weather.kind")))
        assertEquals("""{"weather":{"kind":"clear"}}""", w.update(report(), false, 2_000))
        w.reset()
        assertEquals("""{"weather":{"kind":"clear"}}""", w.update(report(), false, 3_000))
    }

    @Test
    fun `사람이 고른 칸은 날씨가 건드리지 않는다`() {
        assertNull(WeatherSituation().update(report(clouds = 10), false, 2_000, userKeys = setOf("weather.kind")))
    }
}
