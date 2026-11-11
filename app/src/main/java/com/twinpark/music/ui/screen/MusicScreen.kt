package com.twinpark.music.ui.screen

import android.app.Activity
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import com.twinpark.music.audio.BgmController
import com.twinpark.music.audio.BgmService
import com.twinpark.music.audio.BgmState
import com.twinpark.music.audio.BgmStatus
import com.twinpark.music.audio.SituationDirector
import com.twinpark.music.domain.Mood
import com.twinpark.music.domain.TrackerState
import com.twinpark.music.domain.Weather
import com.twinpark.music.ui.component.CloudKnob
import com.twinpark.music.ui.component.orb.BeadOrb

/**
 * 메인 화면 (프로토타입).
 *
 * Pleos UI 가이드: 모든 앱은 Landscape와 Portrait 두 레이아웃을 지원해야 한다.
 * - 가로: 왼쪽 구름 노브 + 오른쪽 오브 (Figma 시안)
 * - 세로: 위 오브 + 아래 구름 노브 (시안 없음, 임시 배치. 디자이너 확인 필요)
 *
 * 분위기 버튼과 저모션 스위치는 개발용 디버그 컨트롤이다.
 * 실제 앱에서는 AI(MoodEngine)와 주행 규제(CarUxRestrictions) 신호가 대신한다.
 */
@Composable
fun MusicScreen(modifier: Modifier = Modifier) {
    // 화면을 다시 열면 연주 중이던 분위기를 이어서 보여 준다
    // 분위기는 컨트롤러가 쥔다: 상황 판단(귀가 알려 준 단어)이나 사용자 조작이 바꾸고, 오브와 하늘은 그에 맞는 장면을 쓴다
    val engineId by BgmController.moodId.collectAsState()
    val mood = Mood.forEngine(engineId)
    var sensed by remember { mutableStateOf(emptySet<String>()) }        // 디버그: 귀가 들은 것으로 치는 단어들
    val situation by SituationDirector.state.collectAsState()
    var reduced by remember { mutableStateOf(false) }
    var volume by rememberSaveable { mutableFloatStateOf(0.7f) }

    // 배경음악: 엔진은 포그라운드 서비스가 쥐고 있어서 화면을 닫아도 계속 연주한다. 화면은 상태를 보여 주고 요청만 보낸다.
    val context = LocalContext.current
    val bgmState by BgmController.state.collectAsState()
    val bgmPaused by BgmController.paused.collectAsState()
    var status by remember { mutableStateOf<BgmStatus?>(null) }
    LaunchedEffect(Unit) { BgmService.start(context) }
    // 귀가 2초마다 단어를 알려 주는 것을 흉내 낸다(지금은 디버그 칩이 고른 단어). 귀 모델이 붙으면 그쪽이 같은 함수를 부른다.
    LaunchedEffect(sensed) {
        while (true) {
            SituationDirector.observe(sensed)
            delay(2000)
        }
    }
    // 음량 노브 0..1은 소리 크기에 제곱을 취해 귀에 고르게 느껴지게 한다
    LaunchedEffect(volume) { BgmController.setVolume(volume * volume) }
    LaunchedEffect(bgmState) {
        while (bgmState == BgmState.Ready) {
            status = BgmController.status()
            delay(500)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF141B3A), Color(0xFF0B0F24))))
            .safeDrawingPadding(),
    ) {
        val landscape = maxWidth > maxHeight

        val knob: @Composable (Modifier) -> Unit = { m ->
            CloudKnob(
                volume = volume,
                onVolumeChange = { volume = it },
                mood = mood,
                reducedMotion = reduced,
                modifier = m,
            )
        }
        val orb: @Composable (Modifier) -> Unit = { m ->
            OrbPanel(
                mood = mood,
                engineId = engineId,
                onEngineChange = { SituationDirector.manualOverride(); BgmController.requestMood(it) },
                sensed = sensed,
                onSensedChange = { sensed = it },
                situation = situation,
                reduced = reduced,
                onReducedChange = { reduced = it },
                bgmState = bgmState,
                paused = bgmPaused,
                status = status,
                pulse = BgmController::pulse,
                beat = BgmController::beat,
                onEnd = { BgmService.stop(context) },
                modifier = m,
            )
        }

        val shortSide = if (maxWidth < maxHeight) maxWidth else maxHeight
        val uiScale = (shortSide / 700.dp).coerceIn(1f, 1.7f)
        CompositionLocalProvider(LocalUiScale provides uiScale) {
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Figma 시안 비율: 구름 영역 약 55%, 오른쪽 오브 영역 약 45%
                    knob(Modifier.weight(0.55f).fillMaxHeight())
                    orb(Modifier.weight(0.45f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    orb(Modifier.weight(1.35f).fillMaxWidth())
                    knob(Modifier.weight(0.65f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun OrbPanel(
    mood: Mood,
    engineId: String,
    onEngineChange: (String) -> Unit,
    sensed: Set<String>,
    onSensedChange: (Set<String>) -> Unit,
    situation: TrackerState?,
    reduced: Boolean,
    onReducedChange: (Boolean) -> Unit,
    bgmState: BgmState,
    paused: Boolean,
    status: BgmStatus?,
    pulse: () -> Float,
    beat: () -> Float,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    var devOpen by remember { mutableStateOf(false) }
    // 가로 화면(폰은 높이 360dp 안팎)처럼 낮으면 컴팩트: 오브가 늘 보이도록 날씨 표를 줄이고 버튼을 낮춘다.
    BoxWithConstraints(modifier) {
        val compact = maxHeight < COMPACT_HEIGHT
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
            // 제목: 위 가로선 + "AI가 연주중"
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White))
                Text("AI가 연주중", color = Color.White, fontSize = ui(16f), modifier = Modifier.padding(top = 4.dp))
                Text(
                    text = when (bgmState) {
                        BgmState.Idle, BgmState.Loading -> "음악 준비 중..."
                        BgmState.Ready -> if (paused) "일시정지" else status?.let { "${it.moodName} · ${it.role} · ${it.key}조 · ♩=${it.bpm}" + if (it.underruns > 0) " · 끊김 ${it.underruns}" else "" } ?: "연주를 시작합니다"
                        is BgmState.Failed -> bgmState.reason
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = ui(12f),
                    maxLines = 1,
                )
            }
            BeadOrb(
                // 오브 모양은 분위기(음악 종류)가 정한다. 분위기마다 Figma 시안 한 장이 대응한다.
                pose = mood.orbPose,
                speed = mood.speed,
                reducedMotion = reduced,
                pulse = pulse,
                beat = beat,
                modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = 90.dp),
            )
            WeatherDetail(mood.weather, compact)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val btnPad = if (compact) PaddingValues(horizontal = 12.dp, vertical = 2.dp) else ButtonDefaults.ContentPadding
                val btnMod = Modifier.weight(1f).height(((if (compact) 36 else 44) * LocalUiScale.current).dp)
                OutlinedButton(
                    onClick = {
                        onEnd()
                        (context as? Activity)?.finish()      // 화면은 바로 닫고, 연주는 서비스가 곡을 마무리한 뒤 끝낸다
                    },
                    modifier = btnMod,
                    contentPadding = btnPad,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color(0x99FFFFFF)),
                ) { Text("끝내기", fontSize = ui(if (compact) 13f else 15f)) }
                Button(
                    onClick = {
                        val list = Mood.EngineMoods
                        val at = list.indexOfFirst { it.id == engineId }
                        onEngineChange(list[(at + 1) % list.size].id)
                    },
                    modifier = btnMod,
                    contentPadding = btnPad,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) { Text("분위기 바꾸기", fontSize = ui(if (compact) 13f else 15f)) }
            }
        }
        // 개발용(디버그 빌드만): 접어 두었다가 펼치면 패널 위에 겹쳐 보인다. 오브 자리를 빼앗지 않는다.
        if (debuggable) {
            TextButton(
                onClick = { devOpen = !devOpen },
                modifier = Modifier.align(Alignment.TopEnd).height(28.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) { Text(if (devOpen) "DEV 닫기" else "DEV", color = Color(0xFF7DD3FC), fontSize = ui(12f)) }
            if (devOpen) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color(0xFF0B0F24), RoundedCornerShape(12.dp)).padding(8.dp),
                ) { DevControls(engineId, onEngineChange, sensed, onSensedChange, situation, reduced, onReducedChange) }
            }
        }
    }
}

/**
 * 창 크기에 따른 글자와 간격의 배율. 짧은 변이 700dp 이하(폰)면 1배, 차량 창(짧은 변 840~1184dp)은 약 1.2~1.7배.
 * Figma 시안은 글자가 화면 폭의 1.3~1.6%라서 1700dp 폭에서 22~27dp가 되는데, 고정 sp로는 그 절반쯤이다.
 */
internal val LocalUiScale = compositionLocalOf { 1f }

@Composable
private fun ui(base: Float): TextUnit = (base * LocalUiScale.current).sp

/** 이 높이보다 낮은 패널은 컴팩트로 그린다(날씨 표 2열, 낮은 버튼). */
private val COMPACT_HEIGHT = 480.dp

/** Figma의 WEATHER DETAIL: 제목 + 가로선, 왼쪽에 4줄 값, 오른쪽에 테두리 빈 박스. */
@Composable
private fun WeatherDetail(w: Weather, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Text("WEATHER DETAIL", color = Color.White, fontSize = ui(if (compact) 14f else 18f))
        Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(1.dp).background(Color.White))
        if (compact) {
            // 낮은 화면: 값 4개를 2x2로 놓고 빈 박스는 뺀다
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    WeatherRow("CLOUDY", w.cloudy.toString(), "%")
                    WeatherRow("WIND", w.windKmh.toString(), "km/h")
                }
                Column(Modifier.weight(1f)) {
                    WeatherRow("HUMIDITY", w.humidity.toString(), "%")
                    WeatherRow("RAIN", w.rainMm.toString(), "mm")
                }
            }
            return
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((3 * LocalUiScale.current).dp)) {
                WeatherRow("CLOUDY", w.cloudy.toString(), "%")
                WeatherRow("HUMIDITY", w.humidity.toString(), "%")
                WeatherRow("WIND", w.windKmh.toString(), "km/h")
                WeatherRow("RAIN", w.rainMm.toString(), "mm")
            }
            Box(Modifier.weight(1f).height((64 * LocalUiScale.current).dp).border(1.dp, Color.White))
        }
    }
}

@Composable
private fun WeatherRow(label: String, value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, color = Color.White, fontSize = ui(13f), modifier = Modifier.weight(1f))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            Text(value, color = Color.White, fontSize = ui(15f))
            Text(unit, color = Color.White, fontSize = ui(12f), modifier = Modifier.padding(start = 2.dp, bottom = 1.dp))
        }
    }
}

/** 개발용: 엔진 분위기 9개를 바로 고르고 저모션을 켠다. 디버그 빌드에서만 보인다. 좁으면 옆으로 밀어 본다. */
@Composable
private fun DevControls(
    engineId: String,
    onEngineChange: (String) -> Unit,
    sensed: Set<String>,
    onSensedChange: (Set<String>) -> Unit,
    situation: TrackerState?,
    reduced: Boolean,
    onReducedChange: (Boolean) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mood.EngineMoods.forEach { em ->
            val selected = em.id == engineId
            Button(
                onClick = { onEngineChange(em.id) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) Color.White else Color(0x33FFFFFF),
                    contentColor = if (selected) Color.Black else Color.White,
                ),
            ) { Text(em.label, fontSize = ui(12f), maxLines = 1) }
        }
        Button(
            onClick = { onReducedChange(!reduced) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (reduced) Color(0xFFFFB74D) else Color(0x33FFFFFF),
                contentColor = if (reduced) Color.Black else Color.White,
            ),
        ) { Text("저모션", fontSize = ui(12f), maxLines = 1) }
    }
    // 귀가 들은 것으로 칠 단어. 누른 단어가 2초마다 상황 판단에 들어가고, 같은 새 상황이 4초 이어지면 분위기가 바뀐다.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("들림:", color = Color.White.copy(alpha = 0.7f), fontSize = ui(12f))
        SENSE_WORDS.forEach { w ->
            val on = w in sensed
            Button(
                onClick = { onSensedChange(if (on) sensed - w else sensed + w) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (on) Color(0xFF7DD3FC) else Color(0x22FFFFFF),
                    contentColor = if (on) Color.Black else Color.White,
                ),
            ) { Text(w, fontSize = ui(12f), maxLines = 1) }
        }
        Button(
            onClick = { onSensedChange(emptySet()) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF), contentColor = Color.White),
        ) { Text("없음", fontSize = ui(12f)) }
    }
    Text(
        text = situationLine(situation),
        color = Color.White.copy(alpha = 0.7f),
        fontSize = ui(12f),
    )
  }
}

/** 디버그 표시: 지금 상황과, 바뀔 후보가 얼마나 이어졌는지, 통화로 낮추는 중인지 */
private fun situationLine(s: TrackerState?): String {
    if (s == null) return "상황: 판단 전"
    val cur = s.current?.let { "${it.situation} -> ${it.engineMoodId}" } ?: "아직 없음"
    val cand = s.candidate?.let { " · 후보 ${it.situation} ${s.candidateMs / 1000}초/4초" } ?: ""
    return "상황: $cur$cand" + if (s.ducking) " · 통화(낮춤)" else ""
}

/** 디버그 칩에 올릴 귀 단어(ear/words.py 가 내는 단어 중 상황에 쓰이는 것) */
private val SENSE_WORDS = listOf(
    "아기 울음", "속삭임", "화난", "고함", "슬픈", "웃음", "신난", "바다", "파도", "비", "천둥", "새소리", "도로", "경적", "전화벨",
)

// Connect-S-17 기본값의 Landscape 창(1700 x 1184 dp)과 Portrait 창(840 x 1184 dp)
@Preview(name = "Landscape 1700x1184", widthDp = 1700, heightDp = 1184)
@Composable
private fun MusicScreenLandscapePreview() {
    MusicScreen()
}

@Preview(name = "Portrait 840x1184", widthDp = 840, heightDp = 1184)
@Composable
private fun MusicScreenPortraitPreview() {
    MusicScreen()
}
