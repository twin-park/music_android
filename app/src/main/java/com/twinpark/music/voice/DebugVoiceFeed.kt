package com.twinpark.music.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.twinpark.music.BuildConfig
import com.twinpark.music.audio.BgmController
import com.twinpark.music.audio.BgmState
import com.twinpark.music.audio.SituationDirector
import com.twinpark.music.domain.DebugSoundSensor
import com.twinpark.music.domain.SoundListener
import com.twinpark.music.domain.heardAll
import com.twinpark.music.sensing.AppClassifier
import com.twinpark.music.sensing.AppSensors
import com.twinpark.music.sensing.MicTap
import com.twinpark.music.sensing.OnnxSoundClassifier
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
        val ear = intent.getStringExtra("mode") == "ear"
        val seconds = intent.getIntExtra("seconds", DebugVoiceFeed.EAR_SECONDS)
        val pending = goAsync()
        if (ear) pending.finish()                         // 오래 걸리므로 방송은 바로 끝낸다(앱이 떠 있는 동안 돈다)
        thread(name = "debug-voice-feed") {
            try {
                if (ear) DebugVoiceFeed.runEar(app, file, seconds) else DebugVoiceFeed.run(app, file)
            } catch (e: Exception) {
                Log.w(TAG, "소리 넣기 실패: ${e.javaClass.simpleName} ${e.message}")
            } finally {
                if (!ear) pending.finish()
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
    private const val OBSERVE_MS = 2_000L           // 앱이 상황을 판단하는 간격(BgmService와 같다)
    private const val TAIL_MS = 12_000L             // 소리가 끝난 뒤에도 결정이 어떻게 이어지는지 지켜보는 시간

    /** 소리 파일에서 앞쪽 몇 초를 쓸지(기본) */
    const val EAR_SECONDS = 30

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

    /**
     * 주변 소리 시험: 소리 파일을 마이크 소리처럼 귀 모델(소리 분류)에 실제 시간으로 흘려 넣고, 앱이 2초마다 하는 대로 상황을 판단해서
     * 지휘자가 어떤 분위기와 장면을 고르는지 로그(DebugFeed)로 남긴다. 주변 소리 → 상황 → 지휘자 → 합성의 핵심 경로를 마이크 없이 본다.
     */
    fun runEar(context: Context, file: File, seconds: Int) {
        val all = AudioFileDecoder.decode(file)
        val samples = all.copyOf(minOf(all.size, seconds * PcmConvert.TARGET_RATE))
        Log.i(TAG, "[귀] 소리 파일 ${file.name}: ${samples.size * 1000L / PcmConvert.TARGET_RATE}ms 사용")
        BgmController.start(context)
        val end = SystemClock.elapsedRealtime() + 90_000
        while (BgmController.state.value != BgmState.Ready && SystemClock.elapsedRealtime() < end) Thread.sleep(250)
        if (BgmController.state.value != BgmState.Ready) {
            Log.w(TAG, "[귀] 엔진이 준비되지 않았음")
            return
        }
        val classifier = AppClassifier.create(context)
        val listener = SoundListener(classifier)
        DebugSoundSensor.fromFile = listener
        try {
            logDecision("시작 전")
            var nextObserve = SystemClock.elapsedRealtime() + OBSERVE_MS
            var off = 0
            while (off < samples.size) {
                val n = minOf(CHUNK, samples.size - off)
                listener.process(samples.copyOfRange(off, off + n), n)
                off += n
                Thread.sleep(100)                                       // 실제 시간(조각 하나가 0.1초)
                if (SystemClock.elapsedRealtime() >= nextObserve) { observe(off); nextObserve += OBSERVE_MS }
            }
            val tailEnd = SystemClock.elapsedRealtime() + TAIL_MS
            while (SystemClock.elapsedRealtime() < tailEnd) {
                Thread.sleep(OBSERVE_MS)
                observe(off)
            }
        } finally {
            DebugSoundSensor.fromFile = null
            (classifier as? OnnxSoundClassifier)?.close()
            Log.i(TAG, "[귀] 끝")
        }
    }

    private fun observe(offset: Int) {
        SituationDirector.observe(AppSensors.all.heardAll())
        logDecision("소리 ${offset / PcmConvert.TARGET_RATE}초")
    }

    /** 지휘자가 지금 고른 것을 한 줄로 남긴다. */
    private fun logDecision(label: String) {
        val c = BgmController.conductor.value
        val words = AppSensors.all.heardAll()
        Log.i(TAG, "[귀] $label | 들린 단어 $words | 현재 ${c?.current} 고른 ${c?.chosen}(${c?.chosenName}) 장면 ${c?.scene} | ${c?.line}")
    }
}
