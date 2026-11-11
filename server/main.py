"""도란 AI를 로컬 앱 시연에 연결하는 작은 HTTP 서버."""

from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import RLock
from typing import Any
from urllib.parse import parse_qs, urlsplit

from doran_ai import DoranAgent
from doran_ai.knowledge import KnowledgeBase
from doran_ai.llm import ChatCompletionsClassifier

from server.candidates import CandidateRanker
from server.config import ServerSettings
from server.db import TripRepository, create_db_engine, session_factory
from server.db.migrate import upgrade

DEMO_PLACES = [
    {"id": "demo-place-1", "name": "시연 장소", "source": "tour_api", "category": "attraction", "along_route": True},
]
DEMO_REST_STOPS = [
    {"id": "demo-rest-1", "name": "시연 휴게소", "source": "expressway_api", "along_route": True},
]
DEMO_TRACKS = [
    {"id": "demo-track-1", "name": "아침 여행", "source": "catalog", "tags": ["bright", "travel"]},
    {"id": "demo-track-2", "name": "차분한 귀갓길", "source": "catalog", "tags": ["calm"]},
]
SESSION_ID = re.compile(r"^[A-Za-z0-9_-]{1,64}$")


@dataclass
class Session:
    quiet: bool = False
    pending_waypoint_id: str | None = None
    pending_until: datetime | None = None
    selected_place_id: str | None = None
    selected_place_until: datetime | None = None
    offered_place_ids: list[str] = field(default_factory=list)
    selected_track_id: str | None = None
    selected_track_until: datetime | None = None
    recent_proposals: list[dict[str, str]] = field(default_factory=list)
    saved_place_ids: list[str] = field(default_factory=list)
    trip: dict[str, list[dict[str, Any]]] = field(default_factory=lambda: {"mentions": [], "stops": [], "capsules": []})


class AgentService:
    def __init__(
        self,
        *,
        places=None,
        rest_stops=None,
        tracks=None,
        trips: TripRepository | None = None,
        knowledge: KnowledgeBase | None = None,
    ):
        llm = ChatCompletionsClassifier.from_env()
        self.agent = DoranAgent(llm, writer=llm)
        self.knowledge = knowledge
        self.places = places if places is not None else DEMO_PLACES
        self.rest_stops = rest_stops if rest_stops is not None else DEMO_REST_STOPS
        self.tracks = tracks if tracks is not None else DEMO_TRACKS
        self.trips = trips
        # 수집 데이터와 경로가 있을 때만 요청마다 후보 순서를 다시 정한다
        self.ranker = (
            CandidateRanker(knowledge, self.places, self.rest_stops) if knowledge and knowledge.route else None
        )
        self.sessions: dict[str, Session] = {}
        self.lock = RLock()

    def respond(self, request: dict[str, Any]) -> dict[str, Any]:
        if not isinstance(request, dict):
            raise ValueError("JSON 객체가 필요합니다")
        session_id = request.get("session_id")
        if not isinstance(session_id, str) or not SESSION_ID.fullmatch(session_id):
            raise ValueError("session_id 형식이 올바르지 않습니다")
        now_value = request.get("now") or datetime.now(timezone(timedelta(hours=9))).isoformat()
        if not isinstance(now_value, str):
            raise ValueError("now 형식이 올바르지 않습니다")
        now = datetime.fromisoformat(now_value.replace("Z", "+00:00"))
        if now.tzinfo is None:
            raise ValueError("now에 시간대가 필요합니다")

        with self.lock:
            session = self._session(session_id)
            if session.pending_until and now >= session.pending_until:
                session.pending_waypoint_id = None
                session.pending_until = None
            if session.selected_place_until and now >= session.selected_place_until:
                session.selected_place_id = None
                session.selected_place_until = None
                session.offered_place_ids.clear()
            if session.selected_track_until and now >= session.selected_track_until:
                session.selected_track_id = None
                session.selected_track_until = None
            requested_place_id = request.get("selected_place_id")
            requested_track_id = request.get("selected_track_id")
            selected_place_id = (
                requested_place_id if requested_place_id in session.offered_place_ids
                else session.selected_place_id if requested_place_id is None else None
            )
            selected_track_id = (
                session.selected_track_id if requested_track_id is None or requested_track_id == session.selected_track_id
                else None
            )
            payload = {
                "kind": request.get("kind"),
                "now": now_value,
                "event": request.get("event"),
                "utterance": request.get("utterance"),
                "vehicle": request.get("vehicle"),
                "navigation": request.get("navigation"),
                "network_available": request.get("network_available", True),
                "places": self.ranker.places(request, self.places) if self.ranker else self.places,
                "rest_stops": self.ranker.rest_stops(request, self.rest_stops) if self.ranker else self.rest_stops,
                "tracks": self.tracks,
                "trip": session.trip,
                "preferences": {"quiet": session.quiet},
                "recent_proposals": session.recent_proposals,
                "selected_place_id": selected_place_id,
                "selected_track_id": selected_track_id,
                "pending_waypoint_id": session.pending_waypoint_id,
            }
            result = self.agent.handle(payload)
            effects: list[dict[str, Any]] = []
            if result["status"] == "invalid_input":
                return {**result, "effects": effects}
            if result["proposal_type"]:
                session.recent_proposals.append({"type": result["proposal_type"], "at": now_value})
                session.recent_proposals = session.recent_proposals[-30:]
            command_types = {command["type"] for command in result["commands"]}
            if request.get("kind") == "user_utterance" and not command_types.intersection({"SET_PENDING_WAYPOINT", "ADD_WAYPOINT", "CLEAR_PENDING"}):
                session.pending_waypoint_id = None
                session.pending_until = None
                if result["selected_place_id"] is None:
                    session.selected_place_id = None
                    session.selected_place_until = None
                    session.offered_place_ids.clear()
            if result["selected_place_id"]:
                session.selected_place_id = result["selected_place_id"]
                session.selected_place_until = now + timedelta(minutes=2)
                session.offered_place_ids = [result["selected_place_id"]]
                session.offered_place_ids.extend(
                    card["place_id"] for card in result["cards"]
                    if isinstance(card, dict)
                    and isinstance(card.get("place_id"), str)
                    and card["place_id"] != result["selected_place_id"]
                )
            if result["selected_track_id"]:
                session.selected_track_id = result["selected_track_id"]
                session.selected_track_until = now + timedelta(minutes=5)
            for command in result["commands"]:
                command_type = command["type"]
                if command_type == "SET_QUIET":
                    session.quiet = bool(command["value"])
                elif command_type == "SET_PENDING_WAYPOINT":
                    if self._place(command["place_id"]):
                        session.pending_waypoint_id = command["place_id"]
                        session.pending_until = now + timedelta(minutes=2)
                elif command_type == "CLEAR_PENDING":
                    session.pending_waypoint_id = None
                    session.pending_until = None
                elif command_type == "ADD_WAYPOINT":
                    place = self._place(command["place_id"])
                    # Pleos 내비는 POI ID 없이 좌표만으로도 경유지를 받는다
                    if place and self._coordinates(place):
                        effects.append({"type": "ADD_WAYPOINT", "place": self._public_place(place)})
                    else:
                        result["status"] = "unavailable"
                        result["speech"] = "내비 경유지 정보를 확인할 수 없어."
                        result["caption"] = result["speech"]
                        result["commands"] = []
                elif command_type == "SAVE_PLACE":
                    place = self._place(command["place_id"])
                    if place and place["id"] not in session.saved_place_ids:
                        session.saved_place_ids.append(place["id"])
                elif command_type == "PLAY_MUSIC":
                    if any(t["id"] == command["track_id"] for t in self.tracks):
                        effects.append({"type": "PLAY_MUSIC", "track_id": command["track_id"]})
                elif command_type == "PLAY_CAPSULE":
                    if any(c.get("id") == command["capsule_id"] for c in session.trip["capsules"]):
                        effects.append({"type": "PLAY_CAPSULE", "capsule_id": command["capsule_id"]})
            cards = [card for card in map(self._card, result["cards"]) if card]
            parked = _mapping(request.get("vehicle")).get("gear") not in {"D", "R"}
            # 차량 이벤트 제안(휴게소, 재탑승)은 카드 없이 오므로, 정차 중이면 제안한 장소를 카드로 보여 준다
            if parked and not cards and result["selected_place_id"] and result["status"] in {"ok", "needs_confirmation"}:
                card = self._card({"place_id": result["selected_place_id"]})
                cards = [card] if card else []
            return {**result, "cards": cards, "effects": effects}

    def record(self, request: dict[str, Any]) -> dict[str, Any]:
        if not isinstance(request, dict):
            raise ValueError("JSON 객체가 필요합니다")
        session_id = request.get("session_id")
        if not isinstance(session_id, str) or not SESSION_ID.fullmatch(session_id):
            raise ValueError("session_id 형식이 올바르지 않습니다")
        kind = request.get("type")
        if kind not in {"mention", "stop", "capsule"}:
            raise ValueError("기록 종류가 올바르지 않습니다")
        item_id = request.get("id")
        if not isinstance(item_id, str) or not item_id or len(item_id) > 128:
            raise ValueError("기록 ID가 올바르지 않습니다")
        position = {"latitude": request.get("latitude"), "longitude": request.get("longitude")}
        if any(v is not None for v in position.values()) and not self._coordinates(position):
            raise ValueError("좌표가 올바르지 않습니다")
        with self.lock:
            session = self._session(session_id)
            if kind == "mention":
                name = request.get("name")
                if not isinstance(name, str) or not name.strip() or len(name) > 80:
                    raise ValueError("장소 이름이 올바르지 않습니다")
                entry = {"id": item_id, "name": name.strip(), "category": request.get("category", "")}
                session.trip["mentions"].append(entry)
            else:
                entry = {"id": item_id}
                # 앱이 이름·좌표를 모르는 정차 기록은 후보 목록에서 채워 앨범 지도에 찍을 수 있게 한다
                place = self._place(item_id) if kind == "stop" else None
                if place:
                    entry |= {"name": place["name"], "category": place.get("category", "rest_stop")}
                    if not any(v is not None for v in position.values()) and self._coordinates(place):
                        position = {"latitude": place["latitude"], "longitude": place["longitude"]}
                elif isinstance(request.get("name"), str):
                    entry["name"] = request["name"].strip()[:80]
                session.trip["stops" if kind == "stop" else "capsules"].append(entry)
            if self.trips:
                self.trips.add_event(
                    session_id,
                    kind,
                    item_id,
                    name=entry.get("name", ""),
                    category=str(entry.get("category", ""))[:32],
                    **position,
                )
            return {"status": "ok"}

    def history(self, session_id: str | None) -> dict[str, Any]:
        if not self.trips:
            raise ValueError("기록 저장소가 없습니다")
        if session_id is None:
            return {"status": "ok", "trips": self.trips.list_trips()}
        if not isinstance(session_id, str) or not SESSION_ID.fullmatch(session_id):
            raise ValueError("session_id 형식이 올바르지 않습니다")
        return {"status": "ok", "session_id": session_id, "events": self.trips.list_events(session_id)}

    def _session(self, session_id: str) -> Session:
        if session_id not in self.sessions:
            session = Session()
            if self.trips:
                session.trip = self.trips.load_trip(session_id)
            self.sessions[session_id] = session
        return self.sessions[session_id]

    def _place(self, place_id: str) -> dict[str, Any] | None:
        return next((p for p in self.places + self.rest_stops if p.get("id") == place_id), None)

    def _card(self, card: Any) -> dict[str, Any] | None:
        """정차 화면 카드에 보여 줄 정보를 붙인다. 후보 목록에 없는 ID는 버린다."""
        if not isinstance(card, dict):
            return None
        place_id, track_id = card.get("place_id"), card.get("track_id")
        if isinstance(place_id, str):
            place = self._place(place_id)
            if not place:
                return None
            is_rest = place.get("source") == "expressway_api"
            return {
                "place_id": place["id"],
                "name": place["name"],
                "kind": "rest_stop" if is_rest else "place",
                "category": "rest_stop" if is_rest else place.get("category", ""),
                "address": place.get("address", ""),
                "summary": _summary(place),
                "image_url": place.get("image_url", ""),
                "distance_km": place.get("route_distance_km"),
            }
        if isinstance(track_id, str):
            track = next((t for t in self.tracks if t.get("id") == track_id), None)
            if not track:
                return None
            return {"track_id": track["id"], "name": track["name"], "artist": track.get("artist", ""), "tags": track.get("tags", [])}
        return None

    @staticmethod
    def _coordinates(place: dict[str, Any]) -> bool:
        lat, lon = place.get("latitude"), place.get("longitude")
        return type(lat) in (int, float) and type(lon) in (int, float) and -90 <= lat <= 90 and -180 <= lon <= 180

    @staticmethod
    def _public_place(place: dict[str, Any]) -> dict[str, Any]:
        result = {key: place[key] for key in ("id", "name", "latitude", "longitude")}
        result["navi_poi_id"] = place.get("navi_poi_id") or None
        result["navi_poi_sub_id"] = place.get("navi_poi_sub_id") or None
        result["address"] = place.get("address", "")
        return result


def _mapping(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}


def _summary(place: dict[str, Any]) -> str:
    """카드 한 줄 설명. 휴게소는 시설, 장소는 운영 시간이나 소개 첫 문장."""
    if place.get("source") == "expressway_api":
        menu = [m for m in place.get("menu", []) if isinstance(m, str)][:2]
        facilities = [f for f in place.get("facilities", []) if isinstance(f, str)][:3]
        return ", ".join(menu + facilities)
    if place.get("opening_hours"):
        return f"운영 {place['opening_hours']}"[:60]
    overview = str(place.get("overview", "")).replace("#", " ").split(".")[0].strip()
    return overview[:60]


def load_catalog(path: str) -> dict[str, list[dict[str, Any]]]:
    with open(path, encoding="utf-8") as source:
        data = json.load(source)
    if not isinstance(data, dict):
        raise ValueError("후보 데이터는 JSON 객체여야 합니다")
    expected = {"places": "tour_api", "rest_stops": "expressway_api", "tracks": "catalog"}
    for key, source in expected.items():
        items = data.get(key)
        if not isinstance(items, list):
            raise ValueError(f"{key} 목록이 필요합니다")
        for item in items:
            if not isinstance(item, dict) or item.get("source") != source:
                raise ValueError(f"{key}의 출처가 올바르지 않습니다")
            if not isinstance(item.get("id"), str) or not item["id"]:
                raise ValueError(f"{key}의 ID가 올바르지 않습니다")
            if not isinstance(item.get("name"), str) or not item["name"].strip():
                raise ValueError(f"{key}의 이름이 올바르지 않습니다")
            if key != "tracks" and not AgentService._coordinates(item):
                raise ValueError(f"{key}의 좌표가 올바르지 않습니다")
            if key != "tracks" and item.get("along_route") is not True:
                raise ValueError(f"{key}의 경로 확인 값이 필요합니다")
    return {key: data[key] for key in expected}


def make_handler(service: AgentService):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            parsed = urlsplit(self.path)
            if parsed.path == "/health":
                self._send(200, {"status": "ok", "mode": "local_demo"})
                return
            if parsed.path == "/v1/trip/record":
                try:
                    values = parse_qs(parsed.query).get("session_id")
                    self._send(200, service.history(values[0] if values else None))
                except (ValueError, TypeError, KeyError):
                    self._send(400, {"error": "invalid_request"})
                return
            self._send(404, {"error": "not_found"})

        def do_POST(self):
            if self.path not in {"/v1/agent/respond", "/v1/trip/record"}:
                self._send(404, {"error": "not_found"})
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= 65536:
                    raise ValueError("요청 크기가 올바르지 않습니다")
                payload = json.loads(self.rfile.read(length))
                result = service.respond(payload) if self.path == "/v1/agent/respond" else service.record(payload)
                self._send(200, result)
            except (ValueError, TypeError, KeyError, json.JSONDecodeError):
                self._send(400, {"error": "invalid_request"})

        def _send(self, status: int, value: dict[str, Any]):
            body = json.dumps(value, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, format, *args):
            pass

    return Handler


def _export_llm_settings(settings: ServerSettings) -> None:
    values = {
        "DORAN_LLM_ENDPOINT": settings.doran_llm_endpoint,
        "DORAN_LLM_MODEL": settings.doran_llm_model,
        "DORAN_LLM_API_KEY": settings.doran_llm_api_key,
        "GROQ_API_KEY": settings.groq_api_key,
        "DORAN_LLM_TIMEOUT_SECONDS": str(settings.doran_llm_timeout_seconds),
    }
    for key, value in values.items():
        if value:
            os.environ.setdefault(key, value)


def main() -> None:
    settings = ServerSettings()
    _export_llm_settings(settings)
    knowledge = KnowledgeBase.load(settings.region_dir)
    catalog_path = os.getenv("DORAN_CATALOG_FILE")
    if catalog_path:
        catalog = load_catalog(catalog_path)
    else:
        catalog = knowledge.agent_catalog() if knowledge.places else {}
    upgrade(settings.doran_database_url)
    trips = TripRepository(session_factory(create_db_engine(settings.doran_database_url)), settings.doran_region)
    service = AgentService(**catalog, trips=trips, knowledge=knowledge)
    server = ThreadingHTTPServer(("127.0.0.1", 8765), make_handler(service))
    llm = os.getenv("DORAN_LLM_MODEL") or "없음(규칙만)"
    print(f"Doran local demo server: http://127.0.0.1:8765  data={settings.doran_region} llm={llm}", flush=True)
    try:
        server.serve_forever()
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
