package com.twinpark.music.ui.component.orb

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 오브 포즈 묶음. 포즈 P개가 같은 점 번호(N개)를 공유하고, 포즈마다 순환 키프레임 K개를 가진다.
 *
 * - [pos]\[포즈\]\[키프레임\]: 점 위치 (x, y, z 반복, 길이 3N). y가 화면 깊이이고 +y가 먼 쪽이다.
 * - [rim]\[포즈\]\[키프레임\]: 가장자리 값 (길이 N, 0..1). 능선과 옆면이 1에 가깝다.
 *
 * 포즈 순서는 [com.twinpark.music.domain.Mood.orbPose]와 같다.
 */
class PosePack(
    val count: Int,
    val frames: Int,
    val poses: Int,
    val pos: Array<Array<FloatArray>>,
    val rim: Array<Array<FloatArray>>,
    /** [포즈][점]: 점마다 구슬 크기 배율(1이 기본). 시안에서 구슬 크기를 읽어 만든 포즈만 1이 아니다. */
    val size: Array<FloatArray>,
)

/**
 * `assets/orb_poses.bin`을 읽는다. 형식(little endian):
 * Int N, Int K, Int P, 이어서 포즈 p, 키프레임 k 순서로 (위치 3N Float, rim N Float), 선택으로 끝에 포즈별 점 크기 배율 P x N Float.
 * Blender에서 만들고 내보내는 방법은 docs/blender-mcp-setup.md의 포즈 내보내기 절을 본다.
 */
object PosePackLoader {
    fun load(context: Context, assetName: String = "orb_poses.bin"): PosePack {
        val bytes = context.assets.open(assetName).use { it.readBytes() }
        val head = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val n = head.int
        val k = head.int
        val p = head.int
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        floats.position(3)
        val pos = Array(p) { Array(k) { FloatArray(3 * n) } }
        val rim = Array(p) { Array(k) { FloatArray(n) } }
        for (pi in 0 until p) {
            for (ki in 0 until k) {
                floats.get(pos[pi][ki])
                floats.get(rim[pi][ki])
            }
        }
        // 파일 끝에 점마다 크기 배율(P x N Float)이 더 있으면 읽는다. 없으면 모두 1.
        val size = Array(p) { FloatArray(n) { 1f } }
        if (floats.remaining() >= p * n) for (pi in 0 until p) floats.get(size[pi])
        return PosePack(n, k, p, pos, rim, size)
    }
}
