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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 배경음악 엔진을 앱 프로세스 전체에서 하나로 쥐고 있는 곳. 화면([com.twinpark.music.ui.screen.MusicScreen])과
 * 포그라운드 서비스([BgmService])가 같이 쓴다. 엔진은 서비스가 살아 있는 동안 계속 돌고, 화면은 열고 닫아도 된다.
 */
object BgmController : MusicEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var player: BgmPlayer? = null
    private val starting = AtomicBoolean(false)

    private val _state = MutableStateFlow<BgmState>(BgmState.Idle)
    override val state: StateFlow<BgmState> = _state.asStateFlow()

    private val _paused = MutableStateFlow(false)

    /** 일시정지 중인가 (오디오 포커스를 잃었거나 사용자가 멈춤) */
    override val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _moodId = MutableStateFlow(DEFAULT_MOOD_ID)

    /**
     * 지금 요청된 엔진 분위기 ID. 화면은 이 값으로 오브와 하늘을 고르고, 상황 판단([SituationDirector])이나
     * 사용자 조작이 [requestMood]로 바꾼다. 화면을 다시 열어도 같은 분위기를 이어 보여 준다.
     */
    override val moodId: StateFlow<String> = _moodId.asStateFlow()

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
                // 지휘자가 있으면 지금 상황(시간대 · 요일은 시계로, 나머지는 차량 · 귀 · 화면)을 3초쯤 보고 고른 분위기로 시작한다
                if (!BgmNative.nativeAutoStart()) p.requestMood(_moodId.value)
                pollConductor()
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

    override fun requestMood(engineId: String) {
        if (engineId == _moodId.value) return
        _moodId.value = engineId
        if (_state.value != BgmState.Ready) return
        if (conductorOn) {                                   // 알아서 중: 사람이 고른 분위기는 15분 지키고, 그 뒤 다시 상황을 따른다
            pickedAtMs = System.currentTimeMillis()
            BgmNative.nativePick(engineId)
        } else player?.requestMood(engineId)
    }

    // ── 지휘자 (ai/engine conductor) ──
    private val _conductor = MutableStateFlow<ConductorInfo?>(null)

    /** 지휘자가 지금 정한 것 (화면 표시). 예전 엔진이거나 아직 안 켜졌으면 null. */
    val conductor: StateFlow<ConductorInfo?> = _conductor.asStateFlow()

    /** 지휘자가 상황을 보고 고르는 중인가 */
    val conductorOn: Boolean get() = _conductor.value?.let { it.loaded && it.auto } == true

    private var pollJob: Job? = null
    @Volatile private var pickedAtMs = 0L

    /** 1초마다 지휘자 결정을 읽어 화면에 알리고, 지휘자가 분위기를 바꾸면 [moodId]도 따라가게 한다(오브 · 하늘). */
    private fun pollConductor() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && _state.value != BgmState.Idle) {
                if (_state.value == BgmState.Ready) {
                    val c = ConductorInfo.parse(BgmNative.nativeConductor())
                    _conductor.value = c
                    val justPicked = System.currentTimeMillis() - pickedAtMs < 12_000   // 고른 분위기로 넘어가는 동안은 화면을 되돌리지 않음
                    if (c != null && c.auto && !justPicked && c.current.isNotEmpty() && c.current != _moodId.value) _moodId.value = c.current
                }
                delay(1000)
            }
        }
    }

    /** 상황 일부 JSON (예: {"place":{"kind":"coast"}} · null 은 지움). 성공하면 빈 글자. */
    fun setSituation(json: String): String = if (_state.value == BgmState.Ready) BgmNative.nativeSituation(json) else "엔진 준비 중"

    /** 상황 모두 지우기 */
    fun resetSituation() { if (_state.value == BgmState.Ready) BgmNative.nativeSituationReset() }

    /** 말로 요청 ("다른 거" · "조용히" · "신나게" · "파도 소리" · "이거 좋다" …) → 지휘자 대답 한 줄 */
    fun say(text: String): String =
        if (_state.value != BgmState.Ready) "" else runCatching { org.json.JSONObject(BgmNative.nativeSay(text)).optString("say") }.getOrDefault("")

    /**
     * CHANGE THE MOOD: 알아서 중이면 지휘자에게 "다른 거" — 지금 분위기는 10분 동안 빼고, 지금 상황에서 다음으로 맞는 분위기로 바로(다음 마디) 넘어간다.
     * 지휘자가 없으면 목록의 다음 분위기([fallbackNext]).
     */
    fun changeMood(fallbackNext: () -> String) {
        if (conductorOn) { say("다른 거"); pickedAtMs = 0L } else requestMood(fallbackNext())
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
    override fun setKnobParams(p: EngineParams) {
        knobParams = p
        if (_state.value == BgmState.Ready) player?.setParams(params)
    }

    override fun setVolume(v: Float) {
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

    override fun pulse(): Float = if (_state.value == BgmState.Ready && !_paused.value) player?.pulse() ?: 0f else 0f

    override fun beat(): Float = if (_state.value == BgmState.Ready && !_paused.value) player?.beat() ?: 0f else 0f

    /** 피아노 화면용: 새로 알려진 음들((프레임 shl 16) or (세기 shl 8) or 음 높이). 연주 중이 아니면 비어 있다. */
    override fun drainNotes(): LongArray = if (_state.value == BgmState.Ready && !_paused.value) player?.notes() ?: LongArray(0) else LongArray(0)

    /** 피아노 화면이 보이는 동안 true: 소리 줄이 더 앞서 만들어 음이 일찍 알려진다(음량 반응은 그만큼 늦다). */
    override fun setPianoLead(on: Boolean) { player?.setLead(if (on) 0.7f else 0.2f) }

    /** 지금 재생 위치(소리 프레임). */
    override fun playedFrame(): Long = if (_state.value == BgmState.Ready) player?.played() ?: 0L else 0L

    override fun status(): BgmStatus? = if (_state.value == BgmState.Ready) player?.status() else null

    /** 처음 켰을 때 틀 엔진 분위기 */
    const val DEFAULT_MOOD_ID = "평온한재즈"
}
