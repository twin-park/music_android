package com.twinpark.music.gleo

import com.twinpark.music.domain.GleoCommand
import com.twinpark.music.domain.GleoCommands.SET_MUSIC_MOOD
import com.twinpark.music.domain.GleoCommands.SET_SLEEP_MODE
import com.twinpark.music.domain.GleoMood

enum class ReplyStatus(val wire: String) { Success("success"), Asking("asking"), Fail("fail") }

/** Gleo에 돌려줄 결과. [data]는 JSON 배열 문자열이다. */
data class GleoReply(val functionName: String, val status: ReplyStatus, val message: String, val data: String? = null)

/** 음악 엔진에서 음성 명령이 쓰는 것. */
interface MoodControl {
    val ready: Boolean
    fun currentMoodId(): String
    fun setMood(engineId: String)
}

class GleoActionHandler(private val control: MoodControl) {
    private var moodBeforeSleep: String? = null

    fun handle(command: GleoCommand): GleoReply = when (command) {
        is GleoCommand.SetMood -> setMood(command.mood)
        is GleoCommand.SetSleep -> setSleep(command.enabled)
        is GleoCommand.NeedsInput -> GleoReply(
            command.functionName, ReplyStatus.Asking, "A valid ${command.field} value is required.", """[{"missingField":"${command.field}"}]""",
        )
        is GleoCommand.Unsupported -> GleoReply(command.functionName.orEmpty(), ReplyStatus.Fail, "This function is not supported.")
    }

    private fun setMood(mood: GleoMood): GleoReply {
        if (!control.ready) return notRunning(SET_MUSIC_MOOD)
        moodBeforeSleep = null
        control.setMood(mood.engineId)
        return GleoReply(SET_MUSIC_MOOD, ReplyStatus.Success, "The music mood was changed.", """[{"mood":"${mood.wire}"}]""")
    }

    private fun setSleep(enabled: Boolean): GleoReply {
        if (!control.ready) return notRunning(SET_SLEEP_MODE)
        if (enabled) {
            if (moodBeforeSleep == null) moodBeforeSleep = control.currentMoodId()
            control.setMood(GleoMood.Lullaby.engineId)
            return GleoReply(SET_SLEEP_MODE, ReplyStatus.Success, "The quiet sleeping mode is on.")
        }
        val previous = moodBeforeSleep ?: return GleoReply(SET_SLEEP_MODE, ReplyStatus.Success, "The quiet sleeping mode was not on.")
        moodBeforeSleep = null
        control.setMood(previous)
        return GleoReply(SET_SLEEP_MODE, ReplyStatus.Success, "The quiet sleeping mode is off.")
    }

    private fun notRunning(functionName: String) = GleoReply(functionName, ReplyStatus.Fail, "The music is not playing right now.")
}
