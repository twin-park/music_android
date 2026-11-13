package com.twinpark.music.sensing

import com.twinpark.music.domain.DrivingState
import com.twinpark.music.domain.SoundSensor

/** 차가 달리는 중이면 도로 소리가 들린 것으로 친다. 그러면 상황 규칙의 "드라이브"(낮 또는 밤)가 된다. 소리로 들은 상황이 있으면 그쪽이 먼저다. */
class DrivingSoundSensor(private val driving: () -> DrivingState) : SoundSensor {
    override fun heard(): Set<String> = if (driving().moving) setOf(ROAD) else emptySet()

    private companion object {
        const val ROAD = "도로"
    }
}
