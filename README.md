# 무드 마에

차 안의 **주변 소리를 듣고, 사용자가 원하는 음악을 그 자리에서 합성해 들려주는** 안드로이드 앱입니다.
마이크로 차 안 소리(대화, 웃음, 음악, 바다 소리, 아기 울음 등)를 계속 듣고, AI 지휘자가 상황을 보고 분위기와 악기를 정하고,
엔진이 그 자리에서 악보를 쓰며 바로 연주합니다. 같은 분위기라도 켤 때마다 다른 곡이 나옵니다.

차량 정보(속도, 제한속도, 운전 시간)와 날씨(비, 눈)도 참고하지만, 중심은 **주변 소리**입니다.
말로도 바꿀 수 있습니다: "다른 거 틀어 줘", "조용히 해 줘", "파도 소리 깔아 줘".

> 받아서 바로 쓰려면 [Releases의 v2.0-final](https://github.com/twin-park/music_android/releases/tag/v2.0-final)에서 APK를 받으세요.

## 받아서 쓰기

| 파일 | 용도 |
|---|---|
| `music_android-v2.0-x86_64.apk` | Pleos 에뮬레이터용 제출용(DEV 패널 없음) |
| `music_android-v2.0-arm64.apk` | 실기기·차량용 제출용(DEV 패널 없음) |
| `music_android-v2.0-x86_64-dev.apk` | 시연 영상용(DEV 패널, CHANGE THE MOOD, 소리 파일 넣기 통로) |

- 용량이 큽니다(약 350MB). 음성 인식 모델(Vosk 한국어, 압축 해제 약 250MB)이 들어 있습니다.
- 설치 후 **마이크, 위치, 알림 권한**을 허용하세요. 기존 앱이 있으면 지우고 설치하세요(서명이 다를 수 있습니다).
- 처음 실행할 때 음성 인식 모델을 풀어 두느라 20~30초 걸립니다. 그동안 음악과 소리 듣기는 동작하고, 음성 명령만 준비 중입니다.
- 마이크는 **앱 화면이 보이는 동안만** 듣습니다.
- 세 파일은 디버그 서명 빌드입니다.

## 무엇을 하나

- **주변 소리 듣기**: 귀(ear) 모델이 2초마다 마이크 소리를 분류합니다(대화, 웃음, 노래, 언성, 음악, 바다·파도, 아기 울음 등). 결과는 상황으로 바뀌어 지휘자에게 전달됩니다.
  - 말소리가 들리면 그 순간 음악을 줄이고 말 대역을 비워 대화를 방해하지 않습니다.
  - 바다 소리가 들리면 바닷가 분위기를 고르고 파도 소리를 음악 밑에 깔 수 있습니다.
- **지휘자(AI)**: 여정, 요일, 시간대, 날씨, 장소, 달리기, 누구랑, 차 안 소리, 말을 보고 분위기 32개 중 하나와 악기, 섞기를 정합니다. 선택이 8초 이상 이어지고 직전 전환 뒤 45초가 지나야 분위기가 바뀝니다.
- **음성 명령**: 호출어 없이 계속 듣다가 명령이 들리면 반영합니다. 앱 안에서 도는 오프라인 인식(Vosk)이라 인터넷이 필요 없고, 답변은 미리 만든 음성 파일로 알립니다.
- **날씨**: 비·눈은 OpenWeatherMap과 와이퍼가 정합니다(소리로는 정하지 않습니다). 위치를 못 받으면 서울 기준입니다.
- **차량**: Pleos Connect SDK의 속도, 제한속도(내비), 와이퍼를 지휘자에게 전달합니다. 오래 운전했는지(운전 시간)도 셉니다.
- **화면**: 분위기에 따라 모양이 바뀌는 오브와 날씨 장면. 개발용(DEV) 빌드에서만 지휘자의 선택 이유와 상황 패널이 보입니다.

### 음성 명령

| 말 | 결과 |
|---|---|
| "다른 거 틀어 줘" | 다른 분위기로 바꿈 |
| "조용히 해 줘", "쉿" | 조용히 |
| "신나는 걸로 틀어 줘" | 신나게 |
| "잔잔하게 틀어 줘" | 잔잔하게 |
| "파도 소리 깔아 줘" | 파도 소리를 깖 |
| "이거 좋다" | 지금 분위기를 오래 둠 |
| "아기 잔다" | 아이 재우기(느리고 부드럽게) |
| "음악 꺼 줘" | 음악을 마무리함 |
| "원래대로 해 줘" | 되돌림 |

알아듣는 낱말은 58개입니다. 확신이 없으면(신뢰도 0.97 미만, 명령 모양이 아님) 명령으로 쓰지 않고 아무 반응도 하지 않습니다.
짧은 "꺼 줘"는 오인식으로 음악이 꺼지는 일이 있어 일부러 받지 않습니다.

## 어떻게 동작하나

```
마이크 ──► 귀 모델(ONNX) ──► 상황 ──┐
   │                                ├─► 지휘자 ──► 엔진(C++) ──► 소리
   └─► 음성 인식(Vosk) ──► 말 ──────┤   (분위기·악기·섞기)  (악보를 쓰며 연주)
날씨 API · 와이퍼 · 차량 속도 ──────┘
```

- **엔진**: C++ 작곡·연주 엔진이 JNI로 앱에 들어 있습니다(`app/src/main/cpp`). 소리는 TinySoundFont로 냅니다.
- **귀 모델**: `app/src/main/assets/ear/ear_wave.onnx`(ONNX Runtime). 말투와 감정도 읽습니다.
- **지휘자**: 상황 낱말, 규칙, 장면 자료(JSON)로 분위기를 정합니다. 빌드 때 앱에 묶입니다.

## 소스에서 빌드

### 준비물

- Android SDK(플랫폼 36), NDK `27.1.12297006`, JDK 11 이상
- **엔진 데이터**: 지휘자 자료와 AI 가중치, 사운드폰트, 피아노 샘플은 이 저장소에 없고 빌드 때 `../ai/engine/data/`에서 가져옵니다. 엔진 저장소 [music_ai](https://github.com/twin-park/music_ai)를 `ai`라는 이름으로 이 저장소 **옆에** 두고, 그 README대로 사운드폰트와 피아노(`sf/`, `piano/`)를 `engine/data/`에 넣으세요. 없으면 음악이 나오지 않는 앱이 만들어지고 빌드가 경고합니다.
- **음성 인식 모델**(음성 명령에 필요): [vosk-model-small-ko-0.22](https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip)를 풀어서 폴더 경로를 `VOSK_MODEL_DIR`로 주세요. 저장소에는 없습니다(약 250MB).
- Pleos SDK는 공개 저장소(`nexus-playground.pleos.ai`)에서 자동으로 받습니다.

### 설정

저장소에 올리지 않는 `android/local.properties`에 적습니다(`-P옵션`, 환경변수로도 줄 수 있습니다).

```properties
sdk.dir=C\:\\Android\\sdk
OPENWEATHER_API_KEY=...        # 날씨(OpenWeatherMap). 없으면 날씨 연동이 꺼집니다
WEATHER_LAT=37.5665            # 위치를 못 받을 때 쓰는 좌표(기본 서울)
WEATHER_LON=126.9780
VOSK_MODEL_DIR=D:/models/vosk-model-small-ko-0.22
```

| 설정 | 기본 | 뜻 |
|---|---|---|
| `DEV_MODE` | false | 개발용 패널, CHANGE THE MOOD, 소리 파일 넣기 통로 |
| `MIC_SENSING` | false | 마이크로 주변 소리를 듣기. **켜야 소리 듣기와 음성 명령이 동작합니다** |
| `VOICE_COMMAND` | false | 음성 명령 |
| `VOICE_ENGINE` | vosk | 음성 인식 구현: `vosk`, `android`(기본 인식), `pleos`(Pleos STT, 아직 열리지 않음) |
| `VOICE_OUTPUT` | recorded | 답변 소리: `recorded`(미리 만든 음성 파일), `android`(기본 TTS), `pleos` |
| `PLEOS_CLIENT_ID`, `PLEOS_CLIENT_SECRET` | (비어 있음) | 서버 방식 Pleos STT용. 지금 코드에서는 쓰지 않습니다 |

### 빌드

```bash
# 실기기·차량(arm64) 제출용
./gradlew :app:assembleDebug -PMIC_SENSING=true -PVOICE_COMMAND=true

# Pleos 에뮬레이터(x86_64) 시연용
./gradlew :app:assembleDebug -PemuAbi=1 -PMIC_SENSING=true -PVOICE_COMMAND=true -PDEV_MODE=true
```

결과는 `app/build/outputs/apk/debug/app-debug.apk`입니다. `-PemuAbi=1`이 없으면 arm64, 있으면 x86_64만 담습니다.

## 시험

- **단위 테스트**: `./gradlew :app:testDebugUnitTest`
- **기기 테스트**: `./gradlew :app:assembleDebugAndroidTest` 후 `adb shell am instrument -w <패키지>.test/androidx.test.runner.AndroidJUnitRunner`. 귀 모델, 음성 인식, 소리 파일 읽기, 주변 소리에서 지휘자 선택까지를 확인합니다.
- **마이크 없이 시험하기**(DEV 빌드): 소리 파일을 마이크 자리에 넣습니다.

```powershell
.\tools\feed-voice.ps1 -File C:\말.wav                    # 음성 명령 시험
.\tools\feed-voice.ps1 -File C:\빗소리.flac -Mode ear      # 주변 소리 → 귀 모델 → 지휘자 시험
```

  `-Mode ear`는 앱이 화면에 떠 있어야 하고, 지휘자가 고른 분위기와 이유를 로그(`DebugFeed`)로 보여 줍니다.

## 폴더

```
app/src/main/java/com/twinpark/music/
  audio/      엔진 연결(JNI), 재생 서비스, 지휘자 상황 전달
  sensing/    마이크, 귀 모델(ONNX) 분류
  domain/     소리 → 낱말 → 상황 규칙(귀·날씨·차량), 순수 로직
  voice/      음성 명령(Vosk), 답변 소리, 소리 파일 넣기(DEV)
  car/        Pleos 차량·내비 연결(속도, 제한속도)
  weather/    날씨 API, 위치
  ui/         화면(오브, 날씨 장면, DEV 패널)
app/src/main/cpp/   엔진과 소리 내기(JNI, TinySoundFont)
app/bgm/            악기 크기 보정표
tools/              소리 파일 넣기 스크립트 등
```

## 알아둘 한계

- **음성 인식**: 한국어에 약한 소형 모델입니다. 합성음 위주로 시험했고, 사람 목소리의 정확한 인식률은 재 보지 못했습니다. 계속 듣는 방식이라 보통 대화가 명령으로 들릴 수 있어 여러 방어(신뢰도, 명령 모양 검사, 간격)를 두었습니다.
- **소리 분류**: 귀 모델은 한국어 말과 아기 울음에 약하고, 빗소리와 바다 소리를 구분하지 못합니다. 그래서 비·눈은 날씨 API와 와이퍼로만 정합니다.
- **부하**: 실기기에서 CPU를 2코어 가까이 쓰고 메모리 약 1GB를 씁니다. 차량 단말에서는 확인하지 못했습니다.
- **앱이 지휘자에게 보내지 않는 상황**: 동승자, 여정(출근·퇴근 등), 창문, 길 안내 음성, 시동 직후, 반려동물은 센서나 자료가 없어 보내지 않습니다(지휘자 규칙 94개 중 약 32개가 이 값을 기다립니다). 장소는 바다만 소리로 알립니다.
- **Pleos SDK**: FusedLocation, TextToSpeech, SpeechToText, 차량 제어는 플랫폼에서 아직 열리지 않아(coming soon) 쓰지 않고, 비슷한 기능을 앱 안에서 대신합니다(위치는 설정 좌표, 음성 인식은 Vosk, 답변은 미리 만든 음성). 열리면 같은 인터페이스에 구현만 바꿔 끼울 수 있습니다(`SpeechSource`, `SpeechOutput`).
- **키**: 날씨 키가 APK 안에 들어 있어 꺼낼 수 있습니다. 공개 배포에 쓴 키는 폐기하고 새로 발급받으세요.

## 출처

- 사운드폰트와 피아노 샘플(GeneralUser GS, Sonatina Symphonic Orchestra 일부, Salamander 피아노), 파도·빗소리 녹음(CC0·퍼블릭 도메인)의 조건은 엔진 저장소 [music_ai](https://github.com/twin-park/music_ai)에 있습니다.
- 귀 모델 가중치는 FSD50K(클립별 CC 라이선스), CREMA-D(ODbL), AxonData 아기 울음 샘플(CC-BY 4.0)로 학습했습니다. 가중치만 들어 있고 원본 소리는 없습니다.
- 음성 인식 [Vosk](https://alphacephei.com/vosk/)(Apache 2.0)와 한국어 소형 모델, 소리 내기 [TinySoundFont](https://github.com/schellingb/TinySoundFont)(MIT), 소리 분류 실행 ONNX Runtime.
- 앱 이름은 "무드 마에"이고, 패키지 이름(`com.twinpark.music`)과 저장소 이름(`music_android`)은 이전 이름 그대로입니다.
