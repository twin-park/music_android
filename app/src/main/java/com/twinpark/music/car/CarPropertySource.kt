package com.twinpark.music.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.twinpark.music.domain.DrivingSource
import com.twinpark.music.domain.DrivingState
import com.twinpark.music.domain.NoDrivingSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 안드로이드 차량 속성(`CarPropertyManager`)에서 현재 속도를 받는다. Pleos SDK(Vehicle, NaviHelper, FusedLocation)에는 현재 속도가 없다. 기어는 [PleosVehicleSource]가 받는다. */
class CarPropertySource private constructor(private val context: Context) : DrivingSource {
    private val _state = MutableStateFlow(DrivingState())
    override val state: StateFlow<DrivingState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var manager: CarPropertyManager? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) = apply(value)
        override fun onErrorEvent(propertyId: Int, zone: Int) {
            Log.w(TAG, "속성 오류 id=$propertyId zone=$zone")
        }
    }

    private fun connect() {
        car = Car.createCar(context, handler, CONNECT_TIMEOUT_MS) { connected, ready ->
            if (ready) listen(connected)
        }
    }

    private fun listen(connected: Car) {
        if (context.checkSelfPermission(SPEED_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "속도 권한이 없어 나중에 다시 시도한다")
            handler.postDelayed({ if (car != null) listen(connected) }, RETRY_MS)
            return
        }
        val properties = connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return
        manager = properties
        val supported = properties.propertyList.map { it.propertyId }.toSet()
        Log.i(TAG, "지원 속성 ${supported.size}개, 속도=${VehiclePropertyIds.PERF_VEHICLE_SPEED in supported}")
        for (id in listOf(VehiclePropertyIds.PERF_VEHICLE_SPEED)) {
            if (id !in supported) continue
            runCatching {
                properties.registerCallback(callback, id, CarPropertyManager.SENSOR_RATE_NORMAL)
                properties.getProperty<Any>(id, 0)?.let(::apply)
            }.onFailure { Log.w(TAG, "속성 $id 등록 실패: ${it.javaClass.simpleName} ${it.message}") }
        }
    }

    private fun apply(value: CarPropertyValue<*>) {
        val before = _state.value.moving
        when (value.propertyId) {
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                val ms = value.value as? Float ?: return
                _state.value = _state.value.copy(speedKmh = ms * MS_TO_KMH)
            }
        }
        if (_state.value.moving != before) Log.i(TAG, "주행 상태 ${if (_state.value.moving) "달림" else "멈춤"}: ${_state.value}")
    }

    override fun close() {
        handler.removeCallbacksAndMessages(null)
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }

    companion object {
        private const val TAG = "CarProperty"
        private const val CONNECT_TIMEOUT_MS = 5_000L
        private const val RETRY_MS = 10_000L
        const val SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
        private const val MS_TO_KMH = 3.6f

        /** 차량이 아니거나 차량 API를 쓸 수 없으면 [NoDrivingSource]를 돌려준다. */
        fun create(context: Context): DrivingSource {
            val app = context.applicationContext
            if (!app.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return NoDrivingSource
            return try {
                CarPropertySource(app).also { it.connect() }
            } catch (e: Throwable) {
                Log.w(TAG, "차량 속성을 쓸 수 없음: ${e.javaClass.simpleName}")
                NoDrivingSource
            }
        }
    }
}
