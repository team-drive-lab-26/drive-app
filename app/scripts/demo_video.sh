#!/usr/bin/env bash
# 접수용 시연 영상을 에뮬레이터 화면 녹화로 만든다. Actions의 android-video 워크플로가 부른다.
# 화면은 Pleos 규격(2560x1440, 160dpi)으로 맞추고 1920x1080으로 녹화한다(안 되면 1280x720). screenrecord는 한 번에 3분까지라 조각으로 나눠 찍는다.
# 소리가 없으므로 사람이 한 말과 장면 이름은 $OUT/captions.tsv(시작초, 끝초, 종류, 글)로 남기고, 워크플로가 영상에 입힌다.
# 결과: $OUT/seg*.mp4, $OUT/captions.tsv, $OUT/logcat.txt
set -u
OUT=${1:-video}
PKG=com.doran.app
mkdir -p "$OUT"
: > "$OUT/captions.tsv"
T0=""

go() { adb shell am start -n $PKG/.MainActivity "$@" > /dev/null; }
now() { date +%s.%N; }
at() { awk -v a="$(now)" -v b="$T0" 'BEGIN { printf "%.2f", a - b }'; }

# 장면 이름: 다음 장면 이름이 나올 때까지 오른쪽 위에 둔다
SCENE_START=""; SCENE_TEXT=""
scene() {
  local t; t=$(at)
  [ -n "$SCENE_TEXT" ] && printf '%s\t%s\tscene\t%s\n' "$SCENE_START" "$t" "$SCENE_TEXT" >> "$OUT/captions.tsv"
  SCENE_START=$t; SCENE_TEXT=$1
}

# 사람이 한 말: 화면 아래에 4초 보인다. 말은 디버그 인텐트로 넣는다
said() {
  local who=$1 text=$2 t
  t=$(at)
  printf '%s\t%s\tsay\t%s\n' "$t" "$(awk -v a="$t" 'BEGIN { printf "%.2f", a + 4 }')" "$who: \"$text\"" >> "$OUT/captions.tsv"
}

tap() {
  local box="" try
  for try in 1 2 3; do
    adb shell rm -f /sdcard/ui.xml > /dev/null 2>&1
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    box=$(adb exec-out cat /sdcard/ui.xml 2>/dev/null | tr '>' '\n' | grep -F "$1" | head -1 \
      | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')
    [ -n "$box" ] && break
    sleep 1
  done
  [ -z "$box" ] && { echo "못 찾음: $1"; return 1; }
  set -- $box
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
tap_or() { local target=$1; shift; tap "$target" || go "$@"; }

rec_loop() {
  local i=0
  while [ ! -f "$OUT/.stop" ]; do
    i=$((i + 1))
    adb shell screenrecord --time-limit 170 --size $SIZE --bit-rate 12000000 /sdcard/seg$i.mp4
  done
}

# 준비: 화면 규격, 설치, 첫 실행(소프트웨어 렌더링 셰이더 준비)
adb shell settings put global hide_error_dialogs 1 || true
adb shell wm size 2560x1440
adb shell wm density 160
# GPU 없는 에뮬레이터는 WebGL 지도를 하얗게 그려서 지도 앨범을 평면 지도로 연다(디버그 빌드 전용 설정)
adb shell settings put global doran_flat_map 1
adb install -r app/build/outputs/apk/debug/app-debug.apk
for i in $(seq 30); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && break; sleep 2; done
sleep 30
adb shell pm clear $PKG > /dev/null
adb shell pm grant $PKG android.permission.RECORD_AUDIO || true
adb shell am start -W -n $PKG/.MainActivity > /dev/null
sleep 60
adb shell am force-stop $PKG
sleep 3
adb shell pm clear $PKG > /dev/null
adb shell pm grant $PKG android.permission.RECORD_AUDIO || true
adb shell rm -f '/sdcard/seg*.mp4'
SIZE=1920x1080
adb shell screenrecord --time-limit 2 --size $SIZE /sdcard/probe.mp4 > /dev/null 2>&1 || SIZE=1280x720
adb shell rm -f /sdcard/probe.mp4
echo "녹화 크기: $SIZE"
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity > /dev/null
sleep 6

rec_loop &
REC=$!
sleep 1
T0=$(now)

scene "처음 실행"
sleep 6
tap_or 'text="동의하고 시작하기"' --es onboarded yes --es route home
sleep 3
# 내비가 없는 에뮬레이터라 목적지·남은 시간·앞길 휴게소는 시연 값으로 채운다
go --es demo tape --es battery 64 --es seats 3 --es caption -
sleep 5

scene "출발 (기어 D)"
go --es gear D
sleep 6
said "아빠" "도란아, 휴게소 어디야"
go --es say "'휴게소 어디야'"
sleep 9

scene "연속 운전 110분"
go --es event continuous_drive
sleep 10
said "아빠" "응"
go --es say "'응'"
sleep 9

scene "휴게소 도착 (기어 P)"
go --es arrive -
sleep 9
said "엄마" "응"
tap_or 'text="남길래"' --es say "'응'"
scene "15초 녹음 (음악 정지, 송풍 최저)"
sleep 18
tap_or 'text="엄마"' --es route home
sleep 6

scene "20분 쉬고 다시 탐"
go --es event reboarded
sleep 10
tap_or 'text="들르기"' --es say "'응'"
sleep 8

scene "정차 중 음악"
tap_or 'text="음악"' --es route music
sleep 8
tap_or 'content-desc="재생"' --es play life-of-riley
sleep 7
adb shell input keyevent KEYCODE_BACK
sleep 2

scene "여행 앨범"
tap_or 'text="앨범"' --es route album
sleep 7
# 들른 곳이 여럿인 40일 전 시연 여행(기록 6개)을 고른다
tap 'text="기록 6개"' || tap 'content-desc="기록 6개"' || true
sleep 4
tap_or 'text="지도 앨범 열기"' --es route albummap
scene "3D 지도 앨범"
sleep 8
tap 'text="엄마"' || tap 'text="아이"' || true
sleep 8
scene "끝"

touch "$OUT/.stop"
adb shell pkill -INT screenrecord || true
wait $REC
for f in $(adb shell ls /sdcard/ | tr -d '\r' | grep '^seg.*\.mp4$'); do adb pull "/sdcard/$f" "$OUT/$f" > /dev/null; done
adb exec-out screencap -p > "$OUT/last.png"
adb logcat -d > "$OUT/logcat.txt"
ls -la "$OUT"
cat "$OUT/captions.tsv"
