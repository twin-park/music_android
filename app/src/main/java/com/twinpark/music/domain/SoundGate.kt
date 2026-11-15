package com.twinpark.music.domain

/**
 * 소리 분류를 잠깐 건너뛰게 하는 문. 앱이 직접 말할 때(음성 답변) 그 목소리를 귀 모델이 차 안 대화로 듣고 상황을 바꾸는 것을 막는다.
 * 닫혀 있는 동안 [SoundListener]는 새 창을 분류하지 않고 마지막 결과를 그대로 유지한다.
 */
object SoundGate {
    @Volatile private var until = Long.MIN_VALUE

    /** [untilMs](벽시계 밀리초)까지 분류를 건너뛴다. 더 이른 시각을 주면 짧아진다. */
    fun muteUntil(untilMs: Long) {
        until = untilMs
    }

    fun isMuted(nowMs: Long): Boolean = nowMs < until

    fun clear() {
        until = Long.MIN_VALUE
    }
}
