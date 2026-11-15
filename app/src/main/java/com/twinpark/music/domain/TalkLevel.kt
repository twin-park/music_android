package com.twinpark.music.domain

/**
 * 귀 모델이 마지막으로 낸 "말소리" 확률. 단어(대화)와 따로, 말소리가 얼마나 또렷한지(0~1)를 지휘자의 `cabin.talk` 칸으로 넘기는 데 쓴다.
 * 문턱을 넘는 말소리만 값이 있고, 넘지 못하면 값이 없다(그러면 엔진이 차 안 소리 상태로 추정한다).
 * 오래된 값은 버린다.
 */
object TalkLevel {
    const val FRESH_MS = 6_000L

    @Volatile private var level = 0f
    @Volatile private var atMs = Long.MIN_VALUE

    /** [speech]는 모델의 말소리 확률, [threshold]는 그 소리의 문턱. [nowMs]는 벽시계(밀리초). */
    fun report(speech: Float, threshold: Float, nowMs: Long) {
        level = if (speech >= threshold) speech.coerceIn(0f, 1f) else 0f
        atMs = nowMs
    }

    /** 신선하고 문턱을 넘은 값이 있으면 0.05 단위로 돌려준다. 없으면 null. */
    fun current(nowMs: Long): Float? {
        if (nowMs - atMs > FRESH_MS) return null
        val v = level
        return if (v > 0f) Math.round(v * 20) / 20f else null
    }

    fun clear() {
        level = 0f
        atMs = Long.MIN_VALUE
    }
}
