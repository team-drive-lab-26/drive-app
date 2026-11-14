"""화면마다 누를 수 있는 것을 전부 찾아 하나씩 눌러 보고, 앱이 죽거나 멈추는지와 어느 화면으로 가는지 적는다.

Actions의 android-ui 워크플로가 에뮬레이터에서 부른다(debug APK, 로컬 서버 켠 상태).
결과: <out>/result.md(화면·버튼·누른 뒤 화면·상태 표), <out>/*.png(누른 뒤 화면), <out>/logcat.txt
한 번이라도 죽거나 멈추면 실패(종료 코드 1)로 끝난다.
"""

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PKG = "com.doran.app"
OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "ui")
OUT.mkdir(parents=True, exist_ok=True)
rows: list[tuple[str, str, str, str]] = []
failed = False
pid = ""


def adb(*args: str, check: bool = False) -> str:
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=check).stdout


def go(*extras: str) -> None:
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity", *extras)


def dump() -> ET.Element | None:
    for _ in range(3):
        adb("shell", "rm", "-f", "/sdcard/ui.xml")
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        raw = adb("exec-out", "cat", "/sdcard/ui.xml")
        if raw.strip().startswith("<?xml"):
            try:
                return ET.fromstring(raw)
            except ET.ParseError:
                pass
        time.sleep(1.5)
    return None


def label(node: ET.Element) -> str:
    own = node.get("text") or node.get("content-desc") or ""
    if own:
        return own.strip()
    parts = [n.get("text") or n.get("content-desc") or "" for n in node.iter()]
    return " ".join(p.strip() for p in parts if p.strip())[:40]


def bounds(node: ET.Element) -> tuple[int, int, int, int]:
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "[0,0][0,0]")))
    return x1, y1, x2, y2


def buttons(root: ET.Element) -> list[tuple[str, tuple[int, int, int, int]]]:
    found = []
    for node in root.iter("node"):
        if node.get("package") != PKG or node.get("clickable") != "true" or node.get("enabled") != "true":
            continue
        b = bounds(node)
        if b[2] - b[0] < 8 or b[3] - b[1] < 8:
            continue
        found.append((label(node) or f"(이름 없음 {b[0]},{b[1]})", b))
    return found


def screen_text(root: ET.Element | None) -> str:
    if root is None:
        return "(화면 읽기 실패)"
    texts = [n.get("text", "").strip() for n in root.iter("node") if n.get("package") == PKG and n.get("text", "").strip()]
    return " / ".join(texts[:4])[:90]


def health(where: str) -> str:
    global failed, pid
    now = adb("shell", "pidof", PKG).strip()
    if not now or (pid and now != pid):
        failed = True
        pid = now
        return "앱 죽음"
    if f"Application Not Responding: {PKG}" in adb("shell", "dumpsys", "window"):
        failed = True
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        return "응답 없음"
    return "정상"


# 화면 이름, 그 화면으로 가는 디버그 인텐트 묶음, 기다릴 초
SCREENS: list[tuple[str, list[list[str]], float]] = [
    ("정차 홈", [["--es", "route", "home"]], 3),
    ("재탑승 제안 카드", [["--es", "route", "home"], ["--es", "event", "reboarded"]], 9),
    ("도착 한마디 패널", [["--es", "route", "home"], ["--es", "arrive", "-"]], 7),
    ("녹음 중", [["--es", "route", "record"]], 3),
    ("녹음 뒤 누가 남겼어", [["--es", "route", "record"]], 18),
    ("음악", [["--es", "route", "music"]], 6),
    ("앨범", [["--es", "route", "album"]], 5),
    ("지도 앨범", [["--es", "route", "albummap"]], 14),
    ("설정", [["--es", "route", "settings"]], 3),
]


def reach(steps: list[list[str]], wait: float) -> None:
    # 매번 정차 홈에서 다시 시작해 앞 버튼의 결과가 다음 버튼에 섞이지 않게 한다
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    go("--es", "gear", "P", "--es", "route", "home", "--es", "caption", "-")
    time.sleep(1.5)
    for extras in steps:
        go(*extras)
        time.sleep(1)
    time.sleep(wait)


def crawl(name: str, steps: list[list[str]], wait: float) -> None:
    reach(steps, wait)
    root = dump()
    if root is None:
        rows.append((name, "-", "화면 읽기 실패", health(name)))
        return
    targets = buttons(root)
    print(f"== {name}: 누를 것 {len(targets)}개", flush=True)
    for i, (text, _) in enumerate(targets):
        reach(steps, wait)
        current = dump()
        match = None
        if current is not None:
            match = next((b for t, b in buttons(current) if t == text), None)
        if match is None:
            rows.append((name, text, "다시 열었을 때 버튼이 없어 건너뜀", health(name)))
            continue
        x1, y1, x2, y2 = match
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(2.5)
        state = health(f"{name}:{text}")
        after = dump()
        shot = OUT / f"{len(rows):03d}_{re.sub(r'[^0-9A-Za-z가-힣]+', '_', name + '_' + text)[:50]}.png"
        shot.write_bytes(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
        rows.append((name, text, screen_text(after), state))
        print(f"  {text} -> {state}", flush=True)
        # 글자 입력칸을 눌렀으면 키보드를 닫는다
        adb("shell", "input", "keyevent", "KEYCODE_ESCAPE")
    # 뒤로 가기
    reach(steps, wait)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(2)
    rows.append((name, "(뒤로 가기)", screen_text(dump()), health(f"{name}:back")))


def main() -> None:
    global pid
    adb("shell", "settings", "put", "global", "hide_error_dialogs", "1")
    adb("shell", "wm", "size", "2560x1440")
    adb("shell", "wm", "density", "160")
    adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk", check=True)
    time.sleep(30)
    adb("shell", "pm", "clear", PKG)
    adb("shell", "pm", "grant", PKG, "android.permission.RECORD_AUDIO")
    # 소프트웨어 렌더링 셰이더 준비
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    time.sleep(60)
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "pm", "clear", PKG)
    adb("shell", "pm", "grant", PKG, "android.permission.RECORD_AUDIO")
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    time.sleep(6)
    pid = adb("shell", "pidof", PKG).strip()

    # 처음 설정: 버튼을 모두 눌러 보고, 마지막에 동의해서 넘어간다
    root = dump()
    for text, (x1, y1, x2, y2) in buttons(root) if root is not None else []:
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(3)
        rows.append(("처음 설정", text, screen_text(dump()), health("onboarding")))
    go("--es", "onboarded", "yes", "--es", "route", "home")
    time.sleep(3)
    go("--es", "demo", "tape", "--es", "battery", "64", "--es", "seats", "3")
    time.sleep(3)

    for name, steps, wait in SCREENS:
        crawl(name, steps, wait)

    # 주행 화면: 버튼이 없어야 하고, 도란을 누르면 듣는다
    go("--es", "gear", "D")
    time.sleep(4)
    root = dump()
    rows.append(("주행", f"누를 수 있는 것 {len(buttons(root)) if root is not None else '?'}개", screen_text(root), health("driving")))
    for text, (x1, y1, x2, y2) in buttons(root) if root is not None else []:
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(3)
        rows.append(("주행", text, screen_text(dump()), health("driving tap")))
    go("--es", "gear", "P")
    time.sleep(2)

    crashes = adb("logcat", "-d", "-b", "crash")
    (OUT / "logcat.txt").write_text(adb("logcat", "-d"), encoding="utf-8")
    if PKG in crashes:
        failed_note = "크래시 로그 있음"
        (OUT / "crash.txt").write_text(crashes, encoding="utf-8")
    else:
        failed_note = "크래시 로그 없음"

    lines = ["| 화면 | 누른 것 | 누른 뒤 화면 | 상태 |", "| --- | --- | --- | --- |"]
    lines += [f"| {a} | {b} | {c} | {d} |" for a, b, c, d in rows]
    bad = [r for r in rows if r[3] != "정상"]
    summary = f"누른 것 {len(rows)}개, 문제 {len(bad)}개, {failed_note}"
    (OUT / "result.md").write_text(summary + "\n\n" + "\n".join(lines) + "\n", encoding="utf-8")
    print(summary)
    sys.exit(1 if failed or bad or PKG in crashes else 0)


if __name__ == "__main__":
    main()
