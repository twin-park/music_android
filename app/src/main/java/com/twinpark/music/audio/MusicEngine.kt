package com.twinpark.music.audio

import com.twinpark.music.domain.EngineParams
import kotlinx.coroutines.flow.StateFlow

/** 화면이 배경음악 엔진에서 읽고 시키는 것. 서비스와 상황 판단이 쓰는 시작, 정지, 상황 조절값은 포함하지 않는다. */
interface MusicEngine {
    val state: StateFlow<BgmState>
    val paused: StateFlow<Boolean>
    val moodId: StateFlow<String>

    fun requestMood(engineId: String)
    fun setVolume(v: Float)
    fun setKnobParams(p: EngineParams)

    fun status(): BgmStatus?
    fun pulse(): Float
    fun beat(): Float

    /** 피아노 화면용: 새로 알려진 음들((프레임 shl 16) or (세기 shl 8) or 음 높이). */
    fun drainNotes(): LongArray
    fun setPianoLead(on: Boolean)
    fun playedFrame(): Long
}
