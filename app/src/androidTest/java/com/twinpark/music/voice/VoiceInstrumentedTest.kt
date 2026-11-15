package com.twinpark.music.voice

import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.twinpark.music.domain.SoundGate
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 기기(에뮬레이터)에서 음성 출력(TTS)과 음성 인식이 실제로 열리는지 본다. 서비스가 없으면 그 사실이 실패로 드러난다. */
@RunWith(AndroidJUnit4::class)
class VoiceInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun cleanUp() = SoundGate.clear()

    private fun waitFor(timeoutMs: Long, check: () -> Boolean): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            if (check()) return true
            Thread.sleep(200)
        }
        return check()
    }

    @Test
    fun ttsOpensKoreanAndSpeaksThenReopensTheGate() {
        val out = AndroidSpeechOutput(context)
        try {
            out.start()
            assertTrue("TTS 엔진과 한국어 음성이 준비돼야 함", waitFor(20_000) { out.isReady })
            val t0 = System.currentTimeMillis()
            out.speak("다른 분위기로 바꿀게요")
            assertTrue("읽기 시작과 함께 소리 분류가 막혀야 함", SoundGate.isMuted(System.currentTimeMillis()))
            // 끝났다는 알림이 오면 막는 시간이 4.5초 뒤로 줄어든다. 15초 안에 그렇게 되는지 본다.
            val done = waitFor(15_000) { !SoundGate.isMuted(System.currentTimeMillis() + 5_000) }
            Log.i("VoiceTest", "TTS 읽기 끝 알림까지 ${System.currentTimeMillis() - t0}ms")
            assertTrue("읽기가 끝났다는 알림이 와서 막는 시간이 줄어야 함", done)
        } finally {
            out.close()
        }
    }

    @Test
    fun pleosTtsOpensAndSpeaksThenReopensTheGate() {
        val out = PleosSpeechOutput(context)
        try {
            out.start()
            val ready = waitFor(20_000) { out.isReady }
            Log.i("VoiceTest", "Pleos TTS 준비: $ready")
            assertTrue("Pleos TTS가 준비돼야 함", ready)
            val t0 = System.currentTimeMillis()
            out.speak("다른 분위기로 바꿀게요")
            assertTrue("읽기 시작과 함께 소리 분류가 막혀야 함", SoundGate.isMuted(System.currentTimeMillis()))
            val done = waitFor(15_000) { !SoundGate.isMuted(System.currentTimeMillis() + 5_000) }
            Log.i("VoiceTest", "Pleos TTS 읽기 끝 알림까지 ${System.currentTimeMillis() - t0}ms: $done")
            assertTrue("읽기가 끝났다는 알림이 와서 막는 시간이 줄어야 함", done)
        } finally {
            out.close()
        }
    }

    @Test
    fun recognitionServiceExistsAndListeningStarts() {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        Log.i("VoiceTest", "음성 인식 서비스 있음: $available")
        assertTrue("기기에 음성 인식 서비스가 있어야 함", available)
        val src = AndroidSpeechSource(context) { Log.i("VoiceTest", "인식됨: $it") }
        try {
            src.start()
            assertTrue("인식기가 준비돼야 함", waitFor(10_000) { src.isReady })
            assertTrue("듣기를 시작할 수 있어야 함", src.listen())
            assertFalse("이미 듣는 중이면 다시 시작하지 않음", src.listen())
            Thread.sleep(6_000)                         // 오류든 결과든 로그(AndroidSpeech)에 남는다
        } finally {
            src.close()
        }
    }
}
