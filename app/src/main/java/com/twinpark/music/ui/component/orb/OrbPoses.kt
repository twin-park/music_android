package com.twinpark.music.ui.component.orb

import androidx.compose.ui.graphics.Color

/**
 * Figma 오브 시안 5장. 시안마다 모양과 색이 다르고, 분위기(음악 종류)마다 한 장이 대응한다.
 * 포즈 번호는 [com.twinpark.music.domain.Mood.orbPose]와 데이터 파일(orb_poses.bin)의 포즈 순서와 같다.
 * (시안에 적힌 퍼센트 20/30/50/70/90은 음량이 아니라 시안 구분 번호다)
 */
object OrbPoses {
    const val COUNT = 5

    /**
     * 포즈별 구슬 크기 배율. 면이 넓게 펼쳐진 포즈(보라, 하늘/노랑)는 같은 점 개수로 더 넓은 면을 덮어
     * 점 간격이 벌어지므로 구슬을 키워 빈틈이 보이지 않게 한다.
     */
    val BEAD_SCALE = floatArrayOf(1.30f, 1.25f, 1.30f, 1.30f, 1.25f)

    /**
     * 포즈별 깊이 어둡기(0이면 앞뒤 밝기가 같다). 어두운 배경 시안은 뒤쪽을 어둡게 해서 깊이를 주고,
     * 밝은 하늘색 시안(하늘/노랑)은 거의 어둡히지 않는다.
     */
    val DEPTH_DIM = floatArrayOf(0.80f, 0.25f, 0.75f, 0.82f, 0.12f)

    /** 포즈별 가장자리 구슬 확대량. 면 포즈(보라, 하늘/노랑)만 옆으로 선 구간을 메우려고 키운다. */
    val EDGE_GROW = floatArrayOf(0f, 0.6f, 0f, 0f, 0f)

    /** 포즈별 최소 밝기(능선이 아닌 면의 밝기). 밝은 시안일수록 높다. */
    val RIM_FLOOR = floatArrayOf(0.55f, 0.7f, 0.70f, 0.50f, 0.78f)

    /** 포즈별 색: 가장자리 0 -> 0.5 -> 1을 base -> mid -> peak로 칠한다. */
    val COLORS: Array<Triple<Color, Color, Color>> = arrayOf(
        // 초록 (20%)
        // 시안 샘플: 정면 라임(124,219,24), 가장자리 노랑(224,235,4)
        Triple(Color(0.12f, 0.78f, 0.06f), Color(0.38f, 0.90f, 0.03f), Color(0.97f, 0.97f, 0.02f)),
        // 보라 (30%)
        Triple(Color(0.08f, 0.16f, 0.80f), Color(0.55f, 0.34f, 1.00f), Color(0.95f, 0.72f, 1.00f)),
        // 파랑 (50%)
        // 시안 샘플: 정면 하늘색(101,185,236), 가장자리 라일락(180,144,239)
        Triple(Color(0.22f, 0.60f, 1.00f), Color(0.26f, 0.58f, 1.00f), Color(0.64f, 0.40f, 0.98f)),
        // 주황 (70%)
        // 시안 샘플: 정면은 연한 라일락 점(작고 성김), 가장자리는 붉은 주황, 능선은 포화된 주황
        Triple(Color(0.55f, 0.42f, 0.92f), Color(0.88f, 0.28f, 0.16f), Color(1.00f, 0.58f, 0.06f)),
        // 하늘/노랑 (90%)
        Triple(Color(0.30f, 0.72f, 1.00f), Color(0.97f, 0.99f, 1.00f), Color(1.00f, 0.92f, 0.50f)),
    )
}
