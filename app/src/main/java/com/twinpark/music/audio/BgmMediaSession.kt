package com.twinpark.music.audio

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.twinpark.music.domain.Mood

/** 차량 미디어 센터와 알림이 보는 미디어 세션. 앱 프로세스에서 하나를 계속 쓴다(토큰이 바뀌지 않게). */
object BgmMediaSession {
    private const val TAG = "BgmSession"
    private const val ACTIONS = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
        PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS

    private var session: MediaSession? = null

    fun token(context: Context): MediaSession.Token = obtain(context).sessionToken

    fun update(context: Context, now: NowPlaying) {
        val s = obtain(context)
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, now.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, now.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, now.album)
                .build(),
        )
        s.setPlaybackState(playbackState(now.kind))
        s.isActive = true
    }

    fun deactivate() {
        session?.apply {
            setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f).build())
            isActive = false
        }
    }

    private fun playbackState(kind: PlaybackKind): PlaybackState {
        val (state, speed) = when (kind) {
            PlaybackKind.Playing -> PlaybackState.STATE_PLAYING to 1f
            PlaybackKind.Paused -> PlaybackState.STATE_PAUSED to 0f
            PlaybackKind.Buffering -> PlaybackState.STATE_BUFFERING to 0f
            PlaybackKind.Error -> PlaybackState.STATE_ERROR to 0f
        }
        return PlaybackState.Builder().setActions(ACTIONS).setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, speed).build()
    }

    private fun obtain(context: Context): MediaSession = session ?: MediaSession(context.applicationContext, TAG)
        .apply { setCallback(Controls(context.applicationContext), Handler(Looper.getMainLooper())) }
        .also { session = it }

    private class Controls(private val context: Context) : MediaSession.Callback() {
        override fun onPlay() = send(BgmService.ACTION_PLAY)
        override fun onPause() = send(BgmService.ACTION_PAUSE)
        override fun onStop() = send(BgmService.ACTION_STOP)
        override fun onSkipToNext() = stepMood(+1)
        override fun onSkipToPrevious() = stepMood(-1)

        private fun send(action: String) {
            context.startService(Intent(context, BgmService::class.java).setAction(action))
        }

        private fun stepMood(step: Int) {
            SituationDirector.manualOverride()
            BgmController.requestMood(Mood.stepEngineId(BgmController.moodId.value, step))
        }
    }
}
