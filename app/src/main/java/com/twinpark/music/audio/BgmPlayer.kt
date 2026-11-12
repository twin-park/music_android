package com.twinpark.music.audio

import android.content.Context
import android.util.Log
import com.twinpark.music.domain.EngineParams
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 배경음악 엔진의 상태. */
sealed interface BgmState {
    data object Idle : BgmState
    data object Loading : BgmState

    /** 재생 준비가 끝났다. 분위기를 요청하면 바로 연주를 시작한다. */
    data object Ready : BgmState

    /** 시작하지 못했다. [reason]은 사람이 읽을 이유. */
    data class Failed(val reason: String) : BgmState
}

/** 지금 연주 상태(엔진이 알려 주는 값). */
data class BgmStatus(val moodName: String, val role: String, val key: String, val bpm: Int, val underruns: Int, val playing: Boolean)

/**
 * 차량 BGM 엔진을 앱에서 쓰는 입구. 엔진은 앱 하나에 하나만 있다.
 *
 * 엔진 데이터(AI 모델, 사운드폰트, 피아노 샘플 약 120MB)는 APK 에셋 `bgm/`에 들어 있다. 엔진이 파일 경로로 읽으므로
 * 첫 실행 때 앱 전용 저장소(`filesDir/bgm`)로 한 번 복사하고, 앱이 업데이트되면 다시 복사한다.
 * 에셋이 없는 빌드(`ai/engine/data`가 없던 때)는 개발용으로 `adb push`한 외부 저장소 폴더를 쓴다(docs/bgm-engine.md 참고).
 */
class BgmPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val internalDir = File(appContext.filesDir, DATA_DIR_NAME)
    private val externalDir = File(appContext.getExternalFilesDir(null), DATA_DIR_NAME)

    /** 엔진이 읽는 데이터 폴더(안내 글에 쓴다). 에셋이 있으면 앱 전용 저장소, 없으면 개발용 외부 저장소. */
    val dataPath: String get() = (if (hasBundledData()) internalDir else externalDir).absolutePath

    private fun hasBundledData(): Boolean = appContext.assets.list(DATA_DIR_NAME)?.isNotEmpty() == true

    /** 엔진을 읽고 소리 장치를 연다. 처음에는 데이터를 복사하느라 몇 초 더 걸린다. 백그라운드에서 돈다. */
    suspend fun start(): BgmState = withContext(Dispatchers.IO) {
        val dir = try {
            prepareData()
        } catch (e: IOException) {
            Log.w(TAG, "데이터 복사 실패", e)
            return@withContext BgmState.Failed("엔진 데이터를 복사하지 못했습니다(저장 공간을 확인하세요): ${e.message}")
        }
        val missing = REQUIRED.filterNot { File(dir, it).exists() }
        if (missing.isNotEmpty()) {
            return@withContext BgmState.Failed("엔진 데이터가 없습니다: ${missing.first()} (폴더 ${dir.absolutePath})")
        }
        val err = BgmNative.nativeStart(dir.absolutePath)
        if (err.isEmpty()) BgmState.Ready else BgmState.Failed(err).also { Log.w(TAG, "시작 실패: $err") }
    }

    /** 데이터 폴더를 준비해 돌려준다. 에셋이 있으면 복사(이미 같은 버전이면 건너뜀), 없으면 개발용 외부 폴더. */
    private fun prepareData(): File {
        if (!hasBundledData()) return externalDir
        val stamp = appContext.packageManager.getPackageInfo(appContext.packageName, 0).lastUpdateTime.toString()
        val marker = File(internalDir, MARKER)
        if (marker.exists() && marker.readText() == stamp && REQUIRED.all { File(internalDir, it).exists() }) return internalDir
        val t0 = System.currentTimeMillis()
        internalDir.deleteRecursively()
        copyAsset(DATA_DIR_NAME, internalDir)
        marker.writeText(stamp)                          // 끝까지 복사한 뒤에만 표시한다(중간에 끊기면 다음에 다시 복사)
        Log.i(TAG, "엔진 데이터 복사 ${System.currentTimeMillis() - t0}ms")
        return internalDir
    }

    private fun copyAsset(path: String, dest: File) {
        val children = appContext.assets.list(path) ?: emptyArray()
        if (children.isEmpty()) {                        // 파일
            appContext.assets.open(path).use { input -> dest.outputStream().buffered().use { input.copyTo(it, COPY_BUFFER) } }
        } else {                                         // 폴더
            dest.mkdirs()
            for (c in children) copyAsset("$path/$c", File(dest, c))
        }
    }

    suspend fun stop() = withContext(Dispatchers.Default) { BgmNative.nativeStop() }

    /** 분위기를 바꾼다. 연주 중이면 멜로디 한 덩어리가 끝나는 곳에서 넘어간다. 처음이면 바로 시작한다. */
    fun requestMood(engineId: String) = BgmNative.nativeMood(engineId)

    /** 음량 0..1 */
    fun setVolume(volume: Float) = BgmNative.nativeVolume(volume)

    /** 조절값(-1..1) 여섯 개. 음 높이/빠르기/음 수/세기/새로움은 다음 덩어리부터, 울림은 바로 바뀐다. */
    fun setParams(p: EngineParams) = BgmNative.nativeParams(p.reg, p.tempo, p.dens, p.energy, p.novelty, p.space)

    /** 곡을 마무리한다. */
    fun end() = BgmNative.nativeEnd()

    /** 소리 장치를 멈춘다(소리 줄과 작곡도 같이 멈춤). */
    fun pause() = BgmNative.nativePause()

    fun resume() = BgmNative.nativeResume()

    /** 다른 소리가 나오는 동안 볼륨을 낮춘다. */
    fun duck(on: Boolean) = BgmNative.nativeDuck(on)

    /** 오브가 쓸 지금 소리 크기(0..1). */
    fun pulse(): Float = BgmNative.nativePulse()

    /** 오브가 쓸 박자 신호(0..1). */
    fun beat(): Float = BgmNative.nativeBeat()
    fun notes(): LongArray = BgmNative.nativeNotes()
    fun played(): Long = BgmNative.nativePlayed()

    fun status(): BgmStatus? {
        val f = BgmNative.nativeStatus().split('|')
        if (f.size < 6) return null
        return BgmStatus(f[0], f[1], f[2], f[3].toIntOrNull() ?: 0, f[4].toIntOrNull() ?: 0, f[5] == "1")
    }

    private companion object {
        const val TAG = "BgmPlayer"
        const val DATA_DIR_NAME = "bgm"
        const val MARKER = ".ready"
        const val COPY_BUFFER = 1 shl 16

        /** 엔진이 읽는 파일들 (ai/engine/data 와 같은 구성) */
        val REQUIRED = listOf("chord.bin", "melody.bin", "calib_tsf.txt", "tune.txt", "sf/GeneralUser-GS.sf2", "sf/sso_small.sf2", "piano/021_A0v04.flac")
    }
}
