package com.twinpark.music.weather

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import com.twinpark.music.domain.GeoPoint
import com.twinpark.music.domain.LocationSource

/**
 * Pleos FusedLocation SDK로 받은 차량 위치. 위치 권한이 없거나 SDK를 쓸 수 없거나 아직 못 받았으면 [fallback]을 돌려준다.
 * 권한은 나중에 허용될 수 있어서 [current]를 부를 때마다 시작을 다시 시도한다.
 */
class PleosLocationSource(
    private val context: Context,
    private val fallback: GeoPoint,
) : LocationSource {
    @Volatile private var latest: GeoPoint? = null
    @Volatile private var failed = false
    private var fused: FusedLocation? = null

    private val listener = object : FusedLocationListener {
        override fun onChanged(data: LocationData?) {
            data ?: return
            val point = GeoPoint(data.latitude, data.longitude)
            if (point.isValid()) {
                if (point != latest) Log.i(TAG, "위치 받음: ${point.lat}, ${point.lon}")
                latest = point
            }
        }

        override fun onError(e: Exception) {
            Log.w(TAG, "위치 오류: ${e.message}")
        }
    }

    override val pending: Boolean get() = !failed && latest == null

    override fun current(): GeoPoint {
        start()
        return latest ?: fallback
    }

    @Synchronized
    private fun start() {
        if (fused != null || failed) return
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) return
        val client = runCatching { FusedLocation(context) }.getOrNull()
        val supported = client != null && runCatching { client.isSupported() }.getOrDefault(false)
        if (!supported) {
            failed = true
            Log.i(TAG, "이 기기는 FusedLocation을 지원하지 않아 설정한 좌표를 쓴다")
            return
        }
        runCatching {
            client!!.initialize()
            client.getLastKnownLocation(listener)
            client.registerFusedLocationCallback(listener)
        }.onSuccess { fused = client }.onFailure {
            failed = true
            Log.w(TAG, "FusedLocation 시작 실패: ${it.message}")
        }
    }

    @Synchronized
    fun stop() {
        val client = fused ?: return
        fused = null
        runCatching {
            client.unregisterFusedLocationCallback(listener)
            client.release()
        }
    }

    private fun GeoPoint.isValid() = lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)

    companion object {
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
        private const val TAG = "PleosLocation"
    }
}
