package com.twinpark.music.domain

import java.util.Calendar

/** 지금 상황 하나와, 그에 맞춰 엔진에 줄 분위기와 조절값. */
data class Directive(
    /** 상황 이름 (화면 표시와 같은 상황인지 비교하는 데 쓴다) */
    val situation: String,
    /** 엔진(`ai/engine`)의 분위기 ID */
    val engineMoodId: String,
    val params: EngineParams = EngineParams(),
)

/**
 * 귀(ear)가 알려 준 단어 -> 상황 -> 엔진 분위기와 조절값. `ai/conduct.py`의 규칙표를 엔진용으로 옮긴 것이다.
 *
 * 감지한 감정을 따라가지 않고 "음악이 해야 할 일"로 바꾼다. 다툼(화난)이 들리면 음악이 빨라지는 게 아니라 느리고 부드러워진다(기획안 4-3).
 * 규칙은 위에서부터 보고 처음 맞는 상황을 쓴다.
 */
object SituationRules {
    /** 즉시층: 이 단어가 들리면 바로 음악을 크게 낮춘다(통화). 상황 전환을 기다리지 않는다. */
    val PHONE_WORDS = setOf("전화벨")

    private class Rule(val situation: String, val words: Set<String>, val moodId: String, val params: EngineParams = EngineParams())

    private val RULES = listOf(
        Rule("아이 재우기", setOf("아기 울음", "속삭임", "숨소리"), "아이재우기", EngineParams(tempo = -0.2f, energy = -0.3f)),
        Rule("진정", setOf("화난", "열이 난", "고함", "격한", "짜증난"), "진정", EngineParams(reg = -0.2f, tempo = -0.3f, energy = -0.3f)),
        Rule("위로", setOf("슬픈", "가라앉은", "울음", "불안한"), "위로"),
        Rule("즐거운 대화", setOf("웃음", "신난", "밝은", "노래", "환호"), "즐거운대화", EngineParams(tempo = 0.2f, dens = 0.2f)),
        Rule("바닷가", setOf("바다", "파도", "갈매기"), "바닷가보사노바", EngineParams(space = 0.3f)),
        Rule("비 오는 길", setOf("비", "천둥"), "비오는날"),
        // 엔진에 '자연 속' 분위기가 없어 가장 가까운 평온한 재즈를 쓴다(임시)
        Rule("자연 속", setOf("새소리", "풀벌레", "바람", "물소리"), "평온한재즈", EngineParams(space = 0.2f)),
        // 드라이브는 밤이면 밤 드라이브(단조, 뮤트 트럼펫), 낮이면 낮 드라이브
        Rule("드라이브", setOf("도로", "경적", "기차"), "낮드라이브"),
    )

    /** 단어 목록으로 상황을 정한다. 맞는 규칙이 없으면 null(= 근거 없음, 지금 상황을 그대로 둔다). [night]은 저녁 7시~아침 6시. */
    fun interpret(words: Collection<String>, night: Boolean = false): Directive? {
        val ws = words.toSet()
        val r = RULES.firstOrNull { rule -> rule.words.any { it in ws } } ?: return null
        val mood = if (r.situation == "드라이브" && night) "밤드라이브" else r.moodId
        return Directive(r.situation, mood, r.params)
    }

    /** 통화 중인가(즉시층). */
    fun isPhone(words: Collection<String>): Boolean = words.any { it in PHONE_WORDS }

    fun isNight(now: Calendar = Calendar.getInstance()): Boolean {
        val h = now.get(Calendar.HOUR_OF_DAY)
        return h >= 19 || h < 6
    }
}
