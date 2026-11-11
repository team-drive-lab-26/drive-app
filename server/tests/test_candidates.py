from doran_ai.knowledge import KnowledgeBase, Place, Point, RestStop, Route

from server.candidates import CandidateRanker

# 동쪽으로 곧게 뻗은 약 88km 경로. 출발(127.0) → 도착(128.0)
ROUTE = Route(
    name="t", approximate=False, points=[Point(latitude=37.0, longitude=127.0), Point(latitude=37.0, longitude=128.0)]
)


def place(id_, name, category, lon, lat=37.005, **kw):
    return Place(id=id_, name=name, category=category, latitude=lat, longitude=lon, along_route=True, **kw)


def rest(id_, lon):
    return RestStop(
        id=id_, name=id_, highway="영동선", direction="하행", latitude=37.0, longitude=lon, along_route=True
    )


PLACES = [
    place("start-food", "출발 식당", "food", 127.01),
    place("tofu", "초당 순두부", "food", 127.98, menu=["순두부백반"]),
    place("tofu-mention", "바다 식당", "food", 127.99, overview="근처 순두부 마을"),
    place("cafe", "바다 카페", "cafe", 127.97),
    place("old-event", "출발지 축제", "event", 127.02),
]
RESTS = [rest("r-early", 127.2), rest("r-mid", 127.5), rest("r-late", 127.8)]


def ranker():
    kb = KnowledgeBase(PLACES, RESTS, [], ROUTE)
    catalog = kb.agent_catalog()
    return CandidateRanker(kb, catalog["places"], catalog["rest_stops"]), catalog


def ids(items):
    return [item["id"] for item in items]


def utterance(text, **kw):
    return {"kind": "user_utterance", "utterance": text, **kw}


def test_menu_match_comes_before_overview_match_and_distance():
    r, catalog = ranker()
    ranked = r.places(utterance("순두부 먹자"), catalog["places"])
    assert ids(ranked)[:2] == ["tofu", "tofu-mention"]


def test_category_words_and_position_order():
    r, catalog = ranker()
    near_start = {"latitude": 37.0, "longitude": 127.0}
    assert ids(r.places(utterance("근처 카페 어디야?", position=near_start), catalog["places"]))[0] == "cafe"
    assert ids(r.places(utterance("근처 어디가 좋아?", position=near_start), catalog["places"]))[0] == "start-food"


def test_without_position_defaults_to_destination_area():
    r, catalog = ranker()
    assert ids(r.places(utterance("근처 어디가 좋아?"), catalog["places"]))[0] == "tofu-mention"


def test_places_already_passed_go_last_and_event_question_skips_them():
    r, catalog = ranker()
    late = {"navigation": {"minutes_since_departure": 90, "eta_minutes": 10}}
    ranked = r.places(utterance("근처 맛집 있어?", **late), catalog["places"])
    assert ids(ranked)[-1] in {"start-food", "old-event"}
    assert r.places(utterance("축제 해?", **late), catalog["places"]) == []


def test_rest_stops_ahead_with_minutes():
    r, catalog = ranker()
    # 진행 45%(약 40km) 지점. 남은 약 49km를 44분에 가는 속도로 계산
    ranked = r.rest_stops({"navigation": {"minutes_since_departure": 36, "eta_minutes": 44}}, catalog["rest_stops"])
    assert ids(ranked) == ["r-mid", "r-late", "r-early"]
    assert ranked[0]["eta_minutes"] == 4 and ranked[1]["eta_minutes"] == 28
    assert "eta_minutes" not in ranked[-1]


def test_ahead_lists_next_rest_stops_with_remaining_km():
    r, catalog = ranker()
    # 88km 경로에서 30분 달렸고 60분 남았으면 약 29km 지점. 첫 휴게소(약 17km)는 지났다
    request = {"navigation": {"minutes_since_departure": 30, "eta_minutes": 60}}
    stops = r.ahead(request, catalog["rest_stops"])
    assert [s["id"] for s in stops] == ["r-mid", "r-late"]
    assert 14 < stops[0]["km"] < 16
    assert stops[0]["km"] < stops[1]["km"]


def test_ahead_is_empty_without_position_or_time():
    r, catalog = ranker()
    assert r.ahead({}, catalog["rest_stops"]) == []
