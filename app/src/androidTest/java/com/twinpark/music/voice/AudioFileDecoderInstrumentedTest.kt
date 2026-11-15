package com.twinpark.music.voice

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 소리 파일 읽기: 직접 읽는 WAV(다른 표본 수·스테레오)와 기기 디코더를 거치는 형식(flac)이 모두 16kHz 모노로 나오는지 본다. */
@RunWith(AndroidJUnit4::class)
class AudioFileDecoderInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun stereo44kWavBecomesMono16k() {
        val f = File(instrumentation.targetContext.cacheDir, "other.wav")
        instrumentation.context.assets.open("voice_in/other_format_quiet.wav").use { i -> f.outputStream().use { i.copyTo(it) } }
        val pcm = AudioFileDecoder.decode(f)
        Log.i("DecoderTest", "wav 44.1kHz 스테레오 -> ${pcm.size}표본(${pcm.size * 1000L / 16_000}ms)")
        assertTrue("0.5초~5초 사이여야 함: ${pcm.size}", pcm.size in 8_000..80_000)
        assertTrue("소리가 있어야 함", pcm.any { kotlin.math.abs(it.toInt()) > 1_000 })
    }

    @Test
    fun flacGoesThroughTheDeviceDecoder() {
        val f = File(instrumentation.targetContext.cacheDir, "sea.flac")
        instrumentation.targetContext.assets.open("bgm/amb/sea.flac").use { i -> f.outputStream().use { i.copyTo(it) } }
        val pcm = AudioFileDecoder.decode(f)
        Log.i("DecoderTest", "flac -> ${pcm.size}표본(${pcm.size * 1000L / 16_000}ms)")
        assertTrue("소리가 나와야 함: ${pcm.size}", pcm.size > 16_000)
        assertTrue("소리가 있어야 함", pcm.any { kotlin.math.abs(it.toInt()) > 100 })
    }
}
