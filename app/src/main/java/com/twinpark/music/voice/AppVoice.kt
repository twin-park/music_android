package com.twinpark.music.voice

import android.content.Context
import android.util.Log
import com.twinpark.music.BuildConfig
import com.twinpark.music.audio.BgmController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 이 앱의 음성 명령. 빌드 설정 `VOICE_COMMAND`가 켜져 있을 때만 동작한다(기본 꺼짐).
 * [listen]으로 한 문장을 듣고, 인식된 글자를 엔진의 `say`(말로 하는 요청)에 넘겨 음악을 바꾼다.
 * 어떤 음성 인식을 쓸지는 [SpeechEngine]이 정한다(`VOICE_ENGINE`, 기본 안드로이드 기본 인식).
 * 엔진이 돌려주는 답변 문장은 [reply]로 알리고, [SpeechOutput](안드로이드 기본 TTS)으로 소리 내어 읽어 준다.
 */
object AppVoice {
    private const val TAG = "AppVoice"
    private var source: SpeechSource? = null
    private var output: SpeechOutput = NoSpeechOutput

    private val _heard = MutableStateFlow("")
    private val _reply = MutableStateFlow("")

    /** 마지막으로 인식한 말 */
    val heard: StateFlow<String> = _heard.asStateFlow()

    /** 엔진의 마지막 답변 */
    val reply: StateFlow<String> = _reply.asStateFlow()

    val enabled: Boolean get() = BuildConfig.VOICE_COMMAND

    /** 이 빌드가 쓰는 음성 인식 구현 */
    val engine: SpeechEngine get() = SpeechEngine.from(BuildConfig.VOICE_ENGINE)

    @Synchronized
    fun start(context: Context) {
        if (!enabled || source != null) return
        Log.i(TAG, "음성 인식 구현: ${engine.id}")
        source = engine.create(context.applicationContext, ::onHeard).also { it.start() }
        output = OutputEngine.from(BuildConfig.VOICE_OUTPUT).create(context.applicationContext).also { it.start() }
    }

    /** 한 문장 듣기. 기능이 꺼져 있거나 준비가 안 됐으면 false. */
    @Synchronized
    fun listen(): Boolean = source?.listen() ?: false

    @Synchronized
    fun stop() {
        source?.close()
        source = null
        output.close()
        output = NoSpeechOutput
    }

    private fun onHeard(text: String) {
        _heard.value = text.ifEmpty { "(못 알아들음)" }
        val answer = BgmController.say(text)
        _reply.value = answer
        Log.i(TAG, "말: $text -> 엔진: ${answer.ifEmpty { "(응답 없음)" }}")
        if (answer.isNotEmpty()) output.speak(answer)                  // 엔진의 답변을 소리로 읽어 준다
    }
}
