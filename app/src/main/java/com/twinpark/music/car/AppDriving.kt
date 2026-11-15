package com.twinpark.music.car

import android.content.Context
import android.content.pm.PackageManager
import com.twinpark.music.domain.DrivingGear
import com.twinpark.music.domain.DrivingSource
import com.twinpark.music.domain.DrivingState
import com.twinpark.music.domain.NoDrivingSource
import com.twinpark.music.domain.WeatherSource

/** 이 앱이 쓰는 차량 주행 값. 음악 서비스가 연주하는 동안 연결을 유지한다. 속도는 시스템 차량 속성, 기어와 와이퍼는 Pleos Vehicle SDK다. */
object AppDriving {
    @Volatile private var speed: DrivingSource = NoDrivingSource
    @Volatile private var gear: DrivingGear? = null
    @Volatile private var limit: Int? = null
    private var vehicle: PleosVehicleSource? = null

    val state: DrivingState get() = speed.state.value.copy(gear = gear, limitKmh = limit)

    /** 내비가 알려 주는 지금 도로의 제한속도(km/h). 0 이하면 모름. */
    fun setSpeedLimit(kmh: Int) { limit = kmh.takeIf { it > 0 } }

    @Synchronized
    fun start(context: Context) {
        if (speed === NoDrivingSource) speed = CarPropertySource.create(context)
        if (vehicle == null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            vehicle = PleosVehicleSource(context.applicationContext, { gear = it }, WeatherSource::setWipersOn).also { it.start() }
        }
    }

    @Synchronized
    fun stop() {
        speed.close()
        speed = NoDrivingSource
        vehicle?.close()
        vehicle = null
        gear = null
        limit = null
        WeatherSource.setWipersOn(false)
    }
}
