#!/usr/bin/env bash
# 시연 흐름을 처음부터 끝까지 한 번에 돌리며 단계마다 화면을 찍고, 앱이 죽거나 멈췄는지 확인한다.
# 순서는 docs/app/README.md '시연 범위'를 따른다: 출발 → 휴게소 제안·경유지 → 도착·"한마디 남길래?" → 녹음 → 재탑승 → 음악 → 앨범.
# 끝에는 서버를 끄고 연결 실패 때 앱이 버티는지 본다. Actions의 android-demo 워크플로가 부른다.
# 결과는 $OUT/result.txt, 화면은 $OUT/*.png, 앱 로그는 $OUT/logcat.txt. 앱이 한 번이라도 죽거나 멈추면 실패로 끝난다.
set -u
OUT=${1:-demo}
PKG=com.doran.app
mkdir -p "$OUT"
RESULT="$OUT/result.txt"
: > "$RESULT"
FAIL=0
PID=""

note() { echo "$*" | tee -a "$RESULT"; }

go() {
  adb shell am start -n $PKG/.MainActivity "$@" > /dev/null
}

# 앱이 그대로 살아 있는지(프로세스가 바뀌지 않았는지), "응답 없음" 창이 떴는지 본다
alive() {
  local now
  now=$(adb shell pidof $PKG | tr -d '\r')
  if [ -z "$now" ] || { [ -n "$PID" ] && [ "$now" != "$PID" ]; }; then
    note "  !! 앱이 죽었음 ($1, 이전 $PID → 지금 ${now:-없음})"
    FAIL=1
    PID="$now"
  fi
  if adb shell dumpsys window | grep -q "Application Not Responding: $PKG"; then
    note "  !! 응답 없음 ($1)"
    FAIL=1
    adb shell input keyevent KEYCODE_BACK
  fi
}

shot() {
  sleep "${2:-2}"
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1
  adb exec-out screencap -p > "$OUT/$1.png"
  alive "$1"
  note "찍음: $1"
}

# 화면 접근성 트리에서 글자(또는 설명)를 찾아 그 자리를 누른다. 예: tap 'text="남길래"'
tap() {
  local box="" try msg=""
  # 도란이 계속 움직여 접근성 트리를 한 번에 못 뜰 때가 있어 세 번까지 다시 뜬다
  for try in 1 2 3; do
    adb shell rm -f /sdcard/ui.xml > /dev/null 2>&1
    msg=$(adb shell uiautomator dump /sdcard/ui.xml 2>&1 | tr -d '\r')
    box=$(adb exec-out cat /sdcard/ui.xml 2>/dev/null | tr '>' '\n' | grep -F "$1" | head -1 \
      | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')
    [ -n "$box" ] && break
    sleep 2
  done
  if [ -z "$box" ]; then
    note "  못 찾음: $1 (uiautomator: $msg)"
    return 1
  fi
  set -- $box
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
  note "  누름: 화면 ($(( ($1 + $3) / 2 )), $(( ($2 + $4) / 2 )))"
}

# 버튼을 누르고, 못 찾으면 같은 뜻의 디버그 인텐트로 잇는다. 예: tap_or 'text="남길래"' --es say 응
tap_or() {
  local target=$1
  shift
  tap "$target" || { note "  대신 인텐트: $*"; go "$@"; }
}

adb shell settings put global hide_error_dialogs 1 || true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear $PKG > /dev/null
adb shell pm grant $PKG android.permission.RECORD_AUDIO || true
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity > /dev/null
sleep 8
PID=$(adb shell pidof $PKG | tr -d '\r')
note "앱 시작 pid=$PID"

note "== 0. 처음 설정"
shot 00_처음설정 2
tap_or 'text="동의하고 시작하기"' --es onboarded yes --es route home
shot 01_홈 4
# 내비가 없는 에뮬레이터라 목적지·남은 시간·앞길 휴게소는 시연 값으로 채운다
go --es demo tape --es battery 64 --es seats 3 --es caption -
shot 02_홈_시연값 3

note "== 1. 출발"
go --es gear D
shot 10_출발_주행 4

note "== 2. 주행 중 휴게소 제안 → 경유지"
go --es event continuous_drive
shot 11_휴게소제안 10
go --es say "'응'"
shot 12_경유지_응 8

note "== 3. 도착해 세우면 \"한마디 남길래?\""
go --es arrive -
shot 20_도착_한마디 6

note "== 4. 녹음"
tap_or 'text="남길래"' --es say "'응'"
shot 21_녹음 4
shot 22_누가남겼어 14
tap_or 'text="엄마"' --es route home
shot 23_저장 5

note "== 5. 다시 타면 재탑승 제안"
go --es event reboarded
shot 30_재탑승제안 10
tap_or 'text="괜찮아"' --es say "'괜찮아'"
shot 31_재탑승_괜찮아 4

note "== 6. 음악"
tap_or 'text="음악"' --es route music
shot 40_음악 8
tap_or 'content-desc="재생"' --es play life-of-riley
shot 41_음악_재생 4
tap 'content-desc="다음 곡"' || true
shot 42_음악_다음곡 3
adb shell input keyevent KEYCODE_BACK
sleep 2

note "== 7. 앨범"
tap_or 'text="앨범"' --es route album
shot 50_앨범 6
tap_or 'text="지도 앨범 열기"' --es route albummap
shot 51_지도앨범 7
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_BACK
sleep 2

note "== 8. 주행·정차 전환과 화면 이동이 겹칠 때"
# 주행 중 들어온 화면 이동은 정차한 뒤에 열린다. 정차 화면까지 같이 닫히면 안 된다
go --es gear D
sleep 2
go --es route music
sleep 1
go --es gear P
shot 60_전환_주행중이동_정차 4
go --es route home --es gear D
sleep 2
go --es gear P --es route settings
shot 61_전환_같이요청 4
adb shell input keyevent KEYCODE_BACK
sleep 1
shot 62_전환_뒤로 2

note "== 9. 서버를 끈 채로"
pkill -f "server.main" || true
sleep 2
go --es route home --es caption -
sleep 2
go --es say "'휴게소 어디야'"
shot 70_서버꺼짐_말 8
go --es event continuous_drive
shot 71_서버꺼짐_이벤트 5
tap_or 'text="음악"' --es route music
shot 72_서버꺼짐_음악 8
adb shell input keyevent KEYCODE_BACK
sleep 2
tap_or 'text="앨범"' --es route album
shot 73_서버꺼짐_앨범 6
adb shell input keyevent KEYCODE_BACK
sleep 2
go --es arrive "'강문해변'"
shot 74_서버꺼짐_도착 6

adb logcat -d > "$OUT/logcat.txt"
if adb logcat -d -b crash | grep -q "$PKG"; then
  note "!! 크래시 로그 있음"
  adb logcat -d -b crash > "$OUT/crash.txt"
  FAIL=1
fi
if [ "$FAIL" = 0 ]; then note "결과: 죽거나 멈춘 곳 없음"; else note "결과: 문제 있음"; fi
exit $FAIL
