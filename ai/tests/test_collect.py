import pytest
from doran_ai.collect.build import mark_rest_stops
from doran_ai.collect.restarea import to_rest_stop
from doran_ai.collect.tourapi import TourApiError, _items
from doran_ai.knowledge import Point, Route

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
