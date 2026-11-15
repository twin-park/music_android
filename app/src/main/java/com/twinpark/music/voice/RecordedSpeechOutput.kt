package com.twinpark.music.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.twinpark.music.domain.SoundGate

/**
 * 미리 만들어 둔 음성 파일([ReplyClips])을 재생해서 답변을 소리로 알린다. TTS 서비스나 권한이 필요 없어서 어디서든 같게 동작한다.
 * 파일에 없는 문장은 읽지 않는다. 재생하는 동안과 그 뒤 [TAIL_MS]는 소리 분류를 건너뛴다([SoundGate]).
 */
class RecordedSpeechOutput(private val context: Context) : SpeechOutput {
    private var player: MediaPlayer? = null
    @Volatile private var ready = false

    override val isReady: Boolean get() = ready

    override fun start() {
        ready = true
        Log.i(TAG, "음성 출력 준비됨(미리 만든 음성 파일)")
    }

    @Synchronized
    override fun speak(text: String) {
        if (!ready) return
        val file = ReplyClips.fileFor(text) ?: run {
            Log.i(TAG, "파일이 없는 문장이라 읽지 않음: $text")
            return
        }
        release()
        SoundGate.muteUntil(System.currentTimeMillis() + MAX_SPEAK_MS)
        runCatching {
            context.assets.openFd("${ReplyClips.DIR}/$file").use { fd ->
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                )
                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                mp.setOnCompletionListener {
                    SoundGate.muteUntil(System.currentTimeMillis() + TAIL_MS)
                    release()
                }
                mp.setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "재생 오류 $what/$extra")
                    SoundGate.muteUntil(System.currentTimeMillis())
                    release()
                    true
                }
                mp.prepare()
                mp.start()
                player = mp
            }
        }.onFailure {
            Log.w(TAG, "재생 실패: ${it.javaClass.simpleName} ${it.message}")
            SoundGate.muteUntil(System.currentTimeMillis())
            release()
        }
    }

    @Synchronized
    private fun release() {
        player?.let { p -> runCatching { p.stop() }; runCatching { p.release() } }
        player = null
    }

    override fun close() {
        ready = false
        release()
        SoundGate.clear()
    }

    private companion object {
        const val TAG = "RecordedTts"
        const val MAX_SPEAK_MS = 8_000L          // 가장 긴 파일은 약 4초
        const val TAIL_MS = 4_500L
    }
}
