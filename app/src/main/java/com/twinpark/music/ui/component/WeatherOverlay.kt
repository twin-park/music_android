package com.twinpark.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.withFrameNanos
import kotlin.math.sin

/** 배경에 내리는 것. */
enum class Precipitation { None, Rain, Snow }

/**
 * 날씨에 맞는 움직이는 배경: 눈송이가 천천히 내리거나 빗방울이 떨어진다.
 *
 * 디자이너 노트: 자연 배경은 움직였으면 하지만, 너무 자주 바뀌거나 빠르면 운전에 방해된다.
 * 그래서 입자 수와 속도, 투명도를 낮게 두고 눈은 천천히 흔들리며 내린다. 주행 규제(저모션)에서는 [enabled]를 끈다.
 *
 * 시간은 draw 단계에서만 읽어서 매 프레임 recomposition이 일어나지 않는다. 입자 위치는 난수가 아니라 인덱스에서 계산해서
 * 상태를 저장하지 않는다.
 */
/** 눈 입자 시계(나노초). 같은 시계를 여러 곳(장면 위, 흐린 패널 뒤)이 나눠 써서 입자 위치가 같다. active가 아니면 멈춘다. */
@Composable
fun rememberWeatherClock(active: Boolean): androidx.compose.runtime.State<Long> {
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val start = withFrameNanos { it } - clock.longValue
        while (true) withFrameNanos { clock.longValue = it - start }
    }
    return clock
}

/**
 * 날씨에 맞는 움직이는 배경(눈송이). 장면 위, 패널 아래에 얹어 패널 뒤에서는 같이 흐려진다(Figma의 유리 패널).
 * 입자 수와 속도, 투명도를 낮게 두고 저모션(주행 규제)에서는 [enabled]를 끈다. 시간은 draw 단계에서만 읽는다.
 */
@Composable
fun WeatherOverlay(
    precipitation: Precipitation,
    clockNs: () -> Long,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** 비: 흘러내리는 방울이 뒤 장면을 뒤집어 비추도록 장면 배경 사진을 준다(없으면 비는 그리지 않는다). */
    lensBase: androidx.compose.ui.graphics.ImageBitmap? = null,
) {
    if (!enabled || precipitation == Precipitation.None) return
    val density = LocalDensity.current.density
    Canvas(modifier.fillMaxSize()) {
        drawPrecipitation(precipitation, clockNs() / 1_000_000_000.0, size.width, size.height, density, lensBase)
    }
}

/** 크기가 (w x h)인 장면 좌표에서 눈을 그린다. 장면의 일부만 그리는 곳(패널)은 translate로 같은 자리를 맞춘다. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPrecipitation(
    p: Precipitation, t: Double, w: Float, h: Float, density: Float, lensBase: androidx.compose.ui.graphics.ImageBitmap? = null,
) {
    when (p) {
        Precipitation.Snow -> drawSnow(t, w, h, density)
        Precipitation.Rain -> if (lensBase != null) drawRain(t, w, h, density, lensBase)
        Precipitation.None -> Unit
    }
}

/** 인덱스 i, 채널 k 에서 0..1 값을 만드는 해시(상태 없이 같은 값이 나온다). */
private fun hash01(i: Int, k: Int): Float {
    var x = i * 374761393 + k * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777215f
}

private const val SNOW_COUNT = 110
private const val DROP_COUNT = 10

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnow(t: Double, w: Float, h: Float, density: Float) {
    for (i in 0 until SNOW_COUNT) {
        val r = (1.6f + 3.2f * hash01(i, 0)) * density            // 반지름 1.6~4.8dp
        val speed = (14f + 22f * hash01(i, 1)) * density           // 초당 14~36dp: 천천히
        val x0 = hash01(i, 2) * w
        val phase = hash01(i, 3) * 6.2832f
        val sway = (10f + 18f * hash01(i, 4)) * density
        val y = ((hash01(i, 5) * h + t * speed) % (h + 2 * r)).toFloat() - r
        val x = x0 + sway * sin(t * (0.35 + 0.4 * hash01(i, 6)) + phase).toFloat()
        val a = 0.35f + 0.4f * hash01(i, 7)
        drawCircle(Color.White.copy(alpha = a), radius = r, center = Offset(x, y))
    }
}

/**
 * 비: 창문 유리에서 **천천히 흘러내리는 물방울**. 맺힌 방울은 장면 이미지(Figma)가 그리고, 여기서는 몇 개만 흘러내린다.
 * 방울은 뒤 장면 사진을 뒤집어 확대해 비추는 렌즈처럼 그려서(실제 물방울이 하는 굴절) 시안의 물방울과 같은 느낌을 낸다.
 * [base]는 장면 배경 사진이고, 장면과 같은 방식(덮도록 자르고 [SCENE_ZOOM]배)으로 놓아야 방울 속 그림이 뒤와 이어진다.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRain(
    t: Double, w: Float, h: Float, density: Float, base: androidx.compose.ui.graphics.ImageBitmap,
) {
    val scale = maxOf(w / base.width, h / base.height) * SCENE_ZOOM
    val dw = base.width * scale
    val dh = base.height * scale
    val dst = androidx.compose.ui.unit.IntOffset(((w - dw) / 2f).toInt(), ((h - dh) / 2f).toInt())
    val dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt())
    for (i in 0 until DROP_COUNT) {
        val r = (9f + 11f * hash01(i, 0)) * density                    // 반지름 9~20dp
        val x = (0.04f + 0.92f * hash01(i, 1)) * w
        val speed = (9f + 14f * hash01(i, 2)) * density                // 초당 9~23dp: 천천히
        val cycle = h + 4f * r
        val y = ((hash01(i, 3) * cycle + t * speed) % cycle).toFloat() - 2f * r
        val c = Offset(x, y)
        // 지나온 자리에 남는 가는 물자국
        val trail = (60f + 120f * hash01(i, 4)) * density
        drawLine(Color.White.copy(alpha = 0.07f), Offset(x, y - trail), Offset(x, y), strokeWidth = r * 0.42f, cap = StrokeCap.Round)
        // 방울 몸통: 뒤 장면을 180도 돌려 키워 비추는 렌즈
        val circle = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
        clipPath(circle) {
            withTransform({
                scale(-LENS_ZOOM, -LENS_ZOOM, pivot = c)
            }) {
                drawImage(base, dstOffset = dst, dstSize = dstSize, filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
            }
            // 가장자리는 어둡게, 안쪽은 살짝 밝게
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(Color(0x14FFFFFF), Color(0x00000000), Color(0x66000000)),
                    center = c, radius = r,
                ),
                radius = r, center = c,
            )
        }
        // 위쪽 작은 하이라이트와 아래쪽 가장자리 반사
        drawCircle(Color.White.copy(alpha = 0.55f), radius = r * 0.2f, center = Offset(x - r * 0.35f, y - r * 0.4f))
        drawCircle(
            Color.White.copy(alpha = 0.18f), radius = r * 0.82f, center = Offset(x + r * 0.05f, y + r * 0.1f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.12f),
        )
    }
}

/** 장면 배경 사진을 덮도록 자른 뒤 키우는 배율. CloudKnob의 장면과 같아야 한다. */
const val SCENE_ZOOM = 1.06f
private const val LENS_ZOOM = 1.7f
