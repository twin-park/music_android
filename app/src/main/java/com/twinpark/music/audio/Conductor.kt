package com.twinpark.music.audio

import org.json.JSONObject

/**
 * 지휘자(ai/engine 의 conductor — 상황 → 분위기 · 악기 · 빠르기 · 주변 소리)가 지금 정한 것. 화면 표시용.
 * 엔진이 0.5초마다 정하고, [BgmController]가 1초마다 읽어 온다.
 */
data class ConductorInfo(
    /** 지휘자 규칙(JSON)을 읽었나. false 면 예전처럼 분위기를 직접 고른다. */
    val loaded: Boolean,
    /** 알아서 하는 중인가 */
    val auto: Boolean,
    /** 지금 나오는 분위기 ID */
    val current: String,
    /** 지휘자가 지금 상황에 고른 분위기 ID · 이름 (몇 초 이어져야 실제로 바뀜) */
    val chosen: String,
    val chosenName: String,
    /** 왜 그렇게 했는지 한 줄 (예: "주말 가족 바닷길 → 바닷가 보사노바, 파도 소리를 깔았어요") */
    val line: String,
    /** 맞은 장면 이름 */
    val scene: String,
    /** 켜진 규칙 (사람이 읽는 말) */
    val because: List<String>,
    /** 주변 소리 크기 (0 끔 · 1 기본) */
    val sea: Float,
    val rain: Float,
) {
    companion object {
        fun parse(json: String): ConductorInfo? = runCatching {
            val o = JSONObject(json)
            if (!o.has("loaded")) return null
            val bc = o.optJSONArray("because")
            val amb = o.optJSONObject("amb")
            ConductorInfo(
                loaded = o.optBoolean("loaded"),
                auto = o.optBoolean("auto"),
                current = o.optString("cur"),
                chosen = o.optString("mood"),
                chosenName = o.optString("mood_name"),
                line = o.optString("line"),
                scene = o.optString("scene_name"),
                because = if (bc == null) emptyList() else List(bc.length()) { bc.optString(it) },
                sea = amb?.optDouble("sea", 0.0)?.toFloat() ?: 0f,
                rain = amb?.optDouble("rain", 0.0)?.toFloat() ?: 0f,
            )
        }.getOrNull()
    }
}
