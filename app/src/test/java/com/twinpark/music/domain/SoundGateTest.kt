package com.twinpark.music.domain

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundGateTest {
    @After fun reset() = SoundGate.clear()

    @Test
    fun `처음에는 열려 있다`() {
        assertFalse(SoundGate.isMuted(0))
        assertFalse(SoundGate.isMuted(System.currentTimeMillis()))
    }

    @Test
    fun `정한 시각까지만 닫힌다`() {
        SoundGate.muteUntil(1_000)
        assertTrue(SoundGate.isMuted(999))
        assertFalse(SoundGate.isMuted(1_000))
    }

    @Test
    fun `더 이른 시각을 주면 짧아지고 지우면 열린다`() {
        SoundGate.muteUntil(15_000)
        SoundGate.muteUntil(4_500)
        assertFalse(SoundGate.isMuted(5_000))
        SoundGate.muteUntil(10_000)
        SoundGate.clear()
        assertFalse(SoundGate.isMuted(0))
    }
}
