package com.twinpark.music.voice

import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechVocabularyTest {
    private val sample = """
        // 상황 어휘 — 설명 줄
        {
          "trip.kind": { "name": "여정" },
          "speech_words": {
            "quiet": ["쉿", "조용히"],
            "change": ["다른 거", "바꿔", "쉿"]
          }
        }
    """.trimIndent()

    @Test
    fun `설명 줄이 있어도 엔진의 낱말을 중복 없이 읽는다`() {
        val words = SpeechVocabulary.words(sample)
        assertEquals(setOf("쉿", "조용히", "다른 거", "바꿔"), words.toSet())      // 읽는 순서는 정해져 있지 않다
        assertEquals("겹치는 낱말은 한 번만", 4, words.size)
    }

    @Test
    fun `읽을 수 없으면 낱말이 없고 문법도 없다`() {
        assertTrue(SpeechVocabulary.words("not json").isEmpty())
        assertTrue(SpeechVocabulary.words("{}").isEmpty())
        assertNull(SpeechVocabulary.grammar(emptyList()))
    }

    @Test
    fun `문법은 낱말 단독과 앞뒤에 모르는 말이 붙은 꼴을 허용한다`() {
        val g = JSONArray(SpeechVocabulary.grammar(listOf("조용히"), extra = listOf("조용히 해 줘"))!!)
        val phrases = (0 until g.length()).map { g.getString(it) }
        assertEquals(listOf("조용히 해 줘", "조용히", "[unk] 조용히", "조용히 [unk]", "[unk] 조용히 [unk]", "[unk]"), phrases)
    }

    @Test
    fun `짧고 강한 명령은 어휘에서 빼고 자연스러운 문장은 넣는다`() {
        val g = JSONArray(SpeechVocabulary.grammar(listOf("꺼 줘", "꺼줘", "음악 꺼"))!!)
        val phrases = (0 until g.length()).map { g.getString(it) }
        assertTrue(phrases.none { it.contains("꺼 줘") && it != "음악 꺼 줘" && it != "노래 꺼 줘" })
        assertTrue("음악 꺼" in phrases && "음악 꺼 줘" in phrases && "파도 소리 깔아 줘" in phrases)
    }

    @Test
    fun `인식 결과에서 모르는 말 표시를 지운다`() {
        assertEquals("다른 거", SpeechVocabulary.clean("[unk] 다른 거  [unk]"))
        assertEquals("", SpeechVocabulary.clean("[unk]"))
    }

    @Test
    fun `실제 엔진 파일의 낱말을 읽는다`() {
        val f = File("../../ai/engine/data/conductor/signals.json")
        if (!f.isFile) return                                       // ai 저장소가 옆에 없으면 건너뛴다
        val words = SpeechVocabulary.words(f.readText())
        assertTrue("다른 거" in words && "조용히" in words && "음악 꺼" in words)
        assertTrue(words.size >= 40)
    }
}
