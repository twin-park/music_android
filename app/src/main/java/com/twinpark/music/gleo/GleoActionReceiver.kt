package com.twinpark.music.gleo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.twinpark.music.audio.BgmController
import com.twinpark.music.audio.BgmState
import com.twinpark.music.audio.SituationDirector
import com.twinpark.music.domain.GleoCommands
import org.json.JSONObject

private object BgmMoodControl : MoodControl {
    override val ready: Boolean get() = BgmController.state.value == BgmState.Ready
    override fun currentMoodId(): String = BgmController.moodId.value

    override fun setMood(engineId: String) {
        SituationDirector.manualOverride()
        BgmController.requestMood(engineId)
    }
}

/** Gleo 음성 비서가 `component: "service"` 함수를 부르면 인텐트 action이 함수 이름이고 `ACTION_PARAM`에 값(JSON)이 담겨 방송으로 온다. */
class GleoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val params = intent.getStringExtra(EXTRA_PARAM)
        val reply = handler.handle(GleoCommands.parse(action, params))
        Log.i(TAG, "$action -> ${reply.status.wire}: ${reply.message}")
        context.sendBroadcast(response(context, reply))
    }

    private fun response(context: Context, reply: GleoReply): Intent {
        val json = JSONObject()
            .put("functionName", reply.functionName)
            .put("status", reply.status.wire)
            .put("message", reply.message)
        reply.data?.let { json.put("data", it) }
        return Intent(ACTION_RESPONSE)
            .putExtra("packageName", context.packageName)
            .putExtra("response", json.toString())
    }

    private companion object {
        const val TAG = "Gleo"
        const val EXTRA_PARAM = "ACTION_PARAM"
        const val ACTION_RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"

        // 받을 때마다 수신기가 새로 만들어지므로 "자는 모드 이전 분위기"를 기억하는 처리기는 프로세스에서 하나만 둔다.
        val handler = GleoActionHandler(BgmMoodControl)
    }
}
