#!/usr/bin/env python3
"""음성 인식 시험을 안내하고 결과를 docs/app/voice-test.md에 남긴다.

디버그 앱이 logcat(DoranVoiceTest)에 남기는 "들은 문장·호출어 판단·서버 답"을 읽는다.
화면에 나온 문장을 차례로 말하면 기대 결과와 비교해 세고, 끝나면 합격 기준과 함께 기록한다.

    python3 app/scripts/voice_test.py                 # 전체 40회
    python3 app/scripts/voice_test.py --groups A,C    # 일부만
    python3 app/scripts/voice_test.py --env "실차"     # 기록에 남길 시험 환경

앱은 처음 설정을 마치고 "도란아" 대기 중이어야 하고, 요청 시험(B)은 AI 서버가 켜져 있어야 한다.
"""

import argparse
import json
import os
import queue
import subprocess
import sys
import threading
import time
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOC = ROOT / "docs" / "app" / "voice-test.md"
RAW_DIR = ROOT / ".local" / "voice-test"
TAG = "DoranVoiceTest"

HEARD_TIMEOUT = 15
REPLY_TIMEOUT = 25

# (묶음, 말할 문장, 말하는 방법)
CASES = (
    [("A", "도란아", "보통 빠르기로")] * 10
    + [("A", "도란아", "빠르게")] * 5
    + [("A", "도란아", "길게 늘여서")] * 5
    + [
        ("B", "도란아 근처 휴게소 있어?", ""),
        ("B", "도란아 좀 쉬었다 가자", ""),
        ("B", "도란아 언제 도착해?", ""),
        ("B", "도란아 얼마나 남았어?", ""),
        ("B", "도란아 노래 틀어 줘", ""),
        ("B", "도란아 신나는 거 듣고 싶어", ""),
        ("B", "도란아 근처에 맛있는 데 있어?", ""),
        ("B", "어 도란아 화장실 가고 싶어", ""),
        ("B", "도란아 조용히 해 줘", ""),
        ("B", "도란아 이제 말해도 돼", ""),
    ]
    + [
        ("C", "드라마 틀어 줘", ""),
        ("C", "이제 돌아가자", ""),
        ("C", "토란 먹자", ""),
        ("C", "도라지 무침 맛있겠다", ""),
        ("C", "그러나 저기 봐", ""),
        ("C", "돌아나가자", ""),
        ("C", "휴게소 어디야", ""),
        ("C", "오늘 날씨 좋다", ""),
        ("C", "아까 도란 앱 얘기했잖아", ""),
        ("C", "이 노래 좋다", ""),
    ]
)

GROUP_NAMES = {"A": "호출어만", "B": "호출어 + 요청", "C": "호출어 없는 말"}

# 묶음별 합격 기준: (항목, 세는 값 이름, 필요한 수 또는 허용 최대)
CRITERIA = {
    "A": [("호출 인식", "wake", 18)],
    "B": [("호출 인식·요청 분리", "split", 9), ("요청에 맞는 답", "answer", 8)],
    "C": [("잘못 깨어남", "false_wake", 0)],
}


def read_logcat(serial: str, out: "queue.Queue[dict]") -> subprocess.Popen:
    proc = subprocess.Popen(
        ["adb", "-s", serial, "logcat", "-T", "1", "-v", "raw", "-s", f"{TAG}:I"],
        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, encoding="utf-8",
    )

    def pump():
        for line in proc.stdout:
            line = line.strip()
            if line.startswith("{"):
                try:
                    out.put(json.loads(line))
                except json.JSONDecodeError:
                    pass

    threading.Thread(target=pump, daemon=True).start()
    return proc


def drain(q: "queue.Queue[dict]") -> None:
    while not q.empty():
        q.get_nowait()


def wait_for(q: "queue.Queue[dict]", kind: str, timeout: float) -> "dict | None":
    end = time.time() + timeout
    while time.time() < end:
        try:
            item = q.get(timeout=max(0.1, end - time.time()))
        except queue.Empty:
            return None
        if item.get("type") == kind:
            return item
    return None


def ask(prompt: str, choices: str) -> str:
    while True:
        answer = input(prompt).strip().lower()
        if answer in choices.split(","):
            return answer


def run_case(q, group, sentence, how) -> "dict | None":
    hint = f" ({how})" if how else ""
    while True:
        print(f"\n  말하세요 → 「{sentence}」{hint}")
        drain(q)
        heard = wait_for(q, "heard", HEARD_TIMEOUT)
        reply = None
        if heard is None:
            print("    못 들었어요(15초 안에 받아 적은 문장 없음)")
        else:
            extra = f" / 요청: {heard['command']}" if heard.get("command") else ""
            print(f"    받아 적기: {heard['text']}  →  {heard['match']}{extra}")
            if group == "B" and heard["match"] == "Command":
                reply = wait_for(q, "reply", REPLY_TIMEOUT)
                print(f"    도란 답: {reply['caption']} ({reply['status']})" if reply else "    답이 오지 않았어요")
        key = ask("    [Enter] 기록  [r] 다시  [q] 그만 > ", ",r,q")
        if key == "q":
            return None
        if key == "r":
            continue
        result = {
            "group": group, "say": sentence, "how": how,
            "heard": heard["text"] if heard else None,
            "match": heard["match"] if heard else "NothingHeard",
            "command": heard.get("command") if heard else None,
            "reply": reply,
        }
        if group == "B" and reply:
            result["answer_ok"] = ask("    답이 요청에 맞았나요? [y/n] > ", "y,n") == "y"
        return result


def score(results: list) -> dict:
    counts = {g: {"total": 0, "wake": 0, "split": 0, "answer": 0, "false_wake": 0} for g in "ABC"}
    for r in results:
        c = counts[r["group"]]
        c["total"] += 1
        woke = r["match"] in ("CallOnly", "Command")
        if r["group"] == "A" and woke:
            c["wake"] += 1
        if r["group"] == "B":
            if r["match"] == "Command":
                c["split"] += 1
            if r.get("answer_ok"):
                c["answer"] += 1
        if r["group"] == "C" and woke:
            c["false_wake"] += 1
    return counts


def verdicts(counts: dict, groups: str, complete: bool) -> "tuple[list, bool]":
    rows, passed = [], complete
    for g in groups:
        c = counts[g]
        planned = sum(1 for case in CASES if case[0] == g)
        for label, key, need in CRITERIA[g]:
            if key == "false_wake":
                ok = c[key] <= need
                rule = f"{need}회 이하"
            else:
                # 중단해 덜 했으면 기준을 같은 비율로 줄이지 않고 그대로 본다
                ok = c[key] >= need
                rule = f"{planned}회 중 {need}회 이상"
            passed = passed and ok
            rows.append((GROUP_NAMES[g], label, f"{c[key]} / {c['total']}", rule, "통과" if ok else "미달"))
    return rows, passed


def write_doc(results: list, groups: str, env: str, started: datetime, complete: bool) -> None:
    counts = score(results)
    rows, passed = verdicts(counts, groups, complete)
    if not DOC.exists():
        DOC.write_text(DOC_HEADER, encoding="utf-8")
    verdict = "합격" if passed else ("중단" if not complete else "불합격")
    lines = [
        "",
        f"## {started:%Y-%m-%d %H:%M} · {env} · {verdict}",
        "",
        "| 묶음 | 항목 | 결과 | 기준 | 판정 |",
        "| --- | --- | --- | --- | --- |",
        *[f"| {a} | {b} | {c} | {d} | {e} |" for a, b, c, d, e in rows],
    ]
    misses = [r for r in results if not case_ok(r)]
    if misses:
        lines += ["", "틀린 경우", ""]
        for r in misses:
            heard = r["heard"] if r["heard"] else "(못 들음)"
            reply = f", 답 「{r['reply']['caption']}」" if r.get("reply") else ""
            lines.append(f"- [{r['group']}] 「{r['say']}」 → 「{heard}」 {r['match']}{reply}")
    with DOC.open("a", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"\n기록했어요: {DOC.relative_to(ROOT)} ({verdict})")
    for a, b, c, d, e in rows:
        print(f"  {a} · {b}: {c} (기준 {d}) {e}")


def case_ok(r: dict) -> bool:
    woke = r["match"] in ("CallOnly", "Command")
    if r["group"] == "A":
        return woke
    if r["group"] == "B":
        return r["match"] == "Command" and r.get("answer_ok", False)
    return not woke


DOC_HEADER = """# 음성 인식 시험 기록

`app/scripts/voice_test.py`가 안내한 문장을 말하고, 디버그 앱이 남긴 받아 적기·호출어 판단·서버 답으로 센 결과입니다. 시험할 때마다 아래에 한 묶음씩 붙습니다.

## 시험 방법과 합격 기준

| 묶음 | 내용 | 횟수 | 합격 기준 |
| --- | --- | --- | --- |
| A 호출어만 | "도란아"를 보통 10회, 빠르게 5회, 길게 5회 | 20 | 18회 이상 호출로 인식(90%) |
| B 호출어 + 요청 | 휴게소, 도착 시간, 음악, 장소, 조용히 모드 요청 10문장 | 10 | 9회 이상 호출어를 떼고 요청만 보냄, 8회 이상 요청에 맞는 답 |
| C 호출어 없는 말 | 드라마, 돌아가자, 토란, 도라지처럼 비슷한 소리와 평소 대화 10문장 | 10 | 잘못 깨어남 0회 |

- 세 묶음을 모두 채워야 합격입니다. 중간에 그만두면 "중단"으로 남깁니다.
- B의 "요청에 맞는 답"은 시험한 사람이 화면의 답을 보고 판단합니다.
- 받아 적은 원문 전체는 저장소에 올리지 않고 `.local/voice-test/`에만 둡니다. 이 문서에는 틀린 경우의 문장만 남깁니다.
"""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", "emulator-5554"))
    parser.add_argument("--groups", default="A,B,C", help="시험할 묶음. 예: A,C")
    parser.add_argument("--env", default="에뮬레이터", help="기록에 남길 시험 환경. 예: 실차, 에뮬레이터(호스트 마이크)")
    args = parser.parse_args()
    groups = "".join(g for g in "ABC" if g in args.groups.upper())

    q: "queue.Queue[dict]" = queue.Queue()
    proc = read_logcat(args.serial, q)
    started = datetime.now()
    cases = [c for c in CASES if c[0] in groups]
    print(f"음성 시험 {len(cases)}회. 앱이 \"도란아\" 대기 중인지 확인하세요.")
    results, complete = [], True
    try:
        current = None
        for i, (group, sentence, how) in enumerate(cases, 1):
            if group != current:
                current = group
                print(f"\n== {group} {GROUP_NAMES[group]} ==")
            print(f"[{i}/{len(cases)}]", end="")
            result = run_case(q, group, sentence, how)
            if result is None:
                complete = False
                break
            results.append(result)
    except (KeyboardInterrupt, EOFError):
        complete = False
    finally:
        proc.terminate()

    if not results:
        print("\n기록할 결과가 없어요.")
        return 1
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    raw = RAW_DIR / f"{started:%Y%m%d-%H%M}.jsonl"
    raw.write_text("\n".join(json.dumps(r, ensure_ascii=False) for r in results) + "\n", encoding="utf-8")
    write_doc(results, groups, args.env, started, complete)
    print(f"원문: {raw.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
