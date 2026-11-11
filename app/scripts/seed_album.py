"""화면 촬영용 여행 기록을 로컬 서버에 넣는다. 강릉 경로 위 휴게소 두 곳과 장소 한 곳에 들르고 목소리 두 개를 남긴 여행."""

import json
import sys
from pathlib import Path
from urllib import request

ROOT = Path(__file__).resolve().parents[2]
SESSION = sys.argv[1] if len(sys.argv) > 1 else "demo-trip-1"
BASE = "http://127.0.0.1:8765/v1/trip/record"


def post(body: dict) -> None:
    data = json.dumps({"session_id": SESSION, **body}).encode()
    req = request.Request(BASE, data=data, headers={"Content-Type": "application/json"})
    request.urlopen(req, timeout=5).read()


rest_stops = [r for r in json.loads((ROOT / "ai/data/gangneung/rest_stops.json").read_text(encoding="utf-8")) if r.get("along_route")]
places = [p for p in json.loads((ROOT / "ai/data/gangneung/places.json").read_text(encoding="utf-8")) if p.get("along_route")]
post({"type": "stop", "id": rest_stops[2]["id"]})
post({"type": "capsule", "id": "cap-demo-1", "name": "엄마"})
post({"type": "stop", "id": rest_stops[-2]["id"]})
post({"type": "stop", "id": places[0]["id"]})
post({"type": "capsule", "id": "cap-demo-2", "name": "아이"})
print("seeded", SESSION)
