package com.twinpark.music.domain

/**
 * 지휘자(ai/engine conductor)에 보내는 상황 칸과 시연용 묶음. 데스크톱 화면(ai/engine/web/index.html)의 "상황 바꿔 보기"와 같은 값이다.
 * 실제 차에서는 내비 · 날씨 · 차량 SDK · 실내 마이크가 보내고, 지금은 DEV 패널에서 고른다.
 * 칸 이름과 값의 뜻은 ai/engine/data/conductor/signals.json.
 */
object SituationPresets {
    data class Group(val path: String, val name: String, val values: List<Pair<String, String>>)

    val GROUPS = listOf(
        Group("trip.kind", "여정", listOf("commute" to "출근", "homeward" to "퇴근", "school_run" to "등하원", "outing" to "나들이", "long_trip" to "장거리", "late_home" to "심야 귀가", "hurry" to "급한 이동", "joyride" to "드라이브")),
        Group("place.kind", "장소", listOf("city" to "도심", "residential" to "주택가", "highway" to "고속도로", "country_road" to "국도", "coast" to "바닷가", "mountain" to "산길", "river" to "강변", "tunnel" to "터널", "school_zone" to "어린이보호구역")),
        Group("weather.kind", "날씨", listOf("clear" to "맑음", "cloudy" to "흐림", "rain" to "비", "storm" to "폭우", "snow" to "눈", "fog" to "안개", "heat" to "폭염", "cold" to "한파")),
        Group("time.slot", "시간대", listOf("dawn" to "새벽", "morning" to "아침", "day" to "낮", "dusk" to "해 질 녘", "evening" to "저녁", "night" to "밤", "latenight" to "심야")),
        Group("time.group", "요일", listOf("mon" to "월요일", "midweek" to "주중", "fri" to "금요일", "weekend" to "주말", "holiday" to "연휴")),
        Group("drive.state", "달리기", listOf("stopped" to "신호 대기", "jam" to "정체", "slow" to "저속", "cruise" to "순항", "fast" to "고속", "speeding" to "과속")),
        Group("people.group", "누구랑", listOf("alone" to "혼자", "partner" to "연인", "family" to "가족", "friends" to "친구들", "elders" to "부모님", "business" to "손님")),
        Group("people.baby", "아기", listOf("awake" to "깸", "sleeping" to "잠듦", "crying" to "울음")),
        Group("cabin.sound", "차 안 소리", listOf("quiet" to "조용", "talk" to "대화", "laugh" to "웃음", "singing" to "따라 부름", "argument" to "언성", "call_car" to "통화", "yawn" to "하품")),
        Group("car.windows", "창문", listOf("closed" to "닫힘", "open" to "열림")),
    )

    /** 한 번에 바꾸는 시연 묶음: 칸 경로 → 값 */
    data class Preset(val name: String, val values: List<Pair<String, Any>>)

    val PRESETS = listOf(
        Preset("비 오는 월요일 출근", listOf("time.group" to "mon", "time.slot" to "morning", "trip.kind" to "commute", "weather.kind" to "rain", "place.kind" to "city", "drive.state" to "cruise", "people.group" to "alone", "car.windows" to "closed", "cabin.sound" to "quiet")),
        Preset("주말 가족 바닷길", listOf("time.group" to "weekend", "time.slot" to "morning", "trip.kind" to "outing", "weather.kind" to "clear", "place.kind" to "coast", "drive.state" to "cruise", "people.group" to "family", "people.baby" to "awake", "car.windows" to "closed", "cabin.sound" to "quiet")),
        Preset("금요일 밤 둘이 · 비", listOf("time.group" to "fri", "time.slot" to "night", "trip.kind" to "homeward", "weather.kind" to "rain", "place.kind" to "city", "drive.state" to "cruise", "people.group" to "partner", "car.windows" to "closed", "cabin.sound" to "quiet")),
        Preset("심야 고속도로 혼자", listOf("time.group" to "midweek", "time.slot" to "latenight", "trip.kind" to "late_home", "weather.kind" to "clear", "place.kind" to "highway", "drive.state" to "fast", "people.group" to "alone", "car.windows" to "closed", "car.drive_min" to 40, "cabin.sound" to "quiet")),
    )

    /**
     * 칸 경로("place.kind")와 값으로 지휘자에 보낼 JSON. 값이 null 이면 그 칸을 지운다.
     * 달리기 상태를 직접 고르면 차량 속도 값은 지운다(데스크톱 화면과 같음).
     */
    fun patch(values: List<Pair<String, Any?>>): String {
        val tree = LinkedHashMap<String, LinkedHashMap<String, Any?>>()
        for ((path, v) in values) {
            val dot = path.indexOf('.')
            if (dot <= 0) continue
            val g = tree.getOrPut(path.substring(0, dot)) { LinkedHashMap() }
            g[path.substring(dot + 1)] = v
            if (path == "drive.state") g["speed"] = null
        }
        return tree.entries.joinToString(",", "{", "}") { (g, kv) ->
            "\"$g\":" + kv.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":" + lit(v) }
        }
    }

    fun patch(path: String, value: Any?): String = patch(listOf(path to value))

    private fun lit(v: Any?): String = when (v) {
        null -> "null"
        is Number, is Boolean -> v.toString()
        else -> "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}

/**
 * 귀(ear)가 들은 단어 → 지휘자 상황 칸. 지휘자가 있으면 [com.twinpark.music.audio.SituationDirector]가 예전 규칙표 대신 이것을 쓴다.
 * 분위기는 지휘자가 상황 전체(시간 · 여정 · 장소 · 날씨 · 누구랑 · 차 안 소리)를 보고 정하고, 귀는 그중 들리는 것만 알려 준다.
 *   - 차 안 소리: 늘 지금 들리는 것 하나 (언성 > 웃음 > 노래 > 대화 > 조용). 몇 초 이어져야 하는지는 지휘자 규칙(hold/release)이 정한다.
 *   - 아기 울음 · 바다 소리 · 비 소리: 들리면 그 칸을 켜고, [RELEASE_MS] 동안 안 들리면 지운다(귀가 켠 칸만).
 * 사람이 화면에서 직접 고른 칸([userKeys])은 귀가 건드리지 않는다.
 */
class EarSituation {
    /** 사람이 DEV · 화면에서 직접 정한 칸 경로 */
    val userKeys = HashSet<String>()
    private val sent = HashMap<String, Any?>()        // 귀가 지휘자에게 마지막으로 보낸 값
    private val heardAt = HashMap<String, Long>()     // 칸 → 마지막으로 들린 시각

    /** 이번에 들린 단어로 바뀐 칸만 JSON 으로. 바뀐 게 없으면 null. */
    fun update(words: Set<String>, nowMs: Long): String? {
        fun heard(vararg ws: String) = ws.any { it in words }
        val want = LinkedHashMap<String, Any?>()
        want["cabin.sound"] = when {
            heard("화난", "열이 난", "고함", "격한", "짜증난") -> "argument"
            heard("웃음", "환호", "신난") -> "laugh"
            heard("노래") -> "singing"
            heard("대화", "아이 목소리", "속삭임") -> "talk"
            else -> "quiet"
        }
        latch(want, "people.baby", "crying", heard("아기 울음"), nowMs)
        latch(want, "place.kind", "coast", heard("바다", "파도", "갈매기"), nowMs)
        latch(want, "weather.kind", if (heard("천둥")) "storm" else "rain", heard("비", "천둥"), nowMs)
        val changed = want.entries.filter { (k, v) -> k !in userKeys && (!sent.containsKey(k) || sent[k] != v) && !(v == null && !sent.containsKey(k)) }
        if (changed.isEmpty()) return null
        for ((k, v) in changed) sent[k] = v
        return SituationPresets.patch(changed.map { it.key to it.value })
    }

    /** 들리면 켜고, 귀가 켠 칸은 한동안 안 들리면 지운다. 귀가 켠 적 없는 칸은 그대로 둔다(빼먹음 = 모름). */
    private fun latch(want: MutableMap<String, Any?>, path: String, value: String, on: Boolean, nowMs: Long) {
        if (on) { heardAt[path] = nowMs; want[path] = value; return }
        val t = heardAt[path] ?: return
        want[path] = if (nowMs - t < RELEASE_MS) sent[path] else null
    }

    /** 사람이 상황을 모두 지웠을 때 */
    fun clear() { userKeys.clear(); sent.clear(); heardAt.clear() }

    companion object {
        const val RELEASE_MS = 30_000L
    }
}
