package com.twinpark.music.ui.component.orb

import androidx.compose.ui.graphics.Color

/**
 * 오브 한 종류가 책임지는 값 전부. 구슬을 어떻게 그리는지(크기, 색, 깊이, 가장자리)와 어떻게 움직이는지(흐르는 속도, 흔들림)를
 * 오브마다 따로 정한다. [BeadOrb]는 이 인터페이스만 보므로, 한 오브를 고쳐도 다른 오브는 그대로다.
 *
 * 구형(초록, 파랑, 주황)은 천천히 일렁이는 게 자연스럽고, 판이 겹치는 형태(보라, 청량, 눈 오는 밤)는 같은 속도로는 둔해 보여
 * 속도를 따로 준다. 포즈 번호는 [com.twinpark.music.domain.Mood.orbPose]와 `orb_poses.bin`의 포즈 순서와 같다.
 */
interface OrbStyle {
    /** `orb_poses.bin`에서 이 오브의 포즈 번호. */
    val pose: Int

    /** 구슬 크기 배율. 면이 넓게 펼쳐져 점 간격이 벌어지는 오브는 키워 빈틈이 보이지 않게 한다. */
    val beadScale: Float

    /** 깊이 어둡기(0이면 앞뒤 밝기가 같다). 어두운 시안은 뒤쪽을 어둡게 해서 깊이를 준다. */
    val depthDim: Float

    /** 가장자리 구슬 확대량. 옆으로 선 구간을 메우려고 키운다. */
    val edgeGrow: Float

    /** 능선이 아닌 면의 최소 밝기. 밝은 시안일수록 높다. */
    val rimFloor: Float

    /** 색: 가장자리 0 -> 0.5 -> 1을 base -> mid -> peak로 칠한다. */
    val colors: Triple<Color, Color, Color>

    /**
     * 키프레임을 따라 흐르는 속도 배율(분위기의 speed에 곱해진다). 1이 기준(초당 [BeadOrb] 기본 이동량).
     * 구형은 낮게, 겹친 판은 높게 둔다.
     */
    val flowRate: Float

    /** 전체가 세로축으로 천천히 흔들리는 정도 배율. */
    val swayAmount: Float
}

/** 값만 담는 기본 구현. 오브마다 필요한 값만 바꿔 쓴다. */
private open class BaseOrb(
    override val pose: Int,
    override val beadScale: Float,
    override val depthDim: Float,
    override val edgeGrow: Float,
    override val rimFloor: Float,
    override val colors: Triple<Color, Color, Color>,
    override val flowRate: Float,
    override val swayAmount: Float = 1f,
) : OrbStyle

/** 구 모양: 천천히 일렁인다. */
private class SphereOrb(
    pose: Int, beadScale: Float, depthDim: Float, edgeGrow: Float, rimFloor: Float, colors: Triple<Color, Color, Color>,
) : BaseOrb(pose, beadScale, depthDim, edgeGrow, rimFloor, colors, flowRate = 0.5f)

/** 판이 겹치는 모양(보라, 청량, 눈 오는 밤): 구보다 빠르게 흐르고 흔들림도 큰 판이 많다. */
private class FoldOrb(
    pose: Int, beadScale: Float, depthDim: Float, edgeGrow: Float, rimFloor: Float, colors: Triple<Color, Color, Color>, flow: Float, sway: Float = 1f,
) : BaseOrb(pose, beadScale, depthDim, edgeGrow, rimFloor, colors, flowRate = flow, swayAmount = sway)

/** 오브 6종. 순서가 포즈 번호다. */
object OrbStyles {
    val ALL: List<OrbStyle> = listOf(
        // 0 초록 (20%, 새벽): 시안 샘플 정면 라임(124,219,24), 가장자리 노랑(224,235,4)
        SphereOrb(0, 0.80f, 0.80f, 1.10f, 0.55f, Triple(Color(0.12f, 0.78f, 0.06f), Color(0.38f, 0.90f, 0.03f), Color(0.97f, 0.97f, 0.02f))),
        // 1 보라 (30%, 밤): 어두운 남색 구슬에 가장자리만 라일락으로 빛남
        FoldOrb(1, 0.80f, 0.80f, 0.35f, 0.58f, Triple(Color(0.10f, 0.17f, 0.66f), Color(0.40f, 0.42f, 1.00f), Color(0.90f, 0.78f, 1.00f)), flow = 0.9f),
        // 2 파랑 (50%, 비): 시안 샘플 정면 하늘색(101,185,236), 가장자리 라일락(180,144,239)
        SphereOrb(2, 1.30f, 0.75f, 0.45f, 0.70f, Triple(Color(0.22f, 0.60f, 1.00f), Color(0.26f, 0.58f, 1.00f), Color(0.64f, 0.40f, 0.98f))),
        // 3 주황 (70%, 황혼): 정면은 연한 라일락 점, 가장자리는 붉은 주황, 능선은 포화된 주황
        SphereOrb(3, 1.30f, 0.82f, 0.55f, 0.50f, Triple(Color(0.55f, 0.42f, 0.92f), Color(0.88f, 0.28f, 0.16f), Color(1.00f, 0.58f, 0.06f))),
        // 4 청량 (90%, 낮): 하늘색 바탕에 흰 구슬, 윗면은 노랑. 구슬마다 시안 색을 읽어 0(파랑) 0.5(흰색) 1(노랑)로 넣는다.
        FoldOrb(4, 1.75f, 0.78f, 0f, 0.85f, Triple(Color(0.20f, 0.62f, 0.98f), Color(0.96f, 0.98f, 1.00f), Color(1.00f, 0.88f, 0.40f)), flow = 0.8f),
        // 5 눈 오는 밤 (Figma 18:589): 짙은 갈색 구슬에서 구리빛 주황을 거쳐 능선은 밝은 노랑 주황
        FoldOrb(5, 1.45f, 0.80f, 0f, 0.78f, Triple(Color(0.62f, 0.24f, 0.06f), Color(0.96f, 0.46f, 0.07f), Color(1.00f, 0.74f, 0.30f)), flow = 1.0f, sway = 1.6f),
    )
}
