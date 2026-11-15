package com.twinpark.music.voice

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyClipsTest {
    /** 엔진(ai/engine/src/engine.cpp 의 hear)이 돌려주는 모든 답변 문장 */
    private val engineReplies = listOf(
        "이 느낌 오래 둘게요", "다른 분위기로 바꿀게요", "음악을 마무리할게요", "자동을 껐어요 · 직접 골라 주세요",
        "아기가 자요 → 잔잔하게 바꿀게요", "조용히 할게요", "신나게 갈게요", "잔잔하게 갈게요",
        "파도 소리를 깔게요", "원래대로 할게요", "잘 못 알아들었어요",
    )

    @Test
    fun `엔진의 모든 답변에 음성 파일이 있다`() {
        for (reply in engineReplies) assertTrue("파일 없음: $reply", ReplyClips.fileFor(reply) != null)
        assertEquals(engineReplies.toSet(), ReplyClips.replies)
    }

    @Test
    fun `모든 음성 파일이 실제로 있고 비어 있지 않다`() {
        for (name in ReplyClips.files) {
            val f = File("src/main/assets/${ReplyClips.DIR}/$name")
            assertTrue("없음: $f", f.isFile)
            assertTrue("너무 작음: $f", f.length() > 20_000)
        }
        assertEquals("파일 이름이 겹치면 안 됨", ReplyClips.files.size, ReplyClips.files.toSet().size)
    }

    @Test
    fun `앞뒤 공백은 무시하고 모르는 문장은 읽지 않는다`() {
        assertEquals("reply_quiet.wav", ReplyClips.fileFor("  조용히 할게요 \n"))
        assertNull(ReplyClips.fileFor("처음 보는 문장"))
        assertNull(ReplyClips.fileFor(""))
    }

    @Test
    fun `출력 구현은 이름으로 고르고 모르면 음성 파일이다`() {
        assertEquals(OutputEngine.Recorded, OutputEngine.from(null))
        assertEquals(OutputEngine.Recorded, OutputEngine.from("whatever"))
        assertEquals(OutputEngine.Android, OutputEngine.from("android"))
        assertEquals(OutputEngine.Pleos, OutputEngine.from(" Pleos "))
    }
}
