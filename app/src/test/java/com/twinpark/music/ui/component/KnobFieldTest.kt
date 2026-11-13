package com.twinpark.music.ui.component

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnobFieldTest {
    private val field = KnobField(width = 1000f, height = 800f, reserved = 0f, travel = 200f)
    private val eps = 1e-3f

    @Test
    fun centerPointIsTheOrigin() {
        val p = field.positionAt(Offset(500f, 400f))
        assertEquals(0f, p.x, eps)
        assertEquals(0f, p.y, eps)
    }

    @Test
    fun edgeOfTravelIsOne() {
        val right = field.positionAt(Offset(500f + 400f, 400f))
        assertEquals(1f, right.x, eps)
        assertEquals(0f, right.y, eps)
        val bottom = field.positionAt(Offset(500f, 400f + 300f))
        assertEquals(1f, bottom.y, eps)
    }

    @Test
    fun pointsOutsideTheEllipseAreClampedToItsEdge() {
        val p = field.positionAt(Offset(5000f, 4000f))
        assertEquals(1f, p.getDistance(), eps)
        assertTrue(p.x > 0f && p.y > 0f)
    }

    @Test
    fun knobIsCenteredAtTheOrigin() {
        val tl = field.topLeft(Offset.Zero, knobPx = 100f)
        assertEquals(450f, tl.x, eps)
        assertEquals(350f, tl.y, eps)
    }

    @Test
    fun topLeftAndPositionAtAgree() {
        val pos = field.positionAt(Offset(700f, 500f))
        val tl = field.topLeft(pos, knobPx = 0f)
        assertEquals(700f, tl.x, eps)
        assertEquals(500f, tl.y, eps)
    }

    @Test
    fun reservedBottomShiftsTheCenterUp() {
        val reserved = KnobField(width = 1000f, height = 800f, reserved = 100f, travel = 200f)
        assertEquals(0f, reserved.positionAt(Offset(500f, 350f)).y, eps)
    }

    @Test
    fun matchesTheOriginalInlineFormulas() {
        val width = 1234f
        val height = 877f
        val reserved = 32f
        val travel = 180f
        val knobPx = 140f
        val field = KnobField(width, height, reserved, travel)
        val random = java.util.Random(7)
        repeat(500) {
            val point = Offset(random.nextFloat() * width * 1.2f - width * 0.1f, random.nextFloat() * height * 1.2f - height * 0.1f)

            val halfW1 = ((width - travel) / 2f).coerceAtLeast(1f)
            val halfH1 = ((height - reserved - travel) / 2f).coerceAtLeast(1f)
            var expected = Offset((point.x - width / 2f) / halfW1, (point.y - (height - reserved) / 2f) / halfH1)
            val length = expected.getDistance()
            if (length > 1f) expected /= length
            val actual = field.positionAt(point)
            assertEquals(expected.x, actual.x, 1e-6f)
            assertEquals(expected.y, actual.y, 1e-6f)

            val halfW0 = ((width - travel) / 2f).coerceAtLeast(0f)
            val halfH0 = ((height - reserved - travel) / 2f).coerceAtLeast(0f)
            val expectedTopLeft = Offset(width / 2f + actual.x * halfW0 - knobPx / 2f, (height - reserved) / 2f + actual.y * halfH0 - knobPx / 2f)
            val topLeft = field.topLeft(actual, knobPx)
            assertEquals(expectedTopLeft.x, topLeft.x, 1e-6f)
            assertEquals(expectedTopLeft.y, topLeft.y, 1e-6f)
        }
    }

    @Test
    fun tinyFieldDoesNotDivideByZero() {
        val tiny = KnobField(width = 50f, height = 50f, reserved = 0f, travel = 200f)
        val p = tiny.positionAt(Offset(60f, 60f))
        assertTrue(p.getDistance() <= 1f + eps)
    }
}
