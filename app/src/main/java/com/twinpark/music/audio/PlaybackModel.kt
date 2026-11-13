package com.twinpark.music.audio

enum class PlaybackKind { Buffering, Playing, Paused, Error }

/** 시스템 미디어 화면(차량 미디어 센터, 알림)에 보일 지금 연주 정보. */
data class NowPlaying(val kind: PlaybackKind, val title: String, val artist: String, val album: String)

object PlaybackModel {
    const val APP_LABEL = "AI IS CONDUCTING"

    fun from(state: BgmState, paused: Boolean, status: BgmStatus?): NowPlaying = when {
        state is BgmState.Failed -> NowPlaying(PlaybackKind.Error, APP_LABEL, state.reason, "")
        state != BgmState.Ready -> NowPlaying(PlaybackKind.Buffering, APP_LABEL, "음악 준비 중...", "")
        else -> {
            val named = status?.takeIf { it.moodName.isNotEmpty() }
            NowPlaying(
                kind = if (paused) PlaybackKind.Paused else PlaybackKind.Playing,
                title = named?.moodName ?: APP_LABEL,
                artist = APP_LABEL,
                album = named?.let { "${it.key}조 · ♩=${it.bpm}" }.orEmpty(),
            )
        }
    }
}
