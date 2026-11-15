package com.twinpark.music.voice

/**
 * 엔진이 돌려주는 답변 문장 → 미리 만들어 둔 음성 파일(`assets/voice/`).
 * 엔진의 답변은 고정 문장이라 미리 읽어 둘 수 있다. 엔진 쪽 문장을 바꾸거나 늘리면 여기와 파일도 같이 바꿔야 한다.
 */
object ReplyClips {
    private val CLIPS = mapOf(
        "이 느낌 오래 둘게요" to "reply_like.wav",
        "다른 분위기로 바꿀게요" to "reply_change.wav",
        "음악을 마무리할게요" to "reply_music_off.wav",
        "자동을 껐어요 · 직접 골라 주세요" to "reply_auto_off.wav",
        "아기가 자요 → 잔잔하게 바꿀게요" to "reply_baby_sleep.wav",
        "조용히 할게요" to "reply_quiet.wav",
        "신나게 갈게요" to "reply_upbeat.wav",
        "잔잔하게 갈게요" to "reply_calm.wav",
        "파도 소리를 깔게요" to "reply_sea_sound.wav",
        "원래대로 할게요" to "reply_reset.wav",
        "잘 못 알아들었어요" to "reply_unknown.wav",
    )

    const val DIR = "voice"

    /** 이 답변에 맞는 파일 이름. 모르는 문장이면 null(읽지 않는다). */
    fun fileFor(reply: String): String? = CLIPS[reply.trim()]

    /** 모든 답변 문장 */
    val replies: Set<String> get() = CLIPS.keys

    /** 모든 파일 이름 */
    val files: Collection<String> get() = CLIPS.values
}
