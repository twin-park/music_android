package com.twinpark.music.domain

/**
 * 날씨 API 값과 와이퍼를 지휘자의 날씨 칸(`weather.kind`)으로 바꾼다. 귀가 들은 비·천둥([EarSituation])과 같은 칸이고 **소리가 1순위**다(위 [update]의 `earHolds`). 사람이 DEV 에서 고른 칸은 건드리지 않는다.
 *   - 눈 → snow, 비: 시간당 [STORM_MM] 이상이면 storm 아니면 rain, 와이퍼만 켜져 있어도 rain, 구름 [CLOUDY_PERCENT]% 이상 → cloudy, 그 밖은 clear.
 *   - 신선한 날씨 값도 와이퍼도 없으면 모르는 것이므로 보내지 않는다(맑다고 단정하지 않는다).
 * 바뀐 칸만 JSON 으로 돌려준다. 안 바뀌었으면 null.
 */
class WeatherSituation {
    private var sent: String? = null
    private var earHeld = false

    /**
     * [earHolds]: 귀가 소리로 날씨 칸을 켜 둔 동안. 소리가 1순위라서 그동안은 날씨 API 값을 보내지 않고, 소리가 칸을 놓으면
     * (귀가 칸을 지우므로) API 값을 다시 보내서 되돌린다. 날씨 API는 소리가 없을 때를 채우는 보조다.
     */
    fun update(report: WeatherReport?, wipersOn: Boolean, wallMs: Long, userKeys: Set<String> = emptySet(), earHolds: Boolean = false): String? {
        if (PATH in userKeys) return null
        if (earHolds) { earHeld = true; return null }
        if (earHeld) { earHeld = false; sent = null }
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
