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
) {
    /** 두 조절값을 더한다(각 -1..1로 자른다). 상황 판단이 정한 값 위에 사용자가 노브로 정한 값을 얹을 때 쓴다. */
    operator fun plus(o: EngineParams) = EngineParams(
        reg = (reg + o.reg).coerceIn(-1f, 1f),
        tempo = (tempo + o.tempo).coerceIn(-1f, 1f),
        dens = (dens + o.dens).coerceIn(-1f, 1f),
        energy = (energy + o.energy).coerceIn(-1f, 1f),
        novelty = (novelty + o.novelty).coerceIn(-1f, 1f),
        space = (space + o.space).coerceIn(-1f, 1f),
    )
}
