package com.twinpark.music.domain

import kotlin.math.abs

/**
 * 차량 값을 지휘자의 칸으로 바꾼다.
 *   - `drive.speed` 속도(km/h): 신호 대기 · 정체 · 고속은 지휘자가 속도로 정한다.
 *   - `drive.limit` 도로 제한속도(km/h): 있으면 지휘자가 과속을 판단한다(제한의 1.1배 + 3km/h 초과). 모르면 지운다.
 *   - `car.drive_min` 운전한 지 몇 분: 오래 운전 · 졸음 · 피곤 추정에 쓰인다(120분 넘으면 장시간). 달리는 동안만 세고, [REST_MS] 넘게 서 있으면 쉬었다고 보고 0부터 다시 센다.
 * 2초마다 부르므로 조금씩 바뀌는 값은 보내지 않는다. 속도는 처음 · 멈춤 ↔ 달림이 바뀔 때 · 마지막에 보낸 값과 [STEP_KMH] 이상 달라졌을 때만,
 * 제한속도는 바뀔 때만, 운전 시간은 분이 바뀔 때만 보낸다. 속도를 모르면(차량 값이 없음) 아무것도 보내지 않는다.
 * 사람이 DEV 에서 달리기 칸(`drive.state` · `drive.speed`)이나 운전 시간 칸을 고른 경우는 건드리지 않는다.
 */
class DriveSituation {
    private var sentSpeed: Int? = null
    private var sentLimit: Int? = null
    private var limitSent = false
    private var sentMinutes: Int? = null

    private var lastMs: Long? = null
    private var driveMs = 0L
    private var stoppedMs = 0L
    private var everMoved = false

    /** [nowMs]는 단조 시계(밀리초). */
    fun update(state: DrivingState, nowMs: Long = 0, userKeys: Set<String> = emptySet()): String? {
        val kmh = state.speedKmh ?: return null
        tick(state.moving, nowMs)

        val out = ArrayList<Pair<String, Any?>>()
        if (userKeys.none { it == "drive.state" || it == "drive.speed" }) {
            val speed = kmh.coerceAtLeast(0f).toInt()
            val last = sentSpeed
            val flipped = last != null && ((last < MOVING) != (speed < MOVING))
            if (last == null || flipped || abs(speed - last) >= STEP_KMH) {
                sentSpeed = speed
                out += "drive.speed" to speed
            }
            if (!limitSent || sentLimit != state.limitKmh) {
                limitSent = true
                sentLimit = state.limitKmh
                out += "drive.limit" to state.limitKmh
            }
        }
        if (everMoved && "car.drive_min" !in userKeys) {
            val minutes = (driveMs / MINUTE_MS).toInt()
            if (minutes != sentMinutes) {
                sentMinutes = minutes
                out += "car.drive_min" to minutes
            }
        }
        return if (out.isEmpty()) null else SituationPresets.patch(out)
    }

    /** 달리는 시간과 서 있는 시간을 센다. 한 번에 너무 긴 간격(앱이 멈췄던 때)은 [MAX_STEP_MS]로 자른다. */
    private fun tick(moving: Boolean, nowMs: Long) {
        val prev = lastMs
        lastMs = nowMs
        val dt = if (prev == null) 0L else (nowMs - prev).coerceIn(0L, MAX_STEP_MS)
        if (moving) {
            everMoved = true
            stoppedMs = 0
            driveMs += dt
        } else {
            stoppedMs += dt
            if (stoppedMs >= REST_MS) driveMs = 0       // 오래 서 있었으면 쉰 것으로 본다
        }
    }

    /** 보냈지만 엔진이 받지 못했거나 상황을 지웠을 때: 다음에 다시 보낸다(센 시간은 그대로). */
    fun reset() {
        sentSpeed = null
        sentLimit = null
        limitSent = false
        sentMinutes = null
    }

    companion object {
        const val STEP_KMH = 5
        const val MINUTE_MS = 60_000L
        const val REST_MS = 10 * MINUTE_MS
        const val MAX_STEP_MS = 5_000L
        private const val MOVING = DrivingState.MOVING_KMH.toInt()
    }
}
