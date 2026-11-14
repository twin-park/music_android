package com.twinpark.music.sensing

import android.content.Context
import android.util.Log
import com.twinpark.music.car.AppDriving
import com.twinpark.music.domain.DebugSoundSensor
import com.twinpark.music.domain.NoSoundClassifier
import com.twinpark.music.domain.SoundClassifier
import com.twinpark.music.domain.SoundSensor
import com.twinpark.music.domain.WeatherSource

/** 이 앱이 쓰는 소리 분류기: 귀(ear2) 모델. 모델을 못 열면 아무것도 알아듣지 못하는 분류기로 대신한다. */
object AppClassifier {
    private const val TAG = "AppClassifier"

    fun create(context: Context): SoundClassifier =
        runCatching { OnnxSoundClassifier.create(context.applicationContext) }
            .onFailure { Log.e(TAG, "귀 모델을 열지 못함", it) }
            .getOrDefault(NoSoundClassifier)
}

/** 이 앱이 쓰는 입력들. */
object AppSensors {
    val all: List<SoundSensor> = listOf(DebugSoundSensor, MicSoundSensor, DrivingSoundSensor { AppDriving.state }, WiperRainSensor { WeatherSource.wipersOn.value })
}
