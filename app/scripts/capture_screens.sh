#!/usr/bin/env bash
# 에뮬레이터에서 앱 화면을 차례로 열어 스크린샷을 남긴다. Actions의 android-screens 워크플로가 부른다.
set -u
OUT=${1:-screens}
PKG=com.doran.app
mkdir -p "$OUT"

shot() {
  sleep 2
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "찍음: $1"
}

# 화면에 보이는 글자로 버튼을 찾아 누른다
tap() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null
  adb pull /sdcard/ui.xml ui.xml > /dev/null
  local bounds
  bounds=$(python3 - "$1" <<'EOF'
import re, sys, xml.etree.ElementTree as ET
label = sys.argv[1]
for node in ET.parse("ui.xml").iter("node"):
    if node.get("text") == label or node.get("content-desc") == label:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
EOF
)
  if [ -z "$bounds" ]; then
    echo "못 찾음: $1"
    return 1
  fi
  adb shell input tap $bounds
}

back() { adb shell input keyevent KEYCODE_BACK; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.RECORD_AUDIO || true
adb shell am start -W -n $PKG/.MainActivity
sleep 5

shot 01_처음설정
tap "설정 끝내기" && shot 02_앨범
tap "3D 지도 앨범" && shot 03_3D지도앨범; back
tap "캐릭터 홈" && shot 04_캐릭터홈; back
tap "설정" && shot 05_설정; back
tap "녹음 (자동 전환 연결 전 임시)" && shot 06_녹음 && tap "녹음 끝" ; shot 07_녹음다음; back; back
tap "음악 추천 (자동 전환 연결 전 임시)" && shot 08_음악; back
tap "D" && shot 09_주행
tap "P" && shot 10_정차
exit 0
