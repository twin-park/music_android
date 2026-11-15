package com.twinpark.music.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.util.Log
import com.twinpark.music.domain.SoundGate
import java.util.Locale

/**
 * Pleos TextToSpeech SDK(온디바이스)로 한국어 문장을 읽어 준다. 차량의 CAAS 서비스가 소리를 낸다.
 * 서비스가 없거나 초기화에 실패하면 아무 일도 하지 않는다. 읽는 동안과 그 뒤 [TAIL_MS]는 소리 분류를 건너뛴다([SoundGate]).
 */
class PleosSpeechOutput(private val context: Context) : SpeechOutput {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    override val isReady: Boolean get() = ready

    private val events = object : EventListener {
        override fun onStart() {}
        override fun onReady() {}
        override fun onDone() { SoundGate.muteUntil(System.currentTimeMillis() + TAIL_MS) }
        override fun onStop() { SoundGate.muteUntil(System.currentTimeMillis() + TAIL_MS) }
        override fun onError(error: ErrorType) {
            Log.w(TAG, "오류 $error")
            SoundGate.muteUntil(System.currentTimeMillis() + TAIL_MS)
        }
        override fun onUpdatedRms(rms: Double) {}
    }

    override fun start() {
        if (tts != null) return
        val client = runCatching { TextToSpeech(context, Mode.ON_DEVICE) }
            .onFailure { Log.i(TAG, "TextToSpeech SDK를 쓸 수 없음: ${it.javaClass.simpleName}") }
            .getOrNull() ?: return
        tts = client
        runCatching {
            client.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    runCatching {
                        client.addEventListener(events)             // 서비스가 만들어진 뒤에만 등록할 수 있다(먼저 부르면 SDK 내부 오류)
                        client.setLocale(Locale.KOREAN)
                        client.setAudioAttributes(
                            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                        )
                    }.onFailure { Log.w(TAG, "설정 실패: ${it.message}") }
                    ready = true
                    Log.i(TAG, "음성 출력 준비됨(Pleos TTS, 온디바이스)")
                }

                override fun onFailure(error: ErrorType) { Log.w(TAG, "초기화 실패: $error") }
            })
        }.onFailure { Log.w(TAG, "초기화 호출 실패: ${it.javaClass.simpleName} ${it.message}") }
    }

    override fun speak(text: String) {
        val client = tts ?: return
        if (!ready || text.isBlank()) return
        SoundGate.muteUntil(System.currentTimeMillis() + MAX_SPEAK_MS)
        runCatching { client.speak(text, true) }
            .onFailure {
                Log.w(TAG, "읽기 실패: ${it.javaClass.simpleName} ${it.message}")
                SoundGate.muteUntil(System.currentTimeMillis())
            }
    }

    override fun close() {
        ready = false
        val client = tts ?: return
        tts = null
        runCatching { client.stop() }
        runCatching { client.removeEventListener(events) }
        runCatching { client.release() }
        SoundGate.clear()
    }

    companion object {
        /** 음성 출력(TTS) 서비스를 쓰는 Pleos 차량 권한. 매니페스트에 선언하고 실행 중에도 요청한다. */
        const val PERMISSION = "pleos.car.permission.TTS_SERVICE"
        private const val TAG = "PleosTts"
        private const val MAX_SPEAK_MS = 15_000L
        private const val TAIL_MS = 4_500L
    }
}
