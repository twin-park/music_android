package com.carbgm.ear;

import java.util.Arrays;

/**
 * 말투 숫자 — 파이썬 ear/features.py 의 prosody() 를 그대로 옮긴 것 (신경망 아님, 4초 창 하나에 몇 ms).
 * 입력: 16kHz 모노 파형 (-1~1). 값은 마이크마다 달라 대략적인 기준으로만 씀.
 * 단어 규칙(EarWords)에 쓰는 것은 loudDb · loudVar · pitchVar · rate 네 개.
 */
public final class Prosody {
    public static final class Result {
        public double loudDb;      // 가장 큰 40ms 구간의 dBFS
        public double loudVar;     // 소리 나는 구간 크기(dB)의 표준편차
        public double voicedRatio; // 목소리(주기 있는 소리) 틀 비율
        public double pitchMed;    // 목소리 높이 가운데값 (Hz)
        public double pitchVar;    // 높낮이 흔들림 (반음 단위 표준편차)
        public double rate;        // 초당 음절 봉우리 수 (말 빠르기)
    }

    private static final int SR = 16000, FL = 640, HOP = 160;   // 40ms 창 · 10ms 간격

    private Prosody() {}

    public static Result compute(float[] x) {
        final int T = x.length >= FL ? (x.length - FL) / HOP + 1 : 0;
        Result r = new Result();
        if (T == 0) return r;
        double[] db = new double[T];
        double loud = -1e300;
        for (int t = 0; t < T; t++) {
            double s = 0;
            for (int k = 0; k < FL; k++) { double v = x[t * HOP + k]; s += v * v; }
            double rms = Math.sqrt(s / FL) + 1e-8;
            db[t] = 20 * Math.log10(rms);
            loud = Math.max(loud, db[t]);
        }
        boolean[] active = new boolean[T];
        int nAct = 0;
        for (int t = 0; t < T; t++) { active[t] = db[t] > loud - 25; if (active[t]) nAct++; }
        // 높낮이: 정규화 자기상관 (60~500Hz → 지연 32~265)
        final int lo = SR / 500, hi = SR / 60;
        double[] w = new double[FL];
        boolean[] voiced = new boolean[T];
        double[] semis = new double[T];
        int nv = 0;
        for (int t = 0; t < T; t++) {
            double mean = 0;
            for (int k = 0; k < FL; k++) mean += x[t * HOP + k];
            mean /= FL;
            for (int k = 0; k < FL; k++) w[k] = x[t * HOP + k] - mean;
            double a0 = 0;
            for (int k = 0; k < FL; k++) a0 += w[k] * w[k];
            double peak = -1e300; int lag = 0;
            for (int L = lo; L < hi; L++) {
                double s = 0;
                for (int k = 0; k + L < FL; k++) s += w[k] * w[k + L];
                double v = s / (a0 + 1e-8);
                if (v > peak) { peak = v; lag = L; }
            }
            voiced[t] = peak > 0.5 && active[t];
            if (voiced[t]) semis[nv++] = 12 * (Math.log((double) SR / lag / 100.0) / Math.log(2));
        }
        // 빠르기: 90ms 로 편 크기 곡선에서 앞뒤 100ms 안 최댓값이면서 주변(150ms) 골보다 3dB 이상 솟은 봉우리 = 음절
        double[] env = new double[T];
        for (int i = 0; i < T; i++) {
            double s = 0; int n = 0;
            for (int j = Math.max(0, i - 4); j <= Math.min(T - 1, i + 4); j++) { s += db[j]; n++; }
            env[i] = s / n;
        }
        int pk = 0;
        for (int i = 0; i < T; i++) {
            double mx = -1e300, mn = 1e300;
            for (int j = Math.max(0, i - 10); j <= Math.min(T - 1, i + 10); j++) mx = Math.max(mx, env[j]);
            for (int j = Math.max(0, i - 15); j <= Math.min(T - 1, i + 15); j++) mn = Math.min(mn, env[j]);
            if (env[i] == mx && env[i] - mn >= 3 && env[i] > loud - 25) pk++;
        }
        double actSec = nAct * (double) HOP / SR;
        r.rate = pk / Math.max(actSec, 0.5);
        r.loudDb = loud;
        if (nAct > 1) {
            double m = 0; for (int t = 0; t < T; t++) if (active[t]) m += db[t];
            m /= nAct;
            double v = 0; for (int t = 0; t < T; t++) if (active[t]) v += (db[t] - m) * (db[t] - m);
            r.loudVar = Math.sqrt(v / (nAct - 1));
        }
        int nVoiced = 0; for (boolean b : voiced) if (b) nVoiced++;
        r.voicedRatio = nVoiced / (double) T;
        if (nv > 0) {
            double[] s = Arrays.copyOf(semis, nv);
            Arrays.sort(s);
            r.pitchMed = 100 * Math.pow(2, s[(nv - 1) / 2] / 12);       // torch.median = 아래쪽 가운데값
        }
        if (nv > 2) {
            double m = 0; for (int i = 0; i < nv; i++) m += semis[i];
            m /= nv;
            double v = 0; for (int i = 0; i < nv; i++) v += (semis[i] - m) * (semis[i] - m);
            r.pitchVar = Math.sqrt(v / (nv - 1));
        }
        return r;
    }
}
