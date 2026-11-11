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
}
