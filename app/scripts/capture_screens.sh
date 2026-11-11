#!/usr/bin/env bash
# 에뮬레이터에서 앱 화면을 차례로 열어 스크린샷을 남긴다. Actions의 android-screens 워크플로가 부른다.
# 화면 이동과 상태 변경은 디버그 빌드의 인텐트(MainActivity.handleDebugIntent)로 한다.
set -u
OUT=${1:-screens}
PKG=com.doran.app
mkdir -p "$OUT"

shot() {
  sleep "${2:-2}"
  # 느린 CI 에뮬레이터에서 뜨는 "응답 없음" 창을 닫는다
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1
  sleep 1
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "찍음: $1"
}

# 예: go --es gear D --es mood speaking
go() {
  adb shell am start -n $PKG/.MainActivity "$@" > /dev/null
}

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.RECORD_AUDIO || true
adb shell am start -W -n $PKG/.MainActivity > /dev/null
sleep 5

shot 01_처음설정

go --es gear D --es mood idle
shot 10_주행_대기
go --es mood listening
shot 11_주행_듣는중
go --es mood thinking
shot 12_주행_생각중
go --es mood speaking
shot 13_주행_말하는중 1
go --es mood happy
shot 14_주행_기쁨 1
go --es mood none --es gear P --es onboarded yes --es route home --es demo tape
shot 15_정차_홈
go --es say "근처 맛집 추천해 줘"
shot 16_정차_제안카드 6
go --es say "휴게소 어디야"
shot 17_정차_휴게소카드 6

go --es route record
shot 20_녹음 4
shot 21_녹음_고른사람 13
go --es route home
sleep 1

go --es route music
shot 30_음악 6
go --es route home
sleep 1

go --es route album
shot 40_앨범 4
go --es route "albummap:demo-trip-1"
shot 41_3D지도앨범 6
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_BACK

go --es route settings
shot 50_설정 3
adb shell input keyevent KEYCODE_BACK

exit 0
