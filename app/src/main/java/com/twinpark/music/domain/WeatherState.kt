package com.twinpark.music.domain

import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 하루 중 시간대. 오브와 장면을 고르는 기준이다. */
enum class TimeOfDay(val label: String) {
    DAWN("새벽"), DAY("낮"), DUSK("황혼"), NIGHT("밤");

    companion object {
        /** 시각(0..23)으로 시간대를 정한다: 새벽 5-8시, 낮 8-17시, 황혼 17-20시, 밤 20-5시. */
        fun of(hour: Int): TimeOfDay = when (hour) {
            in 5 until 8 -> DAWN
            in 8 until 17 -> DAY
            in 17 until 20 -> DUSK
            else -> NIGHT
        }

        fun now(cal: Calendar = Calendar.getInstance()): TimeOfDay = of(cal.get(Calendar.HOUR_OF_DAY))
    }
}

/** [detail]은 실제 날씨 값이 있을 때 WEATHER DETAIL 표에 보일 값이다. 없으면 장면별 기본값을 쓴다. */
data class WeatherState(val time: TimeOfDay, val raining: Boolean = false, val snowing: Boolean = false, val detail: Weather? = null) {
    /**
     * 이 날씨에 맞는 장면(오브 + 하늘). 눈이 오면 시간대와 상관없이 눈 오는 밤(구리빛), 비가 오면 비 장면(파랑),
     * 아니면 새벽 초록, 낮 청량, 황혼 주황, 밤 보라. 눈이 비보다 우선한다.
     */
    fun scene(): Mood = when {
        snowing -> Mood.Snow
        raining -> Mood.Calm
        else -> when (time) {
            TimeOfDay.DAWN -> Mood.Fresh
            TimeOfDay.DAY -> Mood.Bright
            TimeOfDay.DUSK -> Mood.Energetic
            TimeOfDay.NIGHT -> Mood.Sleep
        }
    }
}

/**
 * 화면이 쓰는 날씨의 출처. 수동 값([manual])이 있으면 그것을, 없으면 자동(기기 시계 + 비 없음)을 쓴다.
 * 실제 날씨 데이터(차량, API)가 생기면 [auto]를 그쪽 값으로 바꾼다. 수동 값은 개발용 DEV 패널만 정한다.
 */
object WeatherSource {
    private val _manual = MutableStateFlow<WeatherState?>(null)

    /** DEV 패널이 고른 값. null이면 자동. */
    val manual: StateFlow<WeatherState?> = _manual

    fun setManual(state: WeatherState?) {
        _manual.value = state
    }

    private val _report = MutableStateFlow<WeatherReport?>(null)

    val report: StateFlow<WeatherReport?> = _report

    fun setReport(report: WeatherReport?) {
        _report.value = report
    }

    /** 시간대는 기기 시계, 비와 눈은 신선한([WeatherReport.isFresh]) 날씨 값이 있을 때만 반영한다. */
    fun auto(cal: Calendar = Calendar.getInstance(), report: WeatherReport? = null, nowMs: Long = System.currentTimeMillis()): WeatherState {
        val time = TimeOfDay.now(cal)
        return if (report != null && report.isFresh(nowMs)) WeatherState(time, report.raining, report.snowing, report.toDetail())
        else WeatherState(time, raining = false)
    }

    fun resolve(manual: WeatherState?, cal: Calendar = Calendar.getInstance(), report: WeatherReport? = null, nowMs: Long = System.currentTimeMillis()): WeatherState =
        manual ?: auto(cal, report, nowMs)
}
