package com.twinpark.music.voice

import android.content.Context

/**
 * 말을 글자로 바꾸는 입력. 한 번에 한 문장씩 듣고, 인식된 글자를 만들 때 받은 콜백으로 넘긴다.
 * 구현은 [SpeechEngine]으로 고른다. Pleos STT가 열리면 구현만 바꿔 끼우면 되고, 부르는 쪽([AppVoice])은 그대로다.
 */
interface SpeechSource : AutoCloseable {
    /** 듣기를 시작할 수 있는가(서비스 연결 · 권한 · 언어 확인이 끝났는가) */
    val isReady: Boolean

    /** 준비한다. 서비스가 없거나 실패하면 [isReady]는 false로 남고 앱은 그대로 동작한다. */
    fun start()

    /** 한 문장 듣기를 시작한다. 준비가 안 됐거나 이미 듣는 중이면 false. */
    fun listen(): Boolean
}

/** 쓸 음성 인식 구현. 빌드 설정 `VOICE_ENGINE`(기본 vosk)으로 고른다. */
enum class SpeechEngine(val id: String) {
    /** 앱 안에서 도는 오프라인 인식(Vosk 한국어 모델). 서비스가 필요 없다(기본). 모델은 빌드에 `VOSK_MODEL_DIR`을 줘야 들어간다. */
    Vosk("vosk"),

    /** 안드로이드 기본 음성 인식(`SpeechRecognizer`). 기기에 인식 서비스가 있어야 한다. */
    Android("android"),

    /** Pleos SpeechToText SDK(온디바이스). 플랫폼에서 열리면 쓴다. */
    Pleos("pleos"),
    ;

    companion object {
        /** 모르는 이름이면 기본(Vosk)으로. */
        fun from(id: String?): SpeechEngine = entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: Vosk
    }

    fun create(context: Context, onText: (String) -> Unit): SpeechSource = when (this) {
        Vosk -> VoskSpeechSource(context, onText)
        Android -> AndroidSpeechSource(context, onText)
        Pleos -> PleosSpeechSource(context, onText)
    }
}
