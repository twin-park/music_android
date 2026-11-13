package com.twinpark.music.domain

/** 엔진 분위기 하나. [id]는 `ai/engine`의 분위기 ID, [label]은 화면에 보일 이름. */
data class EngineMood(val id: String, val label: String)

/** 날씨 상세 값. 구름 %, 습도 %, 바람 km/h, 강수 mm. */
data class Weather(val cloudy: Int, val humidity: Int, val windKmh: Int, val rainMm: Int)

/** 오브의 움직임, 구름 노브 장면, 엔진 분위기 묶음을 정한다. 색은 UI 쪽 `Mood.palette`가 가진다. */
data class Mood(
    val label: String,
    /** 표면이 일렁이는 정도. 0이면 완전한 구, 클수록 크게 출렁인다. */
    val amplitude: Float,
    /** 움직임 속도 배율. */
    val speed: Float,
    /** 구름의 짙기 배율 (밤은 성기게) */
    val cloudDensity: Float,
    /**
     * 이 분위기에 대응하는 오브 시안(포즈) 번호. 0 초록(20%), 1 보라(30%), 2 파랑(50%), 3 주황(70%), 4 하늘/노랑(90%), 5 구리빛(눈 오는 밤).
     * 순서는 `OrbStyles.ALL`과 `assets/orb_poses.bin`의 포즈 순서와 같다.
     */
    val orbPose: Int,
    /** 오른쪽 패널의 WEATHER DETAIL 값. 지금은 분위기별 고정값이고, 나중에 실제 날씨 데이터가 대신한다. */
    val weather: Weather,
    /**
     * 이 오브 장면을 쓰는 엔진(`ai/engine`) 분위기 ID들. 엔진 분위기 9개를 오브 5개로 묶는다.
     * 묶음은 `ai/dist/상황별_파라미터.md`의 분위기 분류와, Figma 50% 시안이 비 오는 날 화면인 점을 따랐다.
     */
    val engineIds: List<String>,
    /** 구름 대신 Figma 장면 이미지를 쓰는 분위기의 이미지 키(`ui/component/SceneArt`가 리소스로 바꾼다). null이면 절차적 구름. */
    val sceneKey: String? = null,
) {

    companion object {
        // 날씨 값은 Figma 시안의 장면별 값이다: 20%(초록)와 70%(주황)는 86/42/6/0, 30%(보라)와 90%(하늘/노랑)는 20/18/4/0, 50%(파랑)는 86/62/8/8.

        /** 잔잔함: 오브는 시안의 파랑/보라, 하늘은 해질녘 푸른 톤 */
        val Calm = Mood(
            label = "잔잔",
            amplitude = 0.26f,
            speed = 0.55f,
            cloudDensity = 1.0f,
            orbPose = 2,
            weather = Weather(86, 62, 8, 8),
            engineIds = listOf("비오는날", "위로", "클래식피아노"),
            sceneKey = "rain",
        )

        /** 활기: 오브는 시안의 주황/분홍, 하늘은 석양 (Figma 70% 시안) */
        val Energetic = Mood(
            label = "활기",
            amplitude = 0.34f,
            speed = 1.5f,
            cloudDensity = 1.0f,
            orbPose = 3,
            sceneKey = "dusk",
            weather = Weather(86, 42, 6, 0),
            engineIds = listOf("즐거운대화", "낮드라이브"),
        )

        /** 수면: 가장 고요한 상태, 하늘은 밤 (Figma 30% 시안) */
        val Sleep = Mood(
            label = "수면",
            amplitude = 0.10f,
            speed = 0.22f,
            cloudDensity = 0.5f,
            orbPose = 1,
            weather = Weather(20, 18, 4, 0),
            engineIds = listOf("아이재우기", "밤드라이브", "현악앙상블"),
            sceneKey = "night",
        )

        // 아래 두 분위기의 이름은 임시다. 시안 20%, 90%가 어떤 음악 종류인지 확정되면 바꾼다.

        /** 산뜻: 시안 20% 초록 (임시 이름) */
        val Fresh = Mood(
            label = "산뜻",
            amplitude = 0.30f,
            speed = 0.9f,
            cloudDensity = 0.85f,
            orbPose = 0,
            weather = Weather(86, 42, 6, 0),
            engineIds = listOf("평온한재즈", "진정", "클래식왈츠"),
            sceneKey = "dawn",
        )

        /** 청량: 시안 90% 하늘색과 노랑 (임시 이름) */
        val Bright = Mood(
            label = "청량",
            amplitude = 0.40f,
            // 오브 흐름 속도 = speed * FLOW_RATE(0.5). 청량은 예전 속도(1.2 * 0.18)가 적당했으므로 맞춘다.
            speed = 0.43f,
            cloudDensity = 0.8f,
            orbPose = 4,
            weather = Weather(20, 18, 4, 0),
            engineIds = listOf("바닷가보사노바"),
            sceneKey = "day",
        )

        /** 눈 오는 밤: 구리빛 구, 하늘은 짙은 갈색 (Figma 18:589). 음악이 아니라 날씨(눈)가 고르므로 엔진 분위기는 없다. */
        val Snow = Mood(
            label = "눈 오는 밤",
            amplitude = 0.22f,
            speed = 0.35f,
            cloudDensity = 0.6f,
            orbPose = 5,
            weather = Weather(90, 80, 3, 0),
            engineIds = emptyList(),
            sceneKey = "snow",
        )

        /** 엔진 분위기 9개를 사용자에게 보이는 순서로 나열한다. ("분위기 바꾸기"가 이 순서로 돈다) */
        val EngineMoods = listOf(
            EngineMood("비오는날", "비 오는 날"), EngineMood("평온한재즈", "평온한 재즈"), EngineMood("밤드라이브", "밤 드라이브"),
            EngineMood("즐거운대화", "즐거운 대화"), EngineMood("바닷가보사노바", "바닷가 보사노바"), EngineMood("아이재우기", "아이 재우기"),
            EngineMood("진정", "진정"), EngineMood("위로", "위로"), EngineMood("낮드라이브", "낮 드라이브"),
            EngineMood("클래식피아노", "클래식 피아노"), EngineMood("현악앙상블", "현악 앙상블"), EngineMood("클래식왈츠", "클래식 왈츠"),
        )

        /** [current]에서 [step]칸 떨어진 엔진 분위기 ID. 목록 끝에서는 처음으로 돌아가고, 모르는 ID면 첫 분위기다. */
        fun stepEngineId(current: String?, step: Int): String {
            val ids = EngineMoods.map { it.id }
            val index = ids.indexOf(current)
            return if (index < 0) ids.first() else ids[Math.floorMod(index + step, ids.size)]
        }

        /** 엔진 분위기에 맞는 오브 장면. 모르는 ID면 잔잔(파랑). */
        fun forEngine(engineId: String?): Mood = Presets.firstOrNull { engineId in it.engineIds } ?: Calm

        /** 시안(오브 포즈) 순서대로 나열한다. */
        val Presets = listOf(Fresh, Sleep, Calm, Energetic, Bright, Snow)
    }
}
