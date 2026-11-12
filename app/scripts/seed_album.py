"""화면 촬영용 여행 기록을 켜져 있는 로컬 서버에 넣는다.

여행 구성은 server/demo_trips.json에 있고, 서버가 켜질 때 넣는 server/demo_seed.py와 같은 파일을 쓴다.
촬영은 빈 앨범부터 찍어야 해서 서버 자동 입력(DORAN_SEED_DEMO)은 끄고 이 스크립트로 하나씩 넣는다.
HTTP로 넣으므로 기록 시각은 지금이다.
"""

import json
import sys
from pathlib import Path
from urllib import request

ROOT = Path(__file__).resolve().parents[2]
SESSION = sys.argv[1] if len(sys.argv) > 1 else "demo-trip-1"
BASE = "http://127.0.0.1:8765/v1/trip/record"


def load(path: str) -> list[dict]:
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def catalog_id(items: list[dict], name: str) -> str | None:
    """demo_seed._catalog_id와 같은 순서로 찾는다: 이름이 같은 곳, 경로 위, 이름에 들어 있는 곳"""
    matches = [item for item in items if name in item.get("name", "")]
    exact = [item for item in matches if item.get("name") == name]
    on_route = [item for item in matches if item.get("along_route")]
    for candidate in (exact, on_route, matches):
        if candidate:
            return candidate[0].get("id")
    return None


def post(body: dict) -> None:
    data = json.dumps({"session_id": SESSION, **body}).encode()
    req = request.Request(BASE, data=data, headers={"Content-Type": "application/json"})
    request.urlopen(req, timeout=5).read()


catalog = load("ai/data/gangneung/rest_stops.json") + load("ai/data/gangneung/places.json")
trip = next(t for t in load("server/demo_trips.json") if t["session_id"] == SESSION)
for event in trip["events"]:
    body = {key: value for key, value in event.items() if key not in ("minute", "match")}
    if "match" in event:
        found = catalog_id(catalog, event["match"])
        if found is None:
            print("목록에 없는 장소, 건너뜀:", event["match"])
            continue
        body["id"] = found
    post(body)
print("seeded", SESSION)
