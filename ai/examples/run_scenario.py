"""서버가 상태를 보관하는 과정을 흉내 내는 로컬 시연."""

import json

from doran_ai import DoranAgent


PLACES = [
    {"id": "cafe-1", "name": "별빛 카페", "source": "tour_api", "category": "cafe", "along_route": True},
]
REST_STOPS = [
    {"id": "rest-1", "name": "서해안 휴게소", "source": "expressway_api", "along_route": True},
]
TRACKS = [
    {"id": "track-1", "name": "아침 여행", "source": "catalog", "tags": ["bright"]},
]


def main() -> None:
    agent = DoranAgent()
    state = {"pending_waypoint_id": None, "selected_track_id": None, "recent_proposals": []}
    steps = [
        {"kind": "vehicle_event", "now": "2026-11-07T09:00:00+09:00", "event": "trip_started", "navigation": {"distance_from_home_km": 120}},
        {"kind": "user_utterance", "now": "2026-11-07T09:01:00+09:00", "utterance": "노래 추천해 줘"},
        {"kind": "user_utterance", "now": "2026-11-07T09:02:00+09:00", "utterance": "틀어 줘"},
        {"kind": "vehicle_event", "now": "2026-11-07T11:10:00+09:00", "event": "continuous_drive", "vehicle": {"gear": "D", "continuous_driving_minutes": 130}},
        {"kind": "user_utterance", "now": "2026-11-07T11:11:00+09:00", "utterance": "응"},
        {"kind": "user_utterance", "now": "2026-11-07T15:00:00+09:00", "utterance": "아까 그 카페 이름 뭐야?", "trip": {"mentions": [{"name": "별빛 카페", "category": "cafe"}]}},
        {"kind": "vehicle_event", "now": "2026-11-07T17:15:00+09:00", "event": "return_started", "navigation": {"destination_is_home": True, "minutes_since_departure": 15}, "trip": {"stops": [{"id": "cafe-1"}], "capsules": [{"id": "capsule-1"}]}},
        {"kind": "user_utterance", "now": "2026-11-07T17:16:00+09:00", "utterance": "여행 다시 들려줘", "trip": {"stops": [{"id": "cafe-1"}], "capsules": [{"id": "capsule-1"}]}},
    ]
    for step in steps:
        payload = {
            "vehicle": {"gear": "D"},
            "places": PLACES,
            "rest_stops": REST_STOPS,
            "tracks": TRACKS,
            **state,
            **step,
        }
        result = agent.handle(payload)
        if result["proposal_type"]:
            state["recent_proposals"].append({"type": result["proposal_type"], "at": step["now"]})
        if result["selected_track_id"]:
            state["selected_track_id"] = result["selected_track_id"]
        for command in result["commands"]:
            if command["type"] == "SET_PENDING_WAYPOINT":
                state["pending_waypoint_id"] = command["place_id"]
            elif command["type"] == "CLEAR_PENDING":
                state["pending_waypoint_id"] = None
        print(json.dumps({"input": step.get("event") or step.get("utterance"), "speech": result["speech"], "commands": result["commands"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
