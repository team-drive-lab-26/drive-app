import unittest

from doran_ai import DoranAgent

NOW = "2026-11-07T12:00:00+09:00"


def request(kind="user_utterance", **changes):
    data = {
        "kind": kind,
        "now": NOW,
        "vehicle": {"gear": "D"},
        "utterance": "근처 맛집 추천해 줘",
        "places": [
            {"id": "p1", "name": "바닷가 식당", "source": "tour_api", "category": "food", "along_route": True},
            {"id": "p2", "name": "광장", "source": "tour_api", "category": "attraction", "along_route": True},
        ],
    }
    data.update(changes)
    return data


class AgentTests(unittest.TestCase):
    def setUp(self):
        self.agent = DoranAgent()

    def test_driving_place_reply_uses_trusted_catalog_and_no_cards(self):
        data = request(places=request()["places"] + [{"id": "x", "name": "광고", "source": "unverified"}])
        result = self.agent.handle(data)
        self.assertEqual(result["status"], "ok")
        self.assertEqual(result["selected_place_id"], "p1")
        self.assertEqual(result["cards"], [])
        self.assertEqual(result["commands"], [{"type": "SET_PENDING_WAYPOINT", "place_id": "p1"}])
        self.assertNotIn("광고", result["speech"])

    def test_driving_waypoint_requires_explicit_confirmation(self):
        first = self.agent.handle(request(utterance="여기 들르자", selected_place_id="p1"))
        self.assertEqual(first["status"], "needs_confirmation")
        self.assertEqual(first["commands"], [{"type": "SET_PENDING_WAYPOINT", "place_id": "p1"}])
        unrelated = self.agent.handle(request(utterance="그래서 근처는 어때?", pending_waypoint_id="p1"))
        self.assertFalse(any(c["type"] == "ADD_WAYPOINT" for c in unrelated["commands"]))
        confirmed = self.agent.handle(request(utterance="응", pending_waypoint_id="p1"))
        self.assertEqual(confirmed["commands"][0], {"type": "ADD_WAYPOINT", "place_id": "p1"})
        self.assertIn("식당을", confirmed["speech"])

    def test_unknown_waypoint_id_cannot_be_executed(self):
        result = self.agent.handle(request(utterance="응", pending_waypoint_id="untrusted"))
        self.assertEqual(result["commands"], [])

    def test_parked_question_does_not_add_waypoint(self):
        result = self.agent.handle(request(utterance="이거 경유지야?", selected_place_id="p1", vehicle={"gear": "P"}))
        self.assertEqual(result["commands"], [])

    def test_proactive_rest_respects_quiet_time_and_rate_limit(self):
        base = request("vehicle_event", event="continuous_drive", vehicle={"gear": "D", "continuous_driving_minutes": 120}, rest_stops=[{"id": "r1", "name": "서해안 휴게소", "source": "expressway_api", "along_route": True}])
        normal = self.agent.handle(base)
        self.assertEqual(normal["proposal_type"], "rest")
        self.assertEqual(normal["selected_place_id"], "r1")
        self.assertEqual(normal["commands"], [{"type": "SET_PENDING_WAYPOINT", "place_id": "r1"}])
        self.assertEqual(self.agent.handle({**base, "now": "2026-11-07T21:00:00+09:00"})["status"], "no_action")
        self.assertEqual(self.agent.handle({**base, "preferences": {"quiet": True}})["status"], "no_action")
        self.assertEqual(self.agent.handle({**base, "recent_proposals": [{"type": "rest", "at": NOW}]})["status"], "no_action")
        history = [{"type": "meal", "at": "2026-11-07T11:30:00+09:00"}, {"type": "departure", "at": "2026-11-07T11:45:00+09:00"}]
        self.assertEqual(self.agent.handle({**base, "recent_proposals": history})["status"], "no_action")

    def test_future_proposal_timestamp_does_not_block_current_offer(self):
        data = request("vehicle_event", event="trip_started", navigation={"distance_from_home_km": 120}, recent_proposals=[{"type": "departure", "at": "2026-11-07T12:30:00+09:00"}])
        self.assertEqual(self.agent.handle(data)["proposal_type"], "departure")

    def test_arrival_event_only_at_meal_time(self):
        base = request("vehicle_event", event="arrival_30m", navigation={"eta_minutes": 25, "arrival_local_time": "12:25"})
        self.assertEqual(self.agent.handle(base)["proposal_type"], "meal")
        self.assertEqual(self.agent.handle({**base, "navigation": {"eta_minutes": 25, "arrival_local_time": "15:00"}})["status"], "no_action")

    def test_music_uses_context_tag_and_never_autoplays(self):
        tracks = [
            {"id": "a", "name": "출발", "source": "catalog", "tags": ["travel"]},
            {"id": "b", "name": "차분한 길", "source": "catalog", "tags": ["calm"]},
        ]
        result = self.agent.handle(request(utterance="노래 추천해 줘", tracks=tracks, navigation={"destination_is_home": True}))
        self.assertEqual(result["selected_track_id"], "b")
        self.assertEqual(result["commands"], [])
        play = self.agent.handle(request(utterance="틀어 줘", tracks=tracks, selected_track_id="b"))
        self.assertEqual(play["commands"], [{"type": "PLAY_MUSIC", "track_id": "b"}])

    def test_memory_and_replay_use_trip_records(self):
        trip = {"mentions": [{"name": "별빛 카페", "category": "cafe"}], "stops": [{"id": "s1"}], "capsules": [{"id": "c1"}]}
        self.assertIn("별빛 카페", self.agent.handle(request(utterance="아까 그 카페 이름 뭐야?", trip=trip))["speech"])
        replay = self.agent.handle(request(utterance="여행 다시 들려줘", trip=trip))
        self.assertEqual(replay["commands"], [{"type": "PLAY_CAPSULE", "capsule_id": "c1"}])

    def test_invalid_input_and_no_network_fallback(self):
        invalid = self.agent.handle(request(now="2026-11-07T12:00:00"))
        self.assertEqual(invalid["status"], "invalid_input")
        self.assertEqual(invalid["speech"], "")
        self.assertEqual(self.agent.handle(request(now=7))["status"], "invalid_input")
        result = self.agent.handle(request(network_available=False, places=[]))
        self.assertEqual(result["status"], "unavailable")
        self.assertEqual(result["commands"], [])

    def test_cached_place_still_works_offline(self):
        result = self.agent.handle(request(network_available=False))
        self.assertEqual(result["status"], "ok")
        self.assertEqual(result["selected_place_id"], "p1")

    def test_place_without_route_verification_is_not_recommended(self):
        place = {"id": "p1", "name": "바닷가 식당", "source": "tour_api", "category": "food"}
        result = self.agent.handle(request(places=[place]))
        self.assertEqual(result["status"], "unavailable")

    def test_writer_changes_only_speech_and_caption(self):
        class Writer:
            def rewrite(self, draft, utterance, protected_terms):
                self.args = (draft, utterance, protected_terms)
                return "바닷가 식당에 들러 볼까?"

        writer = Writer()
        raw = self.agent.handle(request())
        result = DoranAgent(writer=writer).handle(request())
        self.assertEqual(result["speech"], "바닷가 식당에 들러 볼까?")
        self.assertEqual(result["caption"], result["speech"])
        self.assertEqual({k: v for k, v in result.items() if k not in {"speech", "caption"}},
                         {k: v for k, v in raw.items() if k not in {"speech", "caption"}})
        self.assertEqual(writer.args[2], ["바닷가 식당"])

    def test_writer_skips_executed_commands_and_offline_requests(self):
        class Writer:
            def rewrite(self, *args):
                raise AssertionError("이 경로에서 모델을 호출하면 안 됩니다")

        agent = DoranAgent(writer=Writer())
        self.assertIn("경유지로", agent.handle(request(utterance="응", pending_waypoint_id="p1"))["speech"])
        self.assertEqual(
            agent.handle(request(network_available=False)),
            self.agent.handle(request(network_available=False)),
        )

    def test_writer_failure_keeps_rule_based_speech(self):
        class Writer:
            def rewrite(self, *args):
                raise TimeoutError()

        self.assertEqual(DoranAgent(writer=Writer()).handle(request()), self.agent.handle(request()))

    def test_quiet_mode_can_be_turned_back_on(self):
        quiet = self.agent.handle(request(utterance="조용히 해"))
        resume = self.agent.handle(request(utterance="다시 말 걸어"))
        self.assertEqual(quiet["commands"], [{"type": "SET_QUIET", "value": True}])
        self.assertEqual(resume["commands"], [{"type": "SET_QUIET", "value": False}])


if __name__ == "__main__":
    unittest.main()
