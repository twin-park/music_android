package com.twinpark.music.domain

/**
 * 엔진 조절값 여섯 개(-1..1). 0이면 분위기의 기본값 그대로이다.
 * 음 높이 / 빠르기 / 음 수 / 세기 / 새로움은 다음 2마디 덩어리부터, 울림은 바로 반영된다.
 */
data class EngineParams(
    val reg: Float = 0f,
    val tempo: Float = 0f,
    val dens: Float = 0f,
    val energy: Float = 0f,
    val novelty: Float = 0f,
    val space: Float = 0f,
)
