package com.twinpark.music.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.twinpark.music.BuildConfig
import com.twinpark.music.sensing.MicTap
import java.io.File
import kotlin.concurrent.thread
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * 앱 안에서 도는 오프라인 음성 인식(Vosk, 한국어 소형 모델)으로 말을 글자로 받는다. 서비스나 통신이 필요 없다.
 * 모델은 앱 에셋 `vosk-ko/`에서 처음 한 번 앱 저장 공간으로 풀어 쓴다(빌드에 `VOSK_MODEL_DIR`을 준 경우만 들어 있다). 없으면 준비되지 않은 채로 남는다.
 * 소리는 [MicSoundSensor]가 이미 열어 둔 마이크를 [MicTap]으로 받는다(그래서 `MIC_SENSING` 빌드에서만 듣는다).
 * [listen]을 부를 때만 듣는다: 늘 듣게 하면 평소 대화("좋아", "별로야")가 음악 명령으로 오인될 수 있다.
 */
class VoskSpeechSource(
    private val context: Context,
    private val onText: (String) -> Unit,
) : SpeechSource {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var model: Model? = null
    @Volatile private var ready = false
    @Volatile private var grammar: String? = null
    @Volatile private var recognizer: Recognizer? = null
    @Volatile private var listening = false
    private val timeout = Runnable { finishListening("시간 초과") }

    override val isReady: Boolean get() = ready

    override fun start() {
        if (model != null || ready) return
        thread(name = "vosk-load") {
            val dir = prepareModelDir() ?: return@thread
            val loaded = runCatching { Model(dir.absolutePath) }
                .onFailure { Log.w(TAG, "모델을 열지 못함: ${it.javaClass.simpleName} ${it.message}") }
                .getOrNull() ?: return@thread
            model = loaded
            grammar = loadGrammar()
            ready = true
            Log.i(TAG, "음성 인식 준비됨(Vosk 한국어, 오프라인)")
        }
    }

    /** 엔진이 알아듣는 낱말로 좁힌 인식 문법. 낱말 파일을 못 읽으면 null(제한 없이 인식). */
    private fun loadGrammar(): String? {
        val json = runCatching { context.assets.open("bgm/conductor/signals.json").use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            ?: return null
        val words = SpeechVocabulary.words(json)
        Log.i(TAG, "알아들을 낱말 ${words.size}개로 인식 범위를 좁힘")
        return SpeechVocabulary.grammar(words)
    }

    /** 에셋의 모델을 저장 공간으로 풀어 폴더를 돌려준다. 이미 풀었으면 그대로 쓰고, 에셋에 모델이 없으면 null. */
    private fun prepareModelDir(): File? {
        val target = File(context.filesDir, "vosk-ko")
        val done = File(target, DONE_MARK)
        if (done.isFile) return target
        val top = runCatching { context.assets.list(ASSET_DIR) }.getOrNull().orEmpty()
        if (top.isEmpty()) {
            Log.i(TAG, "이 빌드에는 음성 인식 모델이 없음(VOSK_MODEL_DIR 없이 빌드)")
            return null
        }
        Log.i(TAG, "음성 인식 모델을 처음 한 번 풀어 둠(약 250MB)")
        target.deleteRecursively()
        return runCatching {
            copyAssets(ASSET_DIR, target)
            done.writeText("ok")
            target
        }.onFailure {
            Log.w(TAG, "모델을 풀지 못함: ${it.javaClass.simpleName} ${it.message}")
            target.deleteRecursively()
        }.getOrNull()
    }

    private fun copyAssets(path: String, to: File) {
        val children = context.assets.list(path).orEmpty()
        if (children.isEmpty()) {                                  // 파일
            to.parentFile?.mkdirs()
            context.assets.open(path).use { input -> to.outputStream().use { input.copyTo(it) } }
        } else {                                                   // 폴더
            to.mkdirs()
            for (c in children) copyAssets("$path/$c", File(to, c))
        }
    }

    override fun listen(): Boolean {
        val m = model ?: return false
        if (!ready || listening) return false
        if (!BuildConfig.MIC_SENSING) {
            Log.w(TAG, "마이크 소리를 받으려면 MIC_SENSING 빌드여야 함")
            return false
        }
        listening = true
        val rec = newRecognizer(m)
        recognizer = rec
        MicTap.sink = { samples, count ->
            if (listening && runCatching { rec.acceptWaveForm(samples, count) }.getOrDefault(false)) {
                val json = rec.result                            // 한 문장이 끝났다
                val heard = Heard(textOf(json), confidenceOf(json))
                main.post { finishListening("인식 끝", heard) }
            }
        }
        main.postDelayed(timeout, LISTEN_MS)
        Log.i(TAG, "듣는 중")
        return true
    }

    /** 듣기를 끝낸다. 믿을 만하게 들었으면 글자를 넘기고, 아니면 빈 글자를 넘긴다(앱이 "잘 못 알아들었어요"라고 답하게). [heard]가 없으면 지금까지 들은 것을 마무리해서 쓴다. */
    private fun finishListening(why: String, heard: Heard? = null) {
        if (!listening) return
        listening = false
        main.removeCallbacks(timeout)
        MicTap.sink = null
        val rec = recognizer
        recognizer = null
        val got = heard ?: rec?.let { r -> runCatching { r.finalResult }.getOrNull()?.let { Heard(textOf(it), confidenceOf(it)) } } ?: Heard("", 0.0)
        runCatching { rec?.close() }
        val ok = accepts(got)
        Log.i(TAG, "듣기 종료: $why, 들은 말 \"${got.text}\", 신뢰도 ${"%.3f".format(got.confidence)}, ${if (ok) "받아들임" else "버림"}")
        onText(if (ok) SpeechPolicy.accept(got.text).orEmpty() else "")
    }

    /** 글자와 그 신뢰도 */
    data class Heard(val text: String, val confidence: Double)

    /** 이미 녹음된 소리([samples], 16kHz 모노)를 한 번에 글자와 신뢰도로 바꾼다(시험용). 준비가 안 됐으면 null. */
    fun recognizeDetailed(samples: ShortArray): Heard? {
        val m = model ?: return null
        newRecognizer(m).use { rec ->
            var off = 0
            while (off < samples.size) {
                val n = minOf(CHUNK, samples.size - off)
                rec.acceptWaveForm(samples.copyOfRange(off, off + n), n)
                off += n
            }
            val json = rec.finalResult
            return Heard(textOf(json), confidenceOf(json))
        }
    }

    /** 이미 녹음된 소리([samples], 16kHz 모노)를 한 번에 글자로 바꾼다(시험용). 준비가 안 됐으면 null. */
    fun recognize(samples: ShortArray): String? {
        val m = model ?: return null
        newRecognizer(m).use { rec ->
            var off = 0
            while (off < samples.size) {
                val n = minOf(CHUNK, samples.size - off)
                rec.acceptWaveForm(samples.copyOfRange(off, off + n), n)
                off += n
            }
            return textOf(rec.finalResult)
        }
    }

    private fun newRecognizer(m: Model): Recognizer =
        (grammar?.let { Recognizer(m, SAMPLE_RATE, it) } ?: Recognizer(m, SAMPLE_RATE)).also { it.setWords(true) }

    /** 인식한 낱말들의 신뢰도 평균(0~1). 모르는 말 표시는 빼고, 낱말이 없으면 0. */
    private fun confidenceOf(json: String): Double {
        val words = runCatching { JSONObject(json).optJSONArray("result") }.getOrNull() ?: return 0.0
        val conf = (0 until words.length()).map { words.getJSONObject(it) }.filter { it.optString("word") != SpeechVocabulary.UNKNOWN }.map { it.optDouble("conf", 0.0) }
        return if (conf.isEmpty()) 0.0 else conf.average()
    }

    private fun textOf(json: String): String = SpeechVocabulary.clean(runCatching { JSONObject(json).optString("text") }.getOrDefault(""))

    override fun close() {
        ready = false
        listening = false
        main.removeCallbacks(timeout)
        MicTap.sink = null
        runCatching { recognizer?.close() }
        recognizer = null
        runCatching { model?.close() }
        model = null
    }

    companion object {
        /**
         * 이보다 신뢰도가 낮으면 버린다. 인식 범위를 낱말로 좁히면 관계없는 말도 가장 가까운 낱말로 들려서 음악이 엉뚱하게 바뀌거나 꺼진다.
         * PC 합성음 14개로 잡은 값(명령 7/8이 1.0, 보통 말은 0.76~0.96)이라 사람 목소리로 다시 맞춰야 한다. 너무 자주 못 알아들으면 낮춘다.
         */
        const val MIN_CONFIDENCE = 0.97

        /** 이 결과를 명령으로 받아들일 만한가. */
        fun accepts(h: Heard): Boolean = h.text.isNotEmpty() && h.confidence >= MIN_CONFIDENCE

        private const val TAG = "VoskSpeech"
        private const val ASSET_DIR = "vosk-ko"
        private const val DONE_MARK = ".unpacked"
        private const val SAMPLE_RATE = 16_000f
        private const val CHUNK = 4_000
        private const val LISTEN_MS = 8_000L                 // 이만큼 지나도 끝나지 않으면 그만 듣는다
    }
}
