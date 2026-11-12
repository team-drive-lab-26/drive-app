"""화면 촬영·시연용 여행 기록을 로컬 서버에 넣는다. 실제 여행 기록이 아니라 앨범 화면을 채워 보이기 위한 값이다.

인자 없이 부르면 여행 세 번을 넣는다.
- demo-trip-1: 오늘. 하남드림·횡성 휴게소와 경로 위 장소 한 곳, 목소리 둘(엄마, 아이)
- demo-trip-2: 40일 전. 횡성 휴게소와 강릉 장소 두 곳, 목소리 셋
- demo-trip-3: 89일 전. 하남드림 휴게소와 장소 한 곳, 목소리 하나
인자로 session_id를 주면 그 여행 하나만 넣는다.

서버 API는 기록 시각을 지금으로 찍으므로, 넣은 뒤 로컬 SQLite(server/var/doran.db 또는 DORAN_DATABASE_URL)에서
여행 시작·기록 시각을 시연 날짜로 옮긴다. DB를 찾지 못하면 시각은 그대로 둔다.
"""

import json
import os
import sqlite3
import sys
from datetime import UTC, datetime, timedelta
from pathlib import Path
from urllib import request

ROOT = Path(__file__).resolve().parents[2]
BASE = "http://127.0.0.1:8765/v1/trip/record"


def post(session: str, body: dict) -> None:
    data = json.dumps({"session_id": session, **body}).encode()
    req = request.Request(BASE, data=data, headers={"Content-Type": "application/json"})
    request.urlopen(req, timeout=5).read()


def load(name: str) -> list[dict]:
    return [r for r in json.loads((ROOT / f"ai/data/gangneung/{name}.json").read_text(encoding="utf-8")) if r.get("along_route")]


rest_stops = load("rest_stops")
places = load("places")
hanam = next(r for r in rest_stops if "하남" in r["name"])
hoengseong = next(r for r in rest_stops if "횡성" in r["name"])


def place(name: str) -> dict:
    return next(p for p in places if p["name"] == name)


def stop(item: dict) -> dict:
    return {"type": "stop", "id": item["id"]}


def voice(capsule_id: str, speaker: str) -> dict:
    return {"type": "capsule", "id": capsule_id, "name": speaker}


# (session_id, 며칠 전, 출발 시각(서울), [(출발 후 분, 기록)])
TRIPS = [
    ("demo-trip-1", 0, (8, 10), [(42, stop(hanam)), (50, voice("cap-demo-1", "엄마")), (118, stop(hoengseong)), (176, stop(places[0])), (190, voice("cap-demo-2", "아이"))]),
    (
        "demo-trip-2",
        40,
        (9, 0),
        [(96, stop(hoengseong)), (101, voice("cap-demo-3", "아빠")), (172, stop(place("고선생 화덕생선구이"))), (180, voice("cap-demo-4", "아이")), (231, stop(place("경포호수광장"))), (236, voice("cap-demo-5", "엄마"))],
    ),
    ("demo-trip-3", 89, (7, 40), [(38, stop(hanam)), (160, stop(place("테라로사 경포호수점"))), (168, voice("cap-demo-6", "아이"))]),
]


def database() -> Path | None:
    url = os.environ.get("DORAN_DATABASE_URL", "")
    path = Path(url.removeprefix("sqlite:///")) if url.startswith("sqlite:///") else ROOT / "server/var/doran.db"
    return path if path.exists() else None


def backdate(db: Path, session: str, days: int, start: tuple[int, int], minutes: list[int]) -> None:
    seoul = datetime.now(UTC) + timedelta(hours=9)
    begin = (seoul - timedelta(days=days)).replace(hour=start[0], minute=start[1], second=0, microsecond=0) - timedelta(hours=9)
    # 오늘 여행이 아직 오지 않은 시각이면 하루 앞당긴다
    if begin > datetime.now(UTC) and days == 0:
        begin -= timedelta(days=1)
    fmt = "%Y-%m-%d %H:%M:%S.%f"
    with sqlite3.connect(db) as con:
        row = con.execute("SELECT id FROM trips WHERE session_id = ?", (session,)).fetchone()
        if not row:
            return
        con.execute("UPDATE trips SET started_at = ? WHERE id = ?", (begin.strftime(fmt), row[0]))
        ids = [r[0] for r in con.execute("SELECT id FROM trip_events WHERE trip_id = ? ORDER BY id", (row[0],))]
        for event_id, minute in zip(ids[-len(minutes):], minutes):
            con.execute("UPDATE trip_events SET recorded_at = ? WHERE id = ?", ((begin + timedelta(minutes=minute)).strftime(fmt), event_id))


only = sys.argv[1] if len(sys.argv) > 1 else None
db = database()
for session, days, start, events in TRIPS:
    if only and session != only:
        continue
    for _, body in events:
        post(session, body)
    if db:
        backdate(db, session, days, start, [m for m, _ in events])
    print("seeded", session, "(시각 조정)" if db else "")
