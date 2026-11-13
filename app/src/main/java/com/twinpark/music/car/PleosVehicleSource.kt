package com.twinpark.music.car

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.util.Log
import com.twinpark.music.domain.DrivingGear

/**
 * Pleos Vehicle SDK로 기어와 앞 유리 와이퍼 상태를 받는다. 차량 서비스가 없거나 값을 못 받으면 콜백을 부르지 않는다.
 * (현재 속도는 이 SDK에 없어서 [CarPropertySource]가 시스템 차량 속성으로 받는다.)
 */
class PleosVehicleSource(
    private val context: Context,
    private val onGear: (DrivingGear?) -> Unit,
    private val onWipers: (Boolean) -> Unit,
) : AutoCloseable {
    private var vehicle: Vehicle? = null
    private val wiperArea = WindowArea(WindowValue.FRONT_WINDSHIELD)

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = publishGear(gear)
        override fun onFailed(e: Exception) { Log.w(TAG, "기어 오류: ${e.message}") }
    }

    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) = publishWiper(state)
        override fun onFailed(e: Exception) { Log.w(TAG, "와이퍼 오류: ${e.message}") }
    }

    fun start() {
        val client = runCatching { Vehicle(context).also { it.initialize() } }
            .onFailure { Log.i(TAG, "Vehicle SDK를 쓸 수 없음: ${it.javaClass.simpleName}") }
            .getOrNull() ?: return
        vehicle = client
        val error = { name: String -> { e: Exception -> Log.w(TAG, "$name 읽기 오류: ${e.message}"); Unit } }
        runCatching {
            client.getGear().getCurrentGearSelectionState(::publishGear, error("기어"))
            client.getGear().registerGearSelection(gearListener)
        }.onFailure { Log.w(TAG, "기어 등록 실패: ${it.javaClass.simpleName} ${it.message}") }
        runCatching {
            client.getWiper().getCurrentWiperState(wiperArea, ::publishWiper, error("와이퍼"))
            client.getWiper().registerWiperState(wiperArea, wiperListener)
        }.onFailure { Log.w(TAG, "와이퍼 등록 실패: ${it.javaClass.simpleName} ${it.message}") }
    }

    private fun publishGear(gear: VehicleGear?) {
        val mapped = gearOf(gear)
        Log.i(TAG, "기어 $gear -> $mapped")
        onGear(mapped)
    }

    private fun publishWiper(state: WiperState?) {
        val on = isWiping(state)
        Log.i(TAG, "와이퍼 $state -> ${if (on) "비" else "비 아님"}")
        onWipers(on)
    }

    override fun close() {
        val client = vehicle ?: return
        vehicle = null
        runCatching { client.getGear().unregisterGearSelection(gearListener) }
        runCatching { client.getWiper().unregisterWiperState(wiperArea, wiperListener) }
        runCatching { client.release() }
    }

    private companion object {
        const val TAG = "PleosVehicle"
    }
}

/** 와이퍼가 움직이는 중인가. 정비 모드(SERVICE)와 알 수 없는 값(OTHER)은 비로 보지 않는다. */
internal fun isWiping(state: WiperState?): Boolean = state == WiperState.ON

/** Vehicle SDK 기어를 앱의 기어로 바꾼다. 1단 이상은 모두 전진이다. 알 수 없으면 null. */
internal fun gearOf(gear: VehicleGear?): DrivingGear? = when (gear) {
    VehicleGear.GEAR_PARK -> DrivingGear.Park
    VehicleGear.GEAR_REVERSE -> DrivingGear.Reverse
    VehicleGear.GEAR_NEUTRAL -> DrivingGear.Neutral
    null, VehicleGear.GEAR_UNKNOWN -> null
    else -> DrivingGear.Drive
}
