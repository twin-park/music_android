package com.twinpark.music.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SituationRulesTest {
    @Test
    fun `단어마다 conduct의 상황으로 간다`() {
        assertEquals("아이재우기", SituationRules.interpret(listOf("아기 울음"))?.engineMoodId)
        assertEquals("진정", SituationRules.interpret(listOf("화난"))?.engineMoodId)
        assertEquals("위로", SituationRules.interpret(listOf("슬픈"))?.engineMoodId)
        assertEquals("즐거운대화", SituationRules.interpret(listOf("웃음"))?.engineMoodId)
        assertEquals("바닷가보사노바", SituationRules.interpret(listOf("파도"))?.engineMoodId)
        assertEquals("비오는날", SituationRules.interpret(listOf("천둥"))?.engineMoodId)
        assertEquals("평온한재즈", SituationRules.interpret(listOf("새소리"))?.engineMoodId)
    }

    @Test
    fun `여러 단어면 위쪽 규칙이 이긴다`() {
        // conduct.py 와 같은 순서: 아이 재우기 > 진정 > 위로 > 즐거운 대화 > 바닷가
        assertEquals("아이 재우기", SituationRules.interpret(listOf("파도", "웃음", "속삭임"))?.situation)
        assertEquals("진정", SituationRules.interpret(listOf("웃음", "화난"))?.situation)
    }

    @Test
    fun `드라이브는 밤이면 밤 드라이브`() {
        assertEquals("낮드라이브", SituationRules.interpret(listOf("도로"), night = false)?.engineMoodId)
        assertEquals("밤드라이브", SituationRules.interpret(listOf("경적"), night = true)?.engineMoodId)
        assertEquals("드라이브", SituationRules.interpret(listOf("도로"), night = true)?.situation)
    }

    @Test
    fun `맞는 단어가 없으면 근거 없음`() {
        assertNull(SituationRules.interpret(emptyList()))
        assertNull(SituationRules.interpret(listOf("시끄러운", "조용한")))      // 말투 단어만으로는 상황을 바꾸지 않는다
    }

    @Test
    fun `진정은 템포와 세기를 낮추고 바닷가는 울림을 넓힌다`() {
        val calm = SituationRules.interpret(listOf("고함"))!!.params
        assertTrue(calm.tempo < 0 && calm.energy < 0)
        assertTrue(SituationRules.interpret(listOf("바다"))!!.params.space > 0)
    }

    @Test
    fun `전화벨은 통화`() {
        assertTrue(SituationRules.isPhone(listOf("전화벨", "대화")))
        assertFalse(SituationRules.isPhone(listOf("대화")))
    }
}

class SituationTrackerTest {
    private fun tracker() = SituationTracker(confirmMs = 4_000, minHoldMs = 15_000, phoneReleaseMs = 3_000)

    @Test
    fun `같은 새 상황이 이어져야 바뀐다`() {
        val t = tracker()
        assertNull(t.update(0, listOf("웃음")).current)             // 아직 확인 중
        assertNull(t.update(2_000, listOf("웃음")).current)
        val s = t.update(4_000, listOf("웃음"))                    // 4초 이어짐
        assertEquals("즐거운대화", s.current?.engineMoodId)
        assertTrue(s.switched)
        assertFalse(t.update(6_000, listOf("웃음")).switched)       // 바뀐 순간에만 true
    }

    @Test
    fun `소리 한 번으로는 바뀌지 않는다`() {
        val t = tracker()
        t.update(0, listOf("웃음")); t.update(2_000, listOf("웃음")); t.update(4_000, listOf("웃음"))
        // 15초가 지난 뒤 잠깐 경적만 한 번
        t.update(20_000, listOf("경적"))
        val s = t.update(22_000, emptyList())                      // 후보가 사라짐
        assertEquals("즐거운 대화", s.current?.situation)
        assertNull(s.candidate)
    }

    @Test
    fun `바꾼 뒤 최소 유지 시간 안에는 다시 바꾸지 않는다`() {
        val t = tracker()
        t.update(0, listOf("웃음")); t.update(4_000, listOf("웃음"))         // 4초에 전환
        t.update(5_000, listOf("화난"))
        val early = t.update(10_000, listOf("화난"))                         // 후보가 5초 이어졌지만 마지막 전환 후 6초
        assertEquals("즐거운 대화", early.current?.situation)
        assertNotNull(early.candidate)
        val later = t.update(19_000, listOf("화난"))                         // 마지막 전환 후 15초
        assertEquals("진정", later.current?.engineMoodId)
        assertTrue(later.switched)
    }

    @Test
    fun `조용해져도 상황을 되돌리지 않는다`() {
        val t = tracker()
        t.update(0, listOf("파도")); t.update(4_000, listOf("파도"))
        val s = t.update(60_000, emptyList())
        assertEquals("바닷가", s.current?.situation)
    }

    @Test
    fun `통화는 기다리지 않고 바로 낮추고 사라진 뒤 되돌린다`() {
        val t = tracker()
        assertTrue(t.update(0, listOf("전화벨")).ducking)                    // 상황 전환 확인을 기다리지 않음
        assertTrue(t.update(2_000, emptyList()).ducking)                    // 소리가 사라져도 3초는 유지
        assertFalse(t.update(3_500, emptyList()).ducking)
    }

    @Test
    fun `직접 고르면 자동 전환이 바로 되돌리지 않는다`() {
        val t = tracker()
        t.update(0, listOf("웃음")); t.update(4_000, listOf("웃음"))
        t.manualOverride(10_000)
        t.update(10_000, listOf("화난"))
        assertNull(t.update(14_000, listOf("화난")).current)               // 최소 유지 시간(15초) 전
        assertEquals("진정", t.update(25_000, listOf("화난")).current?.engineMoodId)
    }
}
