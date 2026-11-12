package com.twinpark.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
@Composable
fun WeatherOverlay(
    precipitation: Precipitation,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!enabled || precipitation == Precipitation.None) return
    var timeNs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(precipitation) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { timeNs = it - start }
    }
    val density = LocalDensity.current.density

    Canvas(modifier.fillMaxSize()) {
        val t = timeNs / 1_000_000_000.0
        val w = size.width
        val h = size.height
        when (precipitation) {
            Precipitation.Snow -> drawSnow(t, w, h, density)
            Precipitation.Rain -> drawRain(t, w, h, density)
            Precipitation.None -> Unit
        }
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
private const val RAIN_COUNT = 90

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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRain(t: Double, w: Float, h: Float, density: Float) {
    val slant = 0.18f                                                // 살짝 기울어 떨어진다
    for (i in 0 until RAIN_COUNT) {
        val len = (14f + 16f * hash01(i, 0)) * density
        val speed = (260f + 160f * hash01(i, 1)) * density         // 초당 260~420dp: 비는 눈보다 빠르되 과하지 않게
        val x0 = hash01(i, 2) * (w + 0.2f * h)
        val y = ((hash01(i, 3) * h + t * speed) % (h + len)).toFloat() - len
        val x = x0 - slant * y
        val a = 0.18f + 0.22f * hash01(i, 4)
        drawLine(
            Color(0xFFCFE3FF).copy(alpha = a),
            start = Offset(x, y),
            end = Offset(x - slant * len, y + len),
            strokeWidth = 1.4f * density,
            cap = StrokeCap.Round,
        )
    }
}
