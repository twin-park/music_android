package com.twinpark.music.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 차량이 알려 주는 주행 값. 알 수 없으면 null이다. */
data class DrivingState(val speedKmh: Float? = null, val gear: DrivingGear? = null) {
    val moving: Boolean get() = (speedKmh ?: 0f) >= MOVING_KMH

    companion object {
        const val MOVING_KMH = 3f
    }
}

enum class DrivingGear { Park, Reverse, Neutral, Drive }

interface DrivingSource : AutoCloseable {
    val state: StateFlow<DrivingState>
}

/** 차량 값을 못 받을 때(폰, 차량 API 없음). */
object NoDrivingSource : DrivingSource {
    override val state: StateFlow<DrivingState> = MutableStateFlow(DrivingState())
    override fun close() = Unit
}
