// 차량 BGM 엔진(ai/engine)을 안드로이드 앱에 붙이는 JNI.
//   소리 줄  : 엔진 render() 로 0.2초 앞까지 소리를 만들어 고리 버퍼에 넣음
//   작곡 줄  : 재생 위치보다 14초 앞까지 단락씩 작곡
//   소리 장치: miniaudio(AAudio/OpenSL)가 고리 버퍼에서 꺼내 감 - 오브가 쓸 소리 크기(pulse)도 여기서 잰다
// 데스크톱판 app.cpp 의 구조와 같고, 웹 화면(HTTP) 대신 Kotlin(BgmNative)이 직접 부른다.
#include "engine.h"
#include "util.h"
#include "../third_party/miniaudio.h"
#include <android/log.h>
#include <atomic>
#include <chrono>
#include <cmath>
#include <jni.h>
#include <memory>
#include <mutex>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <thread>
#include <unistd.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "CarBgm", __VA_ARGS__)

namespace {

struct Ring {
    std::vector<float> b;
    size_t cap;
    std::atomic<size_t> w{0}, r{0};
    explicit Ring(size_t frames) : b(frames * 2), cap(frames) {}
    size_t fill() const { return w.load() - r.load(); }
};

struct Player {
    Engine E;
    Ring ring{48000};
    std::atomic<bool> running{true};
    std::atomic<long> underruns{0};
    std::atomic<float> env{0.f};          // 소리 장치로 나가는 소리 크기(0..1): 빠르게 올라가고 천천히 내려감
    std::atomic<float> beat{0.f};         // 박자 신호(0..1): 음이 시작되는 순간 튀고 0.16초 안팎으로 가라앉음(킥이 가장 크다)
    ma_device dev{};
    bool dev_ok = false;
    std::thread render, composer;
    static constexpr double kFill = .2;   // 초: 소리 줄이 앞서 만들어 두는 양
};

std::mutex g_mtx;
std::unique_ptr<Player> g_player;

void on_audio(ma_device *dev, void *out, const void *, ma_uint32 n) {
    Player &P = *static_cast<Player *>(dev->pUserData);
    float *o = static_cast<float *>(out);
    Ring &ring = P.ring;
    const size_t have = ring.fill(), rd = ring.r.load();
    const size_t k = std::min<size_t>(have, n);
    double ss = 0;
    for (size_t i = 0; i < k; i++) {
        const size_t j = (rd + i) % ring.cap;
        const float l = ring.b[j * 2], r = ring.b[j * 2 + 1];
        o[i * 2] = l; o[i * 2 + 1] = r;
        ss += 0.5 * ((double)l * l + (double)r * r);
    }
    for (size_t i = k; i < n; i++) o[i * 2] = o[i * 2 + 1] = 0;
    if (k < n && P.E.live) P.underruns++;
    ring.r = rd + k;
    // 오브 반응용 소리 크기: 평균 -17dB 쯤(rms 0.14)이 0.5, 큰 소리(rms 0.28)가 1
    const float rms = n ? (float)std::sqrt(ss / n) : 0.f;
    const float x = std::min(1.f, rms / 0.28f);
    const float fall = std::exp(-(float)n / 48000.f / 0.14f);        // 내려가는 시간 약 0.14초
    const float e = P.env.load() * fall;
    P.env.store(std::max(e, x));
    // 박자 신호: 엔진이 알려 준 음 시작 시각이 재생 위치(지금까지 읽은 프레임 수)에 닿은 것을 꺼낸다 -> 소리와 같은 순간에 튄다
    const long played = (long)ring.r.load();
    float hit = 0.f;
    Engine::Onset on;
    while (P.E.pop_onset(played, &on)) hit = std::max(hit, on.s);
    const float bfall = std::exp(-(float)n / 48000.f / 0.16f);
    P.beat.store(std::max(P.beat.load() * bfall, hit));
}

// 현재 스레드의 우선순위(nice 값, 작을수록 먼저 CPU를 받음). 실패해도 연주에는 문제없다.
void set_nice(int nice) { setpriority(PRIO_PROCESS, (id_t)syscall(SYS_gettid), nice); }

double now_s() {
    using namespace std::chrono;
    return duration<double>(steady_clock::now().time_since_epoch()).count();
}

void stop_locked() {
    if (!g_player) return;
    Player &P = *g_player;
    P.running = false;
    if (P.dev_ok) { ma_device_uninit(&P.dev); P.dev_ok = false; }
    if (P.render.joinable()) P.render.join();
    if (P.composer.joinable()) P.composer.join();
    g_player.reset();
    LOGI("엔진 종료");
}

std::string to_str(JNIEnv *env, jstring s) {
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string r = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return r;
}

}  // namespace

extern "C" {

// 엔진을 읽고(AI 모델 · 사운드폰트 · 피아노) 소리 장치를 연다. 성공하면 빈 글자, 실패하면 이유. 몇 초 걸리므로 백그라운드에서 부른다.
JNIEXPORT jstring JNICALL Java_com_twinpark_music_audio_BgmNative_nativeStart(JNIEnv *env, jobject, jstring jdata) {
    std::lock_guard<std::mutex> g(g_mtx);
    stop_locked();
    auto P = std::make_unique<Player>();
    std::string err;
    const double t0 = now_s();
    P->E.calib_name = "calib_tsf.txt";      // 사운드폰트 소리 크기가 fluidsynth 와 달라서 TinySoundFont 기준 보정표를 쓴다
    if (!P->E.init(to_str(env, jdata), &err)) return env->NewStringUTF(err.empty() ? "엔진을 시작하지 못했습니다" : err.c_str());
    LOGI("엔진 읽기 %.1f초", now_s() - t0);
    P->E.play_latency = Player::kFill + .03;
    Player *p = P.get();

    p->render = std::thread([p] {
        set_nice(-16);                                   // 소리 줄은 끊기면 바로 들리므로 가장 먼저(오디오 우선순위)
        std::vector<float> L(1024), R(1024);
        while (p->running) {
            Ring &ring = p->ring;
            if (ring.cap - ring.fill() >= 1024 && ring.fill() < (size_t)(Player::kFill * SR)) {
                p->E.render(L.data(), R.data(), 1024);
                const size_t w = ring.w.load();
                for (int i = 0; i < 1024; i++) { const size_t j = (w + i) % ring.cap; ring.b[j * 2] = L[i]; ring.b[j * 2 + 1] = R[i]; }
                ring.w = w + 1024;
            } else std::this_thread::sleep_for(std::chrono::milliseconds(3));
        }
    });
    p->composer = std::thread([p] {
        set_nice(10);                                    // 작곡은 14초 앞서 있어서 늦어도 괜찮다. 화면 그리기와 소리에 CPU를 양보한다.
        while (p->running)
            if (!p->E.compose_step((double)p->E.frame.load() / SR)) std::this_thread::sleep_for(std::chrono::milliseconds(20));
    });

    ma_device_config cfg = ma_device_config_init(ma_device_type_playback);
    cfg.playback.format = ma_format_f32;
    cfg.playback.channels = 2;
    cfg.sampleRate = SR;
    cfg.periodSizeInMilliseconds = 20;
    cfg.performanceProfile = ma_performance_profile_conservative;     // 배경음악이라 지연보다 안 끊기는 쪽
    cfg.dataCallback = on_audio;
    cfg.pUserData = p;
    if (ma_device_init(nullptr, &cfg, &p->dev) != MA_SUCCESS || ma_device_start(&p->dev) != MA_SUCCESS) {
        p->running = false;
        if (p->render.joinable()) p->render.join();
        if (p->composer.joinable()) p->composer.join();
        return env->NewStringUTF("소리 장치를 열지 못했습니다");
    }
    p->dev_ok = true;
    g_player = std::move(P);
    LOGI("재생 시작");
    return env->NewStringUTF("");
}

JNIEXPORT void JNICALL Java_com_twinpark_music_audio_BgmNative_nativeStop(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> g(g_mtx);
    stop_locked();
}

JNIEXPORT void JNICALL Java_com_twinpark_music_audio_BgmNative_nativeMood(JNIEnv *env, jobject, jstring id) {
    std::lock_guard<std::mutex> g(g_mtx);
    if (g_player) g_player->E.request_mood(to_str(env, id));
}

// 조절값 여섯 개(-1..1): 음 높이 / 빠르기 / 음 수 / 세기 / 새로움 / 울림
JNIEXPORT void JNICALL Java_com_twinpark_music_audio_BgmNative_nativeParams(JNIEnv *, jobject, jfloat reg, jfloat tempo, jfloat dens, jfloat energy, jfloat novelty, jfloat space) {
    std::lock_guard<std::mutex> g(g_mtx);
    if (!g_player) return;
    auto c = [](double v) { return std::max(-1.0, std::min(1.0, v)); };
    Params p = g_player->E.get_params();
    p.reg = c(reg); p.tempo = c(tempo); p.dens = c(dens); p.energy = c(energy); p.novelty = c(novelty); p.space = c(space);
    g_player->E.set_params(p);
}

JNIEXPORT void JNICALL Java_com_twinpark_music_audio_BgmNative_nativeVolume(JNIEnv *, jobject, jfloat v) {
    std::lock_guard<std::mutex> g(g_mtx);
    if (g_player) g_player->E.volume = std::max(0.f, std::min(1.f, v));
}

// 일시정지: 소리 장치를 멈추면 고리 버퍼가 비워지지 않아 소리 줄과 작곡도 함께 멈춘다. 다시 켜면 이어서 연주한다.
JNIEXPORT void JNICALL Java_com_twinpark_music_audio_BgmNative_nativePause(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> g(g_mtx);
    if (g_player && g_player->dev_ok) ma_device_stop(&g_player->dev);
}

JNIEXPORT void JNICALL Java_com_twinpark_music_audio_BgmNative_nativeResume(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> g(g_mtx);
    if (g_player && g_player->dev_ok) ma_device_start(&g_player->dev);
}

// 다른 소리(내비 안내 등)가 잠깐 나올 때 볼륨을 낮춘다
JNIEXPORT void JNICALL Java_com_twinpark_music_audio_BgmNative_nativeDuck(JNIEnv *, jobject, jboolean on) {
    std::lock_guard<std::mutex> g(g_mtx);
    if (g_player) g_player->E.duck = on ? .15f : 1.f;
}

// 곡을 마무리하고 끝낸다 (끝 페이드까지 1~2마디)
JNIEXPORT void JNICALL Java_com_twinpark_music_audio_BgmNative_nativeEnd(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> g(g_mtx);
    if (g_player) g_player->E.request_end();
}

// 오브가 쓰는 지금 소리 크기(0..1). 화면 그릴 때마다 불러도 되게 잠금 없이 읽는다.
JNIEXPORT jfloat JNICALL Java_com_twinpark_music_audio_BgmNative_nativePulse(JNIEnv *, jobject) {
    Player *p = g_player.get();
    return p ? p->env.load() : 0.f;
}

// 오브가 쓰는 박자 신호(0..1): 음이 시작되는 순간 튄다. 소리 크기(pulse)와 달리 박자에 맞춰 짧게 튄다.
JNIEXPORT jfloat JNICALL Java_com_twinpark_music_audio_BgmNative_nativeBeat(JNIEnv *, jobject) {
    Player *p = g_player.get();
    return p ? p->beat.load() : 0.f;
}

// 상태 글자 한 줄: 분위기 이름|마디 역할|조|빠르기|끊김 횟수|연주중(0/1)
JNIEXPORT jstring JNICALL Java_com_twinpark_music_audio_BgmNative_nativeStatus(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> g(g_mtx);
    if (!g_player) return env->NewStringUTF("");
    Engine &E = g_player->E;
    const double t = (double)E.frame.load() / SR - E.play_latency;
    const auto st = E.status_at(t);
    char b[256];
    std::snprintf(b, sizeof b, "%s|%s|%s|%d|%ld|%d", st.mood_name.c_str(), st.role.c_str(), st.key.c_str(), (int)std::lround(st.bpm),
                  g_player->underruns.load(), E.live ? 1 : 0);
    return env->NewStringUTF(b);
}

}  // extern "C"
