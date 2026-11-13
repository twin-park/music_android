package com.twinpark.music

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.twinpark.music.sensing.MicSoundSensor
import com.twinpark.music.weather.PleosLocationSource
import com.twinpark.music.ui.screen.MusicScreen
import com.twinpark.music.ui.theme.MusicTheme

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        MicSoundSensor.start(this)
    }

    override fun onStop() {
        MicSoundSensor.stop()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions()
        setContent {
            // 차량 화면은 어두운 톤이 기준이라 다크 테마로 고정한다. 동적 색상은 쓰지 않는다.
            MusicTheme(darkTheme = true, dynamicColor = false) {
                MusicScreen()
            }
        }
    }

    // 알림(Android 13 이상, 연주 알림 표시), 차량 위치(날씨), 마이크(소리 인식 시험 빌드). 거절해도 연주는 된다.
    private fun requestPermissions() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            add(PleosLocationSource.PERMISSION)
            if (BuildConfig.MIC_SENSING) add(Manifest.permission.RECORD_AUDIO)
        }
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.RECORD_AUDIO] == true) MicSoundSensor.start(this)
        }.launch(wanted.toTypedArray())
    }
}
