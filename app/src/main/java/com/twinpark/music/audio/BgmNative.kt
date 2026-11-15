package com.twinpark.music.audio

/**
 * 차량 BGM 엔진(C++, `ai/engine`)의 JNI 입구. 함수 이름은 `src/main/cpp/bgm_jni.cpp`와 같아야 한다.
 * 직접 쓰지 말고 [BgmPlayer]를 통해 쓴다.
 */
internal object BgmNative {
    init {
        System.loadLibrary("carbgm")
    }

    /** 엔진을 읽고 소리 장치를 연다. 성공하면 빈 글자, 실패하면 이유. 몇 초 걸리므로 메인 스레드에서 부르지 않는다. */
    external fun nativeStart(dataDir: String): String
    external fun nativeStop()
    external fun nativeMood(id: String)
    external fun nativeParams(reg: Float, tempo: Float, dens: Float, energy: Float, novelty: Float, space: Float)
    external fun nativeVolume(v: Float)
    external fun nativeEnd()
    external fun nativePause()
    external fun nativeResume()
    external fun nativeDuck(on: Boolean)

    /** 지금 나가는 소리 크기(0..1). 화면을 그릴 때마다 불러도 된다. */
    external fun nativePulse(): Float

    /** 박자 신호(0..1). 음이 시작되는 순간 튀고 약 0.16초에 가라앉는다. */
    external fun nativeBeat(): Float

    /** "분위기 이름|마디 역할|조|빠르기|끊김 횟수|연주중(0/1)" */
    external fun nativeStatus(): String

    /** 피아노 화면용: 새로 알려진 음들. 값 하나는 (소리 프레임 shl 16) or (세기 shl 8) or 음 높이(MIDI). */
    external fun nativeNotes(): LongArray

    /** 소리 장치가 지금까지 읽어 간 소리 프레임 수(재생 위치). 음의 프레임과 같은 눈금이다. */
    external fun nativePlayed(): Long

    /** 소리 줄이 앞서 만들어 두는 양(초). 피아노 화면이 음을 일찍 알려 받으려고 늘린다. */
    external fun nativeLead(sec: Float)

    // ── 지휘자(ai/engine 11-13 판부터): 상황 → 분위기 · 악기 · 빠르기 · 주변 소리. 자세한 건 [Conductor] ──

    /** 지금 상황을 3초쯤 보고 지휘자가 고른 분위기로 시작. 지휘자가 없으면 false. */
    external fun nativeAutoStart(): Boolean

    /** 알아서 켜기 · 끄기 */
    external fun nativeAuto(on: Boolean)

    /** 사람이 분위기를 직접 고름 (알아서 중이면 15분은 그 분위기) */
    external fun nativePick(id: String)

    /** 상황 일부 JSON (null 은 지움). 성공하면 빈 글자, 아니면 이유. 예: {"place":{"kind":"coast"}} */
    external fun nativeSituation(json: String): String

    /** 상황 모두 지우기 */
    external fun nativeSituationReset()

    /** 지금 상황 JSON (지휘자가 시계로 채운 칸 포함) */
    external fun nativeSituationGet(): String

    /** 말(음성 인식 문장) → 요청. {"intent":"change|like|quiet|…","say":"…"} */
    external fun nativeSay(text: String): String

    /** 지휘자가 지금 정한 것 JSON: line · scene_name · because · mood(고른 것) · cur(지금 나오는 것) · auto · loaded */
    external fun nativeConductor(): String
}
