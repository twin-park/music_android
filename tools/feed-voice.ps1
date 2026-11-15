<#
  마이크 없이 소리 파일로 앱을 시험한다(시연용 DEV 빌드, 에뮬레이터 또는 USB 기기). 파일은 wav · mp3 · m4a · ogg · flac 등 기기가 읽는 것이면 된다.

  음성 명령 시험(기본):   .\tools\feed-voice.ps1 -File C:\말.wav
      파일 소리를 마이크로 들은 것처럼 음성 인식에 넣고, 인식 → 엔진 → 답변 소리까지 돈다.
  주변 소리 시험(핵심):    .\tools\feed-voice.ps1 -File C:\비.flac -Mode ear [-Seconds 30]
      파일 소리를 귀 모델(소리 분류)에 실제 시간으로 넣고, 앱이 2초마다 하는 대로 상황을 판단해서 지휘자가 고르는 분위기와 장면을 보여 준다.

  공통: [-Adb D:\Android\sdk\platform-tools\adb.exe] [-User 10]
  앱이 이 빌드로 한 번 이상 실행돼 있어야 한다. 음성 명령은 처음에 모델을 푸는 20~30초 동안 "준비되지 않음"으로 끝날 수 있어서 그때는 다시 실행한다.
  결과는 `adb logcat -s DebugFeed VoskSpeech AppVoice Situation` 로 본다(이 스크립트가 마지막에 보여 준다).
#>
param(
    [Parameter(Mandatory = $true)][string]$File,
    [ValidateSet("voice", "ear")][string]$Mode = "voice",
    [int]$Seconds = 30,
    [string]$Adb = "adb",
    [int]$User = 10,
    [string]$App = "com.twinpark.music"
)
$ErrorActionPreference = "Stop"
if (-not (Test-Path $File)) { throw "파일이 없음: $File" }
$ext = [IO.Path]::GetExtension($File)
$tmp = "/data/local/tmp/feed$ext"
$dest = "files/feed$ext"
$abs = "/data/user/$User/$App/files/feed$ext"

& $Adb push $File $tmp | Out-Null
# 앱의 비공개 폴더로 옮긴다(adb 쉘은 앱의 저장소에 직접 쓸 수 없다. 디버그 빌드라서 run-as 가 된다)
& $Adb shell "run-as $App --user $User sh -c 'mkdir -p files && cp $tmp $dest'"
& $Adb logcat -c
$extra = @("--es", "file", $abs)
if ($Mode -eq "ear") { $extra += @("--es", "mode", "ear", "--ei", "seconds", "$Seconds") }
& $Adb shell am broadcast --user $User -n "$App/.voice.DebugVoiceFeedReceiver" -a com.twinpark.music.debug.FEED @extra | Out-Null
# 음성 명령은 금방 끝나고, 주변 소리는 파일 길이만큼 + 판단을 지켜보는 시간이 걸린다
$wait = if ($Mode -eq "ear") { $Seconds + 30 } else { 12 }
Write-Output "$wait 초 기다립니다..."
Start-Sleep -Seconds $wait
& $Adb logcat -d -s DebugFeed VoskSpeech AppVoice Situation
& $Adb shell rm -f $tmp
