package com.twinpark.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.withFrameNanos
import com.twinpark.music.audio.BgmController

/**
 * 피아노 화면(선택 기능, 개발 패널에서 켠다): 건반 위로 연주되는 음이 내려와 닿는다. Figma 피아노 화면처럼 배경 없이 반투명이다.
 *
 * 엔진이 재생 위치보다 조금 앞서 알려 주는 음(`BgmController.drainNotes`)을 받아, 닿는 순간이 소리 나는 순간이 되게 그린다.
 * 모든 음을 61건반(C2..C7) 안으로 옥타브를 접어 보여 준다. 타악기는 엔진 쪽에서 이미 뺀다.
 */
@Composable
fun PianoRoll(tint: Color, modifier: Modifier = Modifier) {
    val notes = remember { ArrayList<Long>() }
    var tick by remember { mutableLongStateOf(0L) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        BgmController.setPianoLead(true)                            // 보이는 동안만 음을 일찍 알려 받는다
        onDispose { BgmController.setPianoLead(false) }
    }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { now ->
            for (v in BgmController.drainNotes()) notes.add(v)
            val played = BgmController.playedFrame()
            notes.removeAll { ((it shr 16) - played) < -SR * KEEP_S }
            tick = now
        }
    }

    Canvas(modifier) {
        @Suppress("UNUSED_EXPRESSION") tick                          // 프레임마다 다시 그리게 한다
        val w = size.width
        val h = size.height
        val keysH = h * 0.28f
        val rollH = h - keysH
        val ww = w / WHITE_COUNT
        val bw = ww * 0.62f
        val bh = keysH * 0.62f
        val played = BgmController.playedFrame()
        val speed = rollH / FALL_S                                    // 초당 내려가는 거리(px)

        fun fold(k: Int): Int { var m = k; while (m < LOW) m += 12; while (m > HIGH) m -= 12; return m }
        fun isBlack(m: Int) = (m % 12) in BLACK
        fun whiteIndex(m: Int): Int { var c = 0; for (i in LOW until m) if (!isBlack(i)) c++; return c }
        fun keyX(m: Int): Float = if (isBlack(m)) whiteIndex(m) * ww - bw / 2f else whiteIndex(m) * ww
        fun keyW(m: Int): Float = if (isBlack(m)) bw else ww

        // 지금 눌린 건반(닿은 지 KEEP 이내)
        val glow = FloatArray(HIGH + 1)
        for (v in notes) {
            val dt = ((v shr 16) - played) / SR
            if (dt <= 0.02 && dt > -GLOW_S) {
                val m = fold((v and 0x7F).toInt())
                glow[m] = maxOf(glow[m], (1f + (dt / GLOW_S).toFloat()).coerceIn(0f, 1f))
            }
        }

        // 건반: 흰 건반 먼저, 검은 건반이 위
        for (m in LOW..HIGH) if (!isBlack(m)) {
            drawRect(Color.White.copy(alpha = 0.50f), Offset(keyX(m) + 0.5f, rollH), Size(keyW(m) - 1f, keysH))
            if (glow[m] > 0f) drawRect(tint.copy(alpha = 0.55f * glow[m]), Offset(keyX(m) + 0.5f, rollH), Size(keyW(m) - 1f, keysH))
        }
        for (m in LOW..HIGH) if (isBlack(m)) {
            drawRect(Color(0xFF10151C).copy(alpha = 0.72f), Offset(keyX(m), rollH), Size(keyW(m), bh))
            if (glow[m] > 0f) drawRect(tint.copy(alpha = 0.7f * glow[m]), Offset(keyX(m), rollH), Size(keyW(m), bh))
        }
        // 건반 위 경계선
        drawLine(tint.copy(alpha = 0.7f), Offset(0f, rollH), Offset(w, rollH), strokeWidth = 2f)

        // 내려오는 음
        for (v in notes) {
            val dt = ((v shr 16) - played) / SR
            val vel = ((v shr 8) and 0x7F).toInt()
            val m = fold((v and 0x7F).toInt())
            val len = speed * (0.14f + 0.20f * vel / 127f)
            val bottom = (rollH - dt.toFloat() * speed)               // 닿는 순간(dt=0) 막대 아래끝이 건반 위에 닿는다
            val top = bottom - len
            if (top >= rollH || bottom <= 0f) continue
            val x = keyX(m) + 1f
            val bwid = keyW(m) - 2f
            val y0 = top.coerceAtLeast(0f)
            val y1 = bottom.coerceAtMost(rollH)
            if (y1 <= y0) continue
            drawRoundRect(tint.copy(alpha = 0.55f), Offset(x, y0), Size(bwid, y1 - y0), CornerRadius(bwid * 0.35f))
            drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(x + bwid * 0.25f, y0), Size(bwid * 0.5f, y1 - y0), CornerRadius(bwid * 0.2f))
        }
    }
}

private const val SR = 48000.0
private const val LOW = 36            // C2
private const val HIGH = 96           // C7
private const val WHITE_COUNT = 36    // C2..C7 사이 흰 건반 수
private val BLACK = setOf(1, 3, 6, 8, 10)
// 화면 맨 위에서 건반까지 내려오는 시간(초). 기본 상태에서는 엔진이 음을 재생 약 0.16초 전에야 알려 주므로(2026-11-12 실측)
// 피아노 화면이 보이는 동안 소리 줄 앞서기를 0.7초로 늘려(BgmController.setPianoLead) 위쪽에서부터 내려오게 한다.
private const val FALL_S = 0.8f
private const val KEEP_S = 0.5        // 닿은 뒤에도 이만큼 보관(건반 불빛과 막대가 사라질 때까지)
private const val GLOW_S = 0.28       // 건반이 빛나는 시간
