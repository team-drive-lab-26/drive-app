"""서버를 켤 때 앨범 화면에 보일 시연 여행 기록을 한 번만 넣는다.

앨범은 기록이 없으면 지도가 빈 채로 열린다. 시연에서 그 화면이 나오지 않도록 서버가 뜰 때
여행 세 번(오늘·40일 전·89일 전)을 DB에 넣는다. 실제 여행 기록이 아니라 시연 값이다.

이미 있는 session_id는 건너뛰므로 서버를 여러 번 켜도 기록이 늘지 않는다.
빈 앨범부터 찍어야 하는 화면 촬영 워크플로에서는 ``DORAN_SEED_DEMO=0``으로 끈다.

여행 구성은 ``server/demo_trips.json`` 한 곳에 둔다. ``app/scripts/seed_album.py``도 같은 파일을 읽어
켜져 있는 서버에 HTTP로 넣는다(화면 촬영용). 이 모듈은 서버가 직접 DB에 넣는다.
"""

from __future__ import annotations

import json
from datetime import UTC, datetime, timedelta, timezone
from pathlib import Path
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session, sessionmaker

from server.db.models import Trip, TripEvent

KST = timezone(timedelta(hours=9))


TRIPS_FILE = Path(__file__).with_name("demo_trips.json")


def load_trips() -> list[dict[str, Any]]:
    """시연 여행 구성. app/scripts/seed_album.py도 같은 파일을 읽는다."""
    return json.loads(TRIPS_FILE.read_text(encoding="utf-8"))


def _existing(sessions: sessionmaker[Session]) -> set[str]:
    with sessions() as db:
        return set(db.scalars(select(Trip.session_id)))


def _catalog_id(service: Any, name: str) -> str | None:
    """장소·휴게소 목록에서 이름이 들어맞는 항목의 ID. 데이터가 바뀌어 못 찾으면 None.

    이름이 똑같은 항목을 먼저 찾고, 없으면 이름에 들어 있는 항목 중 경로 위에 있는 곳을 고른다.
    반대 방향 휴게소처럼 이름만 비슷한 곳이 뽑히지 않게 한다.
    """
    items = [*getattr(service, "rest_stops", []), *getattr(service, "places", [])]
    matches = [item for item in items if name in item.get("name", "")]
    for candidate in (
        [item for item in matches if item.get("name") == name],
        [item for item in matches if item.get("along_route")],
        matches,
    ):
        if candidate:
            return candidate[0].get("id")
    return None


def _start_time(days_ago: int, departure: tuple[int, int]) -> datetime:
    """시연 여행의 출발 시각. 오늘 여행이 아직 오지 않은 시각이면 하루 앞당긴다."""
    now = datetime.now(KST)
    start = (now - timedelta(days=days_ago)).replace(hour=departure[0], minute=departure[1], second=0, microsecond=0)
    if days_ago == 0 and start > now:
        start -= timedelta(days=1)
    return start.astimezone(UTC)


def _backdate(sessions: sessionmaker[Session], session_id: str, start: datetime, minutes: list[int]) -> None:
    """서버는 기록 시각을 지금으로 찍으므로, 넣은 뒤 시연 날짜로 옮긴다."""
    with sessions.begin() as db:
        trip = db.scalar(select(Trip).where(Trip.session_id == session_id))
        if trip is None:
            return
        trip.started_at = start
        events = list(db.scalars(select(TripEvent).where(TripEvent.trip_id == trip.id).order_by(TripEvent.id)))
        for event, minute in zip(events[-len(minutes) :], minutes, strict=False):
            event.recorded_at = start + timedelta(minutes=minute)


def seed_demo_trips(service: Any, sessions: sessionmaker[Session]) -> list[str]:
    """없는 시연 여행만 넣고, 넣은 session_id를 돌려준다. 실패해도 서버는 그대로 뜬다."""
    added: list[str] = []
    try:
        have = _existing(sessions)
    except Exception as error:  # noqa: BLE001 - 시연 데이터 때문에 서버가 못 뜨면 안 된다
        print(f"시연 여행 기록을 확인하지 못했습니다: {error}", flush=True)
        return added

    for trip in load_trips():
        session_id = trip["session_id"]
        if session_id in have:
            continue
        try:
            minutes: list[int] = []
            for event in trip["events"]:
                body = {key: value for key, value in event.items() if key not in ("minute", "match")}
                name = event.get("match")
                if name is not None:
                    found = _catalog_id(service, name)
                    if found is None:
                        print(f"시연 여행 {session_id}: 장소 '{name}'을 목록에서 찾지 못해 건너뜁니다", flush=True)
                        continue
                    body["id"] = found
                service.record({"session_id": session_id, **body})
                minutes.append(event["minute"])
            hour, minute = map(int, trip["departure"].split(":"))
            _backdate(sessions, session_id, _start_time(trip["days_ago"], (hour, minute)), minutes)
            added.append(session_id)
        except Exception as error:  # noqa: BLE001 - 한 여행이 실패해도 나머지는 넣는다
            print(f"시연 여행 {session_id}을 넣지 못했습니다: {error}", flush=True)
    return added
