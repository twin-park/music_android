package com.twinpark.music.sensing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 실제 ear2 모델(ONNX Runtime)로 시험 소리 6개를 분류해, 나온 단어가 모델 묶음의 정답과 같은지 본다. */
@RunWith(AndroidJUnit4::class)
class EarModelInstrumentedTest {
    private val testAssets get() = InstrumentationRegistry.getInstrumentation().context.assets

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

    private fun expectedWords(): Map<String, Set<String>> {
        val lines = testAssets.open("ear/expected.txt").bufferedReader().readLines()
        return lines.chunked(5).filter { it.size >= 5 }.associate {
            val words = it[4].removePrefix("words").trim()
            it[0].removePrefix("name").trim() to
                if (words.isEmpty()) emptySet() else words.split(" | ").map { w -> w.substringBefore(':') }.toSet()
        }
    }

    /** expected.txt 의 "sounds" 줄 첫 값(말소리 확률)이 말소리 문턱(0.55)을 넘는 시험 소리 */
    private fun expectedTalk(): Map<String, Boolean> {
        val lines = testAssets.open("ear/expected.txt").bufferedReader().readLines()
        return lines.chunked(5).filter { it.size >= 5 }.associate {
            it[0].removePrefix("name").trim() to (it[1].removePrefix("sounds").trim().split(" ").first().toFloat() >= 0.55f)
        }
    }

    @Test fun reportsTalkLevelOnlyForClearSpeech() {
        for ((name, talking) in expectedTalk()) {
            val samples = wav16(testAssets.open("ear/$name.wav").use { it.readBytes() })
            classifier.classify(samples, OnnxSoundClassifier.SAMPLE_RATE)
            val level = com.twinpark.music.domain.TalkLevel.current(System.currentTimeMillis())
            if (talking) assertTrue("$name 말소리 크기가 있어야 함", level != null && level >= 0.55f) else assertNull("$name 은 말소리 크기가 없어야 함", level)
        }
    }

    @Test fun classifiesTestVectors() {
        val expected = expectedWords()
        assertEquals(6, expected.size)
        for ((name, words) in expected) {
            val samples = wav16(testAssets.open("ear/$name.wav").use { it.readBytes() })
            assertEquals(name, words, classifier.classify(samples, OnnxSoundClassifier.SAMPLE_RATE))
        }
    }

    companion object {
        private lateinit var classifier: OnnxSoundClassifier

        @BeforeClass @JvmStatic fun load() {
            classifier = OnnxSoundClassifier.create(InstrumentationRegistry.getInstrumentation().targetContext)
        }

        @AfterClass @JvmStatic fun unload() = classifier.close()
    }
}
