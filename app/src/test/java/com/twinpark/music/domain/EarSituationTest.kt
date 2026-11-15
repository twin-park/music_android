package com.twinpark.music.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `물소리만 혼자 들리면 비로 본다`() {
        val e = EarSituation()
        assertEquals("""{"cabin":{"sound":"quiet"},"weather":{"kind":"rain"}}""", e.update(setOf("물소리"), 0))
        assertNull(e.update(setOf("물소리"), 2_000))
    }

    @Test
    fun `비 낱말도 그대로 비로 본다`() {
        assertEquals("""{"cabin":{"sound":"quiet"},"weather":{"kind":"rain"}}""", EarSituation().update(setOf("비"), 0))
    }

    @Test
    fun `바다 계열이 같이 들리면 바다가 우선이고 비로 보지 않는다`() {
        val e = EarSituation()
        // 파도 녹음에서 귀 모델이 낸 단어 그대로
        assertEquals("""{"cabin":{"sound":"quiet"},"place":{"kind":"coast"}}""", e.update(setOf("물소리", "비", "바다", "파도"), 0))
        assertNull(e.update(setOf("물소리", "비", "바다", "파도", "기차"), 2_000))
    }

    @Test
    fun `비를 켜 둔 뒤 바다 계열이 들리면 비를 바로 지운다`() {
        val e = EarSituation()
        e.update(setOf("물소리"), 0)
        assertEquals("""{"place":{"kind":"coast"},"weather":{"kind":null}}""", e.update(setOf("물소리", "비", "파도"), 2_000))
    }

    @Test
    fun `바다에서도 천둥은 폭풍으로 본다`() {
        assertEquals("""{"cabin":{"sound":"quiet"},"place":{"kind":"coast"},"weather":{"kind":"storm"}}""", EarSituation().update(setOf("파도", "천둥"), 0))
    }

    @Test
    fun `비 칸은 귀가 켠 적이 없으면 건드리지 않는다`() {
        val e = EarSituation()
        assertEquals("""{"cabin":{"sound":"quiet"},"place":{"kind":"coast"}}""", e.update(setOf("파도"), 0))        // 날씨 칸은 빼먹음 = 모름
    }

    @Test
    fun `비가 안 들린 지 30초가 지나면 비 칸을 지운다`() {
        val e = EarSituation()
        e.update(setOf("물소리"), 0)
        assertNull(e.update(emptySet(), EarSituation.RELEASE_MS - 1))
        assertEquals("""{"weather":{"kind":null}}""", e.update(emptySet(), EarSituation.RELEASE_MS))
    }

    @Test
    fun `소리로 비를 켜 둔 동안만 날씨 칸을 쥔다`() {
        val e = EarSituation()
        assertFalse(e.holdsWeather)
        e.update(setOf("물소리"), 0)
        assertTrue(e.holdsWeather)
        e.update(setOf("물소리", "비", "파도"), 2_000)                  // 바다가 우선: 비를 지움
        assertFalse(e.holdsWeather)
    }

    @Test
    fun `안 들린 지 30초가 지나 비를 지우면 날씨 칸을 놓는다`() {
        val e = EarSituation()
        e.update(setOf("비"), 0)
        assertTrue(e.holdsWeather)
        e.update(emptySet(), EarSituation.RELEASE_MS)
        assertFalse(e.holdsWeather)
    }

    @Test
    fun `바다가 최근에 들렸으면 파도 낱말이 빠진 창에서도 비로 보지 않는다`() {
        val e = EarSituation()
        e.update(setOf("물소리", "비", "바다", "파도"), 0)
        assertNull(e.update(setOf("물소리", "비"), 4_000))                         // 파도 낱말이 잠깐 빠졌다: 깜박이지 않는다
        assertFalse(e.holdsWeather)
    }

    @Test
    fun `바다가 사라진 지 30초가 지나면 물소리는 다시 비로 본다`() {
        val e = EarSituation()
        e.update(setOf("바다", "파도"), 0)
        e.update(emptySet(), EarSituation.RELEASE_MS - 1)
        val patch = e.update(setOf("물소리"), EarSituation.RELEASE_MS)
        assertEquals("""{"place":{"kind":null},"weather":{"kind":"rain"}}""", patch?.replace(""""cabin":{"sound":"quiet"},""", ""))
    }
}
