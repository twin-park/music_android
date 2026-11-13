package com.twinpark.music.domain

/** 이어서 들어오는 소리에서 [size]샘플 창을 [hop]샘플 간격으로 잘라 낸다. */
class SampleWindow(private val size: Int, private val hop: Int) {
    private var pending = FloatArray(0)

    fun push(samples: ShortArray, count: Int): List<FloatArray> {
        pending += FloatArray(count) { samples[it] / PCM16_SCALE }
        val windows = mutableListOf<FloatArray>()
        while (pending.size >= size) {
            windows += pending.copyOfRange(0, size)
            pending = pending.copyOfRange(hop, pending.size)
        }
        return windows
    }

    private companion object {
        const val PCM16_SCALE = 32768f
    }
}

/** 마이크 소리를 창으로 잘라 [classifier]에 넘기고 가장 최근에 들린 단어를 알려 준다. 오래된 결과는 버린다. */
class SoundListener(
    private val classifier: SoundClassifier,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : SoundSensor {
    private val window = SampleWindow(SAMPLE_RATE * WINDOW_SECONDS, SAMPLE_RATE * HOP_SECONDS)

    @Volatile private var latest: Set<String> = emptySet()
    @Volatile private var heardAtMs = Long.MIN_VALUE

    fun process(samples: ShortArray, count: Int) {
        for (chunk in window.push(samples, count)) {
            latest = classifier.classify(chunk, SAMPLE_RATE)
            heardAtMs = nowMs()
        }
    }

    override fun heard(): Set<String> = if (nowMs() - heardAtMs <= FRESH_MS) latest else emptySet()

    companion object {
        const val SAMPLE_RATE = 16_000
        const val WINDOW_SECONDS = 4
        const val HOP_SECONDS = 2
        const val FRESH_MS = 6_000L
    }
}
