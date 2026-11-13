package com.twinpark.music.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleWindowTest {
    private fun pcm(count: Int, value: Short = 0) = ShortArray(count) { value }

    @Test
    fun emitsNothingUntilAFullWindowArrives() {
        val window = SampleWindow(size = 10, hop = 5)
        assertTrue(window.push(pcm(9), 9).isEmpty())
        assertEquals(1, window.push(pcm(1), 1).size)
    }

    @Test
    fun windowsOverlapByTheHop() {
        val window = SampleWindow(size = 4, hop = 2)
        val input = ShortArray(8) { (it * 1000).toShort() }
        val out = window.push(input, 8)
        assertEquals(3, out.size)
        assertEquals(0f, out[0][0], 1e-6f)
        assertEquals(2000f / 32768f, out[1][0], 1e-6f)
        assertEquals(4000f / 32768f, out[2][0], 1e-6f)
    }

    @Test
    fun splitInputGivesTheSameWindowsAsOneBlock() {
        val input = ShortArray(20) { (it * 100).toShort() }
        val whole = SampleWindow(6, 3).push(input, 20)
        val parts = SampleWindow(6, 3).let { w -> w.push(input.copyOfRange(0, 7), 7) + w.push(input.copyOfRange(7, 20), 13) }
        assertEquals(whole.size, parts.size)
        whole.indices.forEach { assertTrue(whole[it].contentEquals(parts[it])) }
    }

    @Test
    fun onlyTheGivenCountIsUsed() {
        val window = SampleWindow(size = 3, hop = 3)
        assertTrue(window.push(ShortArray(10) { 1000 }, 2).isEmpty())
    }
}

class SoundListenerTest {
    private class Clock(var now: Long = 1_000_000L) : () -> Long {
        override fun invoke() = now
    }

    private val fourSeconds = ShortArray(SoundListener.SAMPLE_RATE * SoundListener.WINDOW_SECONDS)

    @Test
    fun hearsNothingBeforeTheFirstWindow() {
        val listener = SoundListener({ _, _ -> setOf("웃음") }, Clock())
        assertTrue(listener.heard().isEmpty())
    }

    @Test
    fun reportsTheLatestWordsFromTheClassifier() {
        val listener = SoundListener({ _, _ -> setOf("웃음") }, Clock())
        listener.process(fourSeconds, fourSeconds.size)
        assertEquals(setOf("웃음"), listener.heard())
    }

    @Test
    fun oldResultsExpire() {
        val clock = Clock()
        val listener = SoundListener({ _, _ -> setOf("웃음") }, clock)
        listener.process(fourSeconds, fourSeconds.size)
        clock.now += SoundListener.FRESH_MS
        assertEquals(setOf("웃음"), listener.heard())
        clock.now += 1
        assertTrue(listener.heard().isEmpty())
    }

    @Test
    fun newerWindowReplacesTheOlderWords() {
        var words = setOf("웃음")
        val listener = SoundListener({ _, _ -> words }, Clock())
        listener.process(fourSeconds, fourSeconds.size)
        words = setOf("울음")
        listener.process(ShortArray(SoundListener.SAMPLE_RATE * SoundListener.HOP_SECONDS), SoundListener.SAMPLE_RATE * SoundListener.HOP_SECONDS)
        assertEquals(setOf("울음"), listener.heard())
    }

    @Test
    fun classifierGetsSixteenKilohertzWindows() {
        var seen = 0 to 0
        val listener = SoundListener({ samples, rate -> seen = samples.size to rate; emptySet() }, Clock())
        listener.process(fourSeconds, fourSeconds.size)
        assertEquals(SoundListener.SAMPLE_RATE * SoundListener.WINDOW_SECONDS to SoundListener.SAMPLE_RATE, seen)
    }

    @Test
    fun noClassifierHearsNothing() {
        val listener = SoundListener(NoSoundClassifier, Clock())
        listener.process(fourSeconds, fourSeconds.size)
        assertTrue(listener.heard().isEmpty())
    }
}
