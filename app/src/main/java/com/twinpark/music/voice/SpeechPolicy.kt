package com.twinpark.music.voice

import ai.pleos.playground.stt.constant.ErrorType
import android.speech.SpeechRecognizer

/** 음성 인식 결과와 오류를 어떻게 다룰지 정하는 규칙(SDK 없이 시험할 수 있게 따로 둔다). */
object SpeechPolicy {
    /** 한 문장으로 받아들이는 최대 글자 수. 너무 긴 말은 명령이 아니라 대화로 본다. */
    const val MAX_CHARS = 120

    /** 인식된 글자를 명령으로 쓸 수 있으면 다듬어서, 아니면 null. */
    fun accept(text: String?): String? {
        val t = text?.trim().orEmpty()
        return if (t.isEmpty() || t.length > MAX_CHARS) null else t
    }

    /** 안드로이드 기본 인식의 오류 번호 중 다시 해 봐도 소용없는 것(권한 없음, 한국어 미지원, 한국어 미설치). */
    fun isFatalAndroid(code: Int): Boolean =
        code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || code == ERROR_LANGUAGE_NOT_SUPPORTED || code == ERROR_LANGUAGE_UNAVAILABLE

    /** 로그에 남길 오류 이름. */
    fun androidErrorName(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "INSUFFICIENT_PERMISSIONS"
        SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_SERVER -> "SERVER"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
        ERROR_LANGUAGE_NOT_SUPPORTED -> "LANGUAGE_NOT_SUPPORTED"
        ERROR_LANGUAGE_UNAVAILABLE -> "LANGUAGE_UNAVAILABLE"
        else -> "UNKNOWN"
    }

    // SpeechRecognizer.ERROR_LANGUAGE_* 는 안드로이드 12(API 31)에서 생긴 상수라, 더 낮은 판에서도 쓸 수 있게 값을 직접 둔다.
    private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
    private const val ERROR_LANGUAGE_UNAVAILABLE = 13

    /** 다시 해 봐도 소용없는 오류(권한 없음, 지원 안 함, 언어 없음, 엔진 고장, 인증 실패). 이 경우 음성 기능을 더 시도하지 않는다. */
    fun isFatal(error: ErrorType): Boolean = when (error) {
        ErrorType.PERMISSION_DENIED, ErrorType.NOT_SUPPORTED_API, ErrorType.UNAVAILABLE_LANGUAGE,
        ErrorType.ON_DEVICE_ENGINE_ERROR, ErrorType.UNAUTHENTICATED -> true
        else -> false
    }
}
