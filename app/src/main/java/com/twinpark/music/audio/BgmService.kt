package com.twinpark.music.audio

import com.twinpark.music.domain.AppSensors
import com.twinpark.music.domain.DebugSoundSensor
import com.twinpark.music.domain.heardAll
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import android.util.Log
import com.twinpark.music.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 배경음악을 앱 화면 밖에서도 이어 가는 포그라운드 서비스.
 *
 * - 화면이 닫히거나 앱이 뒤로 가도 알림을 달고 계속 연주한다.
 * - 오디오 포커스: 통화/영구 손실이면 멈추고, 잠깐 빼앗기면(내비 안내 등) 멈췄다가 돌아오면 이어 연주하고,
 *   "낮춰 써도 됨"이면 볼륨만 낮춘다. 이어폰을 뽑으면(소음 방지) 멈춘다.
 * - 알림에서 일시정지/재생, 끝내기를 할 수 있다.
 */
class BgmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var audio: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var pausedByFocus = false
    private var ending = false
    private var notifyJob: Job? = null
    private var senseJob: Job? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        Log.i(TAG, "오디오 포커스 변화: ${focusName(change)}")
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                BgmController.duck(false)
                if (pausedByFocus) { pausedByFocus = false; BgmController.resume() }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> BgmController.duck(true)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { pausedByFocus = true; BgmController.pause() }
            AudioManager.AUDIOFOCUS_LOSS -> { pausedByFocus = false; BgmController.pause() }     // 다른 앱이 가져감: 알림에서 다시 재생
        }
        refreshNotification()
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) { pausedByFocus = false; BgmController.pause(); refreshNotification() }
        }
    }

    // 개발용: `adb shell am broadcast -a com.twinpark.music.DEBUG_WORDS --es words "아기 울음,속삭임" -p com.twinpark.music`
    // 귀가 들은 것으로 칠 단어를 넣는다(빈 값이면 비움). 디버그 빌드에서만 등록한다.
    private val debugWordsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val w = intent.getStringExtra("words").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            DebugSoundSensor.fromAdb = w
            Log.i(TAG, "디버그 단어: $w")
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            registerReceiver(debugWordsReceiver, IntentFilter(DEBUG_WORDS_ACTION), RECEIVER_EXPORTED)
        }
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        createChannel()
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService 로 시작됐으면 5초 안에 알림을 달아야 한다. 엔진을 읽기 전에 먼저 한다.
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        when (intent?.action) {
            ACTION_TOGGLE -> toggle()
            ACTION_PLAY -> if (BgmController.paused.value) toggle()
            ACTION_PAUSE -> if (!BgmController.paused.value) toggle()
            ACTION_STOP -> finish()
            else -> begin()
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        if (!requestFocus()) Log.w(TAG, "오디오 포커스를 얻지 못함")
        BgmController.start(applicationContext)
        if (senseJob == null) senseJob = scope.launch(Dispatchers.Main) {
            while (true) {
                SituationDirector.observe(AppSensors.all.heardAll())
                delay(SENSE_INTERVAL_MS)
            }
        }
        if (notifyJob == null) notifyJob = scope.launch {
            while (true) {                                  // 알림 글자를 지금 곡 상태로 갱신
                refreshNotification()
                delay(2000)
                if (ending && BgmController.status()?.playing == false) { stopSelfAfterEnd(); break }
            }
        }
    }

    private fun focusName(c: Int) = when (c) {
        AudioManager.AUDIOFOCUS_GAIN -> "GAIN(되돌려 받음)"
        AudioManager.AUDIOFOCUS_LOSS -> "LOSS(영구 손실)"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT(잠깐 손실)"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK(낮춰도 됨)"
        else -> "알 수 없음($c)"
    }

    private fun toggle() {
        if (BgmController.paused.value) {
            if (!requestFocus()) return
            pausedByFocus = false
            BgmController.resume()
        } else {
            pausedByFocus = false
            BgmController.pause()
        }
        refreshNotification()
    }

    /** 곡을 마무리하고(끝 페이드) 끝난 뒤 서비스를 닫는다. 이미 멈춰 있으면 바로 닫는다. */
    private fun finish() {
        if (BgmController.paused.value || BgmController.state.value != BgmState.Ready) { stopSelfAfterEnd(); return }
        ending = true
        BgmController.end()
        scope.launch { delay(8000); stopSelfAfterEnd() }      // 끝 페이드가 안 끝나도 8초 뒤엔 닫는다
    }

    private fun stopSelfAfterEnd() {
        BgmMediaSession.deactivate()
        abandonFocus()
        BgmController.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun requestFocus(): Boolean {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .setWillPauseWhenDucked(false)                  // 낮추는 건 엔진이 직접 한다
            .build()
        focusRequest = req
        return audio.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    // ---- 알림 ----
    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "배경음악", NotificationManager.IMPORTANCE_LOW))
    }

    private fun buildNotification(): Notification {
        val paused = BgmController.paused.value
        val status = BgmController.status()
        val text = when {
            BgmController.state.value is BgmState.Failed -> (BgmController.state.value as BgmState.Failed).reason
            BgmController.state.value != BgmState.Ready -> "음악 준비 중..."
            paused -> "일시정지"
            status != null && status.moodName.isNotEmpty() -> "${status.moodName} · ${status.key}조 · ♩=${status.bpm}"
            else -> "연주를 시작합니다"
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(label: String, action: String, code: Int): Notification.Action = Notification.Action.Builder(
            null, label,
            PendingIntent.getService(this, code, Intent(this, BgmService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE),
        ).build()
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("AI IS CONDUCTING")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(action(if (paused) "재생" else "일시정지", ACTION_TOGGLE, 1))
            .addAction(action("END", ACTION_STOP, 2))
            .build()
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        BgmMediaSession.update(
            applicationContext,
            PlaybackModel.from(BgmController.state.value, BgmController.paused.value, BgmController.status()),
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        runCatching { unregisterReceiver(noisyReceiver) }
        runCatching { unregisterReceiver(debugWordsReceiver) }
        abandonFocus()
        BgmController.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BgmService"
        private const val CHANNEL_ID = "bgm"
        private const val NOTIFICATION_ID = 1
        private const val SENSE_INTERVAL_MS = 2000L
        const val ACTION_TOGGLE = "com.twinpark.music.action.TOGGLE"
        const val ACTION_STOP = "com.twinpark.music.action.STOP"
        const val ACTION_PLAY = "com.twinpark.music.action.PLAY"
        const val ACTION_PAUSE = "com.twinpark.music.action.PAUSE"
        private const val DEBUG_WORDS_ACTION = "com.twinpark.music.DEBUG_WORDS"

        /** 서비스를 시작한다(이미 돌고 있으면 그대로). 화면이 앞에 있을 때 불러야 한다. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, BgmService::class.java))
        }

        /** 곡을 마무리하고 서비스를 닫는다. */
        fun stop(context: Context) {
            context.startService(Intent(context, BgmService::class.java).setAction(ACTION_STOP))
        }
    }
}
