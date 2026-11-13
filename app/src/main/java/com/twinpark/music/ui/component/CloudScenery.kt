package com.twinpark.music.ui.component

import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// ---- 구름 (prototype/cloud.html과 같은 값) ----
/** 광원 방향 (왼쪽 위) */
internal const val LIGHT_X = -0.55f
internal const val LIGHT_Y = -0.83f

/** 그림자 덩어리의 크기 비율, 광원 반대쪽으로 미는 정도 */
internal const val SHADE_SCALE = 0.85f
internal const val SHADE_SHIFT = 0.42f

internal class Cloud(val x: Float, val y: Float, val size: Float, val depth: Float, val alpha: Float)

internal val CLOUDS = listOf(
    Cloud(0.18f, 0.24f, 0.20f, 0.9f, 0.95f),
    Cloud(0.72f, 0.17f, 0.17f, 0.6f, 0.90f),
    Cloud(0.55f, 0.42f, 0.16f, 0.35f, 0.85f),
    Cloud(0.20f, 0.68f, 0.26f, 1.0f, 0.95f),
    Cloud(0.74f, 0.62f, 0.18f, 0.7f, 0.85f),
    Cloud(0.62f, 0.86f, 0.20f, 0.5f, 0.90f),
    Cloud(0.10f, 0.92f, 0.16f, 0.8f, 0.85f),
)

/** 구름마다 (dx, dy, 반지름) 3개씩 이어 붙인 덩어리 배열. 프레임마다 객체를 만들지 않기 위해 FloatArray로 둔다. */
internal val CLOUD_PUFFS: List<FloatArray> = CLOUDS.indices.map { makePuffs(it) }

/**
 * 구름 하나를 이루는 작은 덩어리들. 웹 프리뷰와 같은 모양이 나오도록 같은 LCG를 쓴다.
 * (s = s * 1664525 + 1013904223 mod 2^32, 난수 호출 순서도 웹과 같다)
 */
internal fun makePuffs(index: Int): FloatArray {
    var s = (1000L + index * 977L) and 0xFFFFFFFFL
    fun rnd(): Float {
        s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL
        return (s / 4294967296.0).toFloat()
    }

    val top = 22
    val bottom = 10
    val out = FloatArray((top + bottom) * 3)
    var k = 0
    // 윗면의 봉우리들: 작은 덩어리를 많이 겹쳐 한 덩어리 구름으로 보이게 한다
    for (j in 0 until top) {
        val t = j / (top - 1f)
        val hump = max(0f, 1f - abs(t - 0.5f) * 1.6f)
        out[k++] = (t - 0.5f) * 3.0f + (rnd() - 0.5f) * 0.35f
        out[k++] = 0.12f - (0.15f + 0.55f * rnd()) * hump
        out[k++] = (0.28f + 0.30f * rnd()) * (0.5f + 0.7f * hump)
    }
    // 아랫부분 두께
    for (j in 0 until bottom) {
        out[k++] = (rnd() - 0.5f) * 2.4f
        out[k++] = 0.12f + rnd() * 0.15f
        out[k++] = 0.32f + 0.22f * rnd()
    }
    return out
}

/**
 * [img]를 (w x h)를 덮도록 가운데 기준으로 자르고(Crop) [zoom]배 키워 [dx]만큼 옮겨 그린다.
 * [overlay]는 Figma에서 배경 프레임에 꽉 차게 늘려 맞춘 레이어라, 배경이 그려진 영역과 똑같이(비율 유지 없이) 채운다.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSceneImage(
    img: androidx.compose.ui.graphics.ImageBitmap,
    w: Float,
    h: Float,
    zoom: Float,
    dx: Float,
    alpha: Float = 1f,
    overlay: androidx.compose.ui.graphics.ImageBitmap? = null,
    overlayAlpha: Float = 0.5f,
) {
    val scale = maxOf(w / img.width, h / img.height) * zoom
    val dw = img.width * scale
    val dh = img.height * scale
    val off = androidx.compose.ui.unit.IntOffset(((w - dw) / 2f).roundToInt(), ((h - dh) / 2f).roundToInt())
    val size = androidx.compose.ui.unit.IntSize(dw.roundToInt(), dh.roundToInt())
    // 느린 이동이 한 픽셀씩 끊기지 않게 정수 오프셋 대신 변환(소수점 이동)으로 옮긴다
    translate(left = dx) {
        drawImage(image = img, dstOffset = off, dstSize = size, alpha = alpha, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
        if (overlay != null) {
            drawImage(image = overlay, dstOffset = off, dstSize = size, alpha = overlayAlpha, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
        }
    }
}
