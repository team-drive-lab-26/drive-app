# 서버 DB

여행 기록은 SQLAlchemy 2와 Alembic으로 관리합니다. 기본 DB는 `server/var/doran.db`(SQLite)이고, `DORAN_DATABASE_URL`을 바꾸면 PostgreSQL 등으로 옮길 수 있습니다. 서버를 켜면 `alembic upgrade head`가 자동으로 실행됩니다.

## 테이블

| 테이블 | 용도 | 주요 컬럼 |
| --- | --- | --- |
| `trips` | 여행 1건. 앱의 `session_id` 1개와 1:1 | `id`, `session_id`(고유), `region`, `started_at`, `ended_at` |
| `trip_events` | 장소 언급·방문·음성 캡슐 기록 | `trip_id`, `type`(`mention`/`stop`/`capsule`), `ref_id`, `name`, `category`, `latitude`, `longitude`, `data`(JSON), `recorded_at` |
| `media_assets` | 음성·사진 파일 메타데이터 | `trip_id`, `kind`, `storage_key`(고유), `content_type`, `size_bytes`, `duration_ms` |

파일 본문은 DB에 넣지 않습니다. `server/storage.py`의 `MediaStorage` 인터페이스로 저장하고 DB에는 `storage_key`만 남깁니다. 현재 구현은 `LocalMediaStorage`(`server/var/media/`)이며, 클라우드 저장소가 필요하면 같은 인터페이스로 구현체를 추가합니다. `media_assets`와 파일 업로드 API는 아직 연결하지 않았습니다.

## 메모리에만 있는 값

대화 맥락은 계속 `server/main.py`의 `Session` 객체에 있고 서버를 다시 켜면 사라집니다. 서버가 세션을 처음 만들 때 `trip_events`에서 해당 여행 기록을 읽어 `Session.trip`을 채웁니다.

| 값 | 용도 |
| --- | --- |
| `quiet` | 먼저 말 걸지 않기 설정 |
| `pending_waypoint_id`, `pending_until` | 경유지 추가 확인 대기, 2분 뒤 만료 |
| `selected_place_id`, `selected_track_id` | 다음 발화의 선택 맥락. 장소는 2분, 곡은 5분 뒤 만료 |
| `offered_place_ids` | 같은 세션에 제시한 장소 카드의 ID. 장소 선택 맥락과 함께 2분 뒤 만료 |
| `recent_proposals` | 중복 제안 제한용 종류·시각 |
| `saved_place_ids` | 저장한 장소 ID |

## 스키마 변경 절차

```bash
# server/db/models.py 수정 후
uv run alembic -c server/alembic.ini revision --autogenerate -m "변경 내용"
# 생성된 server/migrations/versions/*.py 검토
uv run alembic -c server/alembic.ini upgrade head
uv run alembic -c server/alembic.ini check   # 모델과 마이그레이션 일치 확인
```

마이그레이션 파일은 커밋하고, 이미 공유된 파일은 고치지 않고 새 리비전을 추가합니다. 사용자 인증, 저장 기간, 기록 접근 권한은 아직 정하지 않았습니다.
