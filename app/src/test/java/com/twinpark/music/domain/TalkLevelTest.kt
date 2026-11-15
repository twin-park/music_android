package com.twinpark.music.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TalkLevelTest {
    @After fun reset() = TalkLevel.clear()

    @Test
    fun `문턱을 넘은 말소리만 0점05 단위로 돌려준다`() {
        TalkLevel.report(0.91f, 0.55f, 1_000)
        assertEquals(0.9f, TalkLevel.current(1_000)!!, 0.001f)
        TalkLevel.report(0.62f, 0.55f, 2_000)
        assertEquals(0.6f, TalkLevel.current(2_000)!!, 0.001f)
    }

    @Test
    fun `문턱 아래이거나 오래되면 값이 없다`() {
        TalkLevel.report(0.49f, 0.55f, 1_000)
        assertNull(TalkLevel.current(1_000))
        TalkLevel.report(0.9f, 0.55f, 1_000)
        assertNull(TalkLevel.current(1_000 + TalkLevel.FRESH_MS + 1))
    }

    @Test
    fun `보고가 없으면 값이 없다`() {
        assertNull(TalkLevel.current(0))
    }
}
