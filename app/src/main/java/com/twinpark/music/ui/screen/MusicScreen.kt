package com.twinpark.music.ui.screen

import android.app.Activity
import android.content.pm.ApplicationInfo
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
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
import com.twinpark.music.audio.MusicEngine
import com.twinpark.music.car.CarUxRestrictionsSource
import com.twinpark.music.audio.SituationDirector
import com.twinpark.music.domain.Mood
import com.twinpark.music.ui.theme.AppFonts
import com.twinpark.music.ui.theme.palette
import com.twinpark.music.domain.TimeOfDay
import com.twinpark.music.domain.WeatherSource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twinpark.music.settings.PreferencesVolumeStore
import com.twinpark.music.domain.DebugSoundSensor
import com.twinpark.music.weather.AppWeather
import com.twinpark.music.domain.WeatherState
import com.twinpark.music.domain.TrackerState
import com.twinpark.music.domain.Weather
import com.twinpark.music.ui.component.CloudKnob
import com.twinpark.music.ui.component.PianoRoll
import com.twinpark.music.ui.component.Precipitation
import com.twinpark.music.ui.component.SceneArt
import com.twinpark.music.ui.component.WeatherOverlay
import com.twinpark.music.ui.component.rememberRainShaderSource
import com.twinpark.music.ui.component.rememberWeatherClock
import com.twinpark.music.ui.component.drawPrecipitation
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
fun MusicScreen(modifier: Modifier = Modifier, engine: MusicEngine = BgmController) {
    // 화면을 다시 열면 연주 중이던 분위기를 이어서 보여 준다
    // 분위기는 컨트롤러가 쥔다: 상황 판단(귀가 알려 준 단어)이나 사용자 조작이 바꾸고, 오브와 하늘은 그에 맞는 장면을 쓴다
    val engineId by engine.moodId.collectAsState()
    // 오브와 하늘 장면은 음악 분위기가 아니라 바깥 날씨(시간대, 비)가 고른다. DEV 패널이 정한 수동 값이 없으면 기기 시계를 따른다.
    val manualWeather by WeatherSource.manual.collectAsState()
    val weatherReport by WeatherSource.report.collectAsState()
    val appContext = LocalContext.current
    LaunchedEffect(Unit) { AppWeather.run(appContext) }
    var clockTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); clockTick++ } }          // 시간대가 넘어가는 순간을 따라간다
    val weatherNow = remember(manualWeather, weatherReport, clockTick) { WeatherSource.resolve(manualWeather, report = weatherReport) }
    val mood = remember(weatherNow) { weatherNow.scene() }
    val skyColors = mood.palette.sky.mapIndexed { i, c -> animateColorAsState(c, tween(1500), label = "bg$i").value }
    var sensed by remember { mutableStateOf(emptySet<String>()) }        // 디버그: 귀가 들은 것으로 치는 단어들
    val situation by SituationDirector.state.collectAsState()
    val context = LocalContext.current
    val restrictions = remember { CarUxRestrictionsSource.create(context) }
    DisposableEffect(restrictions) { onDispose { restrictions.close() } }
    val uxRestricted by restrictions.required.collectAsStateWithLifecycle()
    var devReduced by remember { mutableStateOf(false) }
    val reduced = devReduced || uxRestricted
    val sceneLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    var pianoOn by remember { mutableStateOf(false) }               // 개발용: 오브 대신 피아노 화면(디자인에서 넣을지 미정)
    val precipitation = when { weatherNow.snowing -> Precipitation.Snow; weatherNow.raining -> Precipitation.Rain; else -> Precipitation.None }
    val rainBase = androidx.compose.ui.graphics.ImageBitmap.imageResource(com.twinpark.music.R.drawable.scene_rain_base)
    val lensBase = if (precipitation == Precipitation.Rain) rainBase else null
    val rainAgsl = rememberRainShaderSource()
    val weatherClock = rememberWeatherClock(active = !reduced && precipitation != Precipitation.None)
    val viewModel: MusicViewModel = viewModel(factory = remember(engine) { MusicViewModel.factory(engine, PreferencesVolumeStore(context)) })
    val quadrantLevels by viewModel.levels.collectAsStateWithLifecycle()

    // 배경음악: 엔진은 포그라운드 서비스가 쥐고 있어서 화면을 닫아도 계속 연주한다. 화면은 상태를 보여 주고 요청만 보낸다.
    val bgmState by engine.state.collectAsState()
    val bgmPaused by engine.paused.collectAsState()
    var status by remember { mutableStateOf<BgmStatus?>(null) }
    LaunchedEffect(Unit) { BgmService.start(context) }
    LaunchedEffect(sensed) { DebugSoundSensor.fromScreen = sensed }

    // 구획 값에서 나온 엔진 조절량(음량 제외). 노브를 만지기 전에는 0이라 분위기 기본값 그대로다.
    // 값이 바뀌면 0.9초에 걸쳐 따라간다(음악이 갑자기 달라지지 않게).
    val quadrantTarget = com.twinpark.music.domain.QuadrantControl.toParams(quadrantLevels)
    val quadSpec = androidx.compose.animation.core.tween<Float>(durationMillis = 900)
    val qReg by androidx.compose.animation.core.animateFloatAsState(quadrantTarget.reg, quadSpec, label = "qReg")
    val qTempo by androidx.compose.animation.core.animateFloatAsState(quadrantTarget.tempo, quadSpec, label = "qTempo")
    val qDens by androidx.compose.animation.core.animateFloatAsState(quadrantTarget.dens, quadSpec, label = "qDens")
    val qSpace by androidx.compose.animation.core.animateFloatAsState(quadrantTarget.space, quadSpec, label = "qSpace")
    LaunchedEffect(qReg, qTempo, qDens, qSpace) {
        engine.setKnobParams(com.twinpark.music.domain.EngineParams(reg = qReg, tempo = qTempo, dens = qDens, space = qSpace))
    }
    LaunchedEffect(bgmState) {
        while (bgmState == BgmState.Ready) {
            status = engine.status()
            delay(500)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // 시안은 장면의 색이 화면 전체(오른쪽 패널 뒤까지)에 이어진다: 분위기의 하늘색을 천천히 바꿔 가며 깔고, 오른쪽 글자가 읽히게 살짝 어둡힌다
            .background(Brush.linearGradient(skyColors, start = Offset(0f, 0f), end = Offset.Infinite))
            .background(Color(0x66000000))
            .safeDrawingPadding(),
    ) {
        val landscape = maxWidth > maxHeight
        val screenDensity = androidx.compose.ui.platform.LocalDensity.current.density

        val knob: @Composable (Modifier, androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp) -> Unit = { m, corner, endInset ->
            Box(m) {
                CloudKnob(
                    initialPos = com.twinpark.music.domain.QuadrantControl.initialPos(viewModel.startVolume).let { androidx.compose.ui.geometry.Offset(it.first, it.second) },
                    quadrantLevels = quadrantLevels,
                    quadrantGuides = com.twinpark.music.domain.QuadrantControl.DETAIL_CONTROLS,
                    onPositionChange = { x, y ->
                        viewModel.onKnobMoved(x, y)
                    },
                    mood = mood,
                    reducedMotion = reduced,
                    cornerRadius = corner,
                    endInset = endInset,
                    scene = SceneArt.forKey(mood.sceneKey),
                    sceneLayer = sceneLayer,
                    // 비와 눈은 노브 아래에 그리고, 노브 렌즈가 장면과 함께 굴절해 비춘다
                    weatherContent = { WeatherOverlay(precipitation, clockNs = { weatherClock.value }, enabled = !reduced, lensBase = lensBase, rainAgsl = rainAgsl) },
                    lensExtra = { w, h ->
                        if (!reduced) drawPrecipitation(precipitation, weatherClock.value / 1_000_000_000.0, w, h, screenDensity, lensBase, rainAgsl)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // 시안의 좌상단 브랜드 표기 (시안 글자는 HTUNDAI로 적혀 있으나 오타로 보고 HYUNDAI로 쓴다)
                Text(
                    "HYUNDAI",
                    color = Color.White,
                    fontSize = ui(16f),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding((14 * LocalUiScale.current).dp),
                )
            }
        }
        val orb: @Composable (Modifier) -> Unit = { m ->
            OrbPanel(
                mood = mood,
                engine = engine,
                weather = weatherNow.detail ?: mood.weather,
                engineId = engineId,
                onEngineChange = { SituationDirector.manualOverride(); engine.requestMood(it) },
                sensed = sensed,
                onSensedChange = { sensed = it },
                situation = situation,
                reduced = reduced,
                devReduced = devReduced,
                onReducedChange = { devReduced = it },
                piano = pianoOn,
                onPianoChange = { pianoOn = it },
                bgmState = bgmState,
                paused = bgmPaused,
                status = status,
                pulse = engine::pulse,
                beat = engine::beat,
                onEnd = { BgmService.stop(context) },
                modifier = m,
            )
        }

        val shortSide = if (maxWidth < maxHeight) maxWidth else maxHeight
        val uiScale = (shortSide / 700.dp).coerceIn(1f, 1.7f)
        CompositionLocalProvider(LocalUiScale provides uiScale) {
            if (landscape) {
                // Figma 시안: 장면(하늘, 구름)이 화면 거의 전체에 깔리고, 오른쪽 패널은 그 위에 흐린 반투명 유리로 겹친다.
                // 노브는 왼쪽 영역에서만 움직인다(endInset).
                val panelW = maxWidth * 0.42f
                val density = androidx.compose.ui.platform.LocalDensity.current
                val sceneW = with(density) { maxWidth.toPx() }
                Box(Modifier.fillMaxSize()) {
                    knob(Modifier.fillMaxSize(), 0.dp, panelW)
                    // 눈: 장면 위, 패널 아래. 패널 뒤에서는 아래 흐림 레이어가 같은 입자를 같은 자리에 다시 그려 같이 흐려진다.
                    // 오른쪽 패널: 장면을 같은 자리에서 다시 그려 실제로 흐리게 하고(Figma의 흐린 반투명 유리), 그 위에 옅게 어둡힌다.
                    Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(panelW)) {
                        Canvas(
                            Modifier.fillMaxSize().graphicsLayer {
                                clip = true
                                if (android.os.Build.VERSION.SDK_INT >= 31) {
                                    renderEffect = androidx.compose.ui.graphics.BlurEffect(36f, 36f, androidx.compose.ui.graphics.TileMode.Clamp)
                                }
                            },
                        ) {
                            translate(left = -(sceneW - size.width)) {
                                drawLayer(sceneLayer)
                                if (!reduced) drawPrecipitation(precipitation, weatherClock.value / 1_000_000_000.0, sceneW, size.height, density.density, lensBase, rainAgsl)
                            }
                        }
                        // 맑은 유리: 어두운 틴트는 글자가 읽힐 만큼만, 위쪽에 옅은 빛, 왼쪽 위가 밝은 테두리
                        Box(Modifier.fillMaxSize().background(Color(0x40101820)).background(com.twinpark.music.ui.component.Glass.Fill).border(com.twinpark.music.ui.component.Glass.EdgeStroke))
                        orb(Modifier.fillMaxSize().padding(16.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    orb(Modifier.weight(1.35f).fillMaxWidth())
                    Box(Modifier.weight(0.65f).fillMaxWidth()) {
                        knob(Modifier.fillMaxSize(), 28.dp, 0.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrbPanel(
    mood: Mood,
    engine: MusicEngine,
    weather: Weather,
    engineId: String,
    onEngineChange: (String) -> Unit,
    sensed: Set<String>,
    onSensedChange: (Set<String>) -> Unit,
    situation: TrackerState?,
    reduced: Boolean,
    devReduced: Boolean,
    onReducedChange: (Boolean) -> Unit,
    piano: Boolean,
    onPianoChange: (Boolean) -> Unit,
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
        // 시안은 오른쪽 패널이 위에서 한참 내려와 시작한다(큰 화면에서만). 그 비어 있던 자리에 상단 바(끝내기)를 둔다.
        // 운전 중 짧은 시간에 누르기 쉽도록 돌아가기 버튼은 위쪽에 두고, 터치 영역을 충분히 크게 한다(디자이너 노트).
        val topGap = if (!compact && maxHeight >= 700.dp) maxHeight * 0.08f else 0.dp
        val barH = maxOf(topGap, ((if (compact) 36 else 48) * LocalUiScale.current).dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
            Row(Modifier.fillMaxWidth().height(barH), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val btnH = ((if (compact) 36 else 48) * LocalUiScale.current).dp
                val btnPad = if (compact) PaddingValues(horizontal = 12.dp, vertical = 2.dp) else ButtonDefaults.ContentPadding
                // Figma 시안: END, CHANGE THE MOOD 두 버튼이 패널 맨 위에 나란히 있다(운전 중 누르기 쉽도록 큰 터치 영역).
                OutlinedButton(
                    onClick = {
                        onEnd()
                        (context as? Activity)?.finish()      // 화면은 바로 닫고, 연주는 서비스가 곡을 마무리한 뒤 끝낸다
                    },
                    modifier = Modifier.weight(1f).height(btnH).background(com.twinpark.music.ui.component.Glass.Fill, androidx.compose.foundation.shape.CircleShape),
                    contentPadding = btnPad,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = com.twinpark.music.ui.component.Glass.EdgeStroke,
                ) { Text("END", fontSize = ui(if (compact) 13f else 15f), fontFamily = AppFonts.Pretendard, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                OutlinedButton(
                    onClick = {
                        val list = Mood.EngineMoods
                        val at = list.indexOfFirst { it.id == engineId }
                        onEngineChange(list[(at + 1) % list.size].id)
                    },
                    modifier = Modifier.weight(1f).height(btnH).background(com.twinpark.music.ui.component.Glass.Fill, androidx.compose.foundation.shape.CircleShape),
                    contentPadding = btnPad,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = com.twinpark.music.ui.component.Glass.EdgeStroke,
                ) { Text("CHANGE THE MOOD", fontSize = ui(if (compact) 13f else 15f), fontFamily = AppFonts.Pretendard, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = ui(if (compact) 14f else 17f)) }
                if (debuggable) {
                    TextButton(
                        onClick = { devOpen = !devOpen },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    ) { Text(if (devOpen) "닫기" else "DEV", color = Color(0xFF7DD3FC), fontSize = ui(12f)) }
                }
            }
            // 제목: 위 가로선 + "AI IS CONDUCTING"
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White))
                Text("AI IS CONDUCTING", color = Color.White, fontSize = ui(16f), fontFamily = AppFonts.Pretendard, modifier = Modifier.padding(top = 4.dp))
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
            if (piano) {
                // 개발용 선택 화면: 반투명 건반과 내려오는 음(Figma 피아노 화면). 연주 중일 때만 음이 나온다.
                PianoRoll(tint = mood.palette.peak, engine = engine, modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = 90.dp))
            } else BeadOrb(
                // 오브 모양은 분위기(음악 종류)가 정한다. 분위기마다 Figma 시안 한 장이 대응한다.
                pose = mood.orbPose,
                speed = mood.speed,
                reducedMotion = reduced,
                pulse = pulse,
                beat = beat,
                modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = 90.dp),
            )
            WeatherDetail(weather, compact)
        }
        // 개발용(디버그 빌드만): 접어 두었다가 펼치면 패널 위에 겹쳐 보인다. 오브 자리를 빼앗지 않는다.
        if (debuggable) {
            if (devOpen) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color(0xFF0B0F24), RoundedCornerShape(12.dp)).padding(8.dp),
                ) { DevControls(engineId, onEngineChange, sensed, onSensedChange, situation, devReduced, onReducedChange, piano, onPianoChange) }
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
    piano: Boolean,
    onPianoChange: (Boolean) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    // 날씨: 오브와 장면을 정한다. 자동이면 기기 시계(비 없음), 시간대나 비를 누르면 그 값으로 고정한다.
    val manual by WeatherSource.manual.collectAsState()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("날씨:", color = Color.White.copy(alpha = 0.7f), fontSize = ui(12f))
        val chip: @Composable (String, Boolean, () -> Unit) -> Unit = { text, on, click ->
            Button(
                onClick = click,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (on) Color.White else Color(0x33FFFFFF),
                    contentColor = if (on) Color.Black else Color.White,
                ),
            ) { Text(text, fontSize = ui(12f), maxLines = 1) }
        }
        chip("자동", manual == null) { WeatherSource.setManual(null) }
        TimeOfDay.values().forEach { t ->
            chip(t.label, manual?.time == t && manual?.raining != true && manual?.snowing != true) { WeatherSource.setManual(WeatherState(t, raining = false)) }
        }
        chip("비", manual?.raining == true && manual?.snowing != true) { WeatherSource.setManual(WeatherState(manual?.time ?: TimeOfDay.now(), raining = true)) }
        chip("눈", manual?.snowing == true) { WeatherSource.setManual(WeatherState(manual?.time ?: TimeOfDay.now(), snowing = true)) }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("음악:", color = Color.White.copy(alpha = 0.7f), fontSize = ui(12f))
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
        Button(
            onClick = { onPianoChange(!piano) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (piano) Color(0xFF7DD3FC) else Color(0x33FFFFFF),
                contentColor = if (piano) Color.Black else Color.White,
            ),
        ) { Text("피아노", fontSize = ui(12f), maxLines = 1) }
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
