package com.twinpark.music.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.twinpark.music.domain.NoUxRestrictions
import com.twinpark.music.domain.UxRestrictions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CarUxRestrictionsSource private constructor(private val context: Context) : UxRestrictions {
    private val _required = MutableStateFlow(false)
    override val required: StateFlow<Boolean> = _required.asStateFlow()

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    private fun connect() {
        car = Car.createCar(context, Handler(Looper.getMainLooper()), CONNECT_TIMEOUT_MS) { connected, ready ->
            if (ready) listen(connected)
        }
    }

    private fun listen(connected: Car) {
        val restrictions = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
        manager = restrictions
        _required.value = restrictions.currentCarUxRestrictions.isRequiresDistractionOptimization
        restrictions.registerListener { _required.value = it.isRequiresDistractionOptimization }
        Log.i(TAG, "주행 규제 수신 시작: 주의 분산 최적화 필요 = ${_required.value}")
    }

    override fun close() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }

    companion object {
        private const val TAG = "UxRestrictions"
        private const val CONNECT_TIMEOUT_MS = 5_000L

        /** 차량이 아니거나 차량 API를 쓸 수 없으면 [NoUxRestrictions]를 돌려준다. */
        fun create(context: Context): UxRestrictions {
            val app = context.applicationContext
            if (!app.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return NoUxRestrictions
            return try {
                CarUxRestrictionsSource(app).also { it.connect() }
            } catch (e: Throwable) {
                Log.w(TAG, "차량 API를 쓸 수 없음: ${e.javaClass.simpleName}")
                NoUxRestrictions
            }
        }
    }
}
