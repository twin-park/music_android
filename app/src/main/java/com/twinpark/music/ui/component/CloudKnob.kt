package com.twinpark.music.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.sp
import com.twinpark.music.domain.Mood
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 구름 노브: 구름 위의 유리 원을 끌어서 음량을 조절한다.
 *
 * 조작 (가정: Figma 스케치의 0%/100% 눈금 해석)
 * - 영역 중심이 0%, 상하좌우 끝이 100%다. **중심에서 멀수록 음량이 크다.**
 * - 영역 어디를 눌러도 노브가 그 자리로 오고, 그대로 끌 수 있다. (한 손, 시선 최소화)
 * - 구름은 노브 위치에 따라 천천히 밀려난다. (스케치의 "구름을 움직이는 연출")
 * - 5% 단위로 햅틱 피드백을 준다.
 * - Pleos UI 가이드의 Stepper(+/- 버튼)를 보조 수단으로 함께 둔다. 접근성 setProgress도 지원한다.
 *
 * [volume]은 0..1이다. 외부에서 값이 바뀌면(버튼, 음성) 노브가 현재 방향을 유지한 채 거리만 바뀐다.
 *
 * 구름 표현은 prototype/cloud.html에서 시안과 비교해 맞춘 값이다. 두 곳의 상수와 LCG를 같이 유지한다.
 */
@Composable
fun CloudKnob(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    mood: Mood,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }

    // 노브가 움직이는 범위와 버튼 크기는 카드 크기에 비례한다. 노브가 그려지는 지름은 음량 값에 비례한다
    // (Figma 시안: 값 20%에서 90%로 갈수록 노브 지름이 왼쪽 영역 폭의 12%에서 37%로 커진다).
    val minKnobPx = with(density) { MIN_KNOB.toPx() }
    val maxKnobPx = with(density) { MAX_KNOB.toPx() }
    val travelPx = if (fieldSize == IntSize.Zero) minKnobPx
    else min(fieldSize.width * KNOB_WIDTH_RATIO, fieldSize.height * KNOB_HEIGHT_RATIO).coerceIn(minKnobPx, maxKnobPx)
    val ratio = (KNOB_RATIO_AT_20 + (volume - 0.2f) * KNOB_RATIO_SLOPE).coerceIn(KNOB_RATIO_AT_20 * 0.8f, KNOB_RATIO_AT_20 + 0.7f * KNOB_RATIO_SLOPE)
    val drawnRaw = if (fieldSize == IntSize.Zero) minKnobPx
    else min(fieldSize.width * ratio, fieldSize.height * KNOB_MAX_HEIGHT_RATIO).coerceIn(minKnobPx, maxKnobPx * 1.6f)
    val knobPx = (drawnRaw / 4f).roundToInt() * 4f               // 4px 단위로 맞춰 렌즈 셰이더를 자주 다시 만들지 않는다
    val knobSize = with(density) { knobPx.toDp() }
    val unitScale = with(density) { travelPx.toDp() } / MIN_KNOB   // 버튼과 간격의 배율(노브가 움직이는 범위 기준)
    val textScale = knobSize / MIN_KNOB                          // 퍼센트 글자는 그려진 노브 크기에 맞춘다
    val stepperSize = STEPPER_SIZE * unitScale
    val stepperMargin = STEPPER_MARGIN * unitScale
    // 아래쪽 −/+ 버튼 자리. 노브는 이 자리를 뺀 윗부분에서만 움직여 버튼과 겹치지 않는다.
    val reservedPx = with(density) { (stepperSize + stepperMargin * 2).toPx() }

    /** 노브 위치. 영역 반쪽 크기를 1로 한 정규화 좌표이고, 길이(0..1)가 음량이다. */
    var pos by remember { mutableStateOf(withLength(DEFAULT_DIRECTION, volume)) }
    var lastStep by remember { mutableIntStateOf((volume * STEPS).roundToInt()) }
    val latestOnChange by rememberUpdatedState(onVolumeChange)

    // 버튼이나 음성으로 음량이 바뀌면 방향은 유지하고 거리만 맞춘다.
    LaunchedEffect(volume) {
        if (abs(pos.getDistance() - volume) > 0.005f) pos = withLength(pos, volume)
    }

    /** 노브 왼쪽 위 모서리의 영역 좌표(px) */
    fun knobTopLeft(): Offset {
        val halfW = ((fieldSize.width - travelPx) / 2f).coerceAtLeast(0f)
        val halfH = ((fieldSize.height - reservedPx - travelPx) / 2f).coerceAtLeast(0f)
        return Offset(
            fieldSize.width / 2f + pos.x * halfW - knobPx / 2f,
            (fieldSize.height - reservedPx) / 2f + pos.y * halfH - knobPx / 2f,
        )
    }

    fun moveTo(p: Offset) {
        val halfW = ((fieldSize.width - travelPx) / 2f).coerceAtLeast(1f)
        val halfH = ((fieldSize.height - reservedPx - travelPx) / 2f).coerceAtLeast(1f)
        var n = Offset((p.x - fieldSize.width / 2f) / halfW, (p.y - (fieldSize.height - reservedPx) / 2f) / halfH)
        val len = n.getDistance()
        if (len > 1f) n /= len // 타원 밖으로는 나가지 않는다
        pos = n
        val v = n.getDistance().coerceIn(0f, 1f)
        val step = (v * STEPS).roundToInt()
        if (step != lastStep) {
            lastStep = step
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        latestOnChange(v)
    }

    // 분위기에 따라 하늘과 구름 색이 부드럽게 바뀐다. (sky 개수는 항상 같아야 한다)
    val colorSpec = tween<Color>(durationMillis = 1500, easing = FastOutSlowInEasing)
    val floatSpec = tween<Float>(durationMillis = 1500, easing = FastOutSlowInEasing)
    val sky = mood.sky.mapIndexed { i, c -> animateColorAsState(c, colorSpec, label = "sky$i") }
    val glow = animateColorAsState(mood.glow, colorSpec, label = "glow")
    val lit = animateColorAsState(mood.cloudLit, colorSpec, label = "lit")
    val shade = animateColorAsState(mood.cloudShade, colorSpec, label = "shade")
    val warm = animateColorAsState(mood.cloudWarm, colorSpec, label = "warm")
    val cloudDensity = animateFloatAsState(mood.cloudDensity, floatSpec, label = "density")

    // 구름이 노브에 밀려나는 연출. 스프링으로 천천히 따라온다.
    val pushSpec = spring<Float>(stiffness = Spring.StiffnessVeryLow)
    val pushX = animateFloatAsState(pos.x, pushSpec, label = "pushX")
    val pushY = animateFloatAsState(pos.y, pushSpec, label = "pushY")

    val phase = rememberPhase(PHASE_PERIOD) { if (reducedMotion) DRIFT_REDUCED else 1f }
    // 구름은 아주 느리게 떠다니므로 초당 CLOUD_HZ번만 다시 그린다. 그 사이 프레임은 저장된 결과를 쓴다.
    val cloudTick = remember { derivedStateOf { (phase.doubleValue * CLOUD_HZ).toLong() } }

    val percent = (volume * 100f).roundToInt()
    // 구름 그림을 한 번 기록해 두고, 화면과 노브 안(렌즈)에서 다시 재생한다.
    val lensLayer = rememberGraphicsLayer()
    // 노브는 정사각형이라 크기를 만들 때 한 번만 넣는다(효과를 만든 뒤에 바꾸면 반영되지 않는다)
    val lensShader = remember(knobPx) { createLensShader()?.also { it.setFloatUniform("size", knobPx, knobPx) } }
    val lensEffect = remember(lensShader) { lensShader?.let { android.graphics.RenderEffect.createRuntimeShaderEffect(it, "content").asComposeRenderEffect() } }
    val cloudLayer = rememberGraphicsLayer().also { it.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .onSizeChanged { fieldSize = it }
            .semantics {
                contentDescription = "음량"
                progressBarRangeInfo = ProgressBarRangeInfo(volume, 0f..1f)
                setProgress { target ->
                    latestOnChange(target.coerceIn(0f, 1f))
                    true
                }
            }
            // 누르면 그 자리로 오고, 끌면 따라온다.
            .pointerInput(Unit) { detectTapGestures { moveTo(it) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { moveTo(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        moveTo(change.position)
                    },
                )
            },
    ) {
        // 하늘과 구름
        Canvas(Modifier.fillMaxSize()) {
          cloudLayer.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
            val w = size.width
            val h = size.height
            val t = cloudTick.value / CLOUD_HZ.toFloat()

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
          drawLayer(cloudLayer)
        }

        // 유리 노브
        Box(
            modifier = Modifier
                .offset { knobTopLeft().let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                .size(knobSize)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                val tl = knobTopLeft()
                val glass = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
                val shader = lensShader
                clipPath(glass) {
                    if (shader != null) {
                        // 굴절: 가운데는 확대되고 가장자리로 갈수록 휘어 보이며, 가장자리에서 색이 살짝 갈라진다
                        lensLayer.renderEffect = lensEffect
                        lensLayer.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                            withTransform({ translate(-tl.x, -tl.y) }) { drawLayer(cloudLayer) }
                        }
                        drawLayer(lensLayer)
                    } else {
                        withTransform({
                            translate(-tl.x, -tl.y)
                            scale(LENS_ZOOM, LENS_ZOOM, pivot = Offset(tl.x + c.x, tl.y + c.y))
                        }) { drawLayer(cloudLayer) }
                    }
                    // 글자가 읽히도록 약하게 어둡힌다(대비 4.5:1 기준)
                    drawCircle(Color(0xFF0C1240).copy(alpha = 0.22f))
                    // 두꺼운 유리의 가장자리: 안쪽은 투명하고 바깥으로 갈수록 빛이 모인다
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to Color.Transparent, 0.72f to Color.Transparent,
                            0.93f to Color.White.copy(alpha = 0.14f), 1f to Color.White.copy(alpha = 0.38f),
                            center = c, radius = r,
                        ),
                    )
                    // 면 전체에 걸친 부드러운 반사(왼쪽 위에서 오른쪽 아래로 흐른다)
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent, Color.White.copy(alpha = 0.06f)),
                            start = Offset(size.width * 0.05f, size.height * 0.0f),
                            end = Offset(size.width * 0.95f, size.height * 1.0f),
                        ),
                    )
                }
                // 테두리 빛: 왼쪽 위와 오른쪽 아래만 밝게 번지는 호(둥근 덩어리가 아니라 가장자리를 따라간다)
                val ring = r - 1.dp.toPx()
                val rect = androidx.compose.ui.geometry.Rect(c, ring)
                drawCircle(Color.White.copy(alpha = 0.22f), radius = ring, style = Stroke(width = 1.dp.toPx()))
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent, 0.50f to Color.White.copy(alpha = 0.0f),
                        0.62f to Color.White.copy(alpha = 0.95f), 0.72f to Color.White.copy(alpha = 0.0f), 1f to Color.Transparent,
                        center = c,
                    ),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = rect.topLeft, size = rect.size, style = Stroke(width = 2.2.dp.toPx()),
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.White.copy(alpha = 0.0f), 0.10f to Color.White.copy(alpha = 0.55f), 0.20f to Color.White.copy(alpha = 0.0f), 1f to Color.Transparent,
                        center = c,
                    ),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = rect.topLeft, size = rect.size, style = Stroke(width = 1.6.dp.toPx()),
                )
            }
            Text(
                text = "$percent%",
                color = Color.White,
                fontSize = (34f * textScale).sp,
                fontWeight = FontWeight.Light,
            )
        }

        // 보조 조작: Pleos UI 가이드의 Stepper
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = stepperMargin),
            horizontalArrangement = Arrangement.spacedBy(16.dp * unitScale),
        ) {
            StepButton(label = "−", description = "음량 줄이기", size = stepperSize, unitScale = unitScale) {
                latestOnChange((volume - STEP_SIZE).coerceIn(0f, 1f))
            }
            StepButton(label = "+", description = "음량 높이기", size = stepperSize, unitScale = unitScale) {
                latestOnChange((volume + STEP_SIZE).coerceIn(0f, 1f))
            }
        }
    }
}

private const val LENS_ZOOM = 1.22f

private const val LENS_AGSL = """
uniform shader content;
uniform float2 size;
half4 main(float2 p) {
    float2 c = size * 0.5;
    float R = min(size.x, size.y) * 0.5;
    float2 d = (p - c) / R;
    float r = length(d);
    float edge = smoothstep(0.55, 1.0, r);
    // 가운데는 확대, 가장자리는 원래 위치로 이어져 휘어 보인다
    float k = 1.0 - 0.26 * (1.0 - r * r);
    float ca = 0.035 * edge;
    float2 q = c + d * k * R;
    half4 g = content.eval(q);
    half cr = content.eval(c + d * (k + ca) * R).r;
    half cb = content.eval(c + d * (k - ca) * R).b;
    return half4(cr, g.g, cb, g.a);
}
"""

/** API 33 이상에서만 굴절 셰이더를 쓴다. 그 미만은 null이라 단순 확대로 대체된다. */
private fun createLensShader(): android.graphics.RuntimeShader? =
    if (android.os.Build.VERSION.SDK_INT >= 33) android.graphics.RuntimeShader(LENS_AGSL) else null

/** 노브 지름 범위와 비율. 기본(최소) 크기에서 −/+ 버튼 64dp, 간격 16dp, 글자 34sp/30sp가 나온다. */
private val MIN_KNOB = 112.dp
private val MAX_KNOB = 220.dp
private const val KNOB_WIDTH_RATIO = 0.20f
private const val KNOB_HEIGHT_RATIO = 0.40f

/** 시안의 노브 지름 비율(왼쪽 영역 폭 대비): 값 20%에서 0.12, 90%에서 0.37. 그 사이는 직선으로 잇는다. */
private const val KNOB_RATIO_AT_20 = 0.12f
private const val KNOB_RATIO_SLOPE = 0.357f
private const val KNOB_MAX_HEIGHT_RATIO = 0.42f
private val STEPPER_SIZE = 64.dp
private val STEPPER_MARGIN = 16.dp

@Composable
private fun StepButton(label: String, description: String, size: Dp, unitScale: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.26f), Color(0xFF0C1240).copy(alpha = 0.22f), Color.White.copy(alpha = 0.10f)),
                ),
            )
            .border(
                1.5.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.6f))),
                CircleShape,
            )
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White, fontSize = (30f * unitScale).sp)
    }
}

private fun withLength(direction: Offset, length: Float): Offset {
    val d = direction.getDistance()
    val unit = if (d < 1e-3f) DEFAULT_DIRECTION else direction / d
    return unit * length
}

private const val CLOUD_HZ = 5
private const val STEPS = 20 // 5% 단위
private const val STEP_SIZE = 0.05f
private const val DRIFT_REDUCED = 0.0f
private const val PHASE_PERIOD = 20.0 * PI

/** 시작 방향: 스케치처럼 오른쪽 아래 */
private val DEFAULT_DIRECTION = Offset(0.7071f, 0.7071f)

// ---- 구름 (prototype/cloud.html과 같은 값) ----
/** 광원 방향 (왼쪽 위) */
private const val LIGHT_X = -0.55f
private const val LIGHT_Y = -0.83f

/** 그림자 덩어리의 크기 비율, 광원 반대쪽으로 미는 정도 */
private const val SHADE_SCALE = 0.85f
private const val SHADE_SHIFT = 0.42f

private class Cloud(val x: Float, val y: Float, val size: Float, val depth: Float, val alpha: Float)

private val CLOUDS = listOf(
    Cloud(0.18f, 0.24f, 0.20f, 0.9f, 0.95f),
    Cloud(0.72f, 0.17f, 0.17f, 0.6f, 0.90f),
    Cloud(0.55f, 0.42f, 0.16f, 0.35f, 0.85f),
    Cloud(0.20f, 0.68f, 0.26f, 1.0f, 0.95f),
    Cloud(0.74f, 0.62f, 0.18f, 0.7f, 0.85f),
    Cloud(0.62f, 0.86f, 0.20f, 0.5f, 0.90f),
    Cloud(0.10f, 0.92f, 0.16f, 0.8f, 0.85f),
)

/** 구름마다 (dx, dy, 반지름) 3개씩 이어 붙인 덩어리 배열. 프레임마다 객체를 만들지 않기 위해 FloatArray로 둔다. */
private val CLOUD_PUFFS: List<FloatArray> = CLOUDS.indices.map { makePuffs(it) }

/**
 * 구름 하나를 이루는 작은 덩어리들. 웹 프리뷰와 같은 모양이 나오도록 같은 LCG를 쓴다.
 * (s = s * 1664525 + 1013904223 mod 2^32, 난수 호출 순서도 웹과 같다)
 */
private fun makePuffs(index: Int): FloatArray {
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
