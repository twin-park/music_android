package com.twinpark.music.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 악기 크기 보정표(`app/bgm/calib_tsf.txt`)가 앱의 모든 엔진 분위기를 덮는지 본다. 빠진 분위기는 다른 분위기 평균으로 맞춰져 소리 크기가 어긋난다. */
class CalibrationTableTest {
    private val rows = File("bgm/calib_tsf.txt").readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.trim().split(Regex("""\s+""")) }
        .filter { it.size == 3 }

    @Test
    fun `모든 엔진 분위기에 마스터 이득과 악기 크기가 있다`() {
        for (mood in Mood.EngineMoods) {
            val mine = rows.filter { it[0] == mood.id }
            assertTrue("보정표에 마스터 이득이 없음: ${mood.id}", mine.any { it[1] == "_master" })
            assertTrue("보정표에 악기 크기가 없음: ${mood.id}", mine.any { it[1] != "_master" })
        }
    }

    @Test
    fun `값은 모두 숫자이고 정상 범위다`() {
        for (r in rows) {
            val v = r[2].toDoubleOrNull()
            assertTrue("숫자가 아님: $r", v != null)
            assertTrue("범위 밖: $r", v!! in -60.0..20.0)
        }
    }
}
