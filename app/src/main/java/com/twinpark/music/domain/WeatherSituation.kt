package com.twinpark.music.domain

/**
 * 날씨 API 값과 와이퍼를 지휘자의 날씨 칸(`weather.kind`)으로 바꾼다. 귀가 들은 비·천둥([EarSituation])과 같은 칸이라, 사람이 DEV 에서 고른 칸은 건드리지 않는다.
 *   - 눈 → snow, 비: 시간당 [STORM_MM] 이상이면 storm 아니면 rain, 와이퍼만 켜져 있어도 rain, 구름 [CLOUDY_PERCENT]% 이상 → cloudy, 그 밖은 clear.
 *   - 신선한 날씨 값도 와이퍼도 없으면 모르는 것이므로 보내지 않는다(맑다고 단정하지 않는다).
 * 바뀐 칸만 JSON 으로 돌려준다. 안 바뀌었으면 null.
 */
class WeatherSituation {
    private var sent: String? = null

    fun update(report: WeatherReport?, wipersOn: Boolean, wallMs: Long, userKeys: Set<String> = emptySet()): String? {
        if (PATH in userKeys) return null
        val kind = kindOf(report?.takeIf { it.isFresh(wallMs) }, wipersOn) ?: return null
        if (kind == sent) return null
        sent = kind
        return SituationPresets.patch(PATH, kind)
    }

    /** 보냈지만 엔진이 받지 못했을 때: 다음에 다시 보내게 한다. */
    fun reset() { sent = null }

    companion object {
        const val PATH = "weather.kind"
        const val STORM_MM = 7.5f
        const val CLOUDY_PERCENT = 80

        fun kindOf(report: WeatherReport?, wipersOn: Boolean): String? = when {
            report?.snowing == true -> "snow"
            report?.raining == true -> if (report.precipitationMm >= STORM_MM) "storm" else "rain"
            wipersOn -> "rain"
            report == null -> null
            report.cloudsPercent >= CLOUDY_PERCENT -> "cloudy"
            else -> "clear"
        }
    }
}
