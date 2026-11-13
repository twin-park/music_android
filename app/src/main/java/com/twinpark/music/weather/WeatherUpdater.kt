package com.twinpark.music.weather

import android.content.Context
import android.util.Log
import com.twinpark.music.BuildConfig
import com.twinpark.music.domain.GeoPoint
import com.twinpark.music.domain.LocationSource
import com.twinpark.music.domain.WeatherProvider
import com.twinpark.music.domain.WeatherReport
import com.twinpark.music.domain.WeatherSource
import kotlinx.coroutines.delay

class FixedLocation(private val point: GeoPoint) : LocationSource {
    override fun current() = point
}

class WeatherUpdater(
    private val provider: WeatherProvider,
    private val location: LocationSource,
    private val onReport: (WeatherReport) -> Unit,
    private val onRequest: (GeoPoint) -> Unit = {},
    private val refreshMs: Long = 15 * MINUTE_MS,
    private val retryMs: Long = MINUTE_MS,
) {
    /** 받았으면 true. 실패해도 이전 값은 그대로 둔다. */
    suspend fun refreshOnce(): Boolean = refresh() != Refresh.Failed

    private enum class Refresh { Failed, WithFallback, Done }

    private suspend fun refresh(): Refresh {
        val at = location.current()
        val usedFallback = location.pending
        onRequest(at)
        val report = provider.current(at) ?: return Refresh.Failed
        onReport(report)
        return if (usedFallback) Refresh.WithFallback else Refresh.Done
    }

    suspend fun run() {
        while (true) {
            delay(if (refresh() == Refresh.Done) refreshMs else retryMs)
        }
    }

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}

/** 이 앱이 쓰는 날씨 구성. 키가 없으면 날씨 연동 없이 시계만으로 동작한다. 위치는 차량 위치를 쓰고, 못 받으면 설정한 좌표를 쓴다. */
object AppWeather {
    private val fallback = GeoPoint(BuildConfig.WEATHER_LAT, BuildConfig.WEATHER_LON)

    suspend fun run(context: Context) {
        if (BuildConfig.OPENWEATHER_API_KEY.isBlank()) return
        val location = PleosLocationSource(context.applicationContext, fallback)
        try {
            updater(location).run()
        } finally {
            location.stop()
        }
    }

    private fun updater(location: LocationSource): WeatherUpdater =
        BuildConfig.OPENWEATHER_API_KEY.let { key ->
            WeatherUpdater(
                provider = OpenWeatherProvider(key),
                location = location,
                onRequest = { Log.i("WeatherUpdater", "요청 위치: ${it.lat}, ${it.lon}") },
                onReport = { report ->
                    Log.i("WeatherUpdater", "받음: $report")
                    WeatherSource.setReport(report)
                },
            )
        }
}
