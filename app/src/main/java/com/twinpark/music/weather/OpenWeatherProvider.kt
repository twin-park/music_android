package com.twinpark.music.weather

import android.util.Log
import com.twinpark.music.domain.GeoPoint
import com.twinpark.music.domain.WeatherProvider
import com.twinpark.music.domain.WeatherReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

class OpenWeatherProvider(
    private val apiKey: String,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : WeatherProvider {

    override suspend fun current(at: GeoPoint): WeatherReport? = withContext(Dispatchers.IO) {
        get("$ENDPOINT?lat=${at.lat}&lon=${at.lon}&units=metric&appid=$apiKey")
            ?.let { OpenWeatherParser.parse(it, nowMs()) }
    }

    private fun get(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            if (connection.responseCode == 200) connection.inputStream.bufferedReader().use { it.readText() }
            else null.also { Log.w(TAG, "HTTP ${connection.responseCode}") }
        } catch (e: Exception) {
            // 예외 메시지에 키가 든 주소가 들어갈 수 있어 종류만 남긴다
            Log.w(TAG, e.javaClass.simpleName)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TAG = "OpenWeather"
        const val ENDPOINT = "https://api.openweathermap.org/data/2.5/weather"
        const val TIMEOUT_MS = 8_000
    }
}

internal object OpenWeatherParser {
    private val RAIN_CODES = 200..531
    private val SNOW_CODES = 600..622

    fun parse(json: String, nowMs: Long): WeatherReport? = runCatching {
        val root = JSONObject(json)
        val code = root.getJSONArray("weather").getJSONObject(0).getInt("id")
        val rainMm = root.optJSONObject("rain")?.optDouble("1h", 0.0) ?: 0.0
        val snowMm = root.optJSONObject("snow")?.optDouble("1h", 0.0) ?: 0.0
        // 진눈깨비(611~616)는 비와 눈이 함께 오지만 장면은 눈이 우선이다
        val snowing = code in SNOW_CODES || snowMm > 0.0
        WeatherReport(
            raining = !snowing && (code in RAIN_CODES || rainMm > 0.0),
            snowing = snowing,
            cloudsPercent = root.optJSONObject("clouds")?.optInt("all", 0) ?: 0,
            humidityPercent = root.getJSONObject("main").optInt("humidity", 0),
            windKmh = ((root.optJSONObject("wind")?.optDouble("speed", 0.0) ?: 0.0) * MS_TO_KMH).roundToInt(),
            precipitationMm = (rainMm + snowMm).toFloat(),
            fetchedAtMs = nowMs,
        )
    }.getOrNull()

    private const val MS_TO_KMH = 3.6
}
