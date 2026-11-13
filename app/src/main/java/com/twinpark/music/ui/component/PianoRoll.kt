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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.withFrameNanos
import com.twinpark.music.audio.BgmController

/**
 * 피아노 화면(선택 기능, 개발 패널에서 켠다): 건반 위로 연주되는 음이 내려와 닿는다. Figma 피아노 화면(18:484)처럼 둥근 막대에 후광과 긴 잔상(빛줄기)이 있고,
 * 닿는 곳에 빛 번짐과 불꽃이 튀며, 눌린 건반이 아래로 번지며 빛나고 위로 빛기둥이 솟는다. 색은 장면(오브)의 능선 색을 따른다.
 *
 * 엔진이 재생 위치보다 조금 앞서 알려 주는 음(`BgmController.drainNotes`)을 받아, 닿는 순간이 소리 나는 순간이 되게 그린다.
 * 음은 화면 위 바깥에서 출발해 안으로 들어오며(낙하 시간 [FALL_S]가 엔진 앞서기 0.7초보다 짧다) 들어오는 동안 서서히 나타난다.
 * 모든 음을 61건반(C2..C7) 안으로 옥타브를 접어 보여 준다. 타악기는 엔진 쪽에서 이미 뺀다.
 * 불꽃은 따로 상태를 두지 않고 음의 시작 프레임으로 만든 값(해시)과 경과 시간만으로 그린다.
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
        val u = (w / 520f).coerceIn(0.8f, 2.6f)                       // 패널 폭에 비례하는 두께 단위
        val keysH = h * 0.30f
        val rollH = h - keysH
        val ww = w / WHITE_COUNT
        val bw = ww * 0.62f
        val bh = keysH * 0.62f
        val played = BgmController.playedFrame()
        val speed = rollH / FALL_S                                    // 초당 내려가는 거리(px). 화면 안을 FALL_S 초에 지난다
        val glowColor = lerp(tint, Color.White, 0.35f)                // 막대 머리와 불빛은 능선 색보다 밝게

        fun fold(k: Int): Int { var m = k; while (m < LOW) m += 12; while (m > HIGH) m -= 12; return m }
        fun isBlack(m: Int) = (m % 12) in BLACK
        fun whiteIndex(m: Int): Int { var c = 0; for (i in LOW until m) if (!isBlack(i)) c++; return c }
        fun keyX(m: Int): Float = if (isBlack(m)) whiteIndex(m) * ww - bw / 2f else whiteIndex(m) * ww
        fun keyW(m: Int): Float = if (isBlack(m)) bw else ww

        // 지금 눌린 건반(닿은 지 GLOW_S 이내): 세기 0..1
        val glow = FloatArray(HIGH + 1)
        for (v in notes) {
            val dt = ((v shr 16) - played) / SR
            if (dt <= 0.02 && dt > -GLOW_S) {
                val m = fold((v and 0x7F).toInt())
                glow[m] = maxOf(glow[m], (1f + (dt / GLOW_S).toFloat()).coerceIn(0f, 1f))
            }
        }

        // 건반 쪽으로 갈수록 어두워지는 바탕: 밝은 장면 위에서도 빛나는 막대가 또렷하게 읽히게 한다
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xFF020806).copy(alpha = 0.45f), Color(0xFF020806).copy(alpha = 0.72f)),   // 위는 투명: 위 바깥에서 내려오는 느낌
                startY = 0f, endY = rollH,
            ),
            Offset.Zero, Size(w, rollH),
        )

        // 눌린 건반 위로 솟는 빛기둥(음 막대 뒤에 깔린다)
        val colH = rollH * 0.45f
        for (m in LOW..HIGH) {
            val g = glow[m]
            if (g <= 0f) continue
            val kw = keyW(m)
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, glowColor.copy(alpha = 0.50f * g)), startY = rollH - colH, endY = rollH),
                Offset(keyX(m) + kw * 0.05f, rollH - colH), Size(kw * 0.90f, colH),
            )
        }

        // 내려오는 음: 잔상(긴 빛줄기) > 후광 > 막대(머리가 밝은 그라데이션) > 가운데 하이라이트
        for (v in notes) {
            val dt = ((v shr 16) - played) / SR
            val vel = ((v shr 8) and 0x7F).toInt()
            val m = fold((v and 0x7F).toInt())
            val len = speed * (0.14f + 0.26f * vel / 127f)
            val bottom = (rollH - dt.toFloat() * speed)               // 닿는 순간(dt=0) 막대 아래끝이 건반 위에 닿는다. 아직 멀면 화면 위 바깥(음수)
            val top = bottom - len
            if (top >= rollH || bottom <= 0f) continue
            val kw = keyW(m)
            val bwid = kw * 0.78f
            val x = keyX(m) + (kw - bwid) / 2f
            val y0 = top.coerceAtLeast(0f)
            val y1 = bottom.coerceAtMost(rollH)
            if (y1 <= y0) continue
            val cx = x + bwid / 2f
            val enter = (y1 / (rollH * ENTER_FADE)).coerceIn(0f, 1f)      // 위 가장자리를 넘어 들어오는 동안 서서히 나타난다

            // 잔상: 막대 위쪽으로 길게 사라지는 빛. 넓고 옅은 층 + 가늘고 진한 심
            val trailLen = rollH * (0.32f + 0.26f * vel / 127f)
            val trailTop = top - trailLen
            val tt = trailTop.coerceAtLeast(0f)
            if (top > tt) {
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, tint.copy(alpha = 0.30f * enter)), startY = trailTop, endY = top),
                    Offset(x - bwid * 0.10f, tt), Size(bwid * 1.20f, top - tt),
                )
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, glowColor.copy(alpha = 0.70f * enter)), startY = trailTop, endY = top),
                    Offset(cx - bwid * 0.17f, tt), Size(bwid * 0.34f, top - tt),
                )
            }
            // 후광
            val pad = bwid * 0.36f
            drawRoundRect(tint.copy(alpha = 0.26f * enter), Offset(x - pad, y0 - pad), Size(bwid + 2 * pad, (y1 - y0) + 2 * pad), CornerRadius(bwid * 0.9f))
            // 막대: 위는 능선 색, 건반에 가까운 아래는 밝게
            drawRoundRect(
                Brush.verticalGradient(listOf(tint.copy(alpha = 0.70f * enter), glowColor.copy(alpha = 1.0f * enter)), startY = y0, endY = y1),
                Offset(x, y0), Size(bwid, y1 - y0), CornerRadius(bwid * 0.42f),
            )
            drawRoundRect(Color.White.copy(alpha = 0.45f * enter), Offset(x + bwid * 0.30f, y0 + bwid * 0.2f), Size(bwid * 0.40f, (y1 - y0 - bwid * 0.4f).coerceAtLeast(0f)), CornerRadius(bwid * 0.2f))
        }

        // 건반 바탕: 건반 사이 틈이 어둡게 보이게 먼저 깐다
        drawRect(Color(0xFF050807).copy(alpha = 0.96f), Offset(0f, rollH), Size(w, keysH))

        // 흰 건반(상아색), 눌리면 위에서 아래로 번지는 빛
        for (m in LOW..HIGH) if (!isBlack(m)) {
            val kx = keyX(m) + 0.9f * u
            val kwid = keyW(m) - 1.8f * u
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFFF6F2E6), Color(0xFFD0CAB3)), startY = rollH, endY = h),
                Offset(kx, rollH), Size(kwid, keysH),
            )
            val g = glow[m]
            if (g > 0f) drawRect(
                Brush.verticalGradient(listOf(glowColor.copy(alpha = 1.0f * g), tint.copy(alpha = 0.50f * g)), startY = rollH, endY = h),
                Offset(kx, rollH), Size(kwid, keysH),
            )
        }
        // 검은 건반(광택), 눌리면 같은 색으로 빛난다
        for (m in LOW..HIGH) if (isBlack(m)) {
            val kx = keyX(m)
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFF2A302D), Color(0xFF030504)), startY = rollH, endY = rollH + bh),
                Offset(kx, rollH), Size(keyW(m), bh),
            )
            drawRect(Color.White.copy(alpha = 0.16f), Offset(kx + keyW(m) * 0.16f, rollH), Size(keyW(m) * 0.14f, bh * 0.92f))
            val g = glow[m]
            if (g > 0f) drawRect(
                Brush.verticalGradient(listOf(glowColor.copy(alpha = 0.95f * g), tint.copy(alpha = 0.45f * g)), startY = rollH, endY = rollH + bh),
                Offset(kx, rollH), Size(keyW(m), bh),
            )
        }

        // 건반 위 경계선: 밝은 선과 그 위로 번지는 빛
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, tint.copy(alpha = 0.55f)), startY = rollH - 24f * u, endY = rollH),
            Offset(0f, rollH - 24f * u), Size(w, 24f * u),
        )
        drawLine(glowColor, Offset(0f, rollH), Offset(w, rollH), strokeWidth = 3f * u)

        // 닿는 곳의 빛 번짐과 불꽃
        for (v in notes) {
            val dt = ((v shr 16) - played) / SR
            if (dt > 0.02 || dt <= -GLOW_S) continue
            val g = (1f + (dt / GLOW_S).toFloat()).coerceIn(0f, 1f)        // 닿은 직후 1, 점점 0
            val age = 1f - g
            val m = fold((v and 0x7F).toInt())
            val cx = keyX(m) + keyW(m) / 2f
            val rad = ww * 3.4f * (0.6f + 0.4f * g)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 1.0f * g), glowColor.copy(alpha = 0.65f * g), Color.Transparent),
                    center = Offset(cx, rollH), radius = rad,
                ),
                radius = rad, center = Offset(cx, rollH),
            )
            val seed = (v shr 16)
            for (i in 0 until SPARKS) {
                val a = hash(seed, i * 2)                                   // 좌우로 퍼지는 정도 0..1
                val b = hash(seed, i * 2 + 1)                               // 올라가는 속도 0..1
                val sx = cx + (a * 2f - 1f) * ww * 2.4f * (0.3f + age)
                val sy = rollH - (0.4f + 0.6f * b) * rollH * 0.24f * age - 2f * u
                drawCircle(glowColor.copy(alpha = 0.95f * g * g), radius = (1.1f + 1.8f * b) * u, center = Offset(sx, sy))
            }
        }
    }
}

/** 시작 프레임과 번호로 0..1 값을 만든다(상태 없이 같은 음에 같은 불꽃이 나오게). */
private fun hash(seed: Long, i: Int): Float {
    var x = seed * 2654435761L + i * 40503L + 0x9E3779B9L
    x = x xor (x ushr 15); x *= 2246822519L; x = x xor (x ushr 13); x *= 3266489917L; x = x xor (x ushr 16)
    return ((x and 0xFFFFL).toFloat() / 65535f)
}

private const val SR = 48000.0
private const val LOW = 36            // C2
private const val HIGH = 96           // C7
private const val WHITE_COUNT = 36    // C2..C7 사이 흰 건반 수
private val BLACK = setOf(1, 3, 6, 8, 10)
private const val SPARKS = 7          // 음 하나가 닿을 때 튀는 불꽃 수
// 화면 위에서 건반까지 내려오는 시간(초). 엔진이 피아노 화면에서는 음을 0.7초 앞서 알려 주므로(BgmController.setPianoLead), 이 값을 그보다 짧게 잡아
// 음이 화면 위 바깥에서 출발해 안으로 들어오게 한다(0.55초면 알려진 순간 화면 높이의 약 27% 위 바깥에 있다).
private const val FALL_S = 0.55f
private const val ENTER_FADE = 0.12f  // 위 가장자리에서 화면 높이의 이만큼 들어오는 동안 서서히 나타난다
private const val KEEP_S = 0.5        // 닿은 뒤에도 이만큼 보관(건반 불빛과 막대가 사라질 때까지)
private const val GLOW_S = 0.28       // 건반이 빛나는 시간
