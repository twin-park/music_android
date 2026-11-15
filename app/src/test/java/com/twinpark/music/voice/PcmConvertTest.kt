package com.twinpark.music.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmConvertTest {
    @Test
    fun `16kHz 모노는 그대로`() {
        val s = shortArrayOf(1, -2, 300, 4)
        assertArrayEquals(s, PcmConvert.toMono16k(s, 1, 16_000))
    }

    @Test
    fun `스테레오는 평균으로 합친다`() {
        assertArrayEquals(shortArrayOf(5, -10, 0), PcmConvert.toMono16k(shortArrayOf(0, 10, -20, 0, -4, 4), 2, 16_000))
    }

    @Test
    fun `표본 수를 16kHz에 맞춘다`() {
        assertEquals(1_600, PcmConvert.toMono16k(ShortArray(4_800), 1, 48_000).size)      // 48kHz 0.1초
        assertEquals(1_600, PcmConvert.toMono16k(ShortArray(800), 1, 8_000).size)         // 8kHz 0.1초
        assertEquals(1_600, PcmConvert.toMono16k(ShortArray(8_820), 2, 44_100).size)      // 44.1kHz 스테레오 0.1초(4410 프레임)
    }

    @Test
    fun `내려 줄일 때 값이 크게 틀어지지 않는다`() {
        val ramp = ShortArray(3_000) { (it * 10).toShort() }                              // 48kHz에서 느리게 오르는 값
        val out = PcmConvert.toMono16k(ramp, 1, 48_000)
        assertEquals(1_000, out.size)
        assertEquals(0, out[0].toInt())
        assertEquals(300, out[10].toInt())                                                 // 3배 간격: 10번째는 원래 30번째 값
    }

    @Test
    fun `빈 소리는 빈 소리`() {
        assertEquals(0, PcmConvert.toMono16k(ShortArray(0), 2, 44_100).size)
    }
}
