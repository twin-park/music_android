package com.twinpark.music.voice

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.twinpark.music.audio.BgmController
import com.twinpark.music.audio.BgmState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "인식된 글자 → 엔진의 말로 하는 요청 → 답변 → 소리로 읽기" 구간을 실제 엔진으로 확인한다.
 * 마이크로 말하는 부분(음성 인식 결과가 글자로 나오는 것)은 기기에서 직접 말해야 해서 여기서는 글자를 바로 넣는다.
 */
@RunWith(AndroidJUnit4::class)
class VoiceFlowInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun cleanUp() = AppVoice.stop()

    private fun waitFor(timeoutMs: Long, check: () -> Boolean): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            if (check()) return true
            Thread.sleep(200)
        }
        return check()
    }

    /** 인식 결과가 나온 것처럼 [AppVoice]의 처리 구간에 글자를 넣는다. */
    private fun hear(text: String) {
        val m = AppVoice::class.java.getDeclaredMethod("onHeard", String::class.java)
        m.isAccessible = true
        m.invoke(AppVoice, text)
    }

    @Test
    fun sayingAWordChangesTheEngineAndTheReplyIsShown() {
        assertTrue("이 빌드는 음성 명령이 켜져 있어야 함", AppVoice.enabled)
        BgmController.start(context)
        assertTrue("엔진이 준비돼야 함", waitFor(60_000) { BgmController.state.value == BgmState.Ready })
        AppVoice.start(context)
        Thread.sleep(3_000)

        val expected = mapOf(
            "조용히 해 줘" to "조용히 할게요",
            "신나는 걸로" to "신나게 갈게요",
            "잔잔하게 틀어줘" to "잔잔하게 갈게요",
            "바다 소리 깔아줘" to "파도 소리를 깔게요",
            "다른 거 틀어 줘" to "다른 분위기로 바꿀게요",
        )
        for ((said, reply) in expected) {
            hear(said)
            Log.i("VoiceFlow", "\"$said\" -> \"${AppVoice.reply.value}\"")
            assertEquals(said, AppVoice.heard.value)
            assertEquals(reply, AppVoice.reply.value)
        }
    }
}
