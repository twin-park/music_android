package com.twinpark.music.ui.theme

import androidx.compose.ui.graphics.Color
import com.twinpark.music.domain.Mood

/** 분위기의 색. 도메인(`Mood`)은 화면 기술을 모르므로 색은 UI 쪽이 분위기의 오브 번호로 찾아 쓴다. */
data class MoodPalette(
    /** 표면이 들어간 곳의 색 */
    val base: Color,
    /** 중간 색 */
    val mid: Color,
    /** 표면이 튀어나온 곳(하이라이트)의 색 */
    val peak: Color,
    /** 구름 노브 하늘: 왼쪽 위에서 오른쪽 아래로 이어지는 5단계 그라데이션 */
    val sky: List<Color>,
    /** 광원(왼쪽 위)의 번짐 색 */
    val glow: Color,
    /** 구름의 밝은 면(광원 쪽) 색 */
    val cloudLit: Color,
    /** 구름의 어두운 몸통 색. 광원에서 먼 구름일수록 이 색에 가깝다. */
    val cloudShade: Color,
    /** 광원에 가까운 구름의 따뜻한 그림자 색 */
    val cloudWarm: Color,
)

// 하늘과 구름 값은 prototype/cloud.html에서 시안과 비교해 맞춘 값이다. 두 곳을 같이 수정한다.
private val PALETTES: Map<Int, MoodPalette> = mapOf(
    0 to MoodPalette(  // Fresh
        base = Color(0xFF0B4D38),
        mid = Color(0xFF33D98C),
        peak = Color(0xFFE6F58C),
        // Figma 20%: 짙은 초록 밤(배경 13,43,29)에 점토처럼 부드러운 연초록 구름
        sky = listOf(
            Color(0xFF1B4B36), Color(0xFF14412D), Color(0xFF0E3524), Color(0xFF0A2C1E), Color(0xFF07231A),
        ),
        glow = Color(0xFF6FA085),
        cloudLit = Color(0xFF86AE93),
        cloudShade = Color(0xFF123626),
        cloudWarm = Color(0xFF4E7A62),
    ),
    1 to MoodPalette(  // Sleep
        base = Color(0xFF0E1838),
        mid = Color(0xFF2A4A8A),
        peak = Color(0xFF6C7FD8),
        sky = listOf(
            Color(0xFF14305E), Color(0xFF0F2550), Color(0xFF0B1C42), Color(0xFF081533), Color(0xFF050C22),
        ),
        glow = Color(0xFF5F86C8),
        cloudLit = Color(0xFF7F9CC4),
        cloudShade = Color(0xFF0A1936),
        cloudWarm = Color(0xFF23406E),
    ),
    2 to MoodPalette(  // Calm
        base = Color(0xFF1B2A5C),
        mid = Color(0xFF3FA9F5),
        peak = Color(0xFFB46BFF),
        // Figma 50%: 회색 먹구름(배경 54,68,77, 밝은 구름 112,128,138, 어두운 구름 25,35,42)
        sky = listOf(
            Color(0xFF6C7C86), Color(0xFF55656F), Color(0xFF424F58), Color(0xFF323D45), Color(0xFF232C33),
        ),
        glow = Color(0xFF9AA8B1),
        cloudLit = Color(0xFF8E9DA6),
        cloudShade = Color(0xFF1C262D),
        cloudWarm = Color(0xFF55646D),
    ),
    3 to MoodPalette(  // Energetic
        base = Color(0xFF3A2A6E),
        mid = Color(0xFFFF8A2B),
        peak = Color(0xFFFF3D6E),
        sky = listOf(
            Color(0xFFFFC857), Color(0xFFFF9A2E), Color(0xFFE2644A), Color(0xFF5B4694), Color(0xFF2E2E6E),
        ),
        glow = Color(0xFFFFE9A8),
        cloudLit = Color(0xFFFFD9A8),
        cloudShade = Color(0xFF3E3270),
        cloudWarm = Color(0xFFC27A68),
    ),
    4 to MoodPalette(  // Bright
        base = Color(0xFF33A0FF),
        mid = Color(0xFFEBF5FF),
        peak = Color(0xFFFFE673),
        sky = listOf(
            Color(0xFF9FD8FF), Color(0xFF6FB8F5), Color(0xFF4A98E0), Color(0xFF3A7FCC), Color(0xFF2E66AA),
        ),
        glow = Color(0xFFFFF2B0),
        cloudLit = Color(0xFFF2F8FF),
        cloudShade = Color(0xFF5A86B8),
        cloudWarm = Color(0xFFFFE9A0),
    ),
    5 to MoodPalette(  // Snow
        base = Color(0xFF3E2A22),
        mid = Color(0xFFF57512),
        peak = Color(0xFFFFBD4D),
        // Figma 18:589: 배경 (64,43,41) 짙은 갈색
        sky = listOf(
            Color(0xFF5A4440), Color(0xFF4B3835), Color(0xFF3C2D2B), Color(0xFF2E2322), Color(0xFF221A19),
        ),
        glow = Color(0xFF8A6A5E),
        cloudLit = Color(0xFFB89A8C),
        cloudShade = Color(0xFF2A1F1E),
        cloudWarm = Color(0xFF7A5446),
    ),
)

val Mood.palette: MoodPalette get() = PALETTES.getValue(orbPose)
