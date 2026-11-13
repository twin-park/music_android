package com.twinpark.music.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackModelTest {
    private val status = BgmStatus(moodName = "평온한 재즈", role = "솔로", key = "G", bpm = 104, underruns = 0, playing = true)

    @Test
    fun playingShowsTheMoodAndKey() {
        val now = PlaybackModel.from(BgmState.Ready, paused = false, status = status)
        assertEquals(PlaybackKind.Playing, now.kind)
        assertEquals("평온한 재즈", now.title)
        assertEquals(PlaybackModel.APP_LABEL, now.artist)
        assertEquals("G조 · ♩=104", now.album)
    }

    @Test
    fun pausedKeepsTheMoodButChangesTheKind() {
        val now = PlaybackModel.from(BgmState.Ready, paused = true, status = status)
        assertEquals(PlaybackKind.Paused, now.kind)
        assertEquals("평온한 재즈", now.title)
    }

    @Test
    fun readyWithoutStatusFallsBackToTheAppLabel() {
        val now = PlaybackModel.from(BgmState.Ready, paused = false, status = null)
        assertEquals(PlaybackKind.Playing, now.kind)
        assertEquals(PlaybackModel.APP_LABEL, now.title)
        assertEquals("", now.album)
    }

    @Test
    fun emptyMoodNameIsTreatedAsMissing() {
        val now = PlaybackModel.from(BgmState.Ready, paused = false, status = status.copy(moodName = ""))
        assertEquals(PlaybackModel.APP_LABEL, now.title)
    }

    @Test
    fun loadingAndIdleAreBuffering() {
        assertEquals(PlaybackKind.Buffering, PlaybackModel.from(BgmState.Loading, paused = false, status = null).kind)
        assertEquals(PlaybackKind.Buffering, PlaybackModel.from(BgmState.Idle, paused = false, status = null).kind)
    }

    @Test
    fun failureShowsTheReason() {
        val now = PlaybackModel.from(BgmState.Failed("엔진 데이터가 없습니다"), paused = false, status = null)
        assertEquals(PlaybackKind.Error, now.kind)
        assertEquals("엔진 데이터가 없습니다", now.artist)
    }
}
