package com.twinpark.music.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuadrantControlTest {
    private val eps = 1e-4f

    @Test
    fun quadrantByPosition() {
        assertEquals(Quadrant.Echo, QuadrantControl.quadrantAt(-0.5f, -0.5f))
        assertEquals(Quadrant.Volume, QuadrantControl.quadrantAt(0.5f, -0.5f))
        assertEquals(Quadrant.Pitch, QuadrantControl.quadrantAt(-0.5f, 0.5f))
        assertEquals(Quadrant.Tempo, QuadrantControl.quadrantAt(0.5f, 0.5f))
    }

    @Test
    fun noQuadrantOnAxes() {
        assertNull(QuadrantControl.quadrantAt(0.01f, 0.5f))
        assertNull(QuadrantControl.quadrantAt(0.5f, -0.02f))
    }

    @Test
    fun levelIsDistanceFromCenter() {
        assertEquals(0f, QuadrantControl.levelAt(0f, 0f), eps)
        assertEquals(0.5f, QuadrantControl.levelAt(0.3f, 0.4f), eps)
        assertEquals(1f, QuadrantControl.levelAt(0.9f, 0.9f), eps)   // 가장자리 너머는 100%로 자른다
    }

    @Test
    fun initialLevels() {
        val l = QuadrantControl.initialLevels()
        assertEquals(0.7f, QuadrantControl.volumeOf(l), eps)
        assertEquals(EngineParams(), QuadrantControl.toParams(l))   // 음량 외는 모두 기본이라 조절량 0
    }

    @Test
    fun volumeStaysWhenAnotherQuadrantChanges() {
        // 음량 구획에서 100%를 찍어 두고 템포 구획에서 20%로 바꿔도 음량은 100%
        val levels = QuadrantControl.initialLevels()
        levels[Quadrant.Volume.ordinal] = QuadrantControl.levelAt(0.7071f, -0.7071f)
        levels[Quadrant.Tempo.ordinal] = QuadrantControl.levelAt(0.1414f, 0.1414f)
        assertEquals(1f, QuadrantControl.volumeOf(levels), eps)
        assertEquals(0.2f, levels[Quadrant.Tempo.ordinal], eps)
        assertEquals(-0.6f, QuadrantControl.toParams(levels).tempo, eps)
    }

    @Test
    fun levelsMapToParams() {
        val levels = QuadrantControl.initialLevels()
        levels[Quadrant.Tempo.ordinal] = 0.75f
        levels[Quadrant.Echo.ordinal] = 0.2f
        levels[Quadrant.Pitch.ordinal] = 1f
        val p = QuadrantControl.toParams(levels)
        assertEquals(0.5f, p.tempo, eps)
        assertEquals(-0.6f, p.space, eps)
        assertEquals(1f, p.reg, eps)
        assertEquals(0f, p.dens, eps)
    }

    @Test
    fun initialPosMatchesInitialVolume() {
        val (x, y) = QuadrantControl.initialPos()
        assertEquals(Quadrant.Volume, QuadrantControl.quadrantAt(x, y))
        assertEquals(QuadrantControl.INITIAL_VOLUME, QuadrantControl.levelAt(x, y), 0.001f)
    }
}
