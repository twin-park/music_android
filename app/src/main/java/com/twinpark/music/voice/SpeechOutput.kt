package com.twinpark.music.voice

/**
 * 글자를 소리로 읽어 주는 출력(엔진의 답변 "다른 분위기로 바꿀게요" 같은 문장). 구현은 바꿔 끼울 수 있다.
 * 지금은 안드로이드 기본 TTS([AndroidSpeechOutput])를 쓰고, Pleos TextToSpeech가 열리면 구현만 추가하면 된다.
 */
interface SpeechOutput : AutoCloseable {
    /** 읽을 수 있는가(엔진 연결과 한국어 확인이 끝났는가) */
    val isReady: Boolean

    /** 준비한다. 실패하면 [isReady]는 false로 남고 앱은 그대로 동작한다. */
    fun start()

    /** 문장을 읽는다. 읽던 것이 있으면 끊고 새로 읽는다. 준비가 안 됐으면 아무 일도 하지 않는다. */
    fun speak(text: String)
}

/** 아무것도 읽지 않는 출력(기능이 꺼졌을 때). */
object NoSpeechOutput : SpeechOutput {
    override val isReady: Boolean = false
    override fun start() {}
    override fun speak(text: String) {}
    override fun close() {}
}
