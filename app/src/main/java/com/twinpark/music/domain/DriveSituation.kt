package com.twinpark.music.domain

import kotlin.math.abs

/**
 * 차량 속도를 지휘자의 달리기 칸(`drive.speed`, km/h)으로 바꾼다. 신호 대기 · 정체 · 고속 같은 상태는 속도만 보내면 지휘자가 정한다.
 * 2초마다 부르므로 조금씩 바뀌는 값은 보내지 않는다: 처음, 멈춤 ↔ 달림이 바뀔 때, 마지막에 보낸 값과 [STEP_KMH] 이상 달라졌을 때만.
 * 속도를 모르면(차량 값이 없음) 보내지 않는다. 사람이 DEV 에서 달리기 칸을 고른 경우도 건드리지 않는다.
 */
class DriveSituation {
    private var sent: Int? = null

    fun update(state: DrivingState, userKeys: Set<String> = emptySet()): String? {
        if (userKeys.any { it == "drive.state" || it == "drive.speed" }) return null
        val kmh = state.speedKmh ?: return null
        val speed = kmh.coerceAtLeast(0f).toInt()
        val last = sent
        val flipped = last != null && ((last < MOVING) != (speed < MOVING))
        if (last != null && !flipped && abs(speed - last) < STEP_KMH) return null
        sent = speed
        return SituationPresets.patch("drive.speed", speed)
    }

    /** 보냈지만 엔진이 받지 못했거나 상황을 지웠을 때: 다음에 다시 보낸다. */
    fun reset() { sent = null }

    companion object {
        const val STEP_KMH = 5
        private const val MOVING = DrivingState.MOVING_KMH.toInt()
    }
}
