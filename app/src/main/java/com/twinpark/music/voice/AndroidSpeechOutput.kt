package com.twinpark.music.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.twinpark.music.domain.SoundGate
import java.util.Locale

/**
 * 안드로이드 기본 TTS(`TextToSpeech`)로 한국어 문장을 읽어 준다. 기기에 TTS 엔진이나 한국어 음성이 없으면 아무 일도 하지 않는다.
 * 읽는 동안과 그 뒤 [TAIL_MS] 동안은 소리 분류를 건너뛴다([SoundGate]): 이 목소리를 귀 모델이 차 안 대화로 듣고 상황을 바꾸지 않게.
 */
class AndroidSpeechOutput(private val context: Context) : SpeechOutput {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    override val isReady: Boolean get() = ready

    override fun start() {
        if (tts != null) return
        tts = runCatching {
            TextToSpeech(context) { status ->
                val engine = tts
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    Log.w(TAG, "TTS 엔진을 열지 못함(status $status)")
                    return@TextToSpeech
                }
                val lang = engine.setLanguage(Locale.KOREAN)
                if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "한국어 음성이 없음(결과 $lang)")
                    return@TextToSpeech
                }
                engine.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                )
                engine.setOnUtteranceProgressListener(progress)
                ready = true
                Log.i(TAG, "음성 출력 준비됨(안드로이드 기본 TTS)")
            }
        }.onFailure { Log.w(TAG, "TTS를 만들지 못함: ${it.javaClass.simpleName} ${it.message}") }.getOrNull()
    }

    override fun speak(text: String) {
        val engine = tts ?: return
        if (!ready || text.isBlank()) return
        SoundGate.muteUntil(System.currentTimeMillis() + MAX_SPEAK_MS)       // 끝나는 때를 알기 전까지는 길게 막고, 끝나면 줄인다
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "읽기 실패($result)")
            SoundGate.muteUntil(System.currentTimeMillis())
        }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            SoundGate.muteUntil(System.currentTimeMillis() + TAIL_MS)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            SoundGate.muteUntil(System.currentTimeMillis() + TAIL_MS)
        }
    }

    override fun close() {
        ready = false
        val engine = tts ?: return
        tts = null
        runCatching { engine.stop() }
        runCatching { engine.shutdown() }
        SoundGate.clear()
    }

    private companion object {
        const val TAG = "AndroidTts"
        const val UTTERANCE_ID = "music-reply"
        const val MAX_SPEAK_MS = 15_000L        // 한 문장은 이보다 짧다. 끝났다는 알림이 오면 줄인다
        const val TAIL_MS = 4_500L              // 분류 창(4초)에 목소리가 남아 있는 동안
    }
}
