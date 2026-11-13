package com.twinpark.music.domain

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import com.twinpark.music.car.gearOf
import com.twinpark.music.car.isWiping
import com.twinpark.music.sensing.DrivingSoundSensor
import com.twinpark.music.sensing.WiperRainSensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingStateTest {
    @Test
    fun unknownSpeedIsNotMoving() {
        assertFalse(DrivingState().moving)
    }

    @Test
    fun crawlingBelowThresholdIsNotMoving() {
        assertFalse(DrivingState(speedKmh = DrivingState.MOVING_KMH - 0.1f).moving)
        assertTrue(DrivingState(speedKmh = DrivingState.MOVING_KMH).moving)
    }

    @Test
    fun sdkGearsMapToAppGears() {
        assertEquals(DrivingGear.Park, gearOf(VehicleGear.GEAR_PARK))
        assertEquals(DrivingGear.Reverse, gearOf(VehicleGear.GEAR_REVERSE))
        assertEquals(DrivingGear.Neutral, gearOf(VehicleGear.GEAR_NEUTRAL))
        assertEquals(DrivingGear.Drive, gearOf(VehicleGear.GEAR_DRIVE))
        assertEquals(DrivingGear.Drive, gearOf(VehicleGear.GEAR_1))
        assertEquals(DrivingGear.Drive, gearOf(VehicleGear.GEAR_9))
        assertNull(gearOf(VehicleGear.GEAR_UNKNOWN))
        assertNull(gearOf(null))
    }

    @Test
    fun onlyRunningWipersCountAsRain() {
        assertTrue(isWiping(WiperState.ON))
        assertFalse(isWiping(WiperState.OFF))
        assertFalse(isWiping(WiperState.SERVICE))
        assertFalse(isWiping(WiperState.OTHER))
        assertFalse(isWiping(null))
    }
}

class DrivingSoundSensorTest {
    @Test
    fun movingCarHearsRoad() {
        assertEquals(setOf("도로"), DrivingSoundSensor { DrivingState(speedKmh = 40f) }.heard())
    }

    @Test
    fun stoppedOrUnknownCarHearsNothing() {
        assertTrue(DrivingSoundSensor { DrivingState(speedKmh = 0f) }.heard().isEmpty())
        assertTrue(DrivingSoundSensor { DrivingState() }.heard().isEmpty())
    }

    @Test
    fun roadWordLeadsToDriveSituationAtDayAndNight() {
        val words = DrivingSoundSensor { DrivingState(speedKmh = 60f) }.heard()
        assertEquals("낮드라이브", SituationRules.interpret(words, night = false)?.engineMoodId)
        assertEquals("밤드라이브", SituationRules.interpret(words, night = true)?.engineMoodId)
    }

    @Test
    fun soundBasedSituationWinsOverDriving() {
        val words = DrivingSoundSensor { DrivingState(speedKmh = 60f) }.heard() + "아기 울음"
        assertEquals("아이재우기", SituationRules.interpret(words)?.engineMoodId)
    }
}

class WiperRainTest {
    @Test
    fun wipersOnHearRain() {
        assertEquals(setOf("비"), WiperRainSensor { true }.heard())
        assertTrue(WiperRainSensor { false }.heard().isEmpty())
    }

    @Test
    fun rainWordLeadsToRainySituationEvenWhileDriving() {
        val words = WiperRainSensor { true }.heard() + DrivingSoundSensor { DrivingState(speedKmh = 60f) }.heard()
        assertEquals("비오는날", SituationRules.interpret(words)?.engineMoodId)
    }

    @Test
    fun wipersMakeTheSceneRainyWithoutAWeatherReport() {
        val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 12) }
        assertFalse(WeatherSource.auto(cal).raining)
        assertTrue(WeatherSource.auto(cal, wipersOn = true).raining)
        assertEquals(Mood.Calm, WeatherSource.resolve(null, cal, wipersOn = true).scene())
    }

    @Test
    fun wipersDoNotOverrideSnow() {
        val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 12) }
        val report = WeatherReport(raining = false, snowing = true, cloudsPercent = 90, humidityPercent = 80, windKmh = 3, precipitationMm = 1f, fetchedAtMs = 1_000L)
        assertEquals(Mood.Snow, WeatherSource.auto(cal, report, nowMs = 2_000L, wipersOn = true).scene())
    }

    @Test
    fun manualWeatherStillWins() {
        val manual = WeatherState(TimeOfDay.DAY, raining = false)
        assertFalse(WeatherSource.resolve(manual, wipersOn = true).raining)
    }
}
