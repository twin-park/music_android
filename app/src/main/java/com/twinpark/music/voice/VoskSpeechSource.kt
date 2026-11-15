package com.twinpark.music.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.twinpark.music.BuildConfig
import com.twinpark.music.domain.SoundGate
import com.twinpark.music.sensing.MicTap
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * 앱 안에서 도는 오프라인 음성 인식(Vosk, 한국어 소형 모델)으로 말을 글자로 받는다. 서비스나 통신이 필요 없다.
 * 모델은 앱 에셋 `vosk-ko/`에서 처음 한 번 앱 저장 공간으로 풀어 쓴다(빌드에 `VOSK_MODEL_DIR`을 준 경우만 들어 있다). 없으면 준비되지 않은 채로 남는다.
 *
 * **계속 듣는다**(호출어 없음): 준비가 끝나면 [MicSoundSensor]가 열어 둔 마이크 소리를 [MicTap]으로 계속 받는다. 마이크는 앱 화면이 보이는 동안만 열려 있으므로 그동안만 듣는다.
 * 한 문장이 끝날 때마다 명령 모양인지([SpeechVocabulary.isCoherent])와 신뢰도([CommandFilter])로 가려서, 믿을 만한 명령만 [onText]로 넘긴다. 나머지(보통 대화)는 아무 일도 하지 않고 글자도 남기지 않는다.
 * 앱이 직접 말하는 동안(그리고 그 뒤 잠깐)은 듣지 않는다([SoundGate]). 인식은 마이크 스레드를 막지 않게 따로 돈다.
 * 마이크 소리를 받으려면 `MIC_SENSING` 빌드여야 한다.
 */
class VoskSpeechSource(
    private val context: Context,
    cooldownMs: Long = CommandFilter.DEFAULT_COOLDOWN_MS,
    private val onText: (String) -> Unit,
) : SpeechSource {
    private val main = Handler(Looper.getMainLooper())
    private val filter = CommandFilter(cooldownMs)
    private val queue = LinkedBlockingQueue<ShortArray>(QUEUE)
    @Volatile private var model: Model? = null
    @Volatile private var ready = false
    @Volatile private var grammar: String? = null
    @Volatile private var phrases: List<String> = emptyList()
    @Volatile private var worker: Thread? = null

    override val isReady: Boolean get() = ready

    override val continuous: Boolean get() = true

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
            attach()
        }
    }

    /** 마이크 소리를 받기 시작한다. 이후 화면이 보이는 동안 계속 듣는다. */
    @Synchronized
    private fun attach() {
        if (!BuildConfig.MIC_SENSING) {
            Log.w(TAG, "마이크 소리를 받으려면 MIC_SENSING 빌드여야 함")
            return
        }
        if (worker != null) return
        val m = model ?: return
        queue.clear()
        worker = thread(name = "vosk-listen") { listenLoop(m) }
        MicTap.sink = { samples, count ->
            val chunk = samples.copyOf(count)                       // 마이크 스레드를 막지 않는다. 밀리면 오래된 것부터 버린다
            while (!queue.offer(chunk)) queue.poll()
        }
        Log.i(TAG, "계속 듣는 중(화면이 보이는 동안)")
    }

    private fun listenLoop(m: Model) {
        val rec = newRecognizer(m)
        var muted = false
        var lastChunkAt = SystemClock.elapsedRealtime()
        try {
            while (!Thread.currentThread().isInterrupted) {
                val chunk = queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
                if (SoundGate.isMuted(System.currentTimeMillis())) {   // 앱이 말하는 중: 이 목소리를 명령으로 듣지 않는다
                    if (!muted) { rec.reset(); muted = true }
                    continue
                }
                muted = false
                val now = SystemClock.elapsedRealtime()
                if (now - lastChunkAt > GAP_RESET_MS) rec.reset()       // 마이크가 한동안 멈췄다가 이어지면 이전 소리와 섞지 않는다
                lastChunkAt = now
                if (rec.acceptWaveForm(chunk, chunk.size)) onUtterance(rec.result, now)
            }
        } catch (_: InterruptedException) {
        } finally {
            runCatching { rec.close() }
        }
    }

    /** 한 문장이 끝났다. 명령이면 넘기고, 아니면 글자를 남기지 않고 버린다(보통 대화일 수 있다). */
    private fun onUtterance(json: String, nowMs: Long) {
        val heard = Heard(textOf(json), confidenceOf(json))
        if (heard.text.isEmpty()) return
        if (phrases.isNotEmpty() && !SpeechVocabulary.isCoherent(heard.text, phrases)) {
            Log.d(TAG, "버림(명령 모양이 아님)")                       // 낱말을 이어 붙인 꼴은 보통 말을 잘못 들은 것이다
            return
        }
        if (!filter.accept(heard, nowMs)) {
            Log.d(TAG, "버림(신뢰도 ${"%.3f".format(heard.confidence)})")
            return
        }
        val text = SpeechPolicy.accept(heard.text) ?: return
        Log.i(TAG, "명령 받아들임: \"$text\", 신뢰도 ${"%.3f".format(heard.confidence)}")
        main.post { onText(text) }
    }

    /** 계속 듣고 있으면 true. 별도로 시작할 것이 없다(마이크가 닫혀 있을 때는 소리가 오지 않을 뿐이다). */
    override fun listen(): Boolean = ready && worker != null && MicTap.sink != null

    /** 엔진이 알아듣는 낱말로 좁힌 인식 문법. 낱말 파일을 못 읽으면 null(제한 없이 인식). */
    private fun loadGrammar(): String? {
        val json = runCatching { context.assets.open("bgm/conductor/signals.json").use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            ?: return null
        val words = SpeechVocabulary.words(json)
        Log.i(TAG, "알아들을 낱말 ${words.size}개로 인식 범위를 좁힘")
        phrases = SpeechVocabulary.phrases(words)
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
    fun recognize(samples: ShortArray): String? = recognizeDetailed(samples)?.text

    private fun newRecognizer(m: Model): Recognizer =
        (grammar?.let { Recognizer(m, SAMPLE_RATE, it) } ?: Recognizer(m, SAMPLE_RATE)).also { it.setWords(true) }

    /** 인식한 낱말들의 신뢰도 평균(0~1). 모르는 말 표시는 빼고, 낱말이 없으면 0. */
    private fun confidenceOf(json: String): Double {
        val words = runCatching { JSONObject(json).optJSONArray("result") }.getOrNull() ?: return 0.0
        val conf = (0 until words.length()).map { words.getJSONObject(it) }.filter { it.optString("word") != SpeechVocabulary.UNKNOWN }.map { it.optDouble("conf", 0.0) }
        return if (conf.isEmpty()) 0.0 else conf.average()
    }

    private fun textOf(json: String): String = SpeechVocabulary.clean(runCatching { JSONObject(json).optString("text") }.getOrDefault(""))

    @Synchronized
    override fun close() {
        ready = false
        MicTap.sink = null
        val w = worker
        worker = null
        w?.interrupt()
        runCatching { w?.join(1_000) }                              // 인식기를 쓰는 스레드가 끝난 뒤에 모델을 닫는다
        queue.clear()
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
        private const val QUEUE = 200                    // 0.1초 조각 200개(20초). 이보다 밀리면 오래된 것부터 버린다
        private const val GAP_RESET_MS = 1_500L          // 소리가 이만큼 끊기면 이어 붙이지 않는다
    }
}
