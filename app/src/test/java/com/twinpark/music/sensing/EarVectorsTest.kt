package com.twinpark.music.sensing

import com.carbgm.ear.EarWords
import com.carbgm.ear.Prosody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * 모델 묶음의 시험 소리 6개로 모델 바깥 규칙(말투 숫자, 확률 → 단어)이 파이썬과 같은지 본다.
 * 모델이 낸 확률은 `expected.txt`의 값을 쓴다. 모델 자체는 기기 시험 `EarModelInstrumentedTest`가 본다.
 */
class EarVectorsTest {
    private class Vector(val name: String, val sounds: FloatArray, val emotion: FloatArray, val prosody: FloatArray, val words: String)

    private val thresholds = FloatArray(EarWords.SOUND_IDS.size).also { out ->
        val json = JSONObject(File("src/main/assets/ear/thresholds.json").readText())
        for (i in out.indices) out[i] = json.getDouble(EarWords.SOUND_IDS[i]).toFloat()
    }

    private fun resource(name: String) = checkNotNull(javaClass.getResourceAsStream("/ear/$name")) { "시험 자료 없음: $name" }.use { it.readBytes() }

    private fun numbers(line: String) = line.trim().split(Regex("""\s+""")).drop(1).map { it.toFloat() }.toFloatArray()

    private fun vectors(): List<Vector> {
        val lines = resource("expected.txt").toString(Charsets.UTF_8).lines()
        return lines.chunked(5).filter { it.size >= 5 }.map {
            Vector(it[0].removePrefix("name").trim(), numbers(it[1]), numbers(it[2]), numbers(it[3]), it[4].removePrefix("words").trim())
        }
    }

    private fun wav16(bytes: ByteArray): FloatArray {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        while (pos + 8 <= b.limit()) {                       // RIFF 조각을 따라가며 data를 찾는다
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = b.getInt(pos + 4)
            if (id == "data") return FloatArray(size / 2) { b.getShort(pos + 8 + 2 * it) / 32768f }
            pos += 8 + size + (size and 1)
        }
        error("data 조각 없음")
    }

    @Test fun vectorsAreSix() {
        assertEquals(6, vectors().size)
    }

    @Test fun prosodyAndWordsMatchPython() {
        for (v in vectors()) {
            val p = Prosody.compute(wav16(resource("${v.name}.wav")))
            // expected.txt 의 말투 순서: loud_db, loud_var, pitch_var, rate, voiced_ratio, pitch_med
            val got = doubleArrayOf(p.loudDb, p.loudVar, p.pitchVar, p.rate, p.voicedRatio, p.pitchMed)
            for (k in got.indices) {
                val tol = 1e-3 * max(1f, abs(v.prosody[k])) + 1e-3
                assertTrue("${v.name} 말투[$k] 자바 ${got[k]} 파이썬 ${v.prosody[k]}", abs(got[k] - v.prosody[k]) <= tol)
            }
            val words = EarWords.toWords(v.sounds, thresholds, v.emotion, p).joinToString(" | ") { "${it.word}:${it.score}:${it.kind}" }
            assertEquals(v.name, v.words, words)
        }
    }
}
