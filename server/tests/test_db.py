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
