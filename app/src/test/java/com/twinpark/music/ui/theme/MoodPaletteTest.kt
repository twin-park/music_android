package com.twinpark.music.ui.theme

import androidx.compose.ui.graphics.toArgb
import com.twinpark.music.domain.Mood
import org.junit.Assert.assertEquals
import org.junit.Test

class MoodPaletteTest {
    // 순서: base, mid, peak, sky 5개, glow, cloudLit, cloudShade, cloudWarm (Mood에서 팔레트로 옮기기 전 값)
    private val expected = mapOf(
        0 to listOf(0xFF0B4D38L, 0xFF33D98CL, 0xFFE6F58CL, 0xFF1B4B36L, 0xFF14412DL, 0xFF0E3524L, 0xFF0A2C1EL, 0xFF07231AL, 0xFF6FA085L, 0xFF86AE93L, 0xFF123626L, 0xFF4E7A62L),
        1 to listOf(0xFF0E1838L, 0xFF2A4A8AL, 0xFF6C7FD8L, 0xFF14305EL, 0xFF0F2550L, 0xFF0B1C42L, 0xFF081533L, 0xFF050C22L, 0xFF5F86C8L, 0xFF7F9CC4L, 0xFF0A1936L, 0xFF23406EL),
        2 to listOf(0xFF1B2A5CL, 0xFF3FA9F5L, 0xFFB46BFFL, 0xFF6C7C86L, 0xFF55656FL, 0xFF424F58L, 0xFF323D45L, 0xFF232C33L, 0xFF9AA8B1L, 0xFF8E9DA6L, 0xFF1C262DL, 0xFF55646DL),
        3 to listOf(0xFF3A2A6EL, 0xFFFF8A2BL, 0xFFFF3D6EL, 0xFFFFC857L, 0xFFFF9A2EL, 0xFFE2644AL, 0xFF5B4694L, 0xFF2E2E6EL, 0xFFFFE9A8L, 0xFFFFD9A8L, 0xFF3E3270L, 0xFFC27A68L),
        4 to listOf(0xFF33A0FFL, 0xFFEBF5FFL, 0xFFFFE673L, 0xFF9FD8FFL, 0xFF6FB8F5L, 0xFF4A98E0L, 0xFF3A7FCCL, 0xFF2E66AAL, 0xFFFFF2B0L, 0xFFF2F8FFL, 0xFF5A86B8L, 0xFFFFE9A0L),
        5 to listOf(0xFF3E2A22L, 0xFFF57512L, 0xFFFFBD4DL, 0xFF5A4440L, 0xFF4B3835L, 0xFF3C2D2BL, 0xFF2E2322L, 0xFF221A19L, 0xFF8A6A5EL, 0xFFB89A8CL, 0xFF2A1F1EL, 0xFF7A5446L),
    )

    @Test
    fun everyPresetHasItsOwnPalette() {
        assertEquals(Mood.Presets.size, Mood.Presets.map { it.orbPose }.toSet().size)
        Mood.Presets.forEach { it.palette }
    }

    @Test
    fun skyHasFiveSteps() {
        Mood.Presets.forEach { assertEquals(it.label, 5, it.palette.sky.size) }
    }

    @Test
    fun colorsAreUnchangedByTheMove() {
        Mood.Presets.forEach { mood ->
            val p = mood.palette
            val actual = (listOf(p.base, p.mid, p.peak) + p.sky + listOf(p.glow, p.cloudLit, p.cloudShade, p.cloudWarm))
                .map { it.toArgb().toLong() and 0xFFFFFFFFL }
            assertEquals(mood.label, expected.getValue(mood.orbPose), actual)
        }
    }
}
