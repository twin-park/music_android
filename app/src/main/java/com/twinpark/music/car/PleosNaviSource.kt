package com.twinpark.music.car

import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log

/** Pleos NaviHelper SDK로 내비 이벤트를 받는다. 내비 서비스가 없거나 초기화에 실패하면 아무 일도 하지 않는다. */
class PleosNaviSource(private val context: Context, private val onArrived: () -> Unit) : AutoCloseable {
    private var helper: NaviHelper? = null

    private val listener = object : NaviHelperEventListener {
        override fun onNavigationStatus(status: NavigationStatus) {
            Log.i(TAG, "내비 상태 $status")
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            Log.i(TAG, "목적지 도착: ${info.name}")
            onArrived()
        }
    }

    fun start() {
        val client = runCatching { NaviHelper(context) }
            .onFailure { Log.i(TAG, "NaviHelper SDK를 쓸 수 없음: ${it.javaClass.simpleName}") }
            .getOrNull() ?: return
        helper = client
        runCatching {
            client.initialize(
                { runCatching { client.addListener(listener) }.onFailure { Log.w(TAG, "리스너 등록 실패: ${it.message}") } },
                { Log.w(TAG, "초기화 실패: ${it.javaClass.simpleName} ${it.message}") },
            )
        }.onFailure { Log.w(TAG, "초기화 호출 실패: ${it.javaClass.simpleName} ${it.message}") }
    }

    override fun close() {
        val client = helper ?: return
        helper = null
        runCatching { client.removeListener(listener) }
        runCatching { client.release() }
    }

    private companion object {
        const val TAG = "PleosNavi"
    }
}
