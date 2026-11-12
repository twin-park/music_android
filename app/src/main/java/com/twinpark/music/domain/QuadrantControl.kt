package com.twinpark.music.domain

import kotlin.math.hypot

/**
 * 음량 노브 영역을 가로세로 십자로 나눈 네 구획. 노브가 놓인 구획의 항목이 조절된다(Figma 18:353의 에코, 음수, 템포, 음 높이).
 * 순서가 [QuadrantControl.weights]와 [QuadrantControl.amounts] 배열의 칸 번호다.
 */
enum class Quadrant(val label: String) {
    /** 위 왼쪽: 울림(엔진의 space) */
    Echo("ECHO"),

    /** 위 오른쪽: 음 수(엔진의 dens) */
    Notes("NOTES"),

    /** 아래 왼쪽: 음 높이(엔진의 reg) */
    Pitch("PITCH"),

    /** 아래 오른쪽: 빠르기(엔진의 tempo) */
    Tempo("TEMPO"),
}

/**
 * 노브 위치로 [EngineParams] 조절량을 정한다.
 * - 위치는 영역 반쪽 크기를 1로 한 정규화 좌표다(x는 오른쪽, y는 아래가 양수, 길이 0..1이 음량).
 * - 노브가 있는 구획의 항목은 중심에서 멀수록 커진다. 거리 0이 최소(-1), 0.5가 분위기의 기본값(0), 1이 최대(+1)다.
 * - 다른 구획의 항목은 0(분위기 기본값)이다. 구획 경계 근처에서는 값이 갑자기 바뀌지 않게 가중치로 섞는다.
 */
object QuadrantControl {
    /** 경계(가로축, 세로축)에서 이 폭(정규화 좌표) 안쪽은 이웃 구획과 섞인다. */
    const val BLEND = 0.12f

    private fun smooth(x: Float): Float {
        val t = ((x + BLEND) / (2f * BLEND)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** 구획별 가중치(합 1). [Quadrant] 순서. */
    fun weights(x: Float, y: Float): FloatArray {
        val right = smooth(x)
        val down = smooth(y)
        val left = 1f - right
        val up = 1f - down
        return floatArrayOf(left * up, right * up, left * down, right * down)
    }

    /** 구획별 조절량(-1..1). [Quadrant] 순서. */
    fun amounts(x: Float, y: Float): FloatArray {
        val d = hypot(x, y).coerceIn(0f, 1f)
        val level = (d - 0.5f) * 2f
        return weights(x, y).map { it * level }.toFloatArray()
    }

    /** 위 조절량을 엔진 조절값으로 바꾼다. 세기와 새로움은 건드리지 않는다. */
    fun toParams(a: FloatArray): EngineParams = EngineParams(
        reg = a[Quadrant.Pitch.ordinal],
        tempo = a[Quadrant.Tempo.ordinal],
        dens = a[Quadrant.Notes.ordinal],
        space = a[Quadrant.Echo.ordinal],
    )
}
