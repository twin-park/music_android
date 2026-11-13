package com.twinpark.music.car

import android.car.VehicleGear
import com.twinpark.music.domain.DrivingGear

/** 안드로이드 차량 기어 값을 앱의 기어로 바꾼다. 1단 이상은 모두 전진이다. 알 수 없으면 null. */
fun gearOf(value: Int): DrivingGear? = when {
    value == VehicleGear.GEAR_PARK -> DrivingGear.Park
    value == VehicleGear.GEAR_REVERSE -> DrivingGear.Reverse
    value == VehicleGear.GEAR_NEUTRAL -> DrivingGear.Neutral
    value == VehicleGear.GEAR_DRIVE || value >= VehicleGear.GEAR_FIRST -> DrivingGear.Drive
    else -> null
}
