import pytest
from sqlalchemy import inspect

from server.db import Base, TripRepository, create_db_engine, session_factory
from server.db.migrate import upgrade
from server.main import AgentService
from server.storage import LocalMediaStorage

NOW = "2026-11-07T12:00:00+09:00"


@pytest.fixture
def db_url(tmp_path):
    url = f"sqlite:///{(tmp_path / 'doran.db').as_posix()}"
    upgrade(url)
    return url


def test_migration_matches_models(db_url):
    engine = create_db_engine(db_url)
    tables = set(inspect(engine).get_table_names()) - {"alembic_version"}
    assert tables == set(Base.metadata.tables)
    engine.dispose()


def test_trip_survives_restart(db_url):
    engine = create_db_engine(db_url)
    trips = TripRepository(session_factory(engine), "gangneung")
    first = AgentService(places=[], rest_stops=[], tracks=[], trips=trips)
    first.record({"session_id": "trip-1", "type": "mention", "id": "p1", "name": "별빛 카페", "category": "cafe"})
    first.record({"session_id": "trip-1", "type": "capsule", "id": "c1"})

    second = AgentService(places=[], rest_stops=[], tracks=[], trips=trips)
    result = second.respond(
        {
            "session_id": "trip-1",
            "now": NOW,
            "kind": "user_utterance",
            "vehicle": {"gear": "P"},
            "utterance": "아까 그 카페 이름 뭐야?",
        }
    )
    assert "별빛 카페" in result["speech"]
    assert second.sessions["trip-1"].trip["capsules"] == [{"id": "c1"}]
    engine.dispose()


def test_local_storage_round_trip(tmp_path):
    storage = LocalMediaStorage(tmp_path)
    key = storage.put(b"voice", prefix="trip-1/capsules", suffix=".m4a")
    assert storage.read(key) == b"voice"
    storage.delete(key)
    assert not (tmp_path / key).exists()


def test_local_storage_rejects_escape(tmp_path):
    with pytest.raises(ValueError):
        LocalMediaStorage(tmp_path / "media").read("../secret")


def test_record_keeps_coordinates_for_album(db_url):
    engine = create_db_engine(db_url)
    trips = TripRepository(session_factory(engine), "gangneung")
    service = AgentService(places=[], rest_stops=[], tracks=[], trips=trips)
    service.record({"session_id": "trip-2", "type": "stop", "id": "s1", "latitude": 37.46, "longitude": 128.13})
    with pytest.raises(ValueError):
        service.record({"session_id": "trip-2", "type": "stop", "id": "s2", "latitude": 999, "longitude": 128.13})
    events = trips.list_events("trip-2")
    assert [(e["type"], e["id"], e["latitude"], e["longitude"]) for e in events] == [("stop", "s1", 37.46, 128.13)]
    engine.dispose()


def test_stop_record_takes_name_and_coordinates_from_catalog(db_url):
    engine = create_db_engine(db_url)
    place = {"id": "p1", "name": "바닷가 식당", "source": "tour_api", "category": "food", "latitude": 37.41, "longitude": 127.09}
    service = AgentService(places=[place], rest_stops=[], tracks=[], trips=TripRepository(session_factory(engine), "gangneung"))
    service.record({"session_id": "trip-stop", "type": "stop", "id": "p1"})
    service.record({"session_id": "trip-stop", "type": "stop", "id": "navi-강릉역", "name": "강릉역"})
    events = service.history("trip-stop")["events"]
    assert (events[0]["name"], events[0]["latitude"], events[0]["longitude"]) == ("바닷가 식당", 37.41, 127.09)
    assert (events[1]["name"], events[1]["latitude"]) == ("강릉역", None)
    engine.dispose()
