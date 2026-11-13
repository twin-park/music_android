package com.twinpark.music.gleo

import com.twinpark.music.domain.GleoCommand
import com.twinpark.music.domain.GleoCommands
import com.twinpark.music.domain.GleoMood
import com.twinpark.music.domain.Mood
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GleoCommandsTest {
    @Test
    fun parsesAKnownMood() {
        assertEquals(GleoCommand.SetMood(GleoMood.ClassicalPiano), GleoCommands.parse("SET_MUSIC_MOOD", """{"mood":"classical_piano"}"""))
    }

    @Test
    fun unknownOrMissingMoodNeedsInput() {
        val expected = GleoCommand.NeedsInput("SET_MUSIC_MOOD", "mood")
        assertEquals(expected, GleoCommands.parse("SET_MUSIC_MOOD", """{"mood":"heavy_metal"}"""))
        assertEquals(expected, GleoCommands.parse("SET_MUSIC_MOOD", "{}"))
        assertEquals(expected, GleoCommands.parse("SET_MUSIC_MOOD", null))
        assertEquals(expected, GleoCommands.parse("SET_MUSIC_MOOD", "not json"))
    }

    @Test
    fun sleepModeAcceptsBooleanOrBooleanText() {
        assertEquals(GleoCommand.SetSleep(true), GleoCommands.parse("SET_SLEEP_MODE", """{"enabled":true}"""))
        assertEquals(GleoCommand.SetSleep(false), GleoCommands.parse("SET_SLEEP_MODE", """{"enabled":false}"""))
        assertEquals(GleoCommand.SetSleep(true), GleoCommands.parse("SET_SLEEP_MODE", """{"enabled":"true"}"""))
    }

    @Test
    fun sleepModeWithoutAValidValueNeedsInput() {
        val expected = GleoCommand.NeedsInput("SET_SLEEP_MODE", "enabled")
        assertEquals(expected, GleoCommands.parse("SET_SLEEP_MODE", "{}"))
        assertEquals(expected, GleoCommands.parse("SET_SLEEP_MODE", """{"enabled":"maybe"}"""))
    }

    @Test
    fun otherActionsAreUnsupported() {
        assertEquals(GleoCommand.Unsupported("PLAY_SOMETHING"), GleoCommands.parse("PLAY_SOMETHING", "{}"))
        assertEquals(GleoCommand.Unsupported(null), GleoCommands.parse(null, null))
    }

    @Test
    fun everyGleoMoodMapsToARealEngineMood() {
        val engineIds = Mood.EngineMoods.map { it.id }.toSet()
        assertEquals(engineIds, GleoMood.entries.map { it.engineId }.toSet())
        assertEquals(GleoMood.entries.size, GleoMood.entries.map { it.wire }.toSet().size)
    }
}

class GleoActionHandlerTest {
    private class FakeControl(override var ready: Boolean = true, var current: String = "평온한재즈") : MoodControl {
        val history = mutableListOf<String>()
        override fun currentMoodId() = current
        override fun setMood(engineId: String) {
            current = engineId
            history += engineId
        }
    }

    @Test
    fun settingAMoodSwitchesTheEngineMood() {
        val control = FakeControl()
        val reply = GleoActionHandler(control).handle(GleoCommand.SetMood(GleoMood.NightDrive))
        assertEquals(ReplyStatus.Success, reply.status)
        assertEquals("밤드라이브", control.current)
        assertEquals("""[{"mood":"night_drive"}]""", reply.data)
    }

    @Test
    fun notRunningFailsWithoutChangingAnything() {
        val control = FakeControl(ready = false)
        val handler = GleoActionHandler(control)
        assertEquals(ReplyStatus.Fail, handler.handle(GleoCommand.SetMood(GleoMood.NightDrive)).status)
        assertEquals(ReplyStatus.Fail, handler.handle(GleoCommand.SetSleep(true)).status)
        assertTrue(control.history.isEmpty())
    }

    @Test
    fun sleepModeOnSwitchesToLullabyAndOffRestoresThePreviousMood() {
        val control = FakeControl(current = "낮드라이브")
        val handler = GleoActionHandler(control)
        handler.handle(GleoCommand.SetSleep(true))
        assertEquals("아이재우기", control.current)
        val off = handler.handle(GleoCommand.SetSleep(false))
        assertEquals(ReplyStatus.Success, off.status)
        assertEquals("낮드라이브", control.current)
    }

    @Test
    fun turningSleepOnTwiceKeepsTheOriginalMood() {
        val control = FakeControl(current = "낮드라이브")
        val handler = GleoActionHandler(control)
        handler.handle(GleoCommand.SetSleep(true))
        handler.handle(GleoCommand.SetSleep(true))
        handler.handle(GleoCommand.SetSleep(false))
        assertEquals("낮드라이브", control.current)
    }

    @Test
    fun sleepOffWhenNotOnChangesNothing() {
        val control = FakeControl(current = "낮드라이브")
        GleoActionHandler(control).handle(GleoCommand.SetSleep(false))
        assertTrue(control.history.isEmpty())
    }

    @Test
    fun choosingAMoodCancelsTheSleepRestore() {
        val control = FakeControl(current = "낮드라이브")
        val handler = GleoActionHandler(control)
        handler.handle(GleoCommand.SetSleep(true))
        handler.handle(GleoCommand.SetMood(GleoMood.CheerfulConversation))
        handler.handle(GleoCommand.SetSleep(false))
        assertEquals("즐거운대화", control.current)
    }

    @Test
    fun missingInputAsksWithTheMissingField() {
        val reply = GleoActionHandler(FakeControl()).handle(GleoCommand.NeedsInput("SET_MUSIC_MOOD", "mood"))
        assertEquals(ReplyStatus.Asking, reply.status)
        assertEquals("""[{"missingField":"mood"}]""", reply.data)
    }

    @Test
    fun unsupportedFunctionFails() {
        assertEquals(ReplyStatus.Fail, GleoActionHandler(FakeControl()).handle(GleoCommand.Unsupported("X")).status)
    }
}

class GleoSchemaTest {
    private val schema = JSONObject(File("src/main/assets/schema.json").readText())
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val functions = schema.getJSONArray("actionCalls").let { calls -> (0 until calls.length()).map { calls.getJSONObject(it) } }

    @Test
    fun followsTheDocumentedLimits() {
        assertTrue("actionCalls는 최대 3개", functions.size in 1..3)
        assertEquals("com.twinpark.music", schema.getString("packageName"))
        functions.forEach { call ->
            assertEquals("function", call.getString("type"))
            val function = call.getJSONObject("function")
            assertTrue(function.getString("component") in setOf("activity", "service"))
            assertTrue("함수 이름은 대문자와 밑줄", Regex("[A-Z]+(_[A-Z]+)+").matches(function.getString("name")))
            assertTrue("설명은 영어", function.getString("description").all { it.code < 128 })
        }
        assertEquals("함수 이름은 겹치지 않는다", functions.size, functions.map { it.getJSONObject("function").getString("name") }.toSet().size)
    }

    @Test
    fun parametersFollowTheRules() {
        functions.forEach { call ->
            val parameters = call.getJSONObject("function").getJSONObject("parameters")
            assertEquals("object", parameters.getString("type"))
            val properties = parameters.getJSONObject("properties")
            for (name in properties.keys()) {
                val property = properties.getJSONObject(name)
                assertTrue(property.get("type") is String)
                assertTrue(property.getString("type") in setOf("string", "number", "boolean", "array"))
                assertTrue("설명은 영어", property.getString("description").all { it.code < 128 })
                if (property.has("enum")) {
                    val values = property.getJSONArray("enum")
                    (0 until values.length()).forEach { assertTrue("enum은 문자열만", values.get(it) is String) }
                }
            }
            val required = parameters.getJSONArray("required")
            (0 until required.length()).forEach { assertTrue(properties.has(required.getString(it))) }
        }
    }

    @Test
    fun moodEnumMatchesTheCode() {
        val function = functions.first { it.getJSONObject("function").getString("name") == GleoCommands.SET_MUSIC_MOOD }.getJSONObject("function")
        val values: JSONArray = function.getJSONObject("parameters").getJSONObject("properties").getJSONObject("mood").getJSONArray("enum")
        assertEquals(GleoMood.entries.map { it.wire }, (0 until values.length()).map { values.getString(it) })
    }

    @Test
    fun everyFunctionHasAManifestAction() {
        functions.forEach {
            val name = it.getJSONObject("function").getString("name")
            assertTrue("$name 의 action이 매니페스트에 있어야 한다", manifest.contains("""<action android:name="$name" />"""))
        }
    }
}
