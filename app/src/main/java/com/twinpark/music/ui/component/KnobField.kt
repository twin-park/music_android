package com.twinpark.music.ui.component

import androidx.compose.ui.geometry.Offset

/**
 * 노브가 움직이는 영역의 기하. 위치는 영역 반쪽 크기를 1로 한 정규화 좌표(x 오른쪽, y 아래)이고 타원 안으로 제한된다.
 * [reserved]는 아래쪽에 비워 두는 여백, [travel]은 노브가 가장자리에서 못 가는 폭(노브 지름 기준)이다.
 */
internal class KnobField(
    private val width: Float,
    private val height: Float,
    private val reserved: Float,
    private val travel: Float,
) {
    private val centerX = width / 2f
    private val centerY = (height - reserved) / 2f

    fun topLeft(pos: Offset, knobPx: Float): Offset {
        val halfW = ((width - travel) / 2f).coerceAtLeast(0f)
        val halfH = ((height - reserved - travel) / 2f).coerceAtLeast(0f)
        return Offset(centerX + pos.x * halfW - knobPx / 2f, centerY + pos.y * halfH - knobPx / 2f)
    }

    fun positionAt(point: Offset): Offset {
        val halfW = ((width - travel) / 2f).coerceAtLeast(1f)
        val halfH = ((height - reserved - travel) / 2f).coerceAtLeast(1f)
        val n = Offset((point.x - centerX) / halfW, (point.y - centerY) / halfH)
        val length = n.getDistance()
        return if (length > 1f) n / length else n
    }
}
