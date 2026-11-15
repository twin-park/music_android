package com.twinpark.music.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/**
 * Pleos SpeechToText SDK(온디바이스)로 한국어 말을 글자로 받는다. 차량의 CAAS 서비스가 마이크를 직접 듣고, 한 번에 한 문장씩 인식한다.
 * 서비스가 없거나 권한([PERMISSION])이 없으면 아무 일도 하지 않는다(앱은 그대로 동작). 안드로이드 기본 인식([AndroidSpeechSource])을 대신하는 교체용 구현이다.
 * [listen]을 부를 때만 듣는다: 늘 듣게 하면 평소 대화("좋아", "별로야")가 음악 명령으로 오인될 수 있다.
 */
class PleosSpeechSource(
    private val context: Context,
    private val onText: (String) -> Unit,
) : SpeechSource {
    private val main = Handler(Looper.getMainLooper())
    private var stt: SpeechToText? = null
    @Volatile private var ready = false
    @Volatile private var listening = false
    private val timeout = Runnable { finish("시간 초과") }

    override val isReady: Boolean get() = ready

    private val listener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            if (!isFinal) return
            SpeechPolicy.accept(text)?.let { heard ->
                Log.i(TAG, "들음: $heard")
                main.post { onText(heard) }
            }
        }

        override fun onUpdatedEpdData(onMs: Long, offMs: Long) {}

        override fun onStartedRecognition() {
            Log.i(TAG, "인식 시작")
        }

        override fun onEndedRecognition() {
            main.post { finish("인식 끝") }
        }

        override fun onError(error: ErrorType) {
            Log.w(TAG, "오류 $error")
            if (SpeechPolicy.isFatal(error)) ready = false
            main.post { finish("오류 $error") }
        }

        override fun onReady() {}

        override fun onUpdatedRms(rms: Double) {}
    }

    /** SDK 연결. 실패하면 로그만 남기고 [isReady]는 false로 둔다. */
    override fun start() {
        if (stt != null) return
        val client = runCatching { SpeechToText(context, Mode.ON_DEVICE) }
            .onFailure { Log.i(TAG, "SpeechToText SDK를 쓸 수 없음: ${it.javaClass.simpleName}") }
            .getOrNull() ?: return
        stt = client
        runCatching {
            client.addListener(listener)
            client.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    runCatching { client.setLocale(Locale.KOREAN) }.onFailure { Log.w(TAG, "언어 설정 실패: ${it.message}") }
                    ready = true
                    Log.i(TAG, "음성 인식 준비됨(온디바이스)")
                }

                override fun onFailure(error: ErrorType) {
                    Log.w(TAG, "초기화 실패: $error")
                }
            })
        }.onFailure { Log.w(TAG, "초기화 호출 실패: ${it.javaClass.simpleName} ${it.message}") }
    }

    /** 한 문장 듣기를 시작한다. 준비가 안 됐거나 이미 듣는 중이면 false. */
    override fun listen(): Boolean {
        val client = stt ?: return false
        if (!ready || listening) return false
        listening = true
        main.postDelayed(timeout, LISTEN_MS)
        val ok = runCatching { client.request() }
            .onFailure { Log.w(TAG, "듣기 시작 실패: ${it.javaClass.simpleName} ${it.message}") }
            .isSuccess
        if (!ok) finish("시작 실패")
        return ok
    }

    private fun finish(why: String) {
        if (!listening) return
        listening = false
        main.removeCallbacks(timeout)
        Log.i(TAG, "듣기 종료: $why")
    }

    override fun close() {
        val client = stt ?: return
        stt = null
        ready = false
        listening = false
        main.removeCallbacks(timeout)
        runCatching { client.stop() }
        runCatching { client.removeListener(listener) }
        runCatching { client.release() }
    }

    companion object {
        /** 음성 인식(STT) 서비스를 쓰는 Pleos 차량 권한. 매니페스트에 선언하고 실행 중에도 요청한다. */
        const val PERMISSION = "pleos.car.permission.STT_SERVICE"
        private const val TAG = "PleosSpeech"
        private const val LISTEN_MS = 12_000L          // 이만큼 지나도 끝나지 않으면 그만 듣는다
    }
}
