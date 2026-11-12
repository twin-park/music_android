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

/** 바깥 날씨 입력. 지금은 시간대와 비 여부만 본다. */
data class WeatherState(val time: TimeOfDay, val raining: Boolean = false) {
    /**
     * 이 날씨에 맞는 장면(오브 + 하늘). 비가 오면 시간대와 상관없이 비 장면(파랑),
     * 아니면 새벽 초록, 낮 청량, 황혼 주황, 밤 보라.
     */
    fun scene(): Mood = when {
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

    /** 자동 값: 기기 시계의 시간대, 비는 아직 데이터가 없어 없음. */
    fun auto(cal: Calendar = Calendar.getInstance()): WeatherState = WeatherState(TimeOfDay.now(cal), raining = false)

    fun resolve(manual: WeatherState?, cal: Calendar = Calendar.getInstance()): WeatherState = manual ?: auto(cal)
}
