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

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(context: Context) {
        if (!BuildConfig.MIC_SENSING || running) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "마이크 권한이 없어 듣지 않는다")
            return
        }
        val minBuffer = AudioRecord.getMinBufferSize(SoundListener.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // 에코 제거가 켜져 우리 음악 소리가 덜 섞인다
            SoundListener.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBuffer, SoundListener.SAMPLE_RATE * 2),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            Log.w(TAG, "마이크를 열지 못함")
            return
        }
        val sound = SoundListener(LoggingClassifier(AppClassifier.current))
        listener = sound
        running = true
        worker = thread(name = "mic-sensing") { capture(record, sound) }
        Log.i(TAG, "듣기 시작")
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
                if (count > 0) sound.process(buffer, count)
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
