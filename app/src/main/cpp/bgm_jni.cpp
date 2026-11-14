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
    // 피아노 화면용 음 알림: 엔진이 미리 만들어 둔 음(아직 재생 위치에 닿기 전)을 알려 주어 화면에서 건반으로 내려오게 한다.
    // 소리 장치 스레드가 쓰고 화면 스레드가 읽는다(생산자 하나, 소비자 하나).
    static constexpr size_t kNoteCap = 256;
    long note_q[kNoteCap];                 // (소리 프레임 << 16) | (세기 << 8) | 음 높이
    std::atomic<size_t> note_w{0}, note_r{0};
    size_t announced = 0;                  // 소리 장치 스레드만 씀: 엔진 박자 큐에서 여기까지는 이미 알렸다
    ma_device dev{};
    bool dev_ok = false;
    std::thread render, composer;
    static constexpr double kFill = .2;   // 초: 소리 줄이 앞서 만들어 두는 기본 양
    std::atomic<double> fill{kFill};      // 지금 쓰는 값. 피아노 화면은 음이 위에서 길게 내려오도록 늘린다(대신 음량 반응이 그만큼 늦다). 고리 버퍼(1초) 안이어야 한다.
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
    // 피아노 화면: 박자 큐에 들어와 있는 음(재생 위치보다 앞선 것 포함)을 한 번씩만 알린다. 타악기는 건반이 없어 뺀다.
    {
        const size_t qr = P.E.onset_r.load(std::memory_order_acquire), qw = P.E.onset_w.load(std::memory_order_acquire);
        if (P.announced < qr) P.announced = qr;
        for (; P.announced < qw; P.announced++) {
            const Engine::Onset &o = P.E.onset_q[P.announced % Engine::kOnsetCap];
            if (o.key < 0 || o.track < 0 || o.track >= (int)insts().size() || insts()[o.track].drum) continue;
            const size_t w = P.note_w.load(std::memory_order_relaxed);
            if (w - P.note_r.load(std::memory_order_acquire) >= Player::kNoteCap) break;   // 화면이 못 따라오면 버림
            P.note_q[w % Player::kNoteCap] = (o.frame << 16) | ((long)std::max(0, std::min(127, o.vel)) << 8) | (long)std::max(0, std::min(127, o.key));
            P.note_w.store(w + 1, std::memory_order_release);
        }
    }
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

// 표준 UTF-8 → jstring (NewStringUTF 는 '수정 UTF-8' 이라 4바이트 글자(그림 글자)가 오면 디버그 빌드 CheckJNI 가 앱을 멈춤)
jstring jstr(JNIEnv *env, const std::string &s) {
    std::u16string u;
    u.reserve(s.size());
    for (size_t i = 0; i < s.size();) {
        const unsigned char c = (unsigned char)s[i];
        uint32_t cp; int n;
        if (c < 0x80) { cp = c; n = 1; }
        else if ((c >> 5) == 6) { cp = c & 0x1F; n = 2; }
        else if ((c >> 4) == 14) { cp = c & 0x0F; n = 3; }
        else if ((c >> 3) == 30) { cp = c & 0x07; n = 4; }
        else { u.push_back(0xFFFD); i++; continue; }
        if (i + n > s.size()) { u.push_back(0xFFFD); break; }
        for (int k = 1; k < n; k++) cp = (cp << 6) | ((unsigned char)s[i + k] & 0x3F);
        i += n;
        if (cp >= 0x10000) { cp -= 0x10000; u.push_back((char16_t)(0xD800 + (cp >> 10))); u.push_back((char16_t)(0xDC00 + (cp & 0x3FF))); }
        else u.push_back((char16_t)cp);
    }
    return env->NewString(reinterpret_cast<const jchar *>(u.data()), (jsize)u.size());
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
    LOGI("엔진 읽기 %.1f초 · 지휘자 %s", now_s() - t0, P->E.cond.loaded ? "있음" : "없음");
    P->E.auto_mode = P->E.cond.loaded;                  // 지휘자(상황 → 분위기 · 악기 · 주변 소리)가 있으면 처음부터 알아서
    P->E.play_latency = Player::kFill + .03;
    Player *p = P.get();

    p->render = std::thread([p] {
        set_nice(-16);                                   // 소리 줄은 끊기면 바로 들리므로 가장 먼저(오디오 우선순위)
        std::vector<float> L(1024), R(1024);
        while (p->running) {
            Ring &ring = p->ring;
            if (ring.cap - ring.fill() >= 1024 && ring.fill() < (size_t)(p->fill.load() * SR)) {
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
    Engine &E = g_player->E;
    if (E.auto_mode) {                                   // 알아서: 지휘자가 고른 조절값 위에 노브 양을 더함 (지휘자가 다시 정해도 노브는 남음)
        Params k;
        k.reg = c(reg); k.tempo = c(tempo); k.dens = c(dens); k.energy = c(energy); k.novelty = c(novelty); k.space = c(space);
        Params d;
        { std::lock_guard<std::mutex> cg(E.cond_mtx); d = E.dec.params; E.knob = k; }
        E.set_params(E.with_knob(d));
        return;
    }
    Params p = E.get_params();
    p.reg = c(reg); p.tempo = c(tempo); p.dens = c(dens); p.energy = c(energy); p.novelty = c(novelty); p.space = c(space);
    E.set_params(p);
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

// 피아노 화면: 새로 알려진 음들을 꺼낸다. 값 하나는 (소리 프레임 << 16) | (세기 << 8) | 음 높이(MIDI).
JNIEXPORT jlongArray JNICALL Java_com_twinpark_music_audio_BgmNative_nativeNotes(JNIEnv *env, jobject) {
    Player *p = g_player.get();
    if (!p) return env->NewLongArray(0);
    const size_t r = p->note_r.load(std::memory_order_relaxed), w = p->note_w.load(std::memory_order_acquire);
    const jsize n = (jsize)(w - r);
    jlongArray out = env->NewLongArray(n);
    if (n > 0) {
        std::vector<jlong> tmp(n);
        for (jsize i = 0; i < n; i++) tmp[i] = (jlong)p->note_q[(r + i) % Player::kNoteCap];
        env->SetLongArrayRegion(out, 0, n, tmp.data());
        p->note_r.store(w, std::memory_order_release);
    }
    return out;
}

// 지금까지 소리 장치가 읽어 간 소리 프레임 수(재생 위치). nativeNotes의 프레임과 같은 눈금이다.
JNIEXPORT jlong JNICALL Java_com_twinpark_music_audio_BgmNative_nativePlayed(JNIEnv *, jobject) {
    Player *p = g_player.get();
    return p ? (jlong)p->ring.r.load() : 0L;
}

// 소리 줄이 앞서 만들어 두는 양(초, 0.1~0.8). 피아노 화면은 음을 일찍 알려야 하므로 늘리고, 끄면 원래대로 되돌린다.
JNIEXPORT void JNICALL Java_com_twinpark_music_audio_BgmNative_nativeLead(JNIEnv *, jobject, jfloat sec) {
    Player *p = g_player.get();
    if (!p) return;
    const double v = std::max(0.1, std::min(0.8, (double)sec));
    p->fill.store(v);
    p->E.play_latency = v + .03;
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

// ── 지휘자: 상황 → 분위기 · 악기 · 빠르기 · 주변 소리 (ai/engine/data/conductor/*.json) ──
//   데스크톱판 app.cpp 의 /api/situation · /api/auto · /api/say · /api/decision 과 같은 일

// 알아서 시작: 지금 상황을 3초쯤 보고 지휘자가 고른 분위기로 연주 시작. 지휘자가 없으면 false (그때는 nativeMood 로)
JNIEXPORT jboolean JNICALL Java_com_twinpark_music_audio_BgmNative_nativeAutoStart(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> g(g_mtx);
    if (!g_player || !g_player->E.cond.loaded) return JNI_FALSE;
    Engine &E = g_player->E;
    E.auto_mode = true; E.user_pick_until = -1e9; E.params_at = -1e9; E.auto_start = true;
    return JNI_TRUE;
}

// 알아서 켜기 · 끄기 (끄면 지휘자의 악기 지시를 풀고 지금 분위기를 그대로 둠)
JNIEXPORT void JNICALL Java_com_twinpark_music_audio_BgmNative_nativeAuto(JNIEnv *, jobject, jboolean on) {
    std::lock_guard<std::mutex> g(g_mtx);
    if (!g_player) return;
    Engine &E = g_player->E;
    E.auto_mode = on && E.cond.loaded;
    if (E.auto_mode) { E.user_pick_until = -1e9; E.params_at = -1e9; }
    else E.set_steer(Steer());
}

// 사람이 분위기를 직접 고름: 알아서 중이면 15분은 그 분위기를 지킴 (반응 기록)
JNIEXPORT void JNICALL Java_com_twinpark_music_audio_BgmNative_nativePick(JNIEnv *env, jobject, jstring id) {
    std::lock_guard<std::mutex> g(g_mtx);
    if (g_player) g_player->E.user_mood(to_str(env, id));
}

// 상황 일부 JSON — 예: {"place":{"kind":"coast"}} · {"trip":{"kind":"commute"},"time":{"slot":"morning"}} · null 은 지움. 성공하면 빈 글자, 아니면 이유
JNIEXPORT jstring JNICALL Java_com_twinpark_music_audio_BgmNative_nativeSituation(JNIEnv *env, jobject, jstring json) {
    std::lock_guard<std::mutex> g(g_mtx);
    if (!g_player) return jstr(env, "엔진이 꺼져 있음");
    std::string pe;
    Json patch = Json::parse(to_str(env, json), &pe);
    if (!pe.empty() || !patch.is_obj()) return jstr(env, "상황 JSON 이 아님: " + pe);
    g_player->E.situation(patch);
    return jstr(env, "");
}

// 상황 모두 지우기 (시연 · DEV)
JNIEXPORT void JNICALL Java_com_twinpark_music_audio_BgmNative_nativeSituationReset(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> g(g_mtx);
    if (!g_player) return;
    Engine &E = g_player->E;
    const double now = (double)E.frame.load() / SR;
    std::lock_guard<std::mutex> cg(E.cond_mtx);
    E.cond.sit = Json::obj_(); E.cond.clear_requests(); E.cond.reset_holds(); E.cond.update(Json::obj_(), now);
}

// 지금 상황 (지휘자가 시계 등으로 채운 칸 포함) JSON
JNIEXPORT jstring JNICALL Java_com_twinpark_music_audio_BgmNative_nativeSituationGet(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> g(g_mtx);
    if (!g_player) return jstr(env, "{}");
    Engine &E = g_player->E;
    std::lock_guard<std::mutex> cg(E.cond_mtx);
    return jstr(env, E.cond.derived((double)E.frame.load() / SR).dump());
}

// 말 (음성 인식 문장 · Gleo) → 요청. {"intent":"change|like|quiet|…","say":"다른 분위기로 바꿀게요"}
JNIEXPORT jstring JNICALL Java_com_twinpark_music_audio_BgmNative_nativeSay(JNIEnv *env, jobject, jstring text) {
    std::lock_guard<std::mutex> g(g_mtx);
    if (!g_player) return jstr(env, "{}");
    std::string it;
    const std::string reply = g_player->E.hear(to_str(env, text), &it);
    Json j = Json::obj_();
    j.set("intent", Json::str_(it)); j.set("say", Json::str_(reply));
    return jstr(env, j.dump());
}

// 지휘자가 지금 정한 것 (화면): line(왜 한 줄) · scene_name · because[] · mood(다음에 고를 분위기) + cur(지금 나오는 분위기 ID) · auto · loaded · amb
JNIEXPORT jstring JNICALL Java_com_twinpark_music_audio_BgmNative_nativeConductor(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> g(g_mtx);
    if (!g_player) return jstr(env, "{}");
    Engine &E = g_player->E;
    Json j;
    { std::lock_guard<std::mutex> cg(E.cond_mtx); j = E.dec.to_json(); }
    if (!j.is_obj()) j = Json::obj_();
    j.set("cur", Json::str_(E.cur_mood_id()));
    j.set("auto", Json::bool_(E.auto_mode.load()));
    j.set("loaded", Json::bool_(E.cond.loaded));
    Json amb = Json::obj_();
    amb.set("sea", Json::num_(E.amb.level[0].load())); amb.set("rain", Json::num_(E.amb.level[1].load()));
    j.set("amb", amb);
    return jstr(env, j.dump());
}

}  // extern "C"
