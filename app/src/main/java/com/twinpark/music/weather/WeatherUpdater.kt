package com.twinpark.music.weather

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
    private val refreshMs: Long = 15 * MINUTE_MS,
    private val retryMs: Long = MINUTE_MS,
) {
    /** 받았으면 true. 실패해도 이전 값은 그대로 둔다. */
    suspend fun refreshOnce(): Boolean {
        val report = provider.current(location.current()) ?: return false
        onReport(report)
        return true
    }

    suspend fun run() {
        while (true) {
            delay(if (refreshOnce()) refreshMs else retryMs)
        }
    }

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}

/** 이 앱이 쓰는 날씨 구성. 키가 없으면 null이라 날씨 연동 없이 시계만으로 동작한다. */
object AppWeather {
    val updater: WeatherUpdater? = BuildConfig.OPENWEATHER_API_KEY
        .takeIf { it.isNotBlank() }
        ?.let { key ->
            WeatherUpdater(
                provider = OpenWeatherProvider(key),
                location = FixedLocation(GeoPoint(BuildConfig.WEATHER_LAT, BuildConfig.WEATHER_LON)),
                onReport = { report ->
                    Log.i("WeatherUpdater", "받음: $report")
                    WeatherSource.setReport(report)
                },
            )
        }
}
