"""차량 맥락을 안전한 음성 응답과 실행 요청으로 바꾼다."""

from __future__ import annotations

from datetime import datetime, timedelta
from typing import Any

from .llm import INTENTS, IntentClassifier


YES = {"응", "네", "그래", "좋아", "확인", "해줘", "응 추가해", "네 추가해", "추가해"}
NO = {"아니", "괜찮아", "취소", "됐어", "아니야"}
QUIET = ("조용히 해", "말 걸지 마", "조용히")
QUIET_OFF = ("다시 말 걸어", "조용히 모드 꺼", "먼저 말 걸어")


def _response(
    status: str = "no_action",
    speech: str = "",
    *,
    proposal_type: str | None = None,
    selected_place_id: str | None = None,
    selected_track_id: str | None = None,
    cards: list[dict[str, Any]] | None = None,
    commands: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    return {
        "status": status,
        "speech": speech,
        "caption": speech.split(".")[0][:48],
        "proposal_type": proposal_type,
        "selected_place_id": selected_place_id,
        "selected_track_id": selected_track_id,
        "cards": cards or [],
        "commands": commands or [],
    }


def _time(value: str) -> datetime:
    if not isinstance(value, str):
        raise ValueError("시각은 ISO 8601 문자열이어야 합니다")
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ValueError("now와 제안 시각에는 시간대가 필요합니다")
    return parsed


def _trusted(items: Any, source: str) -> list[dict[str, Any]]:
    if not isinstance(items, list):
        return []
    return [
        item for item in items
        if isinstance(item, dict)
        and item.get("source") == source
        and isinstance(item.get("id"), str) and item["id"]
        and isinstance(item.get("name"), str) and item["name"]
    ]


def _find(items: list[dict[str, Any]], item_id: Any) -> dict[str, Any] | None:
    return next((item for item in items if item["id"] == item_id), None)


def _nonnegative_number(value: Any) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool) and value >= 0


def _mapping(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}


def _list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def _particle(name: str, batchim: str, no_batchim: str) -> str:
    last = name[-1]
    has_batchim = "가" <= last <= "힣" and (ord(last) - ord("가")) % 28 != 0
    return batchim if has_batchim else no_batchim


class DoranAgent:
    """상태는 서버가 보관하고, 매 요청에 최신 맥락과 제안 이력을 전달한다."""

    def __init__(self, classifier: IntentClassifier | None = None):
        self.classifier = classifier

    def handle(self, payload: dict[str, Any]) -> dict[str, Any]:
        try:
            if not isinstance(payload, dict):
                raise ValueError("요청은 JSON 객체여야 합니다")
            now = _time(payload["now"])
            vehicle = payload.get("vehicle")
            if not isinstance(vehicle, dict) or vehicle.get("gear") not in {"P", "R", "N", "D"}:
                raise ValueError("vehicle.gear는 P, R, N, D 중 하나여야 합니다")
            kind = payload.get("kind")
            if kind == "vehicle_event":
                return self._event(payload, now)
            if kind == "user_utterance":
                return self._utterance(payload, now)
            raise ValueError("kind는 vehicle_event 또는 user_utterance여야 합니다")
        except (KeyError, TypeError, ValueError):
            return _response("invalid_input")

    def _event(self, data: dict[str, Any], now: datetime) -> dict[str, Any]:
        if not self._may_propose(data, now):
            return _response()
        event = data.get("event")
        vehicle = data["vehicle"]
        nav = _mapping(data.get("navigation"))
        trip = _mapping(data.get("trip"))
        places = _trusted(data.get("places"), "tour_api")
        rest_stops = _trusted(data.get("rest_stops"), "expressway_api")

        if event == "trip_started" and _nonnegative_number(nav.get("distance_from_home_km")) and nav["distance_from_home_km"] > 30:
            proposal = ("departure", "여행 시작할게. 어울리는 노래 찾아볼까?", None)
        elif event == "arrival_30m" and _nonnegative_number(nav.get("eta_minutes")) and nav["eta_minutes"] <= 30:
            meal = self._meal_time(nav.get("arrival_local_time"))
            if not meal:
                return _response()
            proposal = ("meal", f"도착할 때가 {meal} 시간이야. 근처 먹을 곳 찾아볼까?", None)
        elif event == "reboarded" and vehicle.get("gear") == "P" and vehicle.get("doors_closed") is True and _nonnegative_number(vehicle.get("last_stop_minutes")) and vehicle["last_stop_minutes"] > 15:
            candidate = next((p for p in places if p.get("along_route") is True), None)
            if not candidate:
                return _response()
            proposal = ("reboard", f"다음에 {candidate['name']} 들러 볼까?", candidate["id"])
        elif event == "continuous_drive" and _nonnegative_number(vehicle.get("continuous_driving_minutes")) and vehicle["continuous_driving_minutes"] >= 110:
            candidate = next((p for p in rest_stops if p.get("along_route") is True), None)
            if not candidate:
                return _response()
            proposal = ("rest", f"오래 운전했어. 다음 {candidate['name']}에서 쉬어 갈까?", candidate["id"])
        elif event == "return_started" and nav.get("destination_is_home") is True and _nonnegative_number(nav.get("minutes_since_departure")) and nav["minutes_since_departure"] >= 10:
            if len(_list(trip.get("stops"))) < 2 and not _list(trip.get("capsules")):
                return _response()
            proposal = ("replay", "이번 여행 다시 들을래?", None)
        else:
            return _response()

        if self._already_proposed(data, now, proposal[0]):
            return _response()
        pending = [{"type": "SET_PENDING_WAYPOINT", "place_id": proposal[2]}] if proposal[2] else []
        return _response("ok", proposal[1], proposal_type=proposal[0], selected_place_id=proposal[2], commands=pending)

    @staticmethod
    def _meal_time(value: Any) -> str | None:
        if not isinstance(value, str):
            return None
        try:
            hour, minute = map(int, value.split(":"))
            if not (0 <= hour <= 23 and 0 <= minute <= 59):
                return None
            total = hour * 60 + minute
            if 11 * 60 <= total <= 13 * 60 + 30:
                return "점심"
            if 17 * 60 <= total <= 19 * 60 + 30:
                return "저녁"
        except ValueError:
            pass
        return None

    @staticmethod
    def _may_propose(data: dict[str, Any], now: datetime) -> bool:
        preferences = _mapping(data.get("preferences"))
        return not preferences.get("quiet") and not (now.hour >= 21 or now.hour < 7)

    @staticmethod
    def _already_proposed(data: dict[str, Any], now: datetime, proposal_type: str) -> bool:
        in_hour = 0
        for item in _list(data.get("recent_proposals")):
            if not isinstance(item, dict):
                continue
            try:
                at = _time(item["at"]).astimezone(now.tzinfo)
            except (KeyError, TypeError, ValueError):
                continue
            if at > now:
                continue
            if at.date() == now.date() and item.get("type") == proposal_type:
                return True
            if timedelta(0) <= now - at < timedelta(hours=1):
                in_hour += 1
        return in_hour >= 2

    def _utterance(self, data: dict[str, Any], now: datetime) -> dict[str, Any]:
        utterance = data.get("utterance")
        text = utterance.strip()[:300] if isinstance(utterance, str) else ""
        if not text:
            return _response("invalid_input")
        driving = data["vehicle"]["gear"] in {"D", "R"}
        places = _trusted(data.get("places"), "tour_api")
        rest_stops = _trusted(data.get("rest_stops"), "expressway_api")
        tracks = _trusted(data.get("tracks"), "catalog")
        selected_place = _find(places + rest_stops, data.get("selected_place_id"))
        pending_place = _find(places + rest_stops, data.get("pending_waypoint_id"))
        selected_track = _find(tracks, data.get("selected_track_id"))

        if any(word in text for word in QUIET_OFF):
            return _response("ok", "알겠어. 여행 중에 다시 제안할게.", commands=[{"type": "SET_QUIET", "value": False}])
        if any(word in text for word in QUIET):
            return _response("ok", "알겠어. 먼저 말 걸지 않을게.", commands=[{"type": "SET_QUIET", "value": True}])
        confirmation = text.strip(" .!?。")
        if confirmation in NO:
            return _response("ok", "알겠어.", commands=[{"type": "CLEAR_PENDING"}])
        if pending_place and confirmation in YES:
            name = pending_place["name"]
            return _response("ok", f"{name}{_particle(name, '을', '를')} 경유지로 추가할게.", commands=[{"type": "ADD_WAYPOINT", "place_id": pending_place["id"]}, {"type": "CLEAR_PENDING"}])
        if selected_place and any(word in text for word in ("들르자", "들러 줘", "들러줘", "경유지로 추가", "여기로 가자", "여기 가자")):
            name = selected_place["name"]
            if driving:
                return _response("needs_confirmation", f"{name}{_particle(name, '을', '를')} 경유지로 추가할까?", commands=[{"type": "SET_PENDING_WAYPOINT", "place_id": selected_place["id"]}])
            return _response("ok", f"{name}{_particle(name, '을', '를')} 경유지로 추가할게.", commands=[{"type": "ADD_WAYPOINT", "place_id": selected_place["id"]}])
        if selected_place and any(word in text for word in ("저장해", "기억해")):
            return _response("ok", f"{selected_place['name']} 저장할게.", commands=[{"type": "SAVE_PLACE", "place_id": selected_place["id"]}])
        if selected_track and any(word in text for word in ("틀어", "재생해")):
            return _response("ok", f"{selected_track['name']} 재생할게.", commands=[{"type": "PLAY_MUSIC", "track_id": selected_track["id"]}])

        intent = self._intent(text)
        if intent == "arrival":
            eta = _mapping(data.get("navigation")).get("eta_minutes")
            if _nonnegative_number(eta):
                return _response("ok", f"약 {round(eta)}분 뒤 도착해.")
            return _response("unavailable", "내비에서 남은 시간을 받지 못했어.")
        if intent == "place":
            food = any(word in text for word in ("맛집", "밥", "점심", "저녁", "식당", "먹"))
            candidates = [p for p in places if p.get("along_route") is True and (not food or p.get("category") == "food")][:2]
            if not candidates:
                if data.get("network_available") is False:
                    return _response("unavailable", "연결되면 장소를 찾아볼게.")
                return _response("unavailable", "확인된 장소를 찾지 못했어.")
            names = ", ".join(p["name"] for p in candidates)
            cards = [{"place_id": p["id"], "name": p["name"]} for p in candidates] if not driving else []
            return _response("ok", f"경로에서 {names}{_particle(names, '을', '를')} 찾았어. 들를까?", selected_place_id=candidates[0]["id"], cards=cards, commands=[{"type": "SET_PENDING_WAYPOINT", "place_id": candidates[0]["id"]}])
        if intent == "rest":
            candidate = next((p for p in rest_stops if p.get("along_route") is True), None)
            if not candidate:
                return _response("unavailable", "경로의 휴게소 정보를 받지 못했어.")
            minutes = candidate.get("eta_minutes")
            name = candidate["name"]
            speech = f"다음은 {name}{_particle(name, '이야', '야')}."
            if _nonnegative_number(minutes):
                speech = f"다음 {candidate['name']}까지 약 {round(minutes)}분이야."
            return _response("ok", speech, selected_place_id=candidate["id"])
        if intent == "music":
            if not tracks:
                return _response("unavailable", "추천할 곡 목록을 받지 못했어.")
            track = self._choose_track(tracks, data, now)
            cards = [{"track_id": track["id"], "name": track["name"]}] if not driving else []
            return _response("ok", f"지금은 {track['name']} 어때?", selected_track_id=track["id"], cards=cards)
        if intent == "memory":
            mentions = _list(_mapping(data.get("trip")).get("mentions"))
            valid_mentions = [m for m in mentions if isinstance(m, dict) and isinstance(m.get("name"), str) and m["name"]]
            matching = [m for m in valid_mentions if m["name"] in text or ("카페" in text and m.get("category") == "cafe")]
            if not matching and "아까" in text and "어디" in text:
                matching = valid_mentions
            if matching:
                name = matching[-1]["name"]
                return _response("ok", f"아까 말한 곳은 {name}{_particle(name, '이야', '야')}.")
            return _response("unavailable", "오늘 기록에서 그 장소를 찾지 못했어.")
        if intent == "replay":
            trip = _mapping(data.get("trip"))
            visited = _list(trip.get("stops"))
            capsules = _list(trip.get("capsules"))
            if not visited and not capsules:
                return _response("unavailable", "아직 다시 들을 여행 기록이 없어.")
            speech = f"이번 여행에서 {len(visited)}곳에 들렀어."
            commands = []
            if capsules and isinstance(capsules[0], dict) and capsules[0].get("id"):
                speech += " 첫 번째 목소리를 들려줄게."
                commands = [{"type": "PLAY_CAPSULE", "capsule_id": capsules[0]["id"]}]
            return _response("ok", speech, commands=commands)
        return _response("unavailable", "여행지, 도착 시간, 휴게소, 음악 중에서 물어봐 줘.")

    @staticmethod
    def _choose_track(tracks: list[dict[str, Any]], data: dict[str, Any], now: datetime) -> dict[str, Any]:
        vehicle = data["vehicle"]
        nav = _mapping(data.get("navigation"))
        if nav.get("destination_is_home") is True or now.hour >= 20:
            target = "calm"
        elif _nonnegative_number(vehicle.get("continuous_driving_minutes")) and vehicle["continuous_driving_minutes"] >= 90:
            target = "refresh"
        elif 7 <= now.hour < 11:
            target = "bright"
        else:
            target = "travel"
        return next((track for track in tracks if target in _list(track.get("tags"))), tracks[0])

    def _intent(self, text: str) -> str:
        rules = (
            ("replay", ("다시 듣", "다시 들려", "여행 들려", "여행 요약")),
            ("memory", ("아까", "전에 말한", "기억나")),
            ("rest", ("휴게소", "화장실", "쉬어")),
            ("music", ("노래", "음악", "플레이리스트")),
            ("arrival", ("몇 시", "언제 도착", "얼마나 남", "도착 시간")),
            ("place", ("어디", "근처", "맛집", "명소", "축제", "가볼")),
        )
        for intent, words in rules:
            if any(word in text for word in words):
                return intent
        if self.classifier:
            try:
                result = self.classifier.classify(text)
                return result if result in INTENTS else "unknown"
            except Exception:
                pass
        return "unknown"
