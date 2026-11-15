<#
  마이크 없이 소리 파일로 음성 명령을 시험한다(시연용 DEV 빌드 + 음성 명령 빌드, 에뮬레이터 또는 USB 기기).
  사용:  .\tools\feed-voice.ps1 -File C:\말.wav [-Adb D:\Android\sdk\platform-tools\adb.exe] [-User 10]
  파일은 wav · mp3 · m4a · ogg · flac 등 기기가 읽는 것이면 된다(표본 수와 채널은 알아서 맞춘다).
  앱이 이 빌드로 한 번 이상 실행돼 있어야 하고, 처음에는 음성 인식 모델을 푸는 데 20~30초 걸려서 첫 번째는 "준비되지 않음"으로 끝날 수 있다. 그때는 다시 실행한다.
  결과는 `adb logcat -s DebugFeed VoskSpeech AppVoice` 로 본다(이 스크립트가 마지막에 보여 준다).
#>
param(
    [Parameter(Mandatory = $true)][string]$File,
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
& $Adb shell am broadcast --user $User -n "$App/.voice.DebugVoiceFeedReceiver" -a com.twinpark.music.debug.FEED --es file $abs | Out-Null
Start-Sleep -Seconds 12
& $Adb logcat -d -s DebugFeed VoskSpeech AppVoice
& $Adb shell rm -f $tmp
