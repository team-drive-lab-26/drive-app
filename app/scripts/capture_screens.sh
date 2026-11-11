#!/usr/bin/env bash
# 에뮬레이터에서 앱 화면을 차례로 열어 스크린샷을 남긴다. Actions의 android-screens 워크플로가 부른다.
# 화면 이동과 상태 변경은 디버그 빌드의 인텐트(MainActivity.handleDebugIntent)로 한다.
set -u
OUT=${1:-screens}
PKG=com.doran.app
mkdir -p "$OUT"

shot() {
  sleep "${2:-2}"
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
go --es mood none --es gear P
shot 15_정차

for name in album albummap home settings record speaker music; do
  go --es route "$name"
  shot "2x_$name"
  adb shell input keyevent KEYCODE_BACK
done
exit 0
