package com.twinpark.music.domain

import com.twinpark.music.car.gearOf
import com.twinpark.music.sensing.DrivingSoundSensor
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
    fun androidGearValuesMapToAppGears() {
        assertEquals(DrivingGear.Park, gearOf(4))
        assertEquals(DrivingGear.Reverse, gearOf(2))
        assertEquals(DrivingGear.Neutral, gearOf(1))
        assertEquals(DrivingGear.Drive, gearOf(8))
        assertEquals(DrivingGear.Drive, gearOf(16))
        assertEquals(DrivingGear.Drive, gearOf(4096))
        assertNull(gearOf(0))
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
