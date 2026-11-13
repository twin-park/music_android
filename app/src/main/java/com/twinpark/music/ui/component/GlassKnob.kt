package com.twinpark.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** 유리 노브: 바닥 그림자, 장면을 굴절해 비추는 유리 면, 퍼센트 글자. */
@Composable
internal fun GlassKnob(
    knobSize: Dp,
    topLeft: () -> Offset,
    percent: Int,
    textScale: Float,
    cloudLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    lensLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    lensShader: android.graphics.RuntimeShader?,
    lensEffect: androidx.compose.ui.graphics.RenderEffect?,
    fieldSize: IntSize,
    lensExtra: (androidx.compose.ui.graphics.drawscope.DrawScope.(Float, Float) -> Unit)?,
) {
    // 유리 노브의 바닥 그림자: 뒤 장면 위에 떠 있는 느낌을 준다
    Box(
        Modifier
            .offset { topLeft().let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
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
            .offset { topLeft().let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
            .size(knobSize)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val tl = topLeft()
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
