package com.twinpark.music.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 핵심 경로(주변 소리 → 귀 모델 → 상황 → 지휘자 → 합성)를 마이크 없이 본다.
 * 앱에 들어 있는 실제 빗소리·파도 소리 녹음을 귀 모델에 실제 시간으로 흘려 넣고, 지휘자가 고르는 분위기와 장면을 로그(DebugFeed)로 남긴다.
 * 결과는 `adb logcat -s DebugFeed Situation` 으로 본다.
 */
@RunWith(AndroidJUnit4::class)
class AmbientCoreFlowInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** 소리 파일에서 쓸 앞쪽 길이(초). 분위기는 8초 이상 이어지고 직전 전환 뒤 45초가 지나야 실제로 바뀌어서, 전환까지 보려면 `-e seconds 60`. */
    private val seconds get() = InstrumentationRegistry.getArguments().getString("seconds")?.toIntOrNull() ?: 24

    private fun feed(asset: String, seconds: Int) {
        val f = File(context.cacheDir, asset.substringAfterLast('/'))
        context.assets.open(asset).use { i -> f.outputStream().use { i.copyTo(it) } }
        DebugVoiceFeed.runEar(context, f, seconds)
    }

    @Test
    fun rainRecording() = feed("bgm/amb/rain.flac", seconds)

    @Test
    fun seaRecording() = feed("bgm/amb/sea.flac", seconds)
}
