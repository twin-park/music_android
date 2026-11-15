package com.twinpark.music.voice

import ai.pleos.playground.stt.constant.ErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechPolicyTest {
    @Test
    fun `인식된 글자는 다듬어서 받는다`() {
        assertEquals("다른 거 틀어 줘", SpeechPolicy.accept("  다른 거 틀어 줘 \n"))
    }

    @Test
    fun `비었거나 너무 길면 받지 않는다`() {
        assertNull(SpeechPolicy.accept(null))
        assertNull(SpeechPolicy.accept("   "))
        assertNull(SpeechPolicy.accept("가".repeat(SpeechPolicy.MAX_CHARS + 1)))
        assertEquals("가".repeat(SpeechPolicy.MAX_CHARS), SpeechPolicy.accept("가".repeat(SpeechPolicy.MAX_CHARS)))
    }

    @Test
    fun `다시 해도 소용없는 오류만 치명적이다`() {
        for (e in listOf(ErrorType.PERMISSION_DENIED, ErrorType.NOT_SUPPORTED_API, ErrorType.UNAVAILABLE_LANGUAGE, ErrorType.ON_DEVICE_ENGINE_ERROR, ErrorType.UNAUTHENTICATED)) {
            assertTrue("$e", SpeechPolicy.isFatal(e))
        }
        for (e in listOf(ErrorType.TIMEOUT, ErrorType.MIC_ERROR, ErrorType.NETWORK_ERROR, ErrorType.AUDIO_ERROR, ErrorType.UNKNOWN)) {
            assertFalse("$e", SpeechPolicy.isFatal(e))
        }
    }

    @Test
    fun `안드로이드 기본 인식은 권한과 언어 오류만 치명적이다`() {
        for (c in listOf(android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, 12, 13)) assertTrue("$c", SpeechPolicy.isFatalAndroid(c))
        for (c in listOf(android.speech.SpeechRecognizer.ERROR_NO_MATCH, android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT, android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY, android.speech.SpeechRecognizer.ERROR_NETWORK, android.speech.SpeechRecognizer.ERROR_AUDIO)) {
            assertFalse("$c", SpeechPolicy.isFatalAndroid(c))
        }
        assertEquals("LANGUAGE_UNAVAILABLE", SpeechPolicy.androidErrorName(13))
        assertEquals("UNKNOWN", SpeechPolicy.androidErrorName(999))
    }

    @Test
    fun `음성 인식 구현은 이름으로 고르고 모르면 Vosk이다`() {
        assertEquals(SpeechEngine.Android, SpeechEngine.from("android"))
        assertEquals(SpeechEngine.Vosk, SpeechEngine.from("vosk"))
        assertEquals(SpeechEngine.Pleos, SpeechEngine.from("pleos"))
        assertEquals(SpeechEngine.Pleos, SpeechEngine.from("  Pleos "))
        assertEquals(SpeechEngine.Vosk, SpeechEngine.from(""))
        assertEquals(SpeechEngine.Vosk, SpeechEngine.from(null))
        assertEquals(SpeechEngine.Vosk, SpeechEngine.from("whisper"))
    }

    @Test
    fun `음성 인식 권한 이름`() {
        assertEquals("pleos.car.permission.STT_SERVICE", PleosSpeechSource.PERMISSION)
    }
}
