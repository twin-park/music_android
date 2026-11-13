package com.twinpark.music.sensing

import com.twinpark.music.car.AppDriving
import com.twinpark.music.domain.DebugSoundSensor
import com.twinpark.music.domain.NoSoundClassifier
import com.twinpark.music.domain.SoundClassifier
import com.twinpark.music.domain.SoundSensor

/** 이 앱이 쓰는 소리 분류기. 소리 인식 모델 가중치가 오면 그 구현으로 바꾼다. */
object AppClassifier {
    val current: SoundClassifier = NoSoundClassifier
}

/** 이 앱이 쓰는 입력들. */
object AppSensors {
    val all: List<SoundSensor> = listOf(DebugSoundSensor, MicSoundSensor, DrivingSoundSensor { AppDriving.state })
}
