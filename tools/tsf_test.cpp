// 시험(안드로이드 쪽 도구): TinySoundFont 어댑터(app/src/main/cpp/fluid_tsf.cpp)로 사운드폰트 악기들을 연주해 보고, 소리 크기·주파수·속도를 잰다.
//   tsf_test <사운드폰트.sf2> <출력.wav>
// 악기마다 C 장화음(C4 E4 G4 + 낮은 C3)을 1.5초 누르고 0.5초 울림을 렌더링한다.
#include "dsp.h"      // ai/engine/src (include 경로로 잡는다)
#include "fluid.h"
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdint>
#include <string>
#include <vector>

static void write_wav(const char *path, const std::vector<float> &L, const std::vector<float> &R) {
    FILE *f = std::fopen(path, "wb");
    auto u32 = [&](uint32_t v) { std::fwrite(&v, 4, 1, f); };
    auto u16 = [&](uint16_t v) { std::fwrite(&v, 2, 1, f); };
    uint32_t n = (uint32_t)L.size();
    std::fwrite("RIFF", 1, 4, f); u32(36 + n * 4); std::fwrite("WAVEfmt ", 1, 8, f); u32(16); u16(1); u16(2); u32(SR); u32(SR * 4); u16(4); u16(16);
    std::fwrite("data", 1, 4, f); u32(n * 4);
    for (uint32_t i = 0; i < n; i++)
        for (float x : {L[i], R[i]}) { int16_t s = (int16_t)std::lround(std::max(-1.f, std::min(1.f, x)) * 32767); std::fwrite(&s, 2, 1, f); }
    std::fclose(f);
}

int main(int argc, char **argv) {
    if (argc < 3) { std::printf("tsf_test <sf2> <out.wav>\n"); return 1; }
    if (!FL.load("")) { std::printf("%s\n", FL.err.c_str()); return 1; }
    struct Inst { const char *name; int prog; bool drum; };
    const Inst list[] = {{"피아노", 0, false}, {"나일론기타", 24, false}, {"베이스", 32, false}, {"현악", 48, false},
                         {"트럼펫", 56, false}, {"알토색소폰", 65, false}, {"플루트", 73, false}, {"비브라폰", 11, false}, {"드럼", 0, true}};
    std::vector<float> L, R;
    auto t0 = std::chrono::steady_clock::now();
    for (const Inst &in : list) {
        auto *set = FL.new_settings();
        FL.setnum(set, "synth.sample-rate", SR); FL.setnum(set, "synth.gain", 0.7); FL.setint(set, "synth.polyphony", 256);
        auto *syn = FL.new_synth(set);
        if (FL.sfload(syn, argv[1], 1) < 0) { std::printf("사운드폰트를 못 읽음\n"); return 1; }
        const int notes[4] = {48, 60, 64, 67};
        if (in.drum) FL.program_change(syn, 9, in.prog);
        else { FL.bank_select(syn, 0, 0); FL.program_change(syn, 0, in.prog); FL.cc(syn, 0, 7, 110); FL.cc(syn, 0, 11, 100); }
        const int ch = in.drum ? 9 : 0;
        const int keys[4] = {36, 38, 42, 49};      // 드럼: 킥 · 스네어 · 하이햇 · 크래시
        for (int i = 0; i < 4; i++) FL.noteon(syn, ch, in.drum ? keys[i] : notes[i], 100);
        std::vector<float> bl(32), br(32);
        double pk = 0, ss = 0; long cnt = 0;
        const int total = SR * 2;
        for (int s = 0; s < total; s += 32) {
            if (s == SR * 3 / 2) for (int i = 0; i < 4; i++) FL.noteoff(syn, ch, in.drum ? keys[i] : notes[i]);
            FL.write_float(syn, 32, bl.data(), 0, 1, br.data(), 0, 1);
            for (int i = 0; i < 32; i++) { L.push_back(bl[i]); R.push_back(br[i]); pk = std::max(pk, (double)std::fabs(bl[i])); ss += bl[i] * bl[i]; cnt++; }
        }
        std::printf("%-10s 최고 %.3f · 평균 %.1fdB\n", in.name, pk, 10 * std::log10(ss / cnt + 1e-12));
        FL.delete_synth(syn); FL.delete_settings(set);
    }
    double sec = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
    std::printf("소리 %zu초 만드는 데 %.2f초(읽기 포함)\n", L.size() / SR, sec);
    write_wav(argv[2], L, R);
    return 0;
}
