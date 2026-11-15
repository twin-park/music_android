package com.twinpark.music.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.twinpark.music.BuildConfig
import com.twinpark.music.audio.BgmController
import com.twinpark.music.sensing.MicTap
import java.io.File
import kotlin.concurrent.thread

/**
 * 마이크 대신 소리 파일로 음성 명령을 시험하는 통로(시연용 DEV 빌드 전용 · `DEV_MODE`가 아니면 아무것도 하지 않는다).
 * 쉽게 하려면 `tools/feed-voice.ps1 -File 말.wav`. 직접 하려면(adb 쉘은 앱 저장소에 직접 쓸 수 없어서 디버그 빌드의 run-as 로 앱 비공개 폴더에 넣는다):
 *
 *     adb push 말.wav /data/local/tmp/feed.wav
 *     adb shell "run-as com.twinpark.music --user 10 sh -c 'mkdir -p files && cp /data/local/tmp/feed.wav files/feed.wav'"
 *     adb shell am broadcast --user 10 -n com.twinpark.music/.voice.DebugVoiceFeedReceiver -a com.twinpark.music.debug.FEED --es file /data/user/10/com.twinpark.music/files/feed.wav
 *
 * (Git Bash에서는 `/`로 시작하는 경로가 바뀌지 않게 `MSYS_NO_PATHCONV=1`을 준다.) `file`에 이름만 주면 앱의 외부 저장 폴더에서 찾는다.
 * 앱이 음성 인식을 한 번 듣기 시작한 것처럼 파일 소리를 [MicTap]으로 흘려보내므로, 인식 → 엔진 → 답변 소리가 마이크 없이 그대로 돈다.
 * 모델을 처음 풀 때는 준비될 때까지 기다리다 포기할 수 있다. 그럴 때는 "준비됨" 로그가 뜬 뒤 다시 보낸다.
 */
class DebugVoiceFeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEV_MODE) return
        val name = intent.getStringExtra("file") ?: "feed.wav"
        val app = context.applicationContext
        val file = if (name.startsWith("/")) File(name) else File(app.getExternalFilesDir(null), name)
        val pending = goAsync()
        thread(name = "debug-voice-feed") {
            try {
                DebugVoiceFeed.run(app, file)
            } catch (e: Exception) {
                Log.w(TAG, "소리 넣기 실패: ${e.javaClass.simpleName} ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object { const val TAG = "DebugFeed" }
}

object DebugVoiceFeed {
    private const val TAG = "DebugFeed"
    private const val CHUNK = 1_600                 // 0.1초
    private const val WAIT_READY_MS = 8_000L        // 방송을 받는 동안 기다릴 수 있는 시간에 맞춘다(약 10초)
    private const val TAIL_SILENCE_MS = 1_500L      // 말이 끝난 것을 인식기가 알아채도록 붙이는 고요

    fun run(context: Context, file: File) {
        val samples = AudioFileDecoder.decode(file)
        Log.i(TAG, "소리 파일 ${file.name}: ${samples.size * 1000L / PcmConvert.TARGET_RATE}ms")
        BgmController.start(context)
        AppVoice.start(context)
        val end = SystemClock.elapsedRealtime() + WAIT_READY_MS
        while (!AppVoice.listen() && SystemClock.elapsedRealtime() < end) Thread.sleep(250)
        val sink = MicTap.sink
        if (sink == null) {
            Log.w(TAG, "음성 인식이 아직 준비되지 않았음(처음에는 모델을 푸는 데 20~30초). 준비됨 로그 뒤에 다시 보내세요")
            return
        }
        Log.i(TAG, "듣기 시작, 소리를 넣는다")
        feed(samples)
        feed(ShortArray((PcmConvert.TARGET_RATE * TAIL_SILENCE_MS / 1000).toInt()))
    }

    /** 마이크가 읽는 것처럼 조각내서 넣는다. 인식기가 한 문장이 끝났다고 보고 듣기를 끝냈으면 멈춘다. */
    private fun feed(samples: ShortArray) {
        var off = 0
        while (off < samples.size) {
            val sink = MicTap.sink ?: return
            val n = minOf(CHUNK, samples.size - off)
            sink(samples.copyOfRange(off, off + n), n)
            off += n
            Thread.sleep(20)
        }
    }
}
