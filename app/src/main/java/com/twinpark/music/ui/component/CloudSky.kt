package com.twinpark.music.ui.component

import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 장면 이미지가 없을 때 그리는 하늘과 구름. 구름 색과 밀림은 상태 값으로 받아 그리는 단계에서 읽는다. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSky(
    w: Float,
    h: Float,
    t: Float,
    sky: List<State<Color>>,
    glow: State<Color>,
    lit: State<Color>,
    shade: State<Color>,
    warm: State<Color>,
    cloudDensity: State<Float>,
    pushX: State<Float>,
    pushY: State<Float>,
) {
    // 하늘: 왼쪽 위 > 오른쪽 아래
    drawRect(
        Brush.linearGradient(
            colors = sky.map { it.value },
            start = Offset(0f, h * 0.15f),
            end = Offset(w, h * 0.75f),
        ),
    )
    // 광원(왼쪽 위) 번짐
    val glowRadius = max(w, h) * 0.55f
    val glowCenter = Offset(w * 0.08f, h * 0.06f)
    drawRect(
        Brush.radialGradient(
            colors = listOf(
                glow.value.copy(alpha = 0.75f),
                glow.value.copy(alpha = 0.25f),
                glow.value.copy(alpha = 0f),
            ),
            center = glowCenter,
            radius = glowRadius,
        ),
    )

    val unit = min(w, h)
    for (i in CLOUDS.indices) {
        val c = CLOUDS[i]
        val alpha = c.alpha * cloudDensity.value
        // 천천히 떠다니는 움직임. 계수는 0.1의 배수라 PHASE_PERIOD에서 이음매가 없다.
        val driftX = sin(t * 0.2f + i * 1.7f) * 0.03f * w * (0.5f + c.depth)
        val driftY = sin(t * 0.1f + i * 2.3f) * 0.015f * h
        // 노브 반대 방향으로 밀려난다. 가까운 구름(depth 큼)일수록 많이 움직인다.
        val cx = c.x * w + driftX - pushX.value * c.depth * 0.10f * w
        val cy = c.y * h + driftY - pushY.value * c.depth * 0.10f * h
        val s = c.size * unit
        val puffs = CLOUD_PUFFS[i]

        // 1) 밝은 구름 덩어리를 먼저 깐다
        var k = 0
        while (k < puffs.size) {
            val center = Offset(cx + puffs[k] * s, cy + puffs[k + 1] * s)
            val r = puffs[k + 2] * s
            drawCircle(
                brush = Brush.radialGradient(
                    0f to lit.value.copy(alpha = alpha * 0.95f),
                    0.55f to lit.value.copy(alpha = alpha * 0.80f),
                    0.85f to lit.value.copy(alpha = alpha * 0.30f),
                    1f to Color.Transparent,
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
            k += 3
        }

        // 2) 어두운 그림자를 광원 반대쪽(오른쪽 아래)으로 밀어 얹는다.
        //    광원 쪽(왼쪽 위) 가장자리에만 밝은 테두리가 남고 몸통은 어두워진다.
        //    광원에서 먼 구름(x가 큰 쪽)일수록 그림자가 짙고 차가운 색, 가까운 구름은 옅고 따뜻한 색이다.
        val dark = (0.15f + 0.95f * c.x).coerceIn(0f, 1f)
        val shadeColor = lerp(warm.value, shade.value, dark)
        val shadeAlpha = alpha * (0.28f + 0.72f * dark)
        k = 0
        while (k < puffs.size) {
            val pr = puffs[k + 2]
            val r = pr * s * SHADE_SCALE
            val center = Offset(
                cx + puffs[k] * s - LIGHT_X * pr * s * SHADE_SHIFT,
                cy + puffs[k + 1] * s - LIGHT_Y * pr * s * SHADE_SHIFT,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to shadeColor.copy(alpha = shadeAlpha * 0.92f),
                    0.5f to shadeColor.copy(alpha = shadeAlpha * 0.70f),
                    0.85f to shadeColor.copy(alpha = shadeAlpha * 0.22f),
                    1f to Color.Transparent,
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
            k += 3
        }
    }
}
