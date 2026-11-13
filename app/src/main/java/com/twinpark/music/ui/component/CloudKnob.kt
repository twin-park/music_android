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
import androidx.compose.ui.res.imageResource
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
import com.twinpark.music.domain.Quadrant
import com.twinpark.music.domain.QuadrantControl
import com.twinpark.music.ui.theme.AppFonts
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
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
 * - 접근성 setProgress(음량 올리기, 내리기)를 지원한다. (+/- 버튼은 노브를 직접 끌어 조절하므로 뺐다)
 *
 * 노브의 크기와 숫자는 노브가 놓인 구획의 값(중심에서의 거리)이고, 구획별 값은 밖([quadrantLevels])에서 기억한다.
 *
 * 구름 표현은 prototype/cloud.html에서 시안과 비교해 맞춘 값이다. 두 곳의 상수와 LCG를 같이 유지한다.
 */
@Composable
fun CloudKnob(
    /** 시작할 때 노브 위치(정규화 좌표). 그 뒤로는 사용자가 노브를 움직인 대로 따라간다. */
    initialPos: Offset,
    mood: Mood,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    /** 장면 모서리 둥글기. 화면 전체에 깔 때는 0. */
    cornerRadius: androidx.compose.ui.unit.Dp = 28.dp,
    /** 오른쪽에서 이만큼은 노브가 가지 않는다(다른 패널이 위에 겹치는 자리). 장면(하늘, 구름)은 이 자리까지 그려진다. */
    endInset: androidx.compose.ui.unit.Dp = 0.dp,
    /** 있으면 하늘과 구름 대신 이 Figma 이미지를 장면으로 그린다. */
    scene: SceneArt? = null,
    /** 장면을 기록할 레이어. 밖에서 주면 다른 곳(오른쪽 패널의 흐림 배경)이 같은 장면을 다시 그릴 수 있다. */
    sceneLayer: androidx.compose.ui.graphics.layer.GraphicsLayer? = null,
    /** 사용자가 노브를 움직일 때마다 정규화 위치(x 오른쪽, y 아래, 길이 0..1)를 알린다. 4분면 조절에 쓴다. */
    onPositionChange: (x: Float, y: Float) -> Unit = { _, _ -> },
    /** 장면 위, 노브 아래에 얹는 날씨(눈, 비). 노브 위에 그리면 노브를 굴절 없이 덮는다. */
    weatherContent: @Composable () -> Unit = {},
    /** 노브 렌즈가 장면과 함께 비추는 것(눈, 비). 장면(필드) 좌표로 필드 크기만큼 그린다. */
    lensExtra: (androidx.compose.ui.graphics.drawscope.DrawScope.(Float, Float) -> Unit)? = null,
    /** 십자 안내선과 4분면 이름을 그린다. */
    quadrantGuides: Boolean = true,
    /** 분면별 현재 값(0..1, [Quadrant] 순서). 이름 옆에 %로 보여 준다. */
    quadrantLevels: FloatArray = QuadrantControl.initialLevels(),
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    val endInsetPx = with(density) { endInset.toPx() }
    val fieldW = (fieldSize.width - endInsetPx).coerceAtLeast(1f)            // 노브가 움직이는 영역의 폭

    /** 노브 위치. 영역 반쪽 크기를 1로 한 정규화 좌표이고, 길이(0..1)가 노브가 놓인 구획의 값이다. */
    var pos by remember { mutableStateOf(initialPos) }
    var lastStep by remember { mutableIntStateOf((initialPos.getDistance() * STEPS).roundToInt()) }
    val latestOnPosition by rememberUpdatedState(onPositionChange)

    // 노브가 움직이는 범위와 버튼 크기는 카드 크기에 비례한다. 노브가 그려지는 지름은 음량 값에 비례한다
    // (Figma 시안: 값 20%에서 90%로 갈수록 노브 지름이 왼쪽 영역 폭의 12%에서 37%로 커진다).
    val minKnobPx = with(density) { MIN_KNOB.toPx() }
    val maxKnobPx = with(density) { MAX_KNOB.toPx() }
    val travelPx = if (fieldSize == IntSize.Zero) minKnobPx
    else min(fieldW * KNOB_WIDTH_RATIO, fieldSize.height * KNOB_HEIGHT_RATIO).coerceIn(minKnobPx, maxKnobPx)
    val knobValue = pos.getDistance().coerceIn(0f, 1f)           // 노브의 크기와 숫자는 같은 값(노브가 놓인 구획의 값)
    val ratio = (KNOB_RATIO_AT_20 + (knobValue - 0.2f) * KNOB_RATIO_SLOPE).coerceIn(KNOB_RATIO_AT_20 * 0.8f, KNOB_RATIO_AT_20 + 0.7f * KNOB_RATIO_SLOPE)
    val drawnRaw = if (fieldSize == IntSize.Zero) minKnobPx
    else min(fieldW * ratio, fieldSize.height * KNOB_MAX_HEIGHT_RATIO).coerceIn(minKnobPx, maxKnobPx * 1.6f)
    val knobPx = (drawnRaw / 4f).roundToInt() * 4f               // 4px 단위로 맞춰 렌즈 셰이더를 자주 다시 만들지 않는다
    val knobSize = with(density) { knobPx.toDp() }
    val unitScale = with(density) { travelPx.toDp() } / MIN_KNOB   // 버튼과 간격의 배율(노브가 움직이는 범위 기준)
    val textScale = knobSize / MIN_KNOB                          // 퍼센트 글자는 그려진 노브 크기에 맞춘다
    // 아래쪽 여백. 노브가 화면 맨 아래 가장자리에 붙지 않게 한다.
    val reservedPx = with(density) { (EDGE_MARGIN * unitScale * 2).toPx() }



    val field = KnobField(fieldW, fieldSize.height.toFloat(), reservedPx, travelPx)

    fun knobTopLeft(): Offset = field.topLeft(pos, knobPx)

    fun moveTo(p: Offset) {
        val n = field.positionAt(p)
        pos = n
        val v = n.getDistance().coerceIn(0f, 1f)
        val step = (v * STEPS).roundToInt()
        if (step != lastStep) {
            lastStep = step
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        latestOnPosition(n.x, n.y)
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

    val percent = (knobValue * 100f).roundToInt()
    // 구름 그림을 한 번 기록해 두고, 화면과 노브 안(렌즈)에서 다시 재생한다.
    val lensLayer = rememberGraphicsLayer()
    // 노브는 정사각형이라 크기를 만들 때 한 번만 넣는다(효과를 만든 뒤에 바꾸면 반영되지 않는다)
    val lensShader = remember(knobPx) { createLensShader()?.also { it.setFloatUniform("size", knobPx, knobPx) } }
    val lensEffect = remember(lensShader) { lensShader?.let { android.graphics.RenderEffect.createRuntimeShaderEffect(it, "content").asComposeRenderEffect() } }
    val ownLayer = rememberGraphicsLayer()
    val cloudLayer = (sceneLayer ?: ownLayer).also { it.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen }
    val sceneBase = scene?.let { androidx.compose.ui.graphics.ImageBitmap.imageResource(it.base) }
    val sceneOverlay = scene?.overlay?.let { androidx.compose.ui.graphics.ImageBitmap.imageResource(it) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .onSizeChanged { fieldSize = it }
            .semantics {
                contentDescription = "노브 값"
                progressBarRangeInfo = ProgressBarRangeInfo(knobValue, 0f..1f)
                setProgress { target ->
                    pos = withLength(pos, target.coerceIn(0f, 1f))
                    latestOnPosition(pos.x, pos.y)
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
            if (sceneBase != null) {
                // Figma 장면 이미지: 화면을 덮도록 자르고(Crop), 아주 천천히 흔들려 움직이는 이미지로 보이게 한다.
                // 물방울 렌즈가 얹히는 장면(비)은 고정해 렌즈가 어긋나지 않게 하고, 나머지는 아주 천천히 좌우로 흘러 구름이 움직이는 느낌을 준다.
                // 약 63초에 한 번 왕복하고, 이동 폭은 화면 폭의 1.8%(확대 SCENE_ZOOM 안쪽이라 가장자리가 비지 않는다).
                val drift = if (sceneOverlay == null && !reducedMotion) sin(t * SCENE_DRIFT_SPEED) * SCENE_DRIFT_AMOUNT * w else 0f
                drawSceneImage(sceneBase, w, h, SCENE_ZOOM, drift, 1f, overlay = sceneOverlay, overlayAlpha = scene.overlayAlpha)
                return@record
            }

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

        weatherContent()

        // 4분면 안내: 옅은 십자선과 분면 이름. 노브가 있는 분면의 이름이 밝아진다.
        if (quadrantGuides && fieldSize != IntSize.Zero) {
            val activeQuadrant = QuadrantControl.quadrantAt(pos.x, pos.y)
            val centerX = fieldW / 2f
            val centerY = (fieldSize.height - reservedPx) / 2f
            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 14f))
            Canvas(Modifier.fillMaxSize()) {
                val lineColor = Color.White.copy(alpha = 0.30f)
                drawLine(lineColor, Offset(centerX, centerY * 0.10f), Offset(centerX, centerY * 1.90f), strokeWidth = 1.5f, pathEffect = dash)
                drawLine(lineColor, Offset(fieldW * 0.04f, centerY), Offset(fieldW * 0.96f, centerY), strokeWidth = 1.5f, pathEffect = dash)
                // 기억된 값 표시: 노브가 다른 구획에 있어도 각 구획의 값이 어디쯤인지 작은 고리로 보인다(노브는 놓인 구획의 값을 직접 보여 준다)
                val halfW = ((fieldW - travelPx) / 2f).coerceAtLeast(0f)
                val halfH = ((fieldSize.height - reservedPx - travelPx) / 2f).coerceAtLeast(0f)
                Quadrant.values().forEachIndexed { i, q ->
                    if (q == activeQuadrant) return@forEachIndexed
                    val sx = if (i % 2 == 0) -1f else 1f
                    val sy = if (i < 2) -1f else 1f
                    val l = quadrantLevels[i] * 0.7071f
                    val marker = Offset(centerX + sx * l * halfW, centerY + sy * l * halfH)
                    drawCircle(Color.White.copy(alpha = 0.55f), radius = 7.dp.toPx() * unitScale, center = marker, style = Stroke(width = 1.6.dp.toPx()))
                    drawCircle(Color.White.copy(alpha = 0.55f), radius = 2.2.dp.toPx() * unitScale, center = marker)
                }
            }
            val labelW = 150.dp * unitScale
            val labelH = 28.dp * unitScale
            val wPx = with(density) { labelW.toPx() }
            val hPx = with(density) { labelH.toPx() }
            Quadrant.values().forEachIndexed { i, q ->
                val sx = if (i % 2 == 0) -1f else 1f
                val sy = if (i < 2) -1f else 1f
                val lx = centerX + sx * fieldW * 0.25f
                val ly = centerY + sy * centerY * 0.55f
                Box(
                    Modifier
                        .offset { IntOffset((lx - wPx / 2f).roundToInt(), (ly - hPx / 2f).roundToInt()) }
                        .size(labelW, labelH),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${q.label} ${(quadrantLevels[i] * 100f).roundToInt()}%",
                        color = Color.White.copy(alpha = if (q == activeQuadrant) 1f else 0.40f),
                        fontSize = (13f * unitScale).sp,
                        letterSpacing = 2.sp,
                        fontFamily = AppFonts.Inter,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // 유리 노브의 바닥 그림자: 뒤 장면 위에 떠 있는 느낌을 준다
        Box(
            Modifier
                .offset { knobTopLeft().let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                .size(knobSize)
                .drawBehind {
                    val rr = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.62f to Color.Black.copy(alpha = 0.14f), 1f to Color.Transparent,
                            center = Offset(size.width / 2f, size.height / 2f + rr * 0.16f), radius = rr * 1.32f,
                        ),
                        radius = rr * 1.32f,
                        center = Offset(size.width / 2f, size.height / 2f + rr * 0.16f),
                    )
                },
        )
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
                            withTransform({ translate(-tl.x, -tl.y) }) {
                                drawLayer(cloudLayer)
                                lensExtra?.invoke(this, fieldSize.width.toFloat(), fieldSize.height.toFloat())
                            }
                        }
                        drawLayer(lensLayer)
                    } else {
                        withTransform({
                            translate(-tl.x, -tl.y)
                            scale(KNOB_LENS_ZOOM, KNOB_LENS_ZOOM, pivot = Offset(tl.x + c.x, tl.y + c.y))
                        }) {
                            drawLayer(cloudLayer)
                            lensExtra?.invoke(this, fieldSize.width.toFloat(), fieldSize.height.toFloat())
                        }
                    }
                    // 글자는 그림자로 읽히게 하고(아래 Text), 면은 맑게 둔다. 아주 옅은 푸른 틴트만 얹는다.
                    drawCircle(Color(0xFF0C1240).copy(alpha = 0.10f))
                    // 왼쪽 위의 넓고 부드러운 빛 번짐
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to Color.White.copy(alpha = 0.30f), 1f to Color.Transparent,
                            center = Offset(size.width * 0.30f, size.height * 0.26f), radius = r * 0.75f,
                        ),
                    )
                    // 오른쪽 아래 안쪽에 되비치는 빛(두꺼운 유리 바닥에 모이는 빛)
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.55f to Color.Transparent, 0.9f to Color.White.copy(alpha = 0.16f), 1f to Color.Transparent,
                            center = Offset(size.width * 0.66f, size.height * 0.70f), radius = r * 0.62f,
                        ),
                    )
                    // 두꺼운 유리의 가장자리: 안쪽은 투명하고 바깥으로 갈수록 빛이 모인다
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to Color.Transparent, 0.72f to Color.Transparent,
                            0.92f to Color.White.copy(alpha = 0.20f), 1f to Color.White.copy(alpha = 0.50f),
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
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x33000000), Offset(0f, 1f), 6f)),
            )
        }

    }
}

/** 노브 지름 범위와 비율. 기본(최소) 크기에서 −/+ 버튼 64dp, 간격 16dp, 글자 34sp/30sp가 나온다. */
private val MIN_KNOB = 112.dp
private val MAX_KNOB = 220.dp
private const val KNOB_WIDTH_RATIO = 0.20f
private const val KNOB_HEIGHT_RATIO = 0.40f

/** 시안의 노브 지름 비율(왼쪽 영역 폭 대비): 값 20%에서 0.12, 90%에서 0.37. 그 사이는 직선으로 잇는다. */
private const val KNOB_RATIO_AT_20 = 0.12f
private const val KNOB_RATIO_SLOPE = 0.357f
private const val KNOB_MAX_HEIGHT_RATIO = 0.42f
private val EDGE_MARGIN = 16.dp

private fun withLength(direction: Offset, length: Float): Offset {
    val d = direction.getDistance()
    val unit = if (d < 1e-3f) DEFAULT_DIRECTION else direction / d
    return unit * length
}

private const val CLOUD_HZ = 5
/** 장면 이미지가 흐르는 속도(rad/s)와 이동 폭(화면 폭 비율). 0.1이면 약 63초에 한 번 왕복한다. */
private const val SCENE_DRIFT_SPEED = 0.1f
private const val SCENE_DRIFT_AMOUNT = 0.018f

private const val STEPS = 20 // 5% 단위
private const val STEP_SIZE = 0.05f
private const val DRIFT_REDUCED = 0.0f
private const val PHASE_PERIOD = 20.0 * PI

/** 시작 방향: 스케치처럼 오른쪽 아래 */
private val DEFAULT_DIRECTION = Offset(0.7071f, 0.7071f)
