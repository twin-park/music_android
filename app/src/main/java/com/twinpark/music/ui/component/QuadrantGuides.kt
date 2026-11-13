package com.twinpark.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import com.twinpark.music.domain.Quadrant
import com.twinpark.music.domain.QuadrantControl
import com.twinpark.music.ui.theme.AppFonts
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** 4분면 안내: 옅은 십자선과 분면 이름. 노브가 있는 분면의 이름이 밝아진다. */
@Composable
internal fun QuadrantGuides(
    fieldW: Float,
    fieldHeight: Float,
    reservedPx: Float,
    travelPx: Float,
    unitScale: Float,
    pos: Offset,
    quadrantLevels: FloatArray,
) {
    val density = LocalDensity.current
    val activeQuadrant = QuadrantControl.quadrantAt(pos.x, pos.y)
    val centerX = fieldW / 2f
    val centerY = (fieldHeight - reservedPx) / 2f
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 14f))
    Canvas(Modifier.fillMaxSize()) {
        val lineColor = Color.White.copy(alpha = 0.30f)
        drawLine(lineColor, Offset(centerX, centerY * 0.10f), Offset(centerX, centerY * 1.90f), strokeWidth = 1.5f, pathEffect = dash)
        drawLine(lineColor, Offset(fieldW * 0.04f, centerY), Offset(fieldW * 0.96f, centerY), strokeWidth = 1.5f, pathEffect = dash)
        // 기억된 값 표시: 노브가 다른 구획에 있어도 각 구획의 값이 어디쯤인지 작은 고리로 보인다(노브는 놓인 구획의 값을 직접 보여 준다)
        val halfW = ((fieldW - travelPx) / 2f).coerceAtLeast(0f)
        val halfH = ((fieldHeight - reservedPx - travelPx) / 2f).coerceAtLeast(0f)
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
