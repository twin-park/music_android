package com.twinpark.music.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * 안드로이드 기본 음성 인식(`SpeechRecognizer`)으로 한국어 한 문장을 글자로 받는다. 기기에 인식 서비스가 없으면 아무 일도 하지 않는다.
 * 마이크 권한(RECORD_AUDIO)이 필요하다. `SpeechRecognizer`는 화면 스레드에서만 쓸 수 있어서 모든 호출을 그쪽으로 넘긴다.
 * [listen]을 부를 때만 듣는다: 늘 듣게 하면 평소 대화("좋아", "별로야")가 음악 명령으로 오인될 수 있다.
 */
class AndroidSpeechSource(
    private val context: Context,
    private val onText: (String) -> Unit,
) : SpeechSource {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    @Volatile private var ready = false
    @Volatile private var listening = false
    private val timeout = Runnable { stopListening("시간 초과") }

    override val isReady: Boolean get() = ready

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            listening = false
            main.removeCallbacks(timeout)
            val first = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            SpeechPolicy.accept(first)?.let { heard ->
                Log.i(TAG, "들음: $heard")
                onText(heard)
            } ?: Log.i(TAG, "들은 말이 없음")
        }

        override fun onError(error: Int) {
            listening = false
            main.removeCallbacks(timeout)
            Log.w(TAG, "오류 ${SpeechPolicy.androidErrorName(error)}($error)")
            if (SpeechPolicy.isFatalAndroid(error)) ready = false
        }

        override fun onReadyForSpeech(params: Bundle?) { Log.i(TAG, "듣는 중") }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun start() {
        main.post {
            if (recognizer != null) return@post
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.i(TAG, "이 기기에는 음성 인식 서비스가 없음")
                return@post
            }
            val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }
                .onFailure { Log.w(TAG, "음성 인식을 만들지 못함: ${it.javaClass.simpleName} ${it.message}") }
                .getOrNull() ?: return@post
            r.setRecognitionListener(listener)
            recognizer = r
            ready = true
            Log.i(TAG, "음성 인식 준비됨(안드로이드 기본)")
        }
    }

    override fun listen(): Boolean {
        if (!ready || listening) return false
        listening = true
        main.post {
            main.postDelayed(timeout, LISTEN_MS)
            runCatching { recognizer?.startListening(recognizerIntent()) }
                .onFailure {
                    Log.w(TAG, "듣기 시작 실패: ${it.javaClass.simpleName} ${it.message}")
                    stopListening("시작 실패")
                }
        }
        return true
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)           // 가능하면 기기 안에서 인식(차 안은 통신이 불안정)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    private fun stopListening(why: String) {
        if (!listening) return
        listening = false
        main.removeCallbacks(timeout)
        runCatching { recognizer?.cancel() }
        Log.i(TAG, "듣기 종료: $why")
    }

    override fun close() {
        ready = false
        listening = false
        main.post {
            main.removeCallbacks(timeout)
            recognizer?.let { r -> runCatching { r.cancel() }; runCatching { r.destroy() } }
            recognizer = null
        }
    }

    private companion object {
        const val TAG = "AndroidSpeech"
        const val LISTEN_MS = 12_000L          // 이만큼 지나도 끝나지 않으면 그만 듣는다
    }
}
