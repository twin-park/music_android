package com.twinpark.music.voice

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.twinpark.music.domain.SoundGate
import com.twinpark.music.sensing.MicTap
import com.twinpark.music.audio.BgmController
import com.twinpark.music.audio.BgmState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 앱 안에서 도는 음성 인식(Vosk)이 한국어 명령 소리를 글자로 바꾸고, 그 글자로 엔진이 올바른 답변을 하는지 본다.
 * 시험 소리는 PC의 한국어 합성 음성이라 사람 목소리와 다르다. 이 시험은 인식 경로가 동작하는지 보는 것이고 실사용 정확도는 아니다.
 */
@RunWith(AndroidJUnit4::class)
class VoskInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun waitFor(timeoutMs: Long, check: () -> Boolean): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            if (check()) return true
            Thread.sleep(500)
        }
        return check()
    }

    /** 시험 소리 파일(WAV, 16kHz 모노 16비트)의 소리 값. 머리글 44바이트 뒤가 소리다. */
    private fun pcm(name: String): ShortArray {
        val bytes = instrumentation.context.assets.open("voice_in/$name.wav").use { it.readBytes() }
        val buf = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(buf.remaining()).also { buf.get(it) }
    }

    /** 명령과 보통 말의 신뢰도를 모두 기록한다(걸러내는 기준을 정하려고). */
    @Test
    fun measureConfidence() {
        val src = VoskSpeechSource(context) {}
        try {
            src.start()
            assertTrue("음성 인식 모델이 준비돼야 함", waitFor(600_000) { src.isReady })
            for (name in listOf("change", "quiet", "upbeat", "calm", "sea_sound", "music_off", "like", "reset", "lunch", "meeting", "signal", "aircon", "mom", "tired")) {
                val prefix = if (name in setOf("lunch", "meeting", "signal", "aircon", "mom", "tired")) "neg" else "cmd"
                val h = src.recognizeDetailed(pcm("${prefix}_$name"))!!
                Log.i("VoskConf", "$prefix $name conf=${"%.3f".format(h.confidence)} \"${h.text}\"")
            }
        } finally {
            src.close()
        }
    }

    @Test
    fun spokenCommandsBecomeTextAndEngineReplies() {
        BgmController.start(context)
        assertTrue("엔진이 준비돼야 함", waitFor(60_000) { BgmController.state.value == BgmState.Ready })
        val src = VoskSpeechSource(context) {}
        try {
            src.start()
            // 처음 한 번 모델(약 250MB)을 풀어야 해서 오래 걸릴 수 있다.
            assertTrue("음성 인식 모델이 준비돼야 함(빌드에 VOSK_MODEL_DIR 필요)", waitFor(600_000) { src.isReady })

            val expected = mapOf(
                "change" to "다른 분위기로 바꿀게요",
                "quiet" to "조용히 할게요",
                "upbeat" to "신나게 갈게요",
                "calm" to "잔잔하게 갈게요",
                "sea_sound" to "파도 소리를 깔게요",
                "music_off" to "음악을 마무리할게요",
                "like" to "이 느낌 오래 둘게요",
                "reset" to "원래대로 할게요",
            )
            val wrong = ArrayList<String>()
            var accepted = 0
            for ((name, reply) in expected) {
                val h = src.recognizeDetailed(pcm("cmd_$name"))!!
                val ok = VoskSpeechSource.accepts(h)
                val got = if (ok) BgmController.say(h.text) else "(버림)"
                Log.i("VoskTest", "$name: \"${h.text}\" 신뢰도 ${"%.3f".format(h.confidence)} -> \"$got\"")
                if (ok) { accepted++; if (got != reply) wrong += "$name: \"${h.text}\" -> \"$got\" (기대 \"$reply\")" }
            }
            Log.i("VoskTest", "받아들인 것 $accepted/${expected.size}, 틀린 것 ${wrong.size}")
            assertTrue("받아들였는데 틀린 것: $wrong", wrong.isEmpty())       // 틀리게 동작하는 것이 가장 나쁘다
            assertTrue("너무 많이 버림: $accepted/${expected.size}", accepted >= expected.size - 1)
        } finally {
            src.close()
        }
    }

    /** 명령이 아닌 보통 말이 음악을 바꾸는 명령으로 잘못 들리지 않는지 본다(어휘를 좁히면 생길 수 있는 위험). */
    @Test
    fun ordinaryTalkIsNotTakenAsACommand() {
        BgmController.start(context)
        assertTrue("엔진이 준비돼야 함", waitFor(60_000) { BgmController.state.value == BgmState.Ready })
        val src = VoskSpeechSource(context) {}
        try {
            src.start()
            assertTrue("음성 인식 모델이 준비돼야 함", waitFor(600_000) { src.isReady })
            val wrong = ArrayList<String>()
            for (name in listOf("lunch", "meeting", "signal", "aircon", "mom", "tired")) {
                val h = src.recognizeDetailed(pcm("neg_$name"))!!
                val ok = VoskSpeechSource.accepts(h)
                val got = if (ok) BgmController.say(h.text) else "(버림)"
                Log.i("VoskTest", "$name: \"${h.text}\" 신뢰도 ${"%.3f".format(h.confidence)} -> \"$got\"")
                if (ok && got != "잘 못 알아들었어요") wrong += "$name: \"${h.text}\" -> \"$got\""
            }
            Log.i("VoskTest", "명령으로 잘못 들린 것 ${wrong.size}/6")
            assertTrue("명령으로 잘못 들린 것: $wrong", wrong.isEmpty())
        } finally {
            src.close()
        }
    }

    /**
     * 계속 듣기: 마이크 자리(MicTap)로 보통 말과 명령을 섞어 흘려보내, 명령만 순서대로 받아들이는지 본다.
     * 마이크 스레드(소리를 넣는 쪽)가 막히지 않는지, 앱이 말하는 동안(SoundGate) 듣지 않는지도 본다.
     */
    @Test
    fun continuousListeningTakesCommandsAndIgnoresTalk() {
        SoundGate.clear()
        val heard = java.util.concurrent.CopyOnWriteArrayList<String>()
        val src = VoskSpeechSource(context, cooldownMs = 0) { heard += it }
        try {
            src.start()
            assertTrue("음성 인식 모델이 준비돼야 함", waitFor(600_000) { src.isReady })
            assertTrue("계속 듣기가 시작돼야 함", waitFor(5_000) { src.listen() })

            var worst = 0L
            fun push(chunk: ShortArray) {
                val t0 = SystemClock.elapsedRealtime()
                MicTap.sink!!.invoke(chunk, chunk.size)
                worst = maxOf(worst, SystemClock.elapsedRealtime() - t0)
                Thread.sleep(25)
            }
            fun speak(name: String) {
                val pcm = pcm(name)
                var off = 0
                while (off < pcm.size) {
                    val n = minOf(1_600, pcm.size - off)
                    push(pcm.copyOfRange(off, off + n))
                    off += n
                }
                repeat(15) { push(ShortArray(1_600)) }                  // 1.5초 고요: 한 문장이 끝났다고 알아채게
            }

            for (name in listOf("neg_lunch", "cmd_change", "neg_meeting", "cmd_quiet", "neg_aircon", "cmd_sea_sound",
                "neg_mom", "cmd_music_off", "neg_tired", "cmd_like", "neg_signal", "cmd_reset")) speak(name)
            waitFor(10_000) { heard.size >= 6 }
            Log.i("VoskTest", "계속 듣기: 받아들인 명령 ${heard.toList()}, 소리 넣기 최대 ${worst}ms")
            assertEquals(listOf("다른 거 틀어 줘", "조용히 해 줘", "파도 소리 깔아 줘", "음악 꺼 줘", "이거 좋다", "원래대로 해 줘"), heard.toList())
            assertTrue("소리를 넣는 쪽(마이크 스레드)을 막으면 안 됨: 최대 ${worst}ms", worst < 50)

            // 앱이 말하는 동안에는 명령으로 듣지 않는다
            heard.clear()
            SoundGate.muteUntil(System.currentTimeMillis() + 30_000)
            speak("cmd_change")
            Thread.sleep(3_000)
            assertTrue("앱이 말하는 동안에는 받지 않아야 함: $heard", heard.isEmpty())
            SoundGate.clear()
            speak("cmd_change")
            assertTrue("문이 열리면 다시 들어야 함", waitFor(8_000) { heard.size == 1 })
        } finally {
            SoundGate.clear()
            src.close()
        }
    }
}
