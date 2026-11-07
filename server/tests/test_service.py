import json
import sys
import unittest
from pathlib import Path
from urllib import request
from threading import Thread
from http.server import ThreadingHTTPServer

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from main import AgentService, load_catalog, make_handler


NOW = "2026-11-07T12:00:00+09:00"


class ServiceTests(unittest.TestCase):
    def setUp(self):
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


if __name__ == "__main__":
    unittest.main()
