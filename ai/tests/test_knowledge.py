from datetime import date

from doran_ai.knowledge import KnowledgeBase, Place, Point, RestStop, Route
from doran_ai.knowledge.geo import (
    distance_to_route_km,
    haversine_km,
    route_progress_km,
    sample_route,
    simplify_route,
)
from doran_ai.knowledge.store import write_json

ROUTE = Route(
    name="test",
    approximate=True,
    points=[Point(latitude=37.0, longitude=127.0), Point(latitude=37.0, longitude=128.0)],
)


def place(id_, name, category="attraction", overview="", lat=37.01, along=True, **kw):
    return Place(
        id=id_,
        name=name,
        category=category,
        overview=overview,
        latitude=lat,
        longitude=127.5,
        along_route=along,
        route_distance_km=1.0,
        **kw,
    )


def test_haversine_one_degree_latitude():
    d = haversine_km(Point(latitude=37, longitude=127), Point(latitude=38, longitude=127))
    assert 110 < d < 112


def test_distance_to_route_is_perpendicular():
    d = distance_to_route_km(Point(latitude=37.05, longitude=127.5), ROUTE.points)
    assert 5.4 < d < 5.7


def test_sample_route_includes_both_ends():
    samples = sample_route(ROUTE.points, 15)
    assert samples[0] == ROUTE.points[0]
    assert samples[-1] == ROUTE.points[-1]
    assert len(samples) >= 6


def test_sample_route_spacing_ignores_short_segments():
    dense = [Point(latitude=37.0, longitude=127.0 + i * 0.001) for i in range(1001)]  # 약 89km, 0.09km 간격
    samples = sample_route(dense, 15)
    assert 6 <= len(samples) <= 8
    gaps = [haversine_km(a, b) for a, b in zip(samples, samples[1:-1], strict=False)]
    assert all(14.5 < g < 15.5 for g in gaps)


def test_route_progress_and_simplify():
    progress, offset = route_progress_km(Point(latitude=37.01, longitude=127.5), ROUTE.points)
    assert 44 < progress < 45 and 1.0 < offset < 1.2
    dense = [Point(latitude=37.0, longitude=127.0 + i * 0.001) for i in range(1001)]
    simple = simplify_route(dense, 1.0)
    assert simple[0] == dense[0] and simple[-1] == dense[-1] and 80 < len(simple) <= 90


def test_find_places_filters_category_and_route():
    kb = KnowledgeBase(
        [place("a", "순두부집", "food"), place("b", "해변"), place("c", "먼 식당", "food", along=False)], [], [], ROUTE
    )
    assert [p.id for p in kb.find_places(categories={"food"})] == ["a"]


def test_find_places_ranks_by_text():
    kb = KnowledgeBase(
        [place("a", "경포해변", overview="바다 산책"), place("b", "오죽헌", overview="실내 전시관과 박물관")],
        [],
        [],
        ROUTE,
    )
    assert kb.find_places("비 오면 실내 박물관")[0].id == "b"


def test_event_filtered_by_date():
    festival = place("e", "축제", "event", event_start=date(2026, 11, 1), event_end=date(2026, 11, 8))
    kb = KnowledgeBase([festival], [], [], ROUTE)
    assert kb.find_places(categories={"event"}, on=date(2026, 11, 7))
    assert not kb.find_places(categories={"event"}, on=date(2026, 11, 20))


def test_rest_stop_search_by_menu():
    stop = RestStop(
        id="r1",
        name="횡성휴게소",
        highway="영동선",
        direction="강릉",
        menu=["한우국밥"],
        latitude=37.0,
        longitude=127.5,
        along_route=True,
    )
    kb = KnowledgeBase([], [stop], [], ROUTE)
    assert kb.find_rest_stops("국밥")[0].id == "r1"


def test_load_round_trip_and_agent_catalog(tmp_path):
    write_json(tmp_path / "places.json", [place("a", "해변")])
    write_json(tmp_path / "route.json", ROUTE)
    kb = KnowledgeBase.load(tmp_path)
    catalog = kb.agent_catalog()
    assert catalog["places"][0]["source"] == "tour_api"
    assert catalog["places"][0]["along_route"] is True
    assert catalog["rest_stops"] == [] and catalog["tracks"] == []
