package com.twinpark.music.voice

import com.twinpark.music.voice.VoskSpeechSource.Heard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandFilterTest {
    private fun heard(text: String = "조용히 해 줘", conf: Double = 1.0) = Heard(text, conf)

    @Test
    fun `신뢰도가 높은 말만 받는다`() {
        val f = CommandFilter(cooldownMs = 0)
        assertTrue(f.accept(heard(conf = 1.0), 0))
        assertTrue(f.accept(heard(conf = VoskSpeechSource.MIN_CONFIDENCE), 1))
        assertFalse(f.accept(heard(conf = VoskSpeechSource.MIN_CONFIDENCE - 0.01), 2))
        assertFalse(f.accept(heard(conf = 0.0), 3))
    }

    @Test
    fun `빈 글자는 받지 않는다`() {
        assertFalse(CommandFilter(0).accept(heard(text = "", conf = 1.0), 0))
    }

    @Test
    fun `명령을 받은 뒤 정해진 시간 안의 말은 버린다`() {
        val f = CommandFilter(cooldownMs = 3_000)
        assertTrue(f.accept(heard(), 10_000))
        assertFalse(f.accept(heard(), 12_999))
        assertTrue(f.accept(heard(), 13_000))
    }

    @Test
    fun `버린 말은 간격을 새로 세지 않는다`() {
        val f = CommandFilter(cooldownMs = 3_000)
        assertTrue(f.accept(heard(), 0))
        assertFalse(f.accept(heard(conf = 0.5), 2_000))          // 신뢰도가 낮아 버려짐: 간격에 영향 없음
        assertTrue(f.accept(heard(), 3_000))
    }

    @Test
    fun `처음 들은 말은 시계가 0이어도 받는다`() {
        assertTrue(CommandFilter(3_000).accept(heard(), 0))
    }
}
