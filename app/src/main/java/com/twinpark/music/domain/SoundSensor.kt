package com.twinpark.music.domain

/** 차 안에서 들린 소리를 한국어 단어로 알려 주는 입력. 소리 인식 모델도 이 인터페이스로 붙인다. */
fun interface SoundSensor {
    fun heard(): Set<String>
}

/** 개발용 입력: 화면 DEV 칩과 adb 방송이 넣은 단어를 합쳐서 돌려준다. */
object DebugSoundSensor : SoundSensor {
    @Volatile var fromScreen: Set<String> = emptySet()
    @Volatile var fromAdb: Set<String> = emptySet()

    override fun heard(): Set<String> = fromScreen + fromAdb
}

fun List<SoundSensor>.heardAll(): Set<String> = flatMapTo(mutableSetOf()) { it.heard() }

/** 이 앱이 쓰는 입력들. 소리 인식 모델이 생기면 여기에 추가한다. */
object AppSensors {
    val all: List<SoundSensor> = listOf(DebugSoundSensor)
}
