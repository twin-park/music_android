package com.twinpark.music.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class QuadrantControlTest {
    private val eps = 1e-4f

    @Test
    fun weightsSumToOne() {
        for (x in listOf(-0.9f, -0.1f, 0f, 0.05f, 0.7f)) for (y in listOf(-0.8f, 0f, 0.1f, 0.6f)) {
            assertEquals(1f, QuadrantControl.weights(x, y).sum(), eps)
        }
    }

    @Test
    fun bottomRightIsTempo() {
        // 시안 예: 아래 오른쪽, 중심에서 거리 0.7이면 템포 +0.4
        val d = 0.7f
        val p = QuadrantControl.toParams(QuadrantControl.amounts(d * 0.7071f, d * 0.7071f))
        assertEquals(0.4f, p.tempo, 0.01f)
        assertEquals(0f, p.space, eps)
        assertEquals(0f, p.dens, eps)
        assertEquals(0f, p.reg, eps)
    }

    @Test
    fun eachQuadrantMapsToItsParam() {
        val v = 0.8f * 0.7071f
        assertEquals(0.6f, QuadrantControl.toParams(QuadrantControl.amounts(-v, -v)).space, 0.01f)
        assertEquals(0.6f, QuadrantControl.toParams(QuadrantControl.amounts(v, -v)).dens, 0.01f)
        assertEquals(0.6f, QuadrantControl.toParams(QuadrantControl.amounts(-v, v)).reg, 0.01f)
        assertEquals(0.6f, QuadrantControl.toParams(QuadrantControl.amounts(v, v)).tempo, 0.01f)
    }

    @Test
    fun halfDistanceIsNeutralAndEdgeIsMax() {
        assertEquals(0f, QuadrantControl.toParams(QuadrantControl.amounts(0.5f * 0.7071f, 0.5f * 0.7071f)).tempo, 0.01f)
        assertEquals(1f, QuadrantControl.toParams(QuadrantControl.amounts(0.7071f, 0.7071f)).tempo, 0.01f)
    }

    @Test
    fun axisBlendIsContinuous() {
        // 세로축을 가로질러 움직여도 값이 갑자기 튀지 않는다
        var prev = QuadrantControl.toParams(QuadrantControl.amounts(-0.3f, 0.6f)).reg
        var x = -0.3f
        while (x < 0.3f) {
            x += 0.01f
            val now = QuadrantControl.toParams(QuadrantControl.amounts(x, 0.6f)).reg
            assert(kotlin.math.abs(now - prev) < 0.1f) { "x=$x jump ${now - prev}" }
            prev = now
        }
    }
}
