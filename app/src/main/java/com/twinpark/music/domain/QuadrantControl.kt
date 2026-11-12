package com.twinpark.music.domain

import kotlin.math.abs
import kotlin.math.hypot

/**
 * 노브 영역을 가로세로 십자로 나눈 네 구획. 구획마다 값(%)이 하나씩 있고 서로 독립이다(Figma 18:353의 에코, 음량, 템포, 음 높이).
 * 순서가 [QuadrantControl]이 다루는 배열의 칸 번호다.
 */
enum class Quadrant(val label: String) {
    /** 위 왼쪽: 울림(엔진의 space) */
    Echo("ECHO"),

    /** 위 오른쪽: 음량 */
    Volume("VOLUME"),

    /** 아래 왼쪽: 음 높이(엔진의 reg) */
    Pitch("PITCH"),

    /** 아래 오른쪽: 빠르기(엔진의 tempo) */
    Tempo("TEMPO"),
}

/**
 * 노브 위치로 구획의 값을 정한다. 위치는 영역 반쪽 크기를 1로 한 정규화 좌표다(x는 오른쪽, y는 아래가 양수).
 *
 * - 노브가 놓인 **구획의 값만** 바뀐다. 값은 중심에서의 거리다(가운데 0%, 가장자리 100%). 다른 구획의 값은 그대로 기억한다.
 *   그래서 음량 구획에서 100%를 찍어 두고, 템포 구획에서 20%로 바꿔도 음량은 100%로 남는다.
 * - 음량 외 항목은 50%가 분위기의 기본값이다(0%는 최소, 100%는 최대).
 */
object QuadrantControl {
    /** 가로축과 세로축에서 이 폭(정규화 좌표) 안쪽은 어느 구획도 아니라서 값을 바꾸지 않는다(경계에서 값이 튀지 않게). */
    const val DEAD_ZONE = 0.06f

    /** 분위기 기본값(음량 외 항목). */
    const val NEUTRAL = 0.5f

    /** 시작 음량. */
    const val INITIAL_VOLUME = 0.7f

    /** 노브가 있는 구획. 축 위(경계)면 null이다. */
    fun quadrantAt(x: Float, y: Float): Quadrant? {
        if (abs(x) < DEAD_ZONE || abs(y) < DEAD_ZONE) return null
        val left = x < 0f
        val up = y < 0f
        return when {
            up && left -> Quadrant.Echo
            up -> Quadrant.Volume
            left -> Quadrant.Pitch
            else -> Quadrant.Tempo
        }
    }

    /** 노브 위치가 정하는 값(0..1): 중심에서의 거리. */
    fun levelAt(x: Float, y: Float): Float = hypot(x, y).coerceIn(0f, 1f)

    /** 분면별 값(0..1, [Quadrant] 순서)에서 음량(0..1). */
    fun volumeOf(levels: FloatArray): Float = levels[Quadrant.Volume.ordinal]

    /** 음량을 뺀 항목을 엔진 조절값(-1..1, 0이 기본)으로 바꾼다. 세기와 새로움은 건드리지 않는다. */
    fun toParams(levels: FloatArray): EngineParams {
        fun amt(q: Quadrant) = ((levels[q.ordinal] - NEUTRAL) * 2f).coerceIn(-1f, 1f)
        return EngineParams(reg = amt(Quadrant.Pitch), tempo = amt(Quadrant.Tempo), space = amt(Quadrant.Echo))
    }

    /** 처음 값: 음량은 [INITIAL_VOLUME], 나머지는 기본. */
    fun initialLevels(): FloatArray = FloatArray(Quadrant.values().size) { if (it == Quadrant.Volume.ordinal) INITIAL_VOLUME else NEUTRAL }

    /** 시작할 때 노브 위치: 음량 구획(위 오른쪽)에서 시작 음량만큼 떨어진 곳. */
    fun initialPos(): Pair<Float, Float> = (INITIAL_VOLUME * 0.7071f) to (-INITIAL_VOLUME * 0.7071f)
}
