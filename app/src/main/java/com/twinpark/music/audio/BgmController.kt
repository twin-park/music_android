package com.twinpark.music.audio

import android.content.Context
import com.twinpark.music.domain.EngineParams
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 배경음악 엔진을 앱 프로세스 전체에서 하나로 쥐고 있는 곳. 화면([com.twinpark.music.ui.screen.MusicScreen])과
 * 포그라운드 서비스([BgmService])가 같이 쓴다. 엔진은 서비스가 살아 있는 동안 계속 돌고, 화면은 열고 닫아도 된다.
 */
object BgmController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var player: BgmPlayer? = null
    private val starting = AtomicBoolean(false)

    private val _state = MutableStateFlow<BgmState>(BgmState.Idle)
    val state: StateFlow<BgmState> = _state.asStateFlow()

    private val _paused = MutableStateFlow(false)

    /** 일시정지 중인가 (오디오 포커스를 잃었거나 사용자가 멈춤) */
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _moodId = MutableStateFlow(DEFAULT_MOOD_ID)

    /**
     * 지금 요청된 엔진 분위기 ID. 화면은 이 값으로 오브와 하늘을 고르고, 상황 판단([SituationDirector])이나
     * 사용자 조작이 [requestMood]로 바꾼다. 화면을 다시 열어도 같은 분위기를 이어 보여 준다.
     */
    val moodId: StateFlow<String> = _moodId.asStateFlow()

    private var volume = 1f

    /** 엔진을 읽고 소리 장치를 연다. 이미 읽었거나 읽는 중이면 아무것도 하지 않는다. */
    fun start(context: Context) {
        if (!starting.compareAndSet(false, true)) return
        val p = player ?: BgmPlayer(context.applicationContext).also { player = it }
        _state.value = BgmState.Loading
        scope.launch {
            val result = p.start()
            if (result == BgmState.Ready) {
                p.setVolume(volume)
                p.setParams(params)
                p.requestMood(_moodId.value)
            } else {
                starting.set(false)               // 데이터를 넣은 뒤 다시 시도할 수 있게
            }
            _state.value = result
        }
    }

    /** 엔진을 끈다. 서비스가 끝날 때 부른다. */
    fun shutdown() {
        val p = player ?: return
        scope.launch {
            p.stop()
            starting.set(false)
            _paused.value = false
            _state.value = BgmState.Idle
        }
    }

    fun requestMood(engineId: String) {
        if (engineId == _moodId.value) return
        _moodId.value = engineId
        if (_state.value == BgmState.Ready) player?.requestMood(engineId)
    }

    private var autoParams = EngineParams()
    private var knobParams = EngineParams()

    /** 엔진에 주는 조절값: 상황 판단이 정한 값 위에 노브 4분면 값을 얹는다. */
    private val params: EngineParams get() = autoParams + knobParams

    /** 조절값(-1..1) 여섯 개. 분위기가 바뀌면 상황 판단이 새 분위기의 값으로 다시 준다. */
    fun setParams(p: EngineParams) {
        autoParams = p
        if (_state.value == BgmState.Ready) player?.setParams(params)
    }

    /** 노브의 4분면이 정한 조절량. 위의 상황 판단 값에 더해진다. */
    fun setKnobParams(p: EngineParams) {
        knobParams = p
        if (_state.value == BgmState.Ready) player?.setParams(params)
    }

    fun setVolume(v: Float) {
        volume = v
        if (_state.value == BgmState.Ready) player?.setVolume(v)
    }

    fun end() { player?.end() }

    fun pause() {
        if (_state.value != BgmState.Ready || _paused.value) return
        player?.pause()
        _paused.value = true
    }

    fun resume() {
        if (_state.value != BgmState.Ready || !_paused.value) return
        player?.resume()
        _paused.value = false
    }

    fun duck(on: Boolean) { player?.duck(on) }

    fun pulse(): Float = if (_state.value == BgmState.Ready && !_paused.value) player?.pulse() ?: 0f else 0f

    fun beat(): Float = if (_state.value == BgmState.Ready && !_paused.value) player?.beat() ?: 0f else 0f

    /** 피아노 화면용: 새로 알려진 음들((프레임 shl 16) or (세기 shl 8) or 음 높이). 연주 중이 아니면 비어 있다. */
    fun drainNotes(): LongArray = if (_state.value == BgmState.Ready && !_paused.value) player?.notes() ?: LongArray(0) else LongArray(0)

    /** 피아노 화면이 보이는 동안 true: 소리 줄이 더 앞서 만들어 음이 일찍 알려진다(음량 반응은 그만큼 늦다). */
    fun setPianoLead(on: Boolean) { player?.setLead(if (on) 0.7f else 0.2f) }

    /** 지금 재생 위치(소리 프레임). */
    fun playedFrame(): Long = if (_state.value == BgmState.Ready) player?.played() ?: 0L else 0L

    fun status(): BgmStatus? = if (_state.value == BgmState.Ready) player?.status() else null

    /** 처음 켰을 때 틀 엔진 분위기 */
    const val DEFAULT_MOOD_ID = "평온한재즈"
}
