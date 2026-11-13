package com.twinpark.music.car

import android.content.Context
import com.twinpark.music.domain.DrivingSource
import com.twinpark.music.domain.DrivingState
import com.twinpark.music.domain.NoDrivingSource

/** 이 앱이 쓰는 차량 주행 값. 음악 서비스가 연주하는 동안 연결을 유지한다. */
object AppDriving {
    @Volatile private var source: DrivingSource = NoDrivingSource

    val state: DrivingState get() = source.state.value

    @Synchronized
    fun start(context: Context) {
        if (source === NoDrivingSource) source = CarPropertySource.create(context)
    }

    @Synchronized
    fun stop() {
        source.close()
        source = NoDrivingSource
    }
}
