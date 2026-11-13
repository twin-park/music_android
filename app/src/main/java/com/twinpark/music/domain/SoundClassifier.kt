package com.twinpark.music.domain

/** 소리(16kHz 모노, -1..1)를 한국어 단어로 바꾼다. 소리 인식 모델은 이 인터페이스로 붙인다. */
fun interface SoundClassifier {
    fun classify(samples: FloatArray, sampleRate: Int): Set<String>
}

/** 모델이 없을 때: 아무것도 알아듣지 못한다. */
object NoSoundClassifier : SoundClassifier {
    override fun classify(samples: FloatArray, sampleRate: Int): Set<String> = emptySet()
}
