package com.twinpark.music.domain

/** 차 안에서 들린 소리를 한국어 단어로 알려 주는 입력. 소리 인식 모델도 이 인터페이스로 붙인다. */
fun interface SoundSensor {
    fun heard(): Set<String>
}

/** 개발용 입력: 화면 DEV 칩과 adb 방송이 넣은 단어, 소리 파일을 귀 모델에 통과시킨 결과를 합쳐서 돌려준다. */
object DebugSoundSensor : SoundSensor {
    @Volatile var fromScreen: Set<String> = emptySet()
    @Volatile var fromAdb: Set<String> = emptySet()

    /** 소리 파일을 귀 모델에 통과시킨 결과(`tools/feed-voice.ps1 -Mode ear`). 평소에는 없다. */
    @Volatile var fromFile: SoundSensor? = null

    override fun heard(): Set<String> = fromScreen + fromAdb + fromFile?.heard().orEmpty()
}

fun List<SoundSensor>.heardAll(): Set<String> = flatMapTo(mutableSetOf()) { it.heard() }

