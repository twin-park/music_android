package com.twinpark.music.domain

/** [SituationTracker.update]의 결과. */
data class TrackerState(
    /** 지금 적용 중인 상황. 아직 정해진 적이 없으면 null. */
    val current: Directive?,
    /** 이번 호출에서 상황이 바뀌었는가(바뀐 순간에만 true) */
    val switched: Boolean,
    /** 바뀔 후보 상황과 그것이 이어진 시간(ms). 후보가 없으면 null. 화면의 디버그 표시용. */
    val candidate: Directive?,
    val candidateMs: Long,
    /** 통화 중이라 음악을 크게 낮춰야 하는가(즉시층) */
    val ducking: Boolean,
)

/**
 * 귀가 주기적으로 알려 주는 단어를 받아 상황을 안정적으로 바꾼다. 시계는 밖에서 넣어 주므로(ms) 시험하기 쉽다.
 *
 * - 소리 한 번으로 바로 바꾸지 않고, 같은 새 상황이 [confirmMs] 동안 이어질 때만 바꾼다(기획안 4-2: 음악이 들쭉날쭉하지 않게).
 * - 한 번 바꾸면 [minHoldMs] 동안은 다시 바꾸지 않는다.
 * - 단어가 하나도 안 맞으면(근거 없음) 지금 상황을 그대로 둔다. 조용해졌다고 분위기를 되돌리지 않는다.
 * - 통화(전화벨)는 즉시층이라 기다리지 않고 바로 낮추고, 소리가 사라진 뒤 [phoneReleaseMs] 뒤에 되돌린다.
 */
class SituationTracker(
    private val confirmMs: Long = 4_000,
    private val minHoldMs: Long = 15_000,
    private val phoneReleaseMs: Long = 3_000,
) {
    private var current: Directive? = null
    private var candidate: Directive? = null
    private var candidateSince = 0L
    private var lastSwitch = Long.MIN_VALUE / 2
    private var phoneUntil = Long.MIN_VALUE / 2

    /** [words]는 이번에 들린 단어들. 귀가 2초마다 알려 주면 2초마다 부른다. */
    fun update(nowMs: Long, words: Collection<String>, night: Boolean = false): TrackerState {
        if (SituationRules.isPhone(words)) phoneUntil = nowMs + phoneReleaseMs
        val ducking = nowMs < phoneUntil

        val seen = SituationRules.interpret(words, night)
        var switched = false
        if (seen == null || seen == current) {
            candidate = null                                  // 근거가 없거나 지금 상황이 계속되면 후보를 버린다
        } else {
            if (candidate != seen) { candidate = seen; candidateSince = nowMs }
            val held = nowMs - candidateSince
            // 한 번도 안 바꿨으면 lastSwitch 가 아주 먼 과거라 최소 유지 시간은 자연히 통과한다
            if (held >= confirmMs && nowMs - lastSwitch >= minHoldMs) {
                current = seen; candidate = null; lastSwitch = nowMs; switched = true
            }
        }
        return TrackerState(current, switched, candidate, if (candidate != null) nowMs - candidateSince else 0, ducking)
    }

    /** 사용자가 직접 분위기를 골랐을 때: 후보를 지우고, 한동안 자동 전환이 바로 되돌리지 않게 마지막 전환 시각을 지금으로 둔다. */
    fun manualOverride(nowMs: Long) {
        candidate = null
        current = null
        lastSwitch = nowMs
    }
}
