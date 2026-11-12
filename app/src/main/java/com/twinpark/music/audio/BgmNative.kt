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
}
