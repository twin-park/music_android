package com.twinpark.music.audio

import android.media.browse.MediaBrowser
import android.os.Bundle
import android.service.media.MediaBrowserService

/**
 * 차량 미디어 센터가 앱을 찾아 연결하는 입구. 탐색할 목록은 없고 미디어 세션만 건네 준다.
 * 제어 액션이 있는 [BgmService]는 외부에 공개하지 않고, 이 서비스만 공개한다.
 */
class BgmBrowserService : MediaBrowserService() {
    override fun onCreate() {
        super.onCreate()
        sessionToken = BgmMediaSession.token(applicationContext)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot = BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) {
        result.sendResult(mutableListOf())
    }

    private companion object {
        const val ROOT_ID = "root"
    }
}
