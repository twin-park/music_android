package com.twinpark.music.voice

import org.json.JSONArray
import org.json.JSONObject

/**
 * 음성 인식이 알아들을 말의 범위. 엔진이 알아듣는 낱말(`signals.json`의 `speech_words`)만 인식하게 좁히면, 소형 모델이 엉뚱한 글자로 듣는 일이 크게 준다.
 * 낱말 앞뒤에 다른 말이 붙은 문장("다른 거 틀어 줘")도 받도록 모르는 말 자리([UNKNOWN])를 함께 허용한다.
 */
object SpeechVocabulary {
    /** Vosk에서 "어휘에 없는 말"을 뜻하는 표시. 인식 결과에 섞여 나오면 [clean]으로 지운다. */
    const val UNKNOWN = "[unk]"

    /**
     * 어휘에서 뺄 낱말. 너무 짧고 강한 명령("꺼 줘")은 다른 말("깔아 줘")이 가장 가까운 이 낱말로 잘못 들려 음악이 꺼지는 일이 있었다.
     * 엔진은 이 낱말도 알아듣지만, 음성 인식 쪽에서는 일부러 받지 않는다("음악 꺼 줘"처럼 대상이 있는 말은 받는다).
     */
    val EXCLUDED: Set<String> = setOf("꺼 줘", "꺼줘")

    /** 자주 쓰는 자연스러운 명령 문장. 엔진 낱말이 들어 있어서 엔진이 알아듣는다. 낱말만으로는 놓치는 말("깔아 줘")을 받으려고 함께 허용한다. */
    val NATURAL: List<String> = listOf(
        "다른 거 틀어 줘", "다른 걸로 바꿔 줘", "조용히 해 줘", "조용하게 해 줘", "신나는 걸로 틀어 줘", "신나게 틀어 줘",
        "잔잔하게 틀어 줘", "차분하게 틀어 줘", "파도 소리 깔아 줘", "파도 소리 틀어 줘", "바다 소리 깔아 줘",
        "음악 꺼 줘", "노래 꺼 줘", "원래대로 해 줘", "이거 좋다", "이거 좋아",
    )

    /** `signals.json`에서 엔진이 알아듣는 모든 낱말(중복 없이). 파일에 `//` 설명 줄이 있어도 읽는다. */
    fun words(signalsJson: String): List<String> {
        val body = signalsJson.lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        val sw = runCatching { JSONObject(body).optJSONObject("speech_words") }.getOrNull() ?: return emptyList()
        val out = LinkedHashSet<String>()
        for (intent in sw.keys()) {
            val list: JSONArray = sw.optJSONArray(intent) ?: continue
            for (i in 0 until list.length()) list.optString(i).trim().takeIf { it.isNotEmpty() }?.let(out::add)
        }
        return out.toList()
    }

    /** Vosk 문법(JSON 배열): 낱말 단독, 앞뒤에 모르는 말이 붙은 꼴, 모르는 말만. 낱말이 없으면 null(제한 없이 인식). */
    fun grammar(words: List<String>, extra: List<String> = NATURAL): String? {
        if (words.isEmpty()) return null
        val phrases = LinkedHashSet<String>()
        phrases += extra
        for (w in words.filterNot { it in EXCLUDED }) {
            phrases += w
            phrases += "$UNKNOWN $w"
            phrases += "$w $UNKNOWN"
            phrases += "$UNKNOWN $w $UNKNOWN"
        }
        phrases += UNKNOWN
        return JSONArray(phrases.toList()).toString()
    }

    /** 인식 결과에서 모르는 말 표시를 지우고 공백을 정리한다. */
    fun clean(text: String): String = text.replace(UNKNOWN, " ").replace(Regex("\\s+"), " ").trim()
}
