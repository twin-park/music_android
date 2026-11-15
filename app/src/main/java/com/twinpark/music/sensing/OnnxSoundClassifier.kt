package com.twinpark.music.sensing

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.carbgm.ear.EarWords
import com.carbgm.ear.Prosody
import com.twinpark.music.domain.SoundClassifier
import com.twinpark.music.domain.TalkLevel
import org.json.JSONObject
import java.nio.FloatBuffer

/**
 * 귀(ear2) 모델로 4초 소리를 한국어 단어로 바꾼다. 모델은 파형을 바로 받는다(로그 멜까지 모델 안).
 * 소리 28개 확률에 문턱을 적용하고, 말하는 중이면 감정과 말투 단어도 붙인다(EarWords).
 */
class OnnxSoundClassifier private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val thresholds: FloatArray,
) : SoundClassifier {

    override fun classify(samples: FloatArray, sampleRate: Int): Set<String> {
        require(sampleRate == SAMPLE_RATE && samples.size == WINDOW_SAMPLES) { "16kHz 4초(64000샘플)만 받는다: $sampleRate Hz, ${samples.size}샘플" }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(samples), longArrayOf(1, WINDOW_SAMPLES.toLong())).use { input ->
            session.run(mapOf(INPUT to input)).use { out ->
                @Suppress("UNCHECKED_CAST")
                val sounds = (out.get(SOUNDS).get().value as Array<FloatArray>)[0]
                @Suppress("UNCHECKED_CAST")
                val emotion = (out.get(EMOTION).get().value as Array<FloatArray>)[0]
                TalkLevel.report(sounds[SPEECH], thresholds[SPEECH], System.currentTimeMillis())
                return EarWords.toWords(sounds, thresholds, emotion, Prosody.compute(samples)).map { it.word }.toSet()
            }
        }
    }

    fun close() {
        session.close()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val WINDOW_SAMPLES = 64_000
        private const val SPEECH = 0                   // EarWords.SOUND_IDS 의 첫 번째(speech)
        private const val INPUT = "wav"
        private const val SOUNDS = "sounds"
        private const val EMOTION = "emotion"

        /** 에셋 `ear/ear_wave.onnx`와 `ear/thresholds.json`을 읽어 만든다. 휴대폰 코어를 다 쓰지 않도록 스레드는 2개. */
        fun create(context: Context): OnnxSoundClassifier {
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
            val model = context.assets.open("ear/ear_wave.onnx").use { it.readBytes() }
            val json = JSONObject(context.assets.open("ear/thresholds.json").bufferedReader().use { it.readText() })
            val thresholds = FloatArray(EarWords.SOUND_IDS.size) { json.getDouble(EarWords.SOUND_IDS[it]).toFloat() }
            return OnnxSoundClassifier(env, env.createSession(model, options), thresholds)
        }
    }
}
