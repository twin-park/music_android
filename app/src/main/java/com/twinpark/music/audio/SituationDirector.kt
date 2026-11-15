package com.twinpark.music.audio

import android.util.Log
import com.twinpark.music.domain.Directive
import com.twinpark.music.domain.SituationRules
import com.twinpark.music.domain.SituationTracker
import com.twinpark.music.domain.TrackerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 귀(ear)가 알려 주는 단어를 받아 상황을 판단하고, 엔진 분위기와 조절값, 통화 시 볼륨 낮춤으로 바꾸는 곳.
 * 판단 자체는 [SituationTracker]/[SituationRules]가 하고, 여기서는 그 결과를 [BgmController]에 적용한다.
 *
 * 단어는 [com.twinpark.music.domain.SoundSensor]들이 낸다. [observe]를 2초마다 부르는 쪽이 그 단어를 모아 넘긴다.
 */
object SituationDirector {
    private const val TAG = "Situation"
    private val tracker = SituationTracker()
    private val _state = MutableStateFlow<TrackerState?>(null)

    /** 마지막 판단 결과(화면 디버그 표시용) */
    val state: StateFlow<TrackerState?> = _state.asStateFlow()

    private var manualUntilMs = 0L

    /** 귀 단어 → 지휘자 상황 칸 (지휘자가 있을 때). 사람이 DEV 에서 고른 칸은 [EarSituation.userKeys] 로 비켜 간다. */
    val ear = com.twinpark.music.domain.EarSituation()

    /** 날씨 API · 와이퍼 → 지휘자 날씨 칸. 귀가 들은 비 · 천둥과 같은 칸이다. */
    private val weather = com.twinpark.music.domain.WeatherSituation()

    /** 차량 속도 → 지휘자 달리기 칸. 신호 대기 · 정체 · 고속은 지휘자가 속도로 정한다. */
    private val drive = com.twinpark.music.domain.DriveSituation()

    /** 사람이 상황을 모두 지웠다: 귀와 날씨가 보낸 값도 잊어서 다음 번에 다시 보낸다. */
    @Synchronized
    fun clearSituation() {
        ear.clear()
        weather.reset()
        drive.reset()
    }

    /** 직접 분위기를 고른 뒤 이 시간 동안은 자동 전환을 보류한다. */
    private const val MANUAL_HOLD_MS = 60_000L

    /** 이번에 들린 단어들을 알려 준다. [nowMs]는 단조 시계(밀리초). */
    @Synchronized
    fun observe(words: Collection<String>, nowMs: Long = android.os.SystemClock.elapsedRealtime()) {
        val all = words.toSet()
        val st = tracker.update(nowMs, all, SituationRules.isNight())
        val before = _state.value
        _state.value = st
        if (st.candidate?.situation != before?.candidate?.situation && st.candidate != null) Log.i(TAG, "후보: ${st.candidate.situation} (단어 $all)")
        if (st.ducking != before?.ducking) Log.i(TAG, if (st.ducking) "통화: 음악 낮춤" else "통화 끝: 음악 되돌림")
        BgmController.duck(st.ducking)                                     // 즉시층: 통화는 기다리지 않는다
        if (BgmController.conductorOn) {                                   // 지휘자가 있으면: 들린 단어는 상황 칸으로 넘기고, 분위기 · 악기 · 빠르기는 지휘자가 정한다
            ear.update(all, nowMs)?.let { patch ->
                val err = BgmController.setSituation(patch)
                Log.i(TAG, "귀 → 지휘자 $patch" + if (err.isNotEmpty()) " ($err)" else "")
            }
            weather.update(
                com.twinpark.music.domain.WeatherSource.report.value,
                com.twinpark.music.domain.WeatherSource.wipersOn.value,
                System.currentTimeMillis(),
                ear.userKeys,
            )?.let { patch ->
                val err = BgmController.setSituation(patch)
                Log.i(TAG, "날씨 → 지휘자 $patch" + if (err.isNotEmpty()) " ($err)" else "")
                if (err.isNotEmpty()) weather.reset()                      // 엔진이 아직 준비 안 됐으면 다음 번에 다시 보낸다
            }
            drive.update(com.twinpark.music.car.AppDriving.state, ear.userKeys)?.let { patch ->
                val err = BgmController.setSituation(patch)
                Log.i(TAG, "차량 → 지휘자 $patch" + if (err.isNotEmpty()) " ($err)" else "")
                if (err.isNotEmpty()) drive.reset()
            }
            return
        }
        if (nowMs < manualUntilMs) return                                  // 사용자가 고른 직후에는 자동 전환을 하지 않는다
        if (st.switched) {
            val cur = st.current ?: return
            Log.i(TAG, "상황 전환: ${cur.situation} -> 엔진 ${cur.engineMoodId}, 조절값 ${cur.params}")
            apply(cur)
        }
    }

    /** 사용자가 분위기를 직접 골랐다. 자동 전환이 바로 되돌리지 않게 한다. */
    @Synchronized
    fun manualOverride(nowMs: Long = android.os.SystemClock.elapsedRealtime()) {
        manualUntilMs = nowMs + MANUAL_HOLD_MS
        tracker.manualOverride(nowMs)
        BgmController.setParams(com.twinpark.music.domain.EngineParams())  // 직접 고른 분위기는 기본값 그대로
    }

    private fun apply(d: Directive) {
        BgmController.setParams(d.params)
        BgmController.requestMood(d.engineMoodId)
    }
}
