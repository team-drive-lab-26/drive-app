from datetime import UTC, datetime

import pytest

from server.db import TripRepository, create_db_engine, session_factory
from server.db.migrate import upgrade
from server.demo_seed import load_trips, seed_demo_trips
from server.main import AgentService


def _rest_stop(id_: str, name: str, latitude: float, longitude: float) -> dict:
    return {
        "id": id_,
        "name": name,
        "source": "expressway_api",
        "latitude": latitude,
        "longitude": longitude,
        "along_route": True,
    }


def _place(id_: str, name: str, category: str, latitude: float, longitude: float) -> dict:
    return {
        "id": id_,
        "name": name,
        "source": "tour_api",
        "category": category,
        "latitude": latitude,
        "longitude": longitude,
        "along_route": True,
    }


REST_STOPS = [
    _rest_stop("rest-35-하남드림", "하남드림 휴게소", 37.53, 127.21),
    _rest_stop("rest-50-횡성(인천)", "횡성(인천) 휴게소", 37.47, 128.13),
    _rest_stop("rest-50-횡성(강릉)", "횡성(강릉) 휴게소", 37.49, 128.08),
]
PLACES = [
    _place("tour-1", "스타벅스 강릉강문해변점", "cafe", 37.79, 128.91),
    _place("tour-2", "고선생 화덕생선구이", "restaurant", 37.77, 128.94),
    _place("tour-3", "경포호수광장", "attraction", 37.79, 128.89),
    _place("tour-4", "테라로사 경포호수점", "cafe", 37.78, 128.90),
]


@pytest.fixture
def service(tmp_path):
    url = f"sqlite:///{(tmp_path / 'doran.db').as_posix()}"
    upgrade(url)
    sessions = session_factory(create_db_engine(url))
    trips = TripRepository(sessions, "gangneung")
    return AgentService(places=PLACES, rest_stops=REST_STOPS, tracks=[], trips=trips), trips, sessions


def test_seeds_every_demo_trip(service):
    agent, trips, sessions = service

    assert seed_demo_trips(agent, sessions) == [trip["session_id"] for trip in load_trips()]

    listed = trips.list_trips()
    assert {trip["session_id"] for trip in listed} == {trip["session_id"] for trip in load_trips()}
    assert all(trip["event_count"] > 0 for trip in listed)


def test_second_start_does_not_add_again(service):
    agent, trips, sessions = service
    seed_demo_trips(agent, sessions)
    before = [(trip["session_id"], trip["event_count"]) for trip in trips.list_trips()]

    assert seed_demo_trips(agent, sessions) == []
    assert [(trip["session_id"], trip["event_count"]) for trip in trips.list_trips()] == before


def test_album_pins_have_names_and_coordinates(service):
    agent, trips, sessions = service
    seed_demo_trips(agent, sessions)

    events = trips.list_events("demo-trip-1")
    stops = [event for event in events if event["type"] == "stop"]
    assert stops and all(event["name"] and event["latitude"] and event["longitude"] for event in stops)
    # 이름이 비슷한 반대 방향(인천) 휴게소가 아니라 가는 길의 휴게소여야 한다
    assert "횡성(강릉) 휴게소" in [event["name"] for event in stops]
    assert [event["name"] for event in events if event["type"] == "capsule"] == ["엄마", "아이"]


def test_old_trips_keep_their_demo_date(service):
    agent, trips, sessions = service
    seed_demo_trips(agent, sessions)

    started = datetime.fromisoformat(
        next(trip["started_at"] for trip in trips.list_trips() if trip["session_id"] == "demo-trip-3")
    )
    days_ago = (datetime.now(UTC) - started.replace(tzinfo=started.tzinfo or UTC)).days
    assert 88 <= days_ago <= 90
