// FluidSynth 대신 TinySoundFont(tsf.h, MIT)로 사운드폰트를 연주한다. fluid.h 의 FL 인터페이스를 그대로 채워서
// engine.cpp 는 고치지 않는다. 안드로이드처럼 libfluidsynth 를 쓸 수 없는 곳에서 fluid.cpp 대신 이 파일을 빌드한다.
// (안드로이드 앱 쪽 파일이다. ai/engine 에는 두지 않는다.)
//   - 같은 사운드폰트는 한 번만 읽고 tsf_copy 로 나눠 쓴다(악기마다 따로 읽으면 메모리가 몇 배가 됨).
//   - 울림(reverb)·코러스는 없다(엔진이 어차피 끄고 자체 잔향을 씀).
//   - 크기가 fluidsynth 와 달라서 악기 크기 보정표는 따로 둔다(app/bgm/calib_tsf.txt, 엔진의 calib_name 으로 지정). 바꾼 뒤에는 engine_cli calib 로 다시 만든다.
#include "fluid.h"
#include <cmath>
#include <cstring>
#include <map>
#include <mutex>
#include <vector>

#define TSF_IMPLEMENTATION
#include "third_party/tsf.h"

Fluid FL;

namespace {

struct Settings { double sr = 48000, gain = 1; int poly = 256; };
struct Synth {
    tsf *f = nullptr;
    Settings s;
    int bank[16] = {0};
    std::vector<float> tmp;      // tsf 는 좌우가 섞인 채로 내보내서 잠깐 담는다
};

std::mutex g_mtx;
std::map<std::string, tsf *> g_fonts;      // 경로 → 읽어 둔 사운드폰트 (프로세스 끝까지 둠)

Fluid::settings_t *new_settings() { return new Settings(); }
void delete_settings(Fluid::settings_t *p) { delete static_cast<Settings *>(p); }
int setnum(Fluid::settings_t *p, const char *k, double v) {
    auto *s = static_cast<Settings *>(p);
    if (!std::strcmp(k, "synth.sample-rate")) s->sr = v;
    else if (!std::strcmp(k, "synth.gain")) s->gain = v;
    return 0;
}
int setint(Fluid::settings_t *p, const char *k, int v) {
    auto *s = static_cast<Settings *>(p);
    if (!std::strcmp(k, "synth.polyphony")) s->poly = v;
    return 0;                                   // reverb·chorus 는 아예 없음
}
Fluid::synth_t *new_synth(Fluid::settings_t *p) {
    auto *y = new Synth();
    y->s = *static_cast<Settings *>(p);
    return y;
}
void delete_synth(Fluid::synth_t *p) {
    auto *y = static_cast<Synth *>(p);
    if (y->f) tsf_close(y->f);                  // tsf_copy 로 나눈 것은 읽은 자료를 같이 쓰므로 마지막 하나가 닫힐 때 풀린다
    delete y;
}
int sfload(Fluid::synth_t *p, const char *path, int) {
    auto *y = static_cast<Synth *>(p);
    tsf *base = nullptr;
    {
        std::lock_guard<std::mutex> g(g_mtx);
        auto it = g_fonts.find(path);
        if (it == g_fonts.end()) it = g_fonts.emplace(path, tsf_load_filename(path)).first;
        base = it->second;
    }
    if (!base) return -1;                       // fluidsynth 도 실패하면 음수
    if (y->f) tsf_close(y->f);
    y->f = tsf_copy(base);
    const float gain_db = 20.f * std::log10((float)std::max(1e-4, y->s.gain));
    tsf_set_output(y->f, TSF_STEREO_INTERLEAVED, (int)y->s.sr, gain_db);
    tsf_set_max_voices(y->f, y->s.poly);
    return 0;
}
int noteon(Fluid::synth_t *p, int ch, int key, int vel) {
    auto *y = static_cast<Synth *>(p);
    if (vel <= 0) { tsf_channel_note_off(y->f, ch, key); return 0; }
    return tsf_channel_note_on(y->f, ch, key, vel / 127.f) ? 0 : -1;
}
int noteoff(Fluid::synth_t *p, int ch, int key) { tsf_channel_note_off(static_cast<Synth *>(p)->f, ch, key); return 0; }
int cc(Fluid::synth_t *p, int ch, int num, int val) { return tsf_channel_midi_control(static_cast<Synth *>(p)->f, ch, num, val) ? 0 : -1; }
int pitch_bend(Fluid::synth_t *p, int ch, int val) { return tsf_channel_set_pitchwheel(static_cast<Synth *>(p)->f, ch, val) ? 0 : -1; }
int bank_select(Fluid::synth_t *p, int ch, int bank) { static_cast<Synth *>(p)->bank[ch & 15] = bank; return 0; }
int program_change(Fluid::synth_t *p, int ch, int prog) {
    auto *y = static_cast<Synth *>(p);
    // fluidsynth 는 9번 채널을 드럼(뱅크 128)으로 친다
    if (ch == 9) return tsf_channel_set_presetnumber(y->f, 9, prog, 1) ? 0 : -1;
    return tsf_channel_set_bank_preset(y->f, ch, y->bank[ch & 15], prog) ? 0 : -1;
}
int write_float(Fluid::synth_t *p, int len, void *lbuf, int loff, int lincr, void *rbuf, int roff, int rincr) {
    auto *y = static_cast<Synth *>(p);
    if ((int)y->tmp.size() < len * 2) y->tmp.resize(len * 2);
    tsf_render_float(y->f, y->tmp.data(), len, 0);
    float *L = static_cast<float *>(lbuf), *R = static_cast<float *>(rbuf);
    for (int i = 0; i < len; i++) {
        L[loff + i * lincr] = y->tmp[2 * i];
        R[roff + i * rincr] = y->tmp[2 * i + 1];
    }
    return 0;
}
int all_notes_off(Fluid::synth_t *p, int ch) { tsf_channel_note_off_all(static_cast<Synth *>(p)->f, ch); return 0; }
int all_sounds_off(Fluid::synth_t *p, int ch) { tsf_channel_sounds_off_all(static_cast<Synth *>(p)->f, ch); return 0; }
void *set_log_function(int, void *, void *) { return nullptr; }

}  // namespace

bool Fluid::load(const std::string &) {
    new_settings = ::new_settings; delete_settings = ::delete_settings;
    setnum = ::setnum; setint = ::setint;
    new_synth = ::new_synth; delete_synth = ::delete_synth; sfload = ::sfload;
    noteon = ::noteon; noteoff = ::noteoff; cc = ::cc; pitch_bend = ::pitch_bend;
    program_change = ::program_change; bank_select = ::bank_select;
    write_float = ::write_float; all_notes_off = ::all_notes_off; all_sounds_off = ::all_sounds_off;
    set_log_function = ::set_log_function;
    ok = true;
    return true;
}
