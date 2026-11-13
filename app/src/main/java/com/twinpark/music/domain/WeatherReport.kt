package com.twinpark.music.domain

import kotlin.math.roundToInt

data class GeoPoint(val lat: Double, val lon: Double)

fun interface LocationSource {
    fun current(): GeoPoint
}

interface WeatherProvider {
    /** 받지 못하면 null이다. */
    suspend fun current(at: GeoPoint): WeatherReport?
}

/** 공급자와 상관없는 날씨 관측값. 비와 눈 판단은 공급자가 한다. */
data class WeatherReport(
    val raining: Boolean,
    val snowing: Boolean,
    val cloudsPercent: Int,
    val humidityPercent: Int,
    val windKmh: Int,
    val precipitationMm: Float,
    val fetchedAtMs: Long,
) {
    fun toDetail() = Weather(cloudsPercent, humidityPercent, windKmh, precipitationMm.roundToInt())

    fun isFresh(nowMs: Long, maxAgeMs: Long = MAX_AGE_MS): Boolean = nowMs - fetchedAtMs in 0..maxAgeMs

    companion object {
        const val MAX_AGE_MS = 60L * 60L * 1000L
    }
}
