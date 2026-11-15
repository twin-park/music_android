package com.twinpark.music.sensing

/**
 * 마이크 소리를 잠깐 다른 곳(음성 인식)에도 흘려보내는 통로. 마이크는 [MicSoundSensor]가 이미 열고 있어서 따로 열지 않는다.
 * [sink]가 있는 동안만 16kHz 모노 16비트 소리 조각을 받는다. 녹음 스레드에서 불리므로 빨리 끝내야 한다.
 */
object MicTap {
    @Volatile var sink: ((samples: ShortArray, count: Int) -> Unit)? = null
}
