#!/usr/bin/env bash
# 에뮬레이터에서 앱 화면을 차례로 열어 스크린샷을 남긴다. Actions의 android-screens 워크플로가 부른다.
# 화면 이동과 상태 변경은 디버그 빌드의 인텐트(MainActivity.handleDebugIntent)로 한다.
set -u
OUT=${1:-screens}
PKG=com.doran.app
mkdir -p "$OUT"

shot() {
  sleep "${2:-2}"
  for try in 1 2 3; do
    # 느린 CI 에뮬레이터에서 뜨는 "응답 없음" 창을 닫는다
    adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1
    if adb shell dumpsys window | grep -q "Application Not Responding\|isn't responding"; then
      adb shell input keyevent KEYCODE_BACK
      sleep 2
      continue
    fi
    sleep 1
    adb exec-out screencap -p > "$OUT/$1.png"
    # 화면이 다 그려지기 전의 검은 화면은 파일이 아주 작다. 그러면 다시 찍는다
    if [ "$(stat -c %s "$OUT/$1.png")" -gt 60000 ]; then break; fi
    sleep 3
  done
  echo "찍음: $1"
}

# 예: go --es gear D --es mood speaking
go() {
  adb shell am start -n $PKG/.MainActivity "$@" > /dev/null
}

# 화면에서 글자를 찾아 그 자리를 누른다. 예: tap_text "기록 6개"
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  local box
  box=$(adb exec-out cat /sdcard/ui.xml | tr '>' '\n' | grep -F "$1" | head -1 \
    | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')
  if [ -z "$box" ]; then echo "못 찾음: $1"; return 1; fi
  set -- $box
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

adb shell settings put global hide_error_dialogs 1 || true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.RECORD_AUDIO || true
adb shell am start -W -n $PKG/.MainActivity > /dev/null
sleep 5

shot 01_처음설정 12
go --es screen night
shot 02_처음설정_밤 3

sleep 8
go --es gear D --es mood idle --es caption -
shot 10_주행_대기 4
go --es mood listening --es caption "'휴게소 어디야'"
shot 11_주행_듣는중 3
go --es mood thinking --es caption "'휴게소 어디야'"
shot 12_주행_생각중 3
go --es mood speaking --es caption "'다음 휴게소는 하남드림이야. 12km 남았어'"
shot 13_주행_말하는중 2
go --es mood happy --es caption "'하남드림 휴게소를 경유지로 넣었어'"
shot 14_주행_기쁨 2
go --es mood none --es gear P --es onboarded yes --es route home --es demo tape --es caption - --es battery 78 --es seats 3
shot 15_정차_홈
go --es say "'근처 맛집 추천해 줘'"
shot 16_정차_제안카드 6
go --es say "'가볼 만한 곳 추천해 줘'"
shot 16b_정차_제안카드_사진 8
go --es say "'휴게소 어디야'"
shot 17_정차_휴게소카드 6
go --es battery 15
shot 17b_정차_배터리낮음 2
go --es battery 78
go --es detour "'하남드림 휴게소'" --es caption "'하남드림 휴게소를 경유지로 넣었어'"
shot 18_정차_경유지 4
go --es screen day
shot 19_정차_낮 3
go --es gear D --es mood speaking --es caption "'하남드림까지 12km 남았어'"
shot 19b_주행_낮 3
go --es gear P --es mood none --es wiper on --es caption -
shot 19c_정차_낮_비 3
go --es screen night
shot 19d_정차_밤_비 3
go --es wiper off --es caption -
sleep 2

go --es route record
shot 20_녹음 4
shot 21_녹음_고른사람 13
go --es route home
sleep 1

go --es route music
shot 30_음악 6
go --es play life-of-riley
shot 31_음악_재생중 3
go --es screen day
shot 32_음악_낮 3
go --es screen night
go --es route home
sleep 1

# 앨범은 여행이 없을 때, 하나일 때, 여럿일 때를 차례로 찍는다. 기록은 시연용 값이다(seed_album.py)
go --es route album
shot 42_앨범_빈상태 4
go --es route home
python3 scripts/seed_album.py demo-trip-1 --backdate || true
go --es route album
shot 43_앨범_여행하나 5
go --es route home
python3 scripts/seed_album.py demo-trip-2 --backdate || true
python3 scripts/seed_album.py demo-trip-3 --backdate || true
go --es route album
shot 40_앨범 5
# 다른 여행 표지판을 고른 장면. 40일 전 여행(기록 6개)
tap_text "기록 6개" && shot 40c_앨범_다른여행 3
go --es screen day
shot 40b_앨범_낮 3
go --es screen night
go --es route "albummap:demo-trip-1"
shot 41_3D지도앨범 6
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_BACK

go --es route settings
shot 50_설정 3
go --es screen day
shot 51_설정_낮 2
go --es screen night
adb shell input keyevent KEYCODE_BACK

# 시연 흐름(docs/app/README.md '시연 범위' 순서): 주행 중 휴게소 제안 → 경유지 → 도착해 세우면 "한마디 남길래?" → 녹음 → 다시 타면 재탑승 제안
go --es route home --es caption -
# 화면 이동이 끝난 뒤 기어를 바꿔야 정차 화면이 주행 화면 위로 다시 올라오지 않는다
sleep 3
go --es gear D --es caption -
sleep 3
go --es event continuous_drive
shot 60_시연_주행_휴게소제안 8
go --es detour "'하남드림 휴게소'" --es caption "'하남드림 휴게소를 경유지로 넣었어'"
shot 61_시연_주행_경유지 3
go --es arrive -
shot 62_시연_도착_한마디 6
go --es screen day
shot 62b_시연_도착_한마디_낮 3
go --es screen night
go --es say "'응'"
shot 63_시연_녹음 4
adb shell input keyevent KEYCODE_BACK
go --es route home --es caption -
sleep 2
go --es event reboarded
shot 64_시연_재탑승제안 10
go --es screen day
shot 64b_시연_재탑승제안_낮 3
go --es screen night

exit 0
