# 도란

가족 여행 목소리 앨범. 차가 멈춘 곳과 그곳에서 남긴 가족의 목소리를 차량 데이터로 기록하는 차량용 앱입니다.

## 구조

| 폴더 | 내용 |
| --- | --- |
| `app/` | Android 앱 |
| `server/` | API 서버 |
| `ai/` | AI 에이전트, RAG |
| `docs/` | 기준 자료. [docs/index.md](docs/index.md)부터 읽으세요 |

## 앱 실행

Android Studio에서 `app/` 폴더를 열고 `app` 구성을 실행합니다. 터미널에서는 다음과 같습니다.

```bash
cd app
./gradlew assembleDebug
```

JDK 17 또는 21로 실행합니다. Gradle 8.13은 JDK 25에서 실행되지 않으므로, Android Studio에서는 Settings > Build Tools > Gradle의 Gradle JDK를 21로 맞춥니다.

디버그 빌드 하단의 버튼으로 기어 값을 바꿔 주행·정차 화면 전환을 확인할 수 있습니다.

## AI·서버 실행

Python 패키지는 [uv](https://docs.astral.sh/uv/) 워크스페이스로 관리합니다. 루트 `pyproject.toml`이 `ai`, `server`를 묶고 `uv.lock`으로 버전을 고정합니다. `pip install`로 따로 설치하지 않습니다.

```bash
uv sync                      # Python 3.12와 의존성 설치
cp .env.example .env         # 키 입력, 커밋 금지
uv run pytest                # 전체 테스트
uv run ruff check . && uv run ruff format .
uv run python -m server.main # 로컬 서버 (127.0.0.1:8765)
uv run doran-collect         # TourAPI로 ai/data/<지역>/places.json 생성
```

의존성 추가는 `uv add --package doran-ai <패키지>`처럼 해당 패키지를 지정합니다. 개발 도구는 `uv add --dev <패키지>`로 루트에 둡니다.

서버 DB는 SQLAlchemy·Alembic으로 관리하고 기본값은 `server/var/doran.db`(SQLite)입니다. 스키마 변경 절차는 [docs/db](docs/db/README.md)를 따릅니다.

데이터 출처와 수집 명령은 [docs/data](docs/data/README.md), API 없이 시험할 때는 `DORAN_REGION=dummy`를 씁니다.

에이전트가 읽는 데이터는 `ai/data/<지역>/`에 있습니다. `route.json`(시연 경로), `places.json`(TourAPI 수집 결과), `rest_stops.json`(휴게소), `tracks.json`(곡 목록)입니다. 서버는 `DORAN_CATALOG_FILE`이 없으면 이 폴더를 읽습니다. 현재 `route.json`은 주요 분기점 좌표를 이은 근사치라서 내비 실제 경로로 교체해야 합니다.

## 작업 방식

AI 도구는 [AGENTS.md](AGENTS.md)를 따릅니다. 사람도 같은 규칙을 씁니다.
