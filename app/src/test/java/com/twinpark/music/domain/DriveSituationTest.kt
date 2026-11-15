package com.twinpark.music.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveSituationTest {
    private fun at(kmh: Float?) = DrivingState(speedKmh = kmh)

    @Test
    fun `속도를 모르면 보내지 않는다`() {
        assertNull(DriveSituation().update(at(null)))
    }

    @Test
    fun `처음 한 번 보내고 조금 바뀐 값은 건너뛴다`() {
        val d = DriveSituation()
        assertEquals("""{"drive":{"speed":62}}""", d.update(at(62.4f)))
        assertNull(d.update(at(64f)))
        assertNull(d.update(at(58f)))
        assertEquals("""{"drive":{"speed":67}}""", d.update(at(67.9f)))
    }

    @Test
    fun `멈춤과 달림이 바뀌면 작은 차이라도 보낸다`() {
        val d = DriveSituation()
        assertEquals("""{"drive":{"speed":4}}""", d.update(at(4f)))
        assertEquals("""{"drive":{"speed":2}}""", d.update(at(2f)))
        assertEquals("""{"drive":{"speed":3}}""", d.update(at(3.4f)))
    }

    @Test
    fun `사람이 고른 칸과 다시 보내기`() {
        val d = DriveSituation()
        assertNull(d.update(at(50f), setOf("drive.state")))
        assertEquals("""{"drive":{"speed":50}}""", d.update(at(50f)))
        d.reset()
        assertEquals("""{"drive":{"speed":50}}""", d.update(at(50f)))
    }
}
