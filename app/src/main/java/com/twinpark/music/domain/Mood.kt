package com.twinpark.music.domain

import androidx.compose.ui.graphics.Color

/** 엔진 분위기 하나. [id]는 `ai/engine`의 분위기 ID, [label]은 화면에 보일 이름. */
data class EngineMood(val id: String, val label: String)

/**
 * 음악 분위기. 오브의 움직임과 색, 구름 노브의 하늘 장면을 결정한다.
 *
 * 나중에 AI(MoodEngine)가 상황 판단 결과로 이 값을 만든다.
 * 지금은 프로토타입이라 프리셋만 둔다.
 */
/** 날씨 상세 값. 구름 %, 습도 %, 바람 km/h, 강수 mm. */
data class Weather(val cloudy: Int, val humidity: Int, val windKmh: Int, val rainMm: Int)

data class Mood(
    val label: String,
    /** 표면이 일렁이는 정도. 0이면 완전한 구, 클수록 크게 출렁인다. */
    val amplitude: Float,
    /** 움직임 속도 배율. */
    val speed: Float,
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
    /** 구름의 짙기 배율 (밤은 성기게) */
    val cloudDensity: Float,
    /**
     * 이 분위기에 대응하는 오브 시안(포즈) 번호. 0 초록(20%), 1 보라(30%), 2 파랑(50%), 3 주황(70%), 4 하늘/노랑(90%).
     * 순서는 `OrbPoses`와 `assets/orb_poses.bin`의 포즈 순서와 같다.
     */
    val orbPose: Int,
    /** 오른쪽 패널의 WEATHER DETAIL 값. 지금은 분위기별 고정값이고, 나중에 실제 날씨 데이터가 대신한다. */
    val weather: Weather,
    /**
     * 이 오브 장면을 쓰는 엔진(`ai/engine`) 분위기 ID들. 엔진 분위기 9개를 오브 5개로 묶는다.
     * 묶음은 `ai/dist/상황별_파라미터.md`의 분위기 분류와, Figma 50% 시안이 비 오는 날 화면인 점을 따랐다.
     */
    val engineIds: List<String>,
) {

    companion object {
        // 하늘/구름 값은 prototype/cloud.html에서 시안과 비교해 맞춘 값이다. 두 곳을 같이 수정한다.

        // 날씨 값: 잔잔(Figma 50%)만 시안 값이고, 나머지는 임시다. 시안 20/30/70/90%의 값을 확인해 맞춘다.

        /** 잔잔함: 오브는 시안의 파랑/보라, 하늘은 해질녘 푸른 톤 */
        val Calm = Mood(
            label = "잔잔",
            amplitude = 0.26f,
            speed = 0.55f,
            base = Color(0xFF1B2A5C),
            mid = Color(0xFF3FA9F5),
            peak = Color(0xFFB46BFF),
            sky = listOf(
                Color(0xFF6F86E0), Color(0xFF4B5BB0), Color(0xFF353C8F), Color(0xFF252B70), Color(0xFF1A1F52),
            ),
            glow = Color(0xFFB8C6FF),
            cloudLit = Color(0xFFC9D0FF),
            cloudShade = Color(0xFF1E2558),
            cloudWarm = Color(0xFF5560A8),
            cloudDensity = 0.9f,
            orbPose = 2,
            weather = Weather(86, 62, 8, 8),
            engineIds = listOf("비오는날", "위로"),
        )

        /** 활기: 오브는 시안의 주황/분홍, 하늘은 석양 (Figma 70% 시안) */
        val Energetic = Mood(
            label = "활기",
            amplitude = 0.34f,
            speed = 1.5f,
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
            cloudDensity = 1.0f,
            orbPose = 3,
            weather = Weather(30, 40, 12, 0),
            engineIds = listOf("즐거운대화", "낮드라이브"),
        )

        /** 수면: 가장 고요한 상태, 하늘은 밤 (Figma 30% 시안) */
        val Sleep = Mood(
            label = "수면",
            amplitude = 0.10f,
            speed = 0.22f,
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
            cloudDensity = 0.5f,
            orbPose = 1,
            weather = Weather(20, 18, 4, 0),
            engineIds = listOf("아이재우기", "밤드라이브"),
        )

        // 아래 두 분위기의 이름은 임시다. 시안 20%, 90%가 어떤 음악 종류인지 확정되면 바꾼다.

        /** 산뜻: 시안 20% 초록 (임시 이름) */
        val Fresh = Mood(
            label = "산뜻",
            amplitude = 0.30f,
            speed = 0.9f,
            base = Color(0xFF0B4D38),
            mid = Color(0xFF33D98C),
            peak = Color(0xFFE6F58C),
            sky = listOf(
                Color(0xFF8FD6B0), Color(0xFF4FB58A), Color(0xFF2F8F72), Color(0xFF1F6A5E), Color(0xFF134A4A),
            ),
            glow = Color(0xFFD6F5B8),
            cloudLit = Color(0xFFD2F0D0),
            cloudShade = Color(0xFF1B4A44),
            cloudWarm = Color(0xFF4F9A78),
            cloudDensity = 0.8f,
            orbPose = 0,
            weather = Weather(20, 18, 4, 0),
            engineIds = listOf("평온한재즈", "진정"),
        )

        /** 청량: 시안 90% 하늘색과 노랑 (임시 이름) */
        val Bright = Mood(
            label = "청량",
            amplitude = 0.40f,
            // 오브 흐름 속도 = speed * FLOW_RATE(0.5). 청량은 예전 속도(1.2 * 0.18)가 적당했으므로 맞춘다.
            speed = 0.43f,
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
            cloudDensity = 0.8f,
            orbPose = 4,
            weather = Weather(20, 18, 4, 0),
            engineIds = listOf("바닷가보사노바"),
        )

        /** 엔진 분위기 9개를 사용자에게 보이는 순서로 나열한다. ("분위기 바꾸기"가 이 순서로 돈다) */
        val EngineMoods = listOf(
            EngineMood("비오는날", "비 오는 날"), EngineMood("평온한재즈", "평온한 재즈"), EngineMood("밤드라이브", "밤 드라이브"),
            EngineMood("즐거운대화", "즐거운 대화"), EngineMood("바닷가보사노바", "바닷가 보사노바"), EngineMood("아이재우기", "아이 재우기"),
            EngineMood("진정", "진정"), EngineMood("위로", "위로"), EngineMood("낮드라이브", "낮 드라이브"),
        )

        /** 엔진 분위기에 맞는 오브 장면. 모르는 ID면 잔잔(파랑). */
        fun forEngine(engineId: String?): Mood = Presets.firstOrNull { engineId in it.engineIds } ?: Calm

        /** 시안(오브 포즈) 순서대로 나열한다. */
        val Presets = listOf(Fresh, Sleep, Calm, Energetic, Bright)
    }
}
