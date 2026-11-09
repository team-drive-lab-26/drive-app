import json
import sys
import unittest
from pathlib import Path
from urllib import request
from threading import Thread
from http.server import ThreadingHTTPServer
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from main import AgentService, load_catalog, make_handler


NOW = "2026-11-07T12:00:00+09:00"


class ServiceTests(unittest.TestCase):
    def setUp(self):
        llm_patcher = patch("main.ChatCompletionsClassifier.from_env", return_value=None)
        llm_patcher.start()
        self.addCleanup(llm_patcher.stop)
        self.places = [{"id": "p1", "name": "바닷가 식당", "source": "tour_api", "category": "food", "along_route": True, "latitude": 37.41275833, "longitude": 127.09481944, "navi_poi_id": "navi-1"}]
        self.service = AgentService(places=self.places, rest_stops=[], tracks=[])

    def test_confirmation_keeps_session_state_and_emits_valid_waypoint(self):
        base = {"session_id": "trip-1", "now": NOW, "kind": "user_utterance", "vehicle": {"gear": "D"}}
        proposal = self.service.respond({**base, "utterance": "근처 맛집 추천해 줘"})
        self.assertEqual(proposal["selected_place_id"], "p1")
        self.assertEqual(proposal["effects"], [])
        confirm = self.service.respond({**base, "now": "2026-11-07T12:01:00+09:00", "utterance": "응"})
        self.assertEqual(confirm["effects"][0]["type"], "ADD_WAYPOINT")
        self.assertEqual(confirm["effects"][0]["place"]["latitude"], 37.41275833)
        self.assertEqual(confirm["effects"][0]["place"]["navi_poi_id"], "navi-1")
        self.assertIsNone(self.service.sessions["trip-1"].pending_waypoint_id)

    def test_pending_expires(self):
        base = {"session_id": "trip-2", "now": NOW, "kind": "user_utterance", "vehicle": {"gear": "D"}}
        self.service.respond({**base, "utterance": "근처 맛집 추천해 줘"})
        late = self.service.respond({**base, "now": "2026-11-07T12:03:00+09:00", "utterance": "응"})
        self.assertEqual(late["effects"], [])

    def test_new_topic_clears_pending_waypoint(self):
        base = {"session_id": "trip-topic", "now": NOW, "kind": "user_utterance", "vehicle": {"gear": "D"}}
        self.service.respond({**base, "utterance": "근처 맛집 추천해 줘"})
        self.service.respond({**base, "utterance": "언제 도착해?"})
        self.assertIsNone(self.service.sessions["trip-topic"].pending_waypoint_id)
        later = self.service.respond({**base, "utterance": "응"})
        self.assertEqual(later["effects"], [])

    def test_old_selected_place_expires(self):
        base = {"session_id": "trip-old", "now": NOW, "kind": "user_utterance", "vehicle": {"gear": "P"}}
        self.service.respond({**base, "utterance": "근처 맛집 추천해 줘"})
        later = self.service.respond({**base, "now": "2026-11-07T12:03:00+09:00", "utterance": "여기 가자"})
        self.assertEqual(later["effects"], [])

    def test_expired_or_unoffered_place_cannot_be_selected_by_request(self):
        self.places.append({"id": "p2", "name": "광장", "source": "tour_api", "category": "attraction", "along_route": True, "latitude": 37.5, "longitude": 127.1, "navi_poi_id": "navi-2"})
        base = {"session_id": "trip-selection", "now": NOW, "kind": "user_utterance", "vehicle": {"gear": "P"}}
        self.service.respond({**base, "utterance": "근처 맛집 추천해 줘"})
        forged = self.service.respond({**base, "utterance": "여기 가자", "selected_place_id": "p2"})
        self.assertEqual(forged["effects"], [])
        another_session = self.service.respond({**base, "session_id": "other-session", "utterance": "여기 가자", "selected_place_id": "p1"})
        self.assertEqual(another_session["effects"], [])
        expired = self.service.respond({**base, "now": "2026-11-07T12:03:00+09:00", "utterance": "여기 가자", "selected_place_id": "p1"})
        self.assertEqual(expired["effects"], [])

    def test_parked_user_can_choose_second_offered_card(self):
        self.places.append({"id": "p2", "name": "해변 식당", "source": "tour_api", "category": "food", "along_route": True, "latitude": 37.5, "longitude": 127.1, "navi_poi_id": "navi-2"})
        base = {"session_id": "trip-second-card", "now": NOW, "kind": "user_utterance", "vehicle": {"gear": "P"}}
        proposal = self.service.respond({**base, "utterance": "근처 맛집 추천해 줘"})
        self.assertEqual([card["place_id"] for card in proposal["cards"]], ["p1", "p2"])
        chosen = self.service.respond({**base, "now": "2026-11-07T12:01:00+09:00", "utterance": "여기 가자", "selected_place_id": "p2"})
        self.assertEqual(chosen["effects"][0]["place"]["id"], "p2")

    def test_track_selection_cannot_be_injected_or_reused_after_expiry(self):
        self.service.tracks = [
            {"id": "t1", "name": "여행길", "source": "catalog", "tags": ["travel"]},
            {"id": "t2", "name": "다른 곡", "source": "catalog", "tags": ["calm"]},
        ]
        base = {"session_id": "trip-track", "now": NOW, "kind": "user_utterance", "vehicle": {"gear": "P"}}
        self.service.respond({**base, "utterance": "노래 추천해 줘"})
        injected = self.service.respond({**base, "utterance": "틀어 줘", "selected_track_id": "t2"})
        self.assertEqual(injected["effects"], [])
        expired = self.service.respond({**base, "now": "2026-11-07T12:06:00+09:00", "utterance": "틀어 줘", "selected_track_id": "t1"})
        self.assertEqual(expired["effects"], [])

    def test_invalid_agent_input_has_no_speech_or_effect(self):
        response = self.service.respond({"session_id": "trip-invalid", "now": NOW, "kind": "user_utterance", "vehicle": {"gear": "D"}, "utterance": "  "})
        self.assertEqual(response["status"], "invalid_input")
        self.assertEqual(response["speech"], "")
        self.assertEqual(response["effects"], [])

    def test_server_uses_configured_llm_for_unmatched_utterance(self):
        class PlaceClassifier:
            def classify(self, utterance):
                return "place"

        with patch("main.ChatCompletionsClassifier.from_env", return_value=PlaceClassifier()):
            service = AgentService(places=self.places, rest_stops=[], tracks=[])
        result = service.respond({
            "session_id": "trip-llm", "now": NOW, "kind": "user_utterance",
            "vehicle": {"gear": "P"}, "utterance": "이 근방에 들를 곳을 추천해 줄래?",
        })
        self.assertEqual(result["status"], "ok")
        self.assertEqual(result["selected_place_id"], "p1")

    def test_unverified_coordinates_do_not_emit_navigation(self):
        self.places[0].pop("latitude")
        base = {"session_id": "trip-3", "now": NOW, "kind": "user_utterance", "vehicle": {"gear": "D"}}
        self.service.respond({**base, "utterance": "근처 맛집 추천해 줘"})
        result = self.service.respond({**base, "utterance": "응"})
        self.assertEqual(result["status"], "unavailable")
        self.assertEqual(result["effects"], [])

    def test_missing_navi_poi_id_does_not_emit_navigation(self):
        self.places[0].pop("navi_poi_id")
        base = {"session_id": "trip-5", "now": NOW, "kind": "user_utterance", "vehicle": {"gear": "D"}}
        self.service.respond({**base, "utterance": "근처 맛집 추천해 줘"})
        result = self.service.respond({**base, "utterance": "응"})
        self.assertEqual(result["status"], "unavailable")
        self.assertEqual(result["effects"], [])

    def test_trip_record_is_retrieved(self):
        self.service.record({"session_id": "trip-4", "type": "mention", "id": "p1", "name": "별빛 카페", "category": "cafe"})
        result = self.service.respond({"session_id": "trip-4", "now": NOW, "kind": "user_utterance", "vehicle": {"gear": "P"}, "utterance": "아까 그 카페 이름 뭐야?"})
        self.assertIn("별빛 카페", result["speech"])

    def test_http_contract(self):
        server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(self.service))
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            url = f"http://127.0.0.1:{server.server_port}/v1/agent/respond"
            payload = {"session_id": "http-1", "now": NOW, "kind": "user_utterance", "vehicle": {"gear": "P"}, "utterance": "근처 맛집 추천해 줘"}
            req = request.Request(url, data=json.dumps(payload).encode(), headers={"Content-Type": "application/json"})
            with request.urlopen(req) as response:
                data = json.load(response)
            self.assertEqual(data["selected_place_id"], "p1")
            self.assertIn("effects", data)
        finally:
            server.shutdown()
            server.server_close()

    def test_catalog_file_rejects_unverified_coordinates(self):
        import tempfile

        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", suffix=".json", delete=False) as file:
            json.dump({"places": [{"id": "x", "name": "장소", "source": "tour_api"}], "rest_stops": [], "tracks": []}, file)
            path = file.name
        try:
            with self.assertRaises(ValueError):
                load_catalog(path)
        finally:
            Path(path).unlink()

    def test_full_trip_keeps_effects_explicit_and_handles_missing_data(self):
        rest = {"id": "r1", "name": "서해안 휴게소", "source": "expressway_api", "along_route": True, "latitude": 37.6, "longitude": 127.2, "navi_poi_id": "rest-poi"}
        track = {"id": "t1", "name": "아침 여행", "source": "catalog", "tags": ["bright"]}
        service = AgentService(places=self.places, rest_stops=[rest], tracks=[track])
        base = {"session_id": "trip-flow", "kind": "user_utterance", "vehicle": {"gear": "D"}}

        departure = service.respond({**base, "kind": "vehicle_event", "now": "2026-11-07T09:00:00+09:00", "event": "trip_started", "navigation": {"distance_from_home_km": 120}})
        self.assertEqual(departure["proposal_type"], "departure")
        duplicate = service.respond({**base, "kind": "vehicle_event", "now": "2026-11-07T09:00:30+09:00", "event": "trip_started", "navigation": {"distance_from_home_km": 120}})
        self.assertEqual(duplicate["status"], "no_action")

        music = service.respond({**base, "now": "2026-11-07T09:01:00+09:00", "utterance": "노래 추천해 줘"})
        self.assertEqual(music["selected_track_id"], "t1")
        self.assertEqual(music["effects"], [])
        playback = service.respond({**base, "now": "2026-11-07T09:02:00+09:00", "utterance": "틀어 줘"})
        self.assertEqual(playback["effects"], [{"type": "PLAY_MUSIC", "track_id": "t1"}])

        rest_offer = service.respond({**base, "kind": "vehicle_event", "now": "2026-11-07T11:10:00+09:00", "event": "continuous_drive", "vehicle": {"gear": "D", "continuous_driving_minutes": 130}})
        self.assertEqual(rest_offer["selected_place_id"], "r1")
        self.assertEqual(rest_offer["effects"], [])
        confirmed = service.respond({**base, "now": "2026-11-07T11:11:00+09:00", "utterance": "응"})
        self.assertEqual(confirmed["effects"][0]["place"]["navi_poi_id"], "rest-poi")

        service.record({"session_id": "trip-flow", "type": "mention", "id": "p1", "name": "별빛 카페", "category": "cafe"})
        service.record({"session_id": "trip-flow", "type": "stop", "id": "p1"})
        service.record({"session_id": "trip-flow", "type": "capsule", "id": "c1"})
        memory = service.respond({**base, "now": "2026-11-07T15:00:00+09:00", "utterance": "아까 그 카페 이름 뭐야?"})
        self.assertIn("별빛 카페", memory["speech"])
        service.respond({**base, "kind": "vehicle_event", "now": "2026-11-07T17:15:00+09:00", "event": "return_started", "navigation": {"destination_is_home": True, "minutes_since_departure": 15}})
        replay = service.respond({**base, "now": "2026-11-07T17:16:00+09:00", "utterance": "여행 다시 들려줘"})
        self.assertEqual(replay["effects"], [{"type": "PLAY_CAPSULE", "capsule_id": "c1"}])

        offline = AgentService(places=[], rest_stops=[], tracks=[]).respond({**base, "session_id": "offline", "now": NOW, "network_available": False, "utterance": "근처 맛집 추천해 줘"})
        self.assertEqual(offline["status"], "unavailable")
        self.assertEqual(offline["effects"], [])


if __name__ == "__main__":
    unittest.main()
