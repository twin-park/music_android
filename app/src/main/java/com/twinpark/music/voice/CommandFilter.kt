package com.twinpark.music.voice

/**
 * 계속 듣는 동안 인식된 말 중 음악 명령으로 쓸 것을 가린다. 신뢰도가 기준([VoskSpeechSource.MIN_CONFIDENCE]) 미만이면 버리고,
 * 방금 명령을 받은 뒤 [cooldownMs] 안에 또 들어온 것도 버린다(한 문장이 둘로 나뉘어 들리거나 같은 말이 되풀이되는 것을 막는다).
 */
class CommandFilter(private val cooldownMs: Long = DEFAULT_COOLDOWN_MS) {
    private var lastAcceptedAt = Long.MIN_VALUE / 2

    /** [nowMs]는 단조 시계(밀리초). 받아들이면 true. */
    @Synchronized
    fun accept(heard: VoskSpeechSource.Heard, nowMs: Long): Boolean {
        if (!VoskSpeechSource.accepts(heard)) return false
        if (nowMs - lastAcceptedAt < cooldownMs) return false
        lastAcceptedAt = nowMs
        return true
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 3_000L
    }
}
