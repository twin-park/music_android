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
    fun volumeOnlyChangesVolumeAnywhere() {
        // 세부 조절을 숨기면 어느 구획에 놓아도 음량만 바뀌고 나머지는 기본이다
        val start = QuadrantControl.initialLevels()
        for ((x, y) in listOf(-0.4f to -0.3f, -0.3f to 0.4f, 0.3f to 0.4f, 0.01f to 0.5f)) {
            val l = QuadrantControl.update(start, x, y, detailControls = false)
            assertEquals(QuadrantControl.levelAt(x, y), QuadrantControl.volumeOf(l), eps)
            assertEquals(EngineParams(), QuadrantControl.toParams(l))
        }
    }

    @Test
    fun detailControlsChangeOnlyTheQuadrantUnderTheKnob() {
        val start = QuadrantControl.initialLevels()
        val l = QuadrantControl.update(start, 0.3f, 0.4f, detailControls = true)         // 아래 오른쪽 = 템포
        assertEquals(0.5f, l[Quadrant.Tempo.ordinal], eps)
        assertEquals(QuadrantControl.volumeOf(start), QuadrantControl.volumeOf(l), eps)
        // 축 위(경계)에서는 값이 그대로다
        assertEquals(start.toList(), QuadrantControl.update(start, 0.01f, 0.5f, detailControls = true).toList())
    }

    @Test
    fun updateDoesNotMutateInput() {
        val start = QuadrantControl.initialLevels()
        val before = start.toList()
        QuadrantControl.update(start, 0.6f, -0.2f, detailControls = false)
        assertEquals(before, start.toList())
    }

    @Test
    fun restoreVolumeUsesSavedValue() {
        assertEquals(0.3f, QuadrantControl.restoreVolume(0.3f), eps)
        assertEquals(0f, QuadrantControl.restoreVolume(0f), eps)
        assertEquals(1f, QuadrantControl.restoreVolume(1f), eps)
    }

    @Test
    fun restoreVolumeFallsBackWhenMissingOrBroken() {
        assertEquals(QuadrantControl.INITIAL_VOLUME, QuadrantControl.restoreVolume(null), eps)
        assertEquals(QuadrantControl.INITIAL_VOLUME, QuadrantControl.restoreVolume(Float.NaN), eps)
        assertEquals(QuadrantControl.INITIAL_VOLUME, QuadrantControl.restoreVolume(Float.POSITIVE_INFINITY), eps)
    }

    @Test
    fun restoreVolumeClampsOutOfRange() {
        assertEquals(1f, QuadrantControl.restoreVolume(1.8f), eps)
        assertEquals(0f, QuadrantControl.restoreVolume(-0.4f), eps)
    }

    @Test
    fun startingStateFollowsTheGivenVolume() {
        val levels = QuadrantControl.initialLevels(0.3f)
        assertEquals(0.3f, QuadrantControl.volumeOf(levels), eps)
        assertEquals(EngineParams(), QuadrantControl.toParams(levels))
        val (x, y) = QuadrantControl.initialPos(0.3f)
        assertEquals(0.3f, QuadrantControl.levelAt(x, y), eps)
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
