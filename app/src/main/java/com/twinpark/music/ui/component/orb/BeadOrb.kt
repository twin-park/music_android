package com.twinpark.music.ui.component.orb

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.twinpark.music.ui.component.rememberPhase
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 구슬 점으로 이루어진 오브. 모양은 분위기에 대응하는 포즈 번호([pose])가 정한다 (Figma 시안 5장, [OrbPoses] 참고).
 *
 * - 모양은 Blender에서 미리 계산한 포즈 데이터([PosePack])를 쓴다. 모든 포즈가 같은 점 번호를 공유하므로
 *   두 포즈 사이는 점 위치를 섞기만 하면 된다.
 * - 포즈마다 순환 키프레임이 있어서, 앱은 키프레임 사이를 보간해 표면이 천천히 흐르게 한다.
 * - 분위기가 바뀌어 포즈가 바뀔 때 가중치가 [TRANSITION_MS] 동안 천천히 바뀌므로 모양이 튀지 않는다.
 * - 느린 신호(시간, 포즈 전환)는 모양에, 빠른 신호([pulse], 박자)는 밝기와 구슬 크기에만 쓴다.
 * - 점을 (깊이, 가장자리) 색 단계로 묶어 단계마다 `drawPoints` 한 번으로 그린다.
 * - [reducedMotion]이면 모양 움직임을 멈추고 밝기 반응만 남긴다 (주행 규제).
 *
 * @param pose 0 until [OrbPoses.COUNT]. 바뀌어도 즉시 반영되지 않고 부드럽게 따라간다.
 * @param pulse 0..1 소리 크기(부드럽게 오르내림). draw 단계에서 읽으므로 recomposition이 일어나지 않는다.
 * @param beat 0..1 박자 신호(음이 시작되는 순간 튀고 0.16초 안팎으로 가라앉음). 구슬 크기, 밝기, 전체 크기가 살짝 튄다.
 */
@Composable
fun BeadOrb(
    pose: Int,
    modifier: Modifier = Modifier,
    speed: Float = 1f,
    reducedMotion: Boolean = false,
    pulse: () -> Float = { 0f },
    beat: () -> Float = { 0f },
) {
    val context = LocalContext.current
    val loaded = produceState<PosePack?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { PosePackLoader.load(context) }
    }

    // 포즈 하나가 1, 나머지는 0인 목표 가중치. 값 자체를 천천히 따라가게 해서 포즈 사이를 섞는다.
    val target = FloatArray(OrbPoses.COUNT) { if (it == pose) 1f else 0f }
    val spec = tween<Float>(durationMillis = TRANSITION_MS, easing = FastOutSlowInEasing)
    val w0 = animateFloatAsState(target[0], spec, label = "w0")
    val w1 = animateFloatAsState(target[1], spec, label = "w1")
    val w2 = animateFloatAsState(target[2], spec, label = "w2")
    val w3 = animateFloatAsState(target[3], spec, label = "w3")
    val w4 = animateFloatAsState(target[4], spec, label = "w4")
    val w5 = animateFloatAsState(target[5], spec, label = "w5")
    val motionScale = animateFloatAsState(
        targetValue = if (reducedMotion) 0f else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "motionScale",
    )

    val pack = loaded.value ?: return
    // 키프레임 위치(0..K). 한 바퀴 돌면 되감아서 정밀도가 떨어지지 않는다.
    // 흐르는 속도와 흔들림은 오브마다 다르다(OrbStyle). 포즈를 섞는 중에는 가중치로 섞은 값을 쓴다.
    fun blended(pick: (OrbStyle) -> Float): Float {
        var sum = 0f
        var acc = 0f
        for (p in 0 until OrbPoses.COUNT) {
            val w = when (p) { 0 -> w0.value; 1 -> w1.value; 2 -> w2.value; 3 -> w3.value; 4 -> w4.value; else -> w5.value }
            sum += w
            acc += w * pick(OrbStyles.ALL[p])
        }
        return if (sum > 1e-4f) acc / sum else 1f
    }
    val flow = rememberPhase(pack.frames.toDouble()) { speed * FLOW_RATE * blended { it.flowRate } * motionScale.value }
    val sway = rememberPhase(SWAY_PERIOD) { speed * blended { it.swayAmount } * motionScale.value }

    val buffers = remember(pack) { OrbBuffers(pack.count) }
    val weights = remember { FloatArray(OrbPoses.COUNT) }
    val paint = remember {
        Paint().apply {
            isFilterBitmap = true
            shader = BitmapShader(beadSprite(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
    }

    Canvas(modifier) {
        val n = pack.count
        val energy = pulse().coerceIn(0f, 1f)
        val hit = beat().coerceIn(0f, 1f)

        val cx = size.width / 2f
        val cy = size.height / 2f
        val unit = min(size.width, size.height) / 2f / EXTENT

        weights[0] = w0.value
        weights[1] = w1.value
        weights[2] = w2.value
        weights[3] = w3.value
        weights[4] = w4.value
        weights[5] = w5.value
        var wsum = 0f
        for (p in weights.indices) wsum += weights[p]
        val invW = if (wsum > 1e-4f) 1f / wsum else 0f
        var beadScale = 0f
        var depthDim = 0f
        var rimFloor = 0f
        var edgeGrow = 0f
        for (p in weights.indices) {
            val st = OrbStyles.ALL[p]
            val wp = weights[p] * invW
            edgeGrow += wp * st.edgeGrow
            beadScale += wp * st.beadScale
            depthDim += wp * st.depthDim
            rimFloor += wp * st.rimFloor
        }
        val beadPx = (BEAD_DIAMETER * beadScale * unit * (1f + 0.25f * energy + 0.22f * hit)).coerceAtLeast(2.2f)

        // 키프레임 두 개와 그 사이 비율
        val fpos = flow.doubleValue.toFloat()
        val k0 = fpos.toInt().coerceIn(0, pack.frames - 1)
        val k1 = (k0 + 1) % pack.frames
        // 키프레임에서 멈칫하지 않도록 직선과 부드러운 곡선을 반반 섞는다
        val lin = fpos - k0
        val a = 0.5f * lin + 0.5f * smooth01(lin)

        val t = sway.doubleValue.toFloat()
        val yaw = sin(t * 0.3f) * 0.16f * motionScale.value
        val breath = 1f + (0.03f * sin(t * 0.7f) + 0.025f * hit) * motionScale.value      // 박자마다 전체가 2.5% 정도 튄다(저모션에서는 없음)
        val cosYaw = cos(yaw)
        val sinYaw = sin(yaw)

        val counts = buffers.counts
        counts.fill(0)
        val sx = buffers.screenX
        val sy = buffers.screenY
        val ids = buffers.bucket

        // 가중치가 있는 포즈만 골라, 반복문 밖에서 키프레임 배열을 한 번만 찾는다
        var na = 0
        for (p in weights.indices) {
            val w = weights[p] * invW
            if (w < 0.004f) continue
            buffers.actW[na] = w
            buffers.posA[na] = pack.pos[p][k0]
            buffers.posB[na] = pack.pos[p][k1]
            buffers.rimA[na] = pack.rim[p][k0]
            buffers.rimB[na] = pack.rim[p][k1]
            buffers.sizeP[na] = pack.size[p]
            na++
        }
        val actW = buffers.actW
        val posA = buffers.posA
        val posB = buffers.posB
        val rimA = buffers.rimA
        val rimB = buffers.rimB
        val sizeP = buffers.sizeP
        val sizeMul = buffers.sizeMul

        for (i in 0 until n) {
            var sm = 0f
            for (m in 0 until na) sm += actW[m] * sizeP[m]!![i]
            sizeMul[i] = sm
            // 크기가 거의 0인 점은 이 오브에 없는 구슬(개수가 모자라 채운 칸)이라 위치 계산도, 그리기도 건너뛴다
            if (sm < MIN_BEAD_SIZE) { ids[i] = -1; continue }
            var x = 0f
            var y = 0f
            var z = 0f
            var r = 0f
            val j = i * 3
            for (m in 0 until na) {
                val w = actW[m]
                val pa = posA[m]!!
                val pb = posB[m]!!
                x += w * (pa[j] + (pb[j] - pa[j]) * a)
                y += w * (pa[j + 1] + (pb[j + 1] - pa[j + 1]) * a)
                z += w * (pa[j + 2] + (pb[j + 2] - pa[j + 2]) * a)
                val ra = rimA[m]!!
                val rb = rimB[m]!!
                r += w * (ra[i] + (rb[i] - ra[i]) * a)
            }
            // 화면 세로축 기준으로 천천히 흔들기
            val rx = x * cosYaw - y * sinYaw
            val ry = x * sinYaw + y * cosYaw
            sx[i] = cx + rx * unit * breath
            sy[i] = cy - z * unit * breath

            val rbin = RIM_BIN[(r.coerceIn(0f, 1f) * (RIM_TABLE - 1)).toInt()]
            val depth = ((ry + DEPTH_HALF) / (2f * DEPTH_HALF)).coerceIn(0f, 0.9999f)
            // 먼 쪽이 먼저 그려지도록 깊이 순서를 뒤집어 센다 (0번 칸이 가장 먼 쪽)
            val id = (DEPTH_LEVELS - 1 - (depth * DEPTH_LEVELS).toInt()) * RIM_LEVELS + rbin
            ids[i] = id
            counts[id + 1]++
        }
        for (b in 0 until BUCKETS) counts[b + 1] += counts[b]
        val valid = counts[BUCKETS]                   // 실제로 그릴 구슬 수
        val cursor = buffers.cursor
        System.arraycopy(counts, 0, cursor, 0, BUCKETS)

        // 포즈별 색 세 개를 가중치로 섞는다
        var br = 0f; var bg = 0f; var bb = 0f
        var mr = 0f; var mg = 0f; var mb = 0f
        var pr = 0f; var pg = 0f; var pb2 = 0f
        for (p in weights.indices) {
            val w = weights[p] * invW
            if (w <= 0f) continue
            val (cb, cm, cp) = OrbStyles.ALL[p].colors
            br += w * cb.red; bg += w * cb.green; bb += w * cb.blue
            mr += w * cm.red; mg += w * cm.green; mb += w * cm.blue
            pr += w * cp.red; pg += w * cp.green; pb2 += w * cp.blue
        }
        val cBase = Color(br, bg, bb)
        val cMid = Color(mr, mg, mb)
        val cPeak = Color(pr, pg, pb2)
        val bright = 1.25f * (1f + 0.3f * energy + 0.3f * hit)

        // 색 단계(깊이 x 가장자리)별 ARGB 표와 구슬 크기 표. 칸 번호는 위의 id와 같다(0번 깊이 칸이 가장 먼 쪽).
        val table = buffers.colorTable
        val halfTable = buffers.halfTable
        for (pd in 0 until DEPTH_LEVELS) {
            val db = DEPTH_LEVELS - 1 - pd
            val dimFactor = (1f - depthDim * (db + 0.5f) / DEPTH_LEVELS).coerceAtLeast(0.15f)
            for (rb in 0 until RIM_LEVELS) {
                val rr = (rb + 0.5f) / RIM_LEVELS
                val ramp = if (rr < 0.5f) lerp(cBase, cMid, rr * 2f) else lerp(cMid, cPeak, (rr - 0.5f) * 2f)
                val f = dimFactor * (rimFloor + (1f - rimFloor) * min(1f, rr * 1.8f)) * bright
                table[pd * RIM_LEVELS + rb] = Color(
                    red = (ramp.red * f).coerceIn(0f, 1f),
                    green = (ramp.green * f).coerceIn(0f, 1f),
                    blue = (ramp.blue * f).coerceIn(0f, 1f),
                ).toArgb()
                // 옆으로 선 면(가장자리 값이 큰 곳)은 점이 겹쳐 가늘어지므로 구슬을 키워 이어 보이게 한다
                val edge = max(0f, ((rb + 0.5f) / RIM_LEVELS - 0.5f) * 2f)
                halfTable[pd * RIM_LEVELS + rb] = beadPx * (1f + edgeGrow * edge * edge) / 2f
            }
        }

        // 구슬 하나를 네모 판(정점 4개) 하나로 만들어, 먼 쪽부터의 순서 위치에 바로 쓴다. 스프라이트가 둥근 모양을 입힌다.
        // 정점 인덱스가 Short라서 CHUNK개씩 묶음 배열로 나눈다. 오프셋을 쓰면 두 번째 묶음부터 그려지지 않으므로
        // 묶음마다 별도 배열을 앞(0)부터 채우고 바로 그린다.
        val vertChunks = buffers.vertChunks
        val colorChunks = buffers.colorChunks
        for (i in 0 until n) {
            val id = ids[i]
            if (id < 0) continue
            val c = cursor[id]++
            val ch = c / CHUNK
            val l = c - ch * CHUNK
            val verts = vertChunks[ch]
            val half = halfTable[id] * sizeMul[i]
            val x = sx[i]
            val y = sy[i]
            val v = l * 8
            verts[v] = x - half; verts[v + 1] = y - half
            verts[v + 2] = x + half; verts[v + 3] = y - half
            verts[v + 4] = x + half; verts[v + 5] = y + half
            verts[v + 6] = x - half; verts[v + 7] = y + half
            val argb = table[id]
            val colors = colorChunks[ch]
            val ci = l * 4
            colors[ci] = argb; colors[ci + 1] = argb; colors[ci + 2] = argb; colors[ci + 3] = argb
        }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            for (ch in vertChunks.indices) {
                val quads = min(CHUNK, valid - ch * CHUNK)
                if (quads <= 0) break
                drawBeads(native, buffers, ch, quads, paint)
            }
        }
    }
}

private fun drawBeads(native: android.graphics.Canvas, buffers: OrbBuffers, chunk: Int, quads: Int, paint: Paint) {
    @Suppress("DEPRECATION")
    native.drawVertices(
        android.graphics.Canvas.VertexMode.TRIANGLES, quads * 8, // 정점 수가 아니라 verts 배열의 값 개수(x, y)
        buffers.vertChunks[chunk], 0, buffers.texCoords, 0, buffers.colorChunks[chunk], 0,
        buffers.indices, 0, quads * 6, paint,
    )
}

private fun smooth01(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private const val RIM_LEVELS = 32
private const val DEPTH_LEVELS = 5
private const val BUCKETS = RIM_LEVELS * DEPTH_LEVELS
private const val RIM_TABLE = 256

/** 화면 반폭에 해당하는 모델 단위. 포즈 데이터는 반지름 약 1.3 안에 들어오도록 만들어져 있다. */
private const val EXTENT = 1.40f
private const val DEPTH_HALF = 1.45f

/**
 * 구슬 지름(모델 단위). 시안은 지름을 가로질러 구슬이 약 55개이고 구슬 지름이 점 간격의 약 47%라서,
 * 점이 약 1.15만 개인 구(점 간격 약 0.0355)에서 0.0165이고, 시안 오브는 폭 대비 구슬이 더 커서(점 간격의 약 55%) 0.0195다.
 */
private const val BEAD_DIAMETER = 0.0195f

/** 이보다 작은 구슬 크기 배율은 "없는 구슬"로 본다(오브마다 구슬 수가 달라 모자라는 칸을 크기 0으로 채운다). */
private const val MIN_BEAD_SIZE = 0.02f

/** 초당 키프레임 이동량의 공통 배율. 오브마다의 속도는 [OrbStyle.flowRate]가 정한다(구 0.5, 판형 0.7~0.9). 키프레임이 8개라 0.5이면 한 바퀴 약 16초, 0.9이면 약 9초. */
private const val FLOW_RATE = 1.0f

/** 포즈가 바뀔 때 가중치가 따라가는 시간. */
private const val TRANSITION_MS = 1600

/** 흔들림 사인 주파수(0.3)가 20π마다 정확히 한 바퀴를 돈다. */
private const val SWAY_PERIOD = 20.0 * Math.PI

/** 가장자리 값(0..1)을 색 단계로 바꾸는 표. 시안 대비를 위해 1.3제곱으로 휘어 놓는다. */
private val RIM_BIN = IntArray(RIM_TABLE) { i ->
    val v = i / (RIM_TABLE - 1f)
    min(RIM_LEVELS - 1, (v.toDouble().pow(1.3) * RIM_LEVELS).toInt())
}

private const val CHUNK = 15000
private const val SPRITE_SIZE = 24

/** 스프라이트 가장자리 번짐 폭(반지름 대비). 클수록 부드럽고 작은 구슬이 매끈하며, 작을수록 선명하고 크게 키워도 흐려지지 않는다. */
private const val SPRITE_EDGE = 0.18f

/** 구슬 음영의 가장 어두운 곳 밝기(0..1). 낮출수록 입체감이 커지고 전체가 어두워진다. */
private const val BEAD_SHADE_BASE = 0.84f

private class OrbBuffers(n: Int) {
    val screenX = FloatArray(n)
    val screenY = FloatArray(n)
    val bucket = IntArray(n)
    val counts = IntArray(BUCKETS + 1)
    val cursor = IntArray(BUCKETS)
    val colorTable = IntArray(BUCKETS)
    val halfTable = FloatArray(BUCKETS)
    private val chunkCount = (n + CHUNK - 1) / CHUNK
    val vertChunks = Array(chunkCount) { FloatArray(CHUNK * 8) }
    val colorChunks = Array(chunkCount) { IntArray(CHUNK * 4) }

    // 가중치가 있는 포즈(최대 포즈 수)의 키프레임 배열 참조
    val actW = FloatArray(OrbPoses.COUNT)
    val posA = arrayOfNulls<FloatArray>(OrbPoses.COUNT)
    val posB = arrayOfNulls<FloatArray>(OrbPoses.COUNT)
    val rimA = arrayOfNulls<FloatArray>(OrbPoses.COUNT)
    val rimB = arrayOfNulls<FloatArray>(OrbPoses.COUNT)
    val sizeP = arrayOfNulls<FloatArray>(OrbPoses.COUNT)
    val sizeMul = FloatArray(n)

    /** 구슬 하나(정점 4개)의 스프라이트 좌표. 모든 구슬이 같아서 한 번만 채운다. */
    val texCoords = FloatArray(CHUNK * 8).also {
        val e = SPRITE_SIZE.toFloat()
        for (i in 0 until CHUNK) {
            val o = i * 8
            it[o] = 0f; it[o + 1] = 0f
            it[o + 2] = e; it[o + 3] = 0f
            it[o + 4] = e; it[o + 5] = e
            it[o + 6] = 0f; it[o + 7] = e
        }
    }
    val indices = ShortArray(CHUNK * 6).also {
        for (i in 0 until CHUNK) {
            val v = (i * 4).toShort()
            val o = i * 6
            it[o] = v; it[o + 1] = (v + 1).toShort(); it[o + 2] = (v + 2).toShort()
            it[o + 3] = v; it[o + 4] = (v + 2).toShort(); it[o + 5] = (v + 3).toShort()
        }
    }
}

/**
 * 하얀 원 모양 이미지. 구슬은 화면에서 3~6픽셀이라 이미지도 작게 만들고, 가장자리를 부드럽게 줄여
 * 줄여 그려도 네모로 보이지 않게 한다. 색은 정점 색이 입힌다.
 */
private fun beadSprite(): Bitmap {
    val size = SPRITE_SIZE
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = size / 2f
    for (y in 0 until size) {
        for (x in 0 until size) {
            val d = kotlin.math.hypot(x + 0.5f - c, y + 0.5f - c) / c // 0 중심, 1 가장자리
            val t = ((1f - d) / SPRITE_EDGE).coerceIn(0f, 1f)
            val a = t * t * (3f - 2f * t)
            // 구체 음영: 왼쪽 위에서 빛이 오고(diffuse), 작은 하이라이트(spec)가 얹힌다. 색은 정점 색이 곱해진다.
            val nx = (x + 0.5f - c) / c
            val ny = (y + 0.5f - c) / c
            val nz = kotlin.math.sqrt((1f - nx * nx - ny * ny).coerceAtLeast(0f))
            val diff = (-0.42f * nx - 0.52f * ny + 0.74f * nz).coerceAtLeast(0f)
            val hx = -0.21f * 1f; val hy = -0.26f; val hz = 0.87f              // 반사 벡터(빛+보는 방향 절반)
            val spec = Math.pow((nx * hx + ny * hy + nz * hz).coerceAtLeast(0f).toDouble(), 28.0).toFloat()
            val g = (BEAD_SHADE_BASE + (1f - BEAD_SHADE_BASE) * diff + 0.30f * spec).coerceIn(0f, 1f)
            val gi = (g * 255f).toInt()
            bmp.setPixel(x, y, android.graphics.Color.argb((a * 255f).toInt(), gi, gi, gi))
        }
    }
    return bmp
}
