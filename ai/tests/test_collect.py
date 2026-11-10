from datetime import date

import pytest
from doran_ai.collect.build import collect_places, mark_rest_stops
from doran_ai.collect.restarea import to_rest_stop
from doran_ai.collect.tourapi import CONTENT_TYPE_OF, TourApiClient, TourApiError, _items
from doran_ai.knowledge import Place, Point, Route

ITEM = {
    "entrpsNm": "강릉(강릉)",
    "roadRouteNo": "50",
    "roadRouteNm": "영동선",
    "roadRouteDrc": "하행",
    "latitude": "37.758206",
    "longitude": "128.806648",
    "oltYn": "Y",
    "elctyYn": "Y",
    "toiletYn": "Y",
    "parmacyYn": "N",
    "etcCvntl": "",
    "rprsntvRstrt": "감자옹심이, 커피",
}


def test_items_accepts_both_wrappers():
    body = {"body": {"items": {"item": [{"a": 1}]}}}
    assert _items({"response": {"header": {"resultCode": "0000"}, **body}}) == [{"a": 1}]
    assert _items({"header": {"resultCode": "00"}, **body}) == [{"a": 1}]
    with pytest.raises(TourApiError):
        _items({"header": {"resultCode": "30", "resultMsg": "SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}})


def test_rest_area_item_maps_to_rest_stop():
    stop = to_rest_stop(ITEM)
    assert stop.name == "강릉(강릉) 휴게소"
    assert stop.highway == "영동선" and stop.direction == "하행"
    assert stop.facilities == ["주유소", "전기차 충전소", "화장실"]
    assert stop.menu == ["감자옹심이", "커피"]
    assert to_rest_stop({**ITEM, "latitude": ""}) is None


def test_mark_rest_stops_keeps_nearby_only():
    route = Route(
        name="t",
        approximate=True,
        points=[Point(latitude=37.75, longitude=128.70), Point(latitude=37.76, longitude=128.90)],
    )
    near = to_rest_stop(ITEM)
    far = to_rest_stop({**ITEM, "entrpsNm": "먼곳", "latitude": "35.0", "longitude": "127.0"})
    marked = mark_rest_stops([near, far], route)
    assert [s.name for s in marked] == ["강릉(강릉) 휴게소"]
    assert marked[0].along_route


def test_route_highways_filter_direction():
    route = Route(
        name="t",
        approximate=True,
        highways={"영동선": "하행"},
        points=[Point(latitude=37.75, longitude=128.70), Point(latitude=37.76, longitude=128.90)],
    )
    down = to_rest_stop(ITEM)
    up = to_rest_stop({**ITEM, "entrpsNm": "강릉(인천)", "roadRouteDrc": "상행"})
    other = to_rest_stop({**ITEM, "entrpsNm": "다른노선", "roadRouteNm": "동해선"})
    marked = {s.name: s.along_route for s in mark_rest_stops([down, up, other], route)}
    assert marked == {"강릉(강릉) 휴게소": True, "강릉(인천) 휴게소": False, "다른노선 휴게소": False}


class FakeTourClient:
    def __init__(self, nearby, festivals):
        self._nearby, self._festivals = nearby, festivals
        self.intro_calls = []

    def nearby(self, center, radius_m, content_type, rows=100):
        return [p for p in self._nearby if CONTENT_TYPE_OF[p.category] == content_type][:rows]

    def festivals(self, start, rows=500):
        return self._festivals

    def overview(self, content_id):
        return "소개"

    def intro(self, place):
        self.intro_calls.append(place.id)
        return {"opening_hours": "09:00~18:00", "menu": ["순두부"] if place.category == "food" else []}


def _place(id_, name, category, lat, lon, **kw):
    return Place(id=id_, name=name, category=category, latitude=lat, longitude=lon, **kw)


def test_collect_places_keeps_destination_area_and_current_festivals():
    route = Route(
        name="t",
        approximate=False,
        points=[Point(latitude=37.5, longitude=127.0), Point(latitude=37.79, longitude=128.91)],
    )
    near_destination = _place("tour-1", "초당 순두부", "food", 37.79, 128.98)  # 경로 끝에서 약 6km
    far = _place("tour-2", "먼 식당", "food", 36.5, 127.5)
    toilet = _place("tour-3", "해변 화장실", "attraction", 37.79, 128.91)
    ongoing = _place(
        "tour-4", "커피축제", "event", 37.77, 128.95, event_start=date(2026, 11, 1), event_end=date(2026, 11, 20)
    )
    ended = _place(
        "tour-5", "여름축제", "event", 37.77, 128.95, event_start=date(2026, 7, 1), event_end=date(2026, 7, 5)
    )
    client = FakeTourClient([near_destination, far, toilet], [ongoing, ended])

    places = {p.id: p for p in collect_places(client, route, with_details=True, today=date(2026, 11, 10))}

    assert set(places) == {"tour-1", "tour-2", "tour-4"}
    assert places["tour-1"].along_route and places["tour-1"].menu == ["순두부"]
    assert not places["tour-2"].along_route and "tour-2" not in client.intro_calls
    assert places["tour-4"].along_route


def test_intro_parses_menu_and_hours():
    class Client(TourApiClient):
        def __init__(self):
            pass

        def _get(self, operation, **params):
            assert params["contentTypeId"] == 39
            return [
                {"firstmenu": "초당순두부", "treatmenu": "순두부전골, 모두부<br>", "opentimefood": "07:00~19:00<br>"}
            ]

    update = Client().intro(_place("tour-9", "식당", "cafe", 37.0, 128.0))
    assert update["menu"] == ["초당순두부", "순두부전골", "모두부"]
    assert update["opening_hours"] == "07:00~19:00"
