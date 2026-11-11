package com.twinpark.music

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.twinpark.music.ui.screen.MusicScreen
import com.twinpark.music.ui.theme.MusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Android 13 이상: 연주 알림을 보이려면 알림 권한이 필요하다. 거절해도 연주는 된다.
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            // 차량 화면은 어두운 톤이 기준이라 다크 테마로 고정한다. 동적 색상은 쓰지 않는다.
            MusicTheme(darkTheme = true, dynamicColor = false) {
                MusicScreen()
            }
        }
    }
}
