package com.twinpark.music.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveSituationTest {
    private fun at(kmh: Float?, limit: Int? = null) = DrivingState(speedKmh = kmh, limitKmh = limit)
    private val min = DriveSituation.MINUTE_MS

    @Test
    fun `속도를 모르면 보내지 않는다`() {
        assertNull(DriveSituation().update(at(null)))
    }

    @Test
    fun `처음 한 번 보내고 조금 바뀐 값은 건너뛴다`() {
        val d = DriveSituation()
        assertEquals("""{"drive":{"speed":62,"limit":null},"car":{"drive_min":0}}""", d.update(at(62.4f), 0))
        assertNull(d.update(at(64f), 2_000))
        assertNull(d.update(at(58f), 4_000))
        assertEquals("""{"drive":{"speed":67}}""", d.update(at(67.9f), 6_000))
    }

    @Test
    fun `멈춤과 달림이 바뀌면 작은 차이라도 보낸다`() {
        val d = DriveSituation()
        d.update(at(4f), 0)
        assertEquals("""{"drive":{"speed":2}}""", d.update(at(2f), 2_000))
        assertEquals("""{"drive":{"speed":3}}""", d.update(at(3.4f), 4_000))
    }

    @Test
    fun `제한속도는 바뀔 때만 보내고 모르면 지운다`() {
        val d = DriveSituation()
        assertEquals("""{"drive":{"speed":80,"limit":60},"car":{"drive_min":0}}""", d.update(at(80f, 60), 0))
        assertNull(d.update(at(80f, 60), 2_000))
        assertEquals("""{"drive":{"limit":100}}""", d.update(at(80f, 100), 4_000))
        assertEquals("""{"drive":{"limit":null}}""", d.update(at(80f, null), 6_000))
    }

    @Test
    fun `운전 시간은 달리는 동안만 세고 분이 바뀔 때 보낸다`() {
        val d = DriveSituation()
        d.update(at(50f), 0)
        var now = 0L
        var last: String? = null
        while (now < 61_000) { now += 2_000; d.update(at(50f), now)?.let { last = it } }
        assertEquals("""{"car":{"drive_min":1}}""", last)
        assertNull(d.update(at(50f), now + 2_000))
    }

    @Test
    fun `서 있는 동안은 세지 않고 오래 서 있으면 0으로 돌아간다`() {
        val d = DriveSituation()
        d.update(at(50f), 0)
        var now = 0L
        while (now < 2 * min) { now += 2_000; d.update(at(50f), now) }       // 2분 달림
        val before = d.update(at(0f), now + 2_000)                          // 서기 시작: 시간은 그대로
        assertNull(before?.takeIf { it.contains("drive_min") })
        var out: String? = null
        val rest = now + DriveSituation.REST_MS
        while (now < rest + 4_000) { now += 2_000; d.update(at(0f), now)?.let { out = it } }
        assertEquals("""{"car":{"drive_min":0}}""", out)
    }

    @Test
    fun `앱이 오래 멈췄던 간격은 한 번에 5초까지만 센다`() {
        val d = DriveSituation()
        d.update(at(50f), 0)
        d.update(at(50f), 60 * min)                                         // 1시간 뒤 첫 호출
        assertNull(d.update(at(50f), 60 * min + 1_000))                     // 아직 0분
    }

    @Test
    fun `사람이 고른 칸과 다시 보내기`() {
        val d = DriveSituation()
        assertNull(d.update(at(50f), 0, setOf("drive.state", "car.drive_min")))
        assertEquals("""{"drive":{"speed":50,"limit":null},"car":{"drive_min":0}}""", d.update(at(50f), 2_000))
        d.reset()
        assertEquals("""{"drive":{"speed":50,"limit":null},"car":{"drive_min":0}}""", d.update(at(50f), 4_000))
    }
}
