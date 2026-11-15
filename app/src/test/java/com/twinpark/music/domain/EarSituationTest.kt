package com.twinpark.music.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarSituationTest {
    @Test
    fun `차 안 소리는 바뀔 때만 보낸다`() {
        val e = EarSituation()
        assertEquals("""{"cabin":{"sound":"quiet"}}""", e.update(emptySet(), 0))
        assertNull(e.update(emptySet(), 2_000))
        assertEquals("""{"cabin":{"sound":"talk"}}""", e.update(setOf("대화"), 4_000))
        assertEquals("""{"cabin":{"sound":"argument"}}""", e.update(setOf("대화", "고함"), 6_000))    // 언성이 먼저
    }

    @Test
    fun `바다 소리는 들리면 켜고 한동안 안 들리면 지운다`() {
        val e = EarSituation()
        e.update(emptySet(), 0)
        assertEquals("""{"place":{"kind":"coast"}}""", e.update(setOf("파도"), 1_000))
        assertNull(e.update(emptySet(), 1_000 + EarSituation.RELEASE_MS - 1))
        assertEquals("""{"place":{"kind":null}}""", e.update(emptySet(), 1_000 + EarSituation.RELEASE_MS))
        assertNull(e.update(emptySet(), 1_000 + 2 * EarSituation.RELEASE_MS))
    }

    @Test
    fun `사람이 고른 칸은 귀가 건드리지 않는다`() {
        val e = EarSituation()
        e.userKeys += "cabin.sound"
        e.userKeys += "place.kind"
        assertNull(e.update(setOf("웃음", "바다"), 0))
        e.clear()
        val p = e.update(setOf("웃음", "바다"), 0)!!
        assertTrue(p.contains(""""sound":"laugh"""") && p.contains(""""kind":"coast""""))
    }

    @Test
    fun `말소리 크기는 또렷할 때만 보내고 아니면 지운다`() {
        val e = EarSituation()
        assertEquals("""{"cabin":{"sound":"talk","talk":0.9}}""", e.update(setOf("대화"), 0, 0.9f))
        assertNull(e.update(setOf("대화"), 2_000, 0.9f))
        assertEquals("""{"cabin":{"talk":0.6}}""", e.update(setOf("대화"), 4_000, 0.6f))
        assertEquals("""{"cabin":{"sound":"quiet","talk":null}}""", e.update(emptySet(), 6_000, null))
        assertNull(e.update(emptySet(), 8_000, null))
    }

    @Test
    fun `말소리 크기를 안 주면 예전처럼 동작한다`() {
        val e = EarSituation()
        assertEquals("""{"cabin":{"sound":"talk"}}""", e.update(setOf("대화"), 0))
    }

    @Test
    fun `상황 묶음 JSON`() {
        assertEquals("""{"place":{"kind":"coast"}}""", SituationPresets.patch("place.kind", "coast"))
        assertEquals("""{"drive":{"state":"fast","speed":null}}""", SituationPresets.patch("drive.state", "fast"))
        assertEquals("""{"car":{"drive_min":40}}""", SituationPresets.patch("car.drive_min", 40))
        assertEquals("""{"weather":{"kind":null}}""", SituationPresets.patch("weather.kind", null))
    }
}
