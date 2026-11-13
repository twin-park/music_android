package com.twinpark.music.sensing

import com.twinpark.music.domain.SoundSensor

/** 와이퍼가 켜져 있으면 비 소리가 들린 것으로 친다. 그러면 상황 규칙의 "비 오는 길"이 된다. */
class WiperRainSensor(private val wipersOn: () -> Boolean) : SoundSensor {
    override fun heard(): Set<String> = if (wipersOn()) setOf(RAIN) else emptySet()

    private companion object {
        const val RAIN = "비"
    }
}
