package com.twinpark.music.sensing

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.twinpark.music.BuildConfig
import com.twinpark.music.domain.SoundClassifier
import com.twinpark.music.domain.SoundListener
import com.twinpark.music.domain.SoundSensor
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 마이크로 차 안 소리를 듣는 입력. 빌드 설정 `MIC_SENSING`이 켜져 있고 권한이 있을 때만 동작하며, 앱 화면이 보이는 동안만 듣는다.
 * 소리는 저장하거나 보내지 않고 창 단위로 분류기에 넘긴 뒤 버린다.
 */
object MicSoundSensor : SoundSensor {
    private const val TAG = "MicSensing"
    private const val CHUNK_SAMPLES = SoundListener.SAMPLE_RATE / 10
    private const val STOP_WAIT_MS = 500L

    @Volatile private var running = false
    private var worker: Thread? = null

    @Volatile private var listener: SoundListener? = null

    /**
     * 마이크 종류, 앞에서부터 열리는 것을 쓴다. 음성 인식용([MediaRecorder.AudioSource.VOICE_RECOGNITION])이 기본이다:
     * 통화용(VOICE_COMMUNICATION)은 잡음 제거가 켜져 빗소리 같은 일정한 소음을 지우므로(같은 빗소리가 소리 크기 0.001로 들어왔다) 주변 소리를 듣는 데 맞지 않다.
     * 대신 통화용의 에코 제거가 없어서 앱이 틀어 둔 음악이 마이크에 더 섞인다.
     */
    private val SOURCES = listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.VOICE_COMMUNICATION)

    /** 시험용: 마이크 종류를 바꿔 본다(`DebugVoiceFeedReceiver`, DEV 빌드). 있으면 [SOURCES]보다 먼저 쓴다. */
    @Volatile var sourceOverride: Int? = null

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(context: Context) {
        if (!BuildConfig.MIC_SENSING || running) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "마이크 권한이 없어 듣지 않는다")
            return
        }
        val minBuffer = AudioRecord.getMinBufferSize(SoundListener.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val buffer = max(minBuffer, SoundListener.SAMPLE_RATE * 2)
        var source = -1
        var record: AudioRecord? = null
        for (candidate in listOfNotNull(sourceOverride) + SOURCES) {
            val r = AudioRecord(candidate, SoundListener.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer)
            if (r.state == AudioRecord.STATE_INITIALIZED) { record = r; source = candidate; break }
            r.release()
        }
        val rec = record ?: run {
            Log.w(TAG, "마이크를 열지 못함")
            return
        }
        // 모델 파일(약 23MB)을 읽는 일은 화면 스레드가 아니라 듣는 스레드에서 한다
        val model = lazy { AppClassifier.create(context) }
        val sound = SoundListener(LoggingClassifier { samples, rate -> model.value.classify(samples, rate) })
        listener = sound
        running = true
        worker = thread(name = "mic-sensing") {
            model.value
            try {
                capture(rec, sound)
            } finally {
                if (model.isInitialized()) (model.value as? OnnxSoundClassifier)?.close()
            }
        }
        Log.i(TAG, "듣기 시작(마이크 종류 $source)")
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        worker?.join(STOP_WAIT_MS)
        worker = null
        listener = null
        Log.i(TAG, "듣기 끝")
    }

    override fun heard(): Set<String> = listener?.heard().orEmpty()

    private fun capture(record: AudioRecord, sound: SoundListener) {
        val buffer = ShortArray(CHUNK_SAMPLES)
        try {
            record.startRecording()
            while (running) {
                val count = record.read(buffer, 0, buffer.size)
                if (count < 0) break
                if (count > 0) {
                    sound.process(buffer, count)
                    MicTap.sink?.invoke(buffer, count)          // 음성 인식이 듣는 동안만 같은 소리를 넘긴다
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    private class LoggingClassifier(private val inner: SoundClassifier) : SoundClassifier {
        override fun classify(samples: FloatArray, sampleRate: Int): Set<String> {
            val words = inner.classify(samples, sampleRate)
            val rms = sqrt(samples.fold(0.0) { sum, x -> sum + x * x } / samples.size)
            Log.d(TAG, "창 ${samples.size}샘플, 크기(RMS) ${"%.4f".format(rms)}, 단어 $words")
            return words
        }
    }
}
