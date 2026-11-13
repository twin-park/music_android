package com.twinpark.music

import com.twinpark.music.domain.Mood
import com.twinpark.music.domain.TimeOfDay
import com.twinpark.music.domain.Weather
import com.twinpark.music.domain.WeatherReport
import com.twinpark.music.domain.WeatherSource
import com.twinpark.music.domain.WeatherState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WeatherStateTest {
    @Test fun 시간대_경계() {
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.of(4))
        assertEquals(TimeOfDay.DAWN, TimeOfDay.of(5))
        assertEquals(TimeOfDay.DAWN, TimeOfDay.of(7))
        assertEquals(TimeOfDay.DAY, TimeOfDay.of(8))
        assertEquals(TimeOfDay.DAY, TimeOfDay.of(16))
        assertEquals(TimeOfDay.DUSK, TimeOfDay.of(17))
        assertEquals(TimeOfDay.DUSK, TimeOfDay.of(19))
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.of(20))
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.of(0))
    }

    @Test fun 시간대별_장면() {
        assertEquals(Mood.Fresh, WeatherState(TimeOfDay.DAWN).scene())
        assertEquals(Mood.Bright, WeatherState(TimeOfDay.DAY).scene())
        assertEquals(Mood.Energetic, WeatherState(TimeOfDay.DUSK).scene())
        assertEquals(Mood.Sleep, WeatherState(TimeOfDay.NIGHT).scene())
    }

    @Test fun 비가_오면_시간대와_상관없이_비_장면() {
        TimeOfDay.values().forEach { assertEquals(Mood.Calm, WeatherState(it, raining = true).scene()) }
    }

    @Test fun 수동값이_있으면_자동보다_우선() {
        val manual = WeatherState(TimeOfDay.NIGHT, raining = true)
        assertEquals(manual, WeatherSource.resolve(manual))
    }

    @Test fun 오브_번호는_날씨_대응과_같다() {
        assertEquals(0, WeatherState(TimeOfDay.DAWN).scene().orbPose)   // 초록 새벽
        assertEquals(1, WeatherState(TimeOfDay.NIGHT).scene().orbPose)  // 보라 밤
        assertEquals(2, WeatherState(TimeOfDay.DAY, true).scene().orbPose) // 파랑 비
        assertEquals(3, WeatherState(TimeOfDay.DUSK).scene().orbPose)   // 주황 황혼
        assertEquals(4, WeatherState(TimeOfDay.DAY).scene().orbPose)    // 청량 낮
    }

    @Test fun 눈이_오면_눈_장면이_비보다_우선() {
        TimeOfDay.values().forEach { assertEquals(Mood.Snow, WeatherState(it, snowing = true).scene()) }
        assertEquals(Mood.Snow, WeatherState(TimeOfDay.DAY, raining = true, snowing = true).scene())
        assertEquals(5, WeatherState(TimeOfDay.NIGHT, snowing = true).scene().orbPose)
    }

    private val rainReport = WeatherReport(
        raining = true, snowing = false, cloudsPercent = 90, humidityPercent = 70, windKmh = 12, precipitationMm = 2.4f, fetchedAtMs = 1_000L,
    )
    private val noon = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 12) }

    @Test fun 신선한_날씨값은_비와_상세값에_반영() {
        val state = WeatherSource.auto(noon, rainReport, nowMs = 2_000L)
        assertEquals(TimeOfDay.DAY, state.time)
        assertTrue(state.raining)
        assertEquals(Weather(cloudy = 90, humidity = 70, windKmh = 12, rainMm = 2), state.detail)
    }

    @Test fun 오래된_날씨값은_버리고_시계만_쓴다() {
        val state = WeatherSource.auto(noon, rainReport, nowMs = 1_000L + WeatherReport.MAX_AGE_MS + 1)
        assertFalse(state.raining)
        assertEquals(null, state.detail)
    }

    @Test fun 날씨값이_없으면_시계만_쓴다() {
        assertEquals(WeatherState(TimeOfDay.DAY), WeatherSource.auto(noon, report = null))
    }

    @Test fun 수동값은_날씨값보다_우선() {
        val manual = WeatherState(TimeOfDay.NIGHT, snowing = true)
        assertEquals(manual, WeatherSource.resolve(manual, noon, rainReport, nowMs = 2_000L))
    }

    @Test fun 분위기_순환은_목록_끝에서_처음으로_돌아간다() {
        val ids = Mood.EngineMoods.map { it.id }
        assertEquals(ids[1], Mood.stepEngineId(ids[0], 1))
        assertEquals(ids[0], Mood.stepEngineId(ids.last(), 1))
        assertEquals(ids.last(), Mood.stepEngineId(ids[0], -1))
        assertEquals(ids[0], Mood.stepEngineId("모르는ID", 1))
        assertEquals(ids[0], Mood.stepEngineId(null, -1))
    }
}
