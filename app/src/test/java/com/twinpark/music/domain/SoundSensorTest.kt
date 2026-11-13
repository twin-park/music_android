package com.twinpark.music.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundSensorTest {
    @After
    fun reset() {
        DebugSoundSensor.fromScreen = emptySet()
        DebugSoundSensor.fromAdb = emptySet()
    }

    @Test
    fun debugSensorMergesScreenAndAdbWords() {
        DebugSoundSensor.fromScreen = setOf("웃음")
        DebugSoundSensor.fromAdb = setOf("파도", "웃음")
        assertEquals(setOf("웃음", "파도"), DebugSoundSensor.heard())
    }

    @Test
    fun debugSensorStartsEmpty() {
        assertEquals(emptySet<String>(), DebugSoundSensor.heard())
    }

    @Test
    fun heardAllUnionsEverySensor() {
        val sensors = listOf(SoundSensor { setOf("비") }, SoundSensor { setOf("비", "천둥") }, SoundSensor { emptySet() })
        assertEquals(setOf("비", "천둥"), sensors.heardAll())
    }

    @Test
    fun noSensorsHearNothing() {
        assertEquals(emptySet<String>(), emptyList<SoundSensor>().heardAll())
    }
}
