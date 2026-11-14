package com.carbgm.ear;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 귀 모델 출력 → 한국어 단어. 파이썬 ear/words.py 의 to_words 를 그대로 옮긴 것 (시험 벡터로 같은 결과 확인함).
 *
 * sounds  : 모델 출력 "sounds" 28개 (0~1, 아래 SOUND_IDS 순서)
 * th      : 소리마다 문턱 28개 (labels.json 의 threshold, 같은 순서)
 * emotion : 모델 출력 "emotion" 6개 (softmax 된 확률, EMOTIONS 순서)
 * p       : Prosody.compute(같은 4초 파형) — 말투 숫자. 없으면 null (말투 단어만 빠짐)
 */
public final class EarWords {
    public static final String[] SOUND_IDS = {
        "speech", "child", "baby_cry", "crying", "laughter", "shout", "whisper", "singing", "breathing", "cough",
        "music", "phone", "sea", "gull", "bird", "insect", "rain", "thunder", "wind", "water",
        "traffic", "horn", "siren", "train", "dog", "crowd", "bang", "fireworks"};
    public static final String[][] SOUND_WORDS = {
        {"대화"}, {"아이 목소리"}, {"아기 울음"}, {"울음"}, {"웃음"}, {"고함"}, {"속삭임"}, {"노래"}, {"숨소리"}, {"기침"},
        {"음악"}, {"전화벨"}, {"바다", "파도"}, {"갈매기"}, {"새소리"}, {"풀벌레"}, {"비"}, {"천둥"}, {"바람"}, {"물소리"},
        {"도로"}, {"경적"}, {"사이렌"}, {"기차"}, {"강아지"}, {"사람들", "환호"}, {"쾅 소리"}, {"불꽃놀이"}};
    public static final String[] EMOTIONS = {"anger", "disgust", "fear", "happy", "neutral", "sad"};
    public static final String[][] EMOTION_WORDS = {{"화난", "열이 난"}, {"짜증난"}, {"불안한"}, {"밝은", "신난"}, {"차분한"}, {"가라앉은", "슬픈"}};
    private static final Set<String> VOCAL = new HashSet<>(Arrays.asList("speech", "child", "baby_cry", "crying", "laughter", "shout", "whisper", "singing"));

    public static final class Word {
        public final String word;
        public final double score;
        public final String kind;           // "소리" · "감정" · "말투"
        Word(String w, double s, String k) { word = w; score = s; kind = k; }
        @Override public String toString() { return word + "(" + score + "," + kind + ")"; }
    }

    private EarWords() {}

    private static int idx(String id) {
        for (int i = 0; i < SOUND_IDS.length; i++) if (SOUND_IDS[i].equals(id)) return i;
        throw new IllegalArgumentException(id);
    }

    private static double r3(double v) { return Math.round(v * 1000.0) / 1000.0; }

    public static List<Word> toWords(float[] sounds, float[] th, float[] emotion, Prosody.Result p) {
        final LinkedHashMap<String, Word> out = new LinkedHashMap<>();
        final class Adder {
            void add(String w, double s, String kind) {
                double rs = r3(s);
                Word old = out.get(w);
                if (old == null || old.score < rs) out.put(w, new Word(w, rs, kind));   // 이미 있으면 자리(넣은 차례)는 그대로 · 점수만 바뀜
            }
        }
        Adder a = new Adder();
        // 문턱 넘은 소리 (소리 순서 그대로)
        LinkedHashMap<String, Float> on = new LinkedHashMap<>();
        for (int i = 0; i < SOUND_IDS.length; i++) if (sounds[i] >= th[i]) on.put(SOUND_IDS[i], sounds[i]);
        if (on.containsKey("baby_cry")) on.remove("crying");
        int vocal = 0;
        for (String g : on.keySet()) if (VOCAL.contains(g)) vocal++;
        if (vocal > 1) on.remove("speech");                 // 더 구체적인 목소리 단어가 있으면 '대화'는 뺌
        for (Map.Entry<String, Float> e : on.entrySet())
            for (String w : SOUND_WORDS[idx(e.getKey())]) a.add(w, e.getValue(), "소리");

        final float sp = sounds[idx("speech")];
        final boolean talking = sp >= th[idx("speech")] || on.containsKey("shout");
        if (talking) {
            int k = 0;
            for (int i = 1; i < emotion.length; i++) if (emotion[i] > emotion[k]) k = i;
            if (emotion[k] >= 0.4) for (String w : EMOTION_WORDS[k]) a.add(w, (double) emotion[k] * sp, "감정");
            if (p != null) {
                if (p.rate >= 3.8) a.add("템포 빠른", Math.min(1.0, p.rate / 5.0), "말투");
                else if (p.rate > 0 && p.rate <= 1.9) a.add("템포 느린", 0.6, "말투");
                if (p.pitchVar >= 4.0 && p.loudVar >= 8) a.add("격한", Math.min(1.0, p.pitchVar / 6.0), "말투");
            }
        }
        if (p != null) {
            if (p.loudDb >= -12) a.add("시끄러운", 0.7, "말투");
            else if (p.loudDb <= -42) a.add("조용한", 0.7, "말투");
            if (on.isEmpty() && p.loudDb <= -42) a.add("고요한", 0.8, "소리");
        }
        List<Word> res = new ArrayList<>(out.values());
        res.sort((x, y) -> Double.compare(y.score, x.score));   // 안정 정렬: 같은 점수는 넣은 차례
        return res;
    }

    /** 여러 창(예: 최근 2~3개)을 합칠 때: 소리는 최댓값, 감정은 '대화' 확률로 무게를 준 평균 (파이썬 listen.py 와 같음) */
    public static float[][] merge(List<float[]> soundsPerWin, List<float[]> emotionPerWin) {
        float[] s = new float[SOUND_IDS.length];
        double[] e = new double[EMOTIONS.length];
        double ws = 0;
        for (int w = 0; w < soundsPerWin.size(); w++) {
            float[] c = soundsPerWin.get(w);
            for (int i = 0; i < s.length; i++) s[i] = Math.max(s[i], c[i]);
            double sp = c[0];
            float[] em = emotionPerWin.get(w);
            for (int i = 0; i < e.length; i++) e[i] += em[i] * sp;
            ws += sp;
        }
        float[] ef = new float[e.length];
        for (int i = 0; i < e.length; i++) ef[i] = (float) (e[i] / Math.max(ws, 1e-6));
        return new float[][] {s, ef};
    }
}
