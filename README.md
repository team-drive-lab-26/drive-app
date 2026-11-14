# 도란

가족 여행 목소리 앨범. 가는 길에 들를 곳을 말로 찾아 내비 경유지로 넣고, 들른 곳에서 남긴 가족의 목소리를 3D 지도 앨범으로 모아 주는 차량용 앱입니다.

운전 중에는 화면을 볼 필요가 없습니다. "도란아"라고 부르면 도란이 듣고, 가는 길의 휴게소와 들를 만한 곳을 찾아 내비 경유지로 넣습니다. 멈춘 곳에서는 한마디를 남길 수 있고, 여행이 끝나면 들른 곳과 목소리가 3D 지도 앨범 한 권으로 남습니다.

## 할 수 있는 것

| 기능 | 내용 |
| --- | --- |
| "도란아" 호출 | 버튼 없이 이름을 불러 말을 겁니다. 발음이 조금 달라도 알아듣습니다 |
| 길 위의 추천 | 가는 길의 휴게소·맛집·카페·행사를 현재 위치와 남은 시간에 맞춰 제안합니다 |
| 경유지 추가 | 고른 곳을 내비 경유지로 넣습니다 |
| 목소리 남기기 | 멈춘 곳에서 15초 녹음하고 남긴 사람을 고릅니다. 녹음하는 동안 음악을 멈추고 송풍을 가장 약하게 줄입니다 |
| 여정 음악 | 분위기에 맞는 곡을 추천하고 바로 재생합니다 |
| 3D 지도 앨범 | 다녀온 길을 실제 지형 위에 그리고, 들른 곳을 누르면 그 자리에서 남긴 목소리가 나옵니다 |
| 차량 맥락 | 기어·도어·배터리·전조등·와이퍼·탑승 인원을 읽어 제안 시점과 화면(낮/밤)을 바꿉니다 |

## 사용한 Pleos SDK

| SDK | 버전 | 쓰는 곳 |
| --- | --- | --- |
| Vehicle | 2.3.2 | 기어·도어·배터리·전조등·와이퍼·안전벨트(탑승 인원)를 읽어 주행/정차 화면, 제안 시점, 낮/밤 화면을 정합니다. 녹음하는 동안 송풍을 가장 약하게 줄였다가 되돌립니다 |
| NaviHelper | 3.0.5 | 목적지·남은 거리와 시간을 받고, 고른 곳을 경유지로 넣고, 도착을 알아챕니다 |
| SpeechToText | 3.3.1 | "도란아" 호출과 이어지는 말을 듣습니다 |
| TextToSpeech | 3.3.1 | 도란의 대답을 읽어 줍니다 |

## 테스트 환경

| 항목 | 값 |
| --- | --- |
| 에뮬레이터 | Pleos Connect v2.1.0 (Android 14, API 34, arm64-v8a) |
| 화면 | 2560 × 1440, 160dpi, 가로 |
| 개발 PC | macOS (Apple Silicon) |

## APK

실행용 release APK `도란의검_도란_release.apk`는 [GitHub Release v1.0](https://github.com/team-drive-lab-26/drive-app/releases/tag/v1.0)에 있습니다. 파일이 112MB라 저장소 파일 한도(100MB)를 넘어서 Release에 올렸고, [`apk/`](apk/) 폴더에 같은 링크를 적어 두었습니다.

```
adb install -r 도란의검_도란_release.apk
```

release APK는 인터넷에 올려 둔 AI 서버(`https://doran-agent.onrender.com`)에 붙으므로, 에뮬레이터에 설치만 하면 바로 써 볼 수 있습니다. 무료 호스팅이라 15분 동안 요청이 없으면 서버가 잠듭니다. 앱을 켜면 서버를 깨우는 데 1분쯤 걸리니 첫 화면에서 잠시 기다린 뒤 말을 걸어 주세요. 서버가 다시 켜지면 시연 여행 3건만 남고 그 전 기록은 지워집니다.

## 직접 빌드해서 실행하기

### 준비물

| 도구 | 버전 |
| --- | --- |
| JDK | 17 또는 21 (Gradle 8.13은 JDK 25에서 돌지 않습니다) |
| Android Studio | Pleos Connect v2.1.0 에뮬레이터 |
| Python | 3.12 이상, [uv](https://docs.astral.sh/uv/) |

API 키는 없어도 됩니다. 장소·휴게소·곡 데이터가 저장소(`ai/data/gangneung/`)에 들어 있고, LLM 키가 없으면 서버가 규칙만으로 대답합니다.

### 1. 서버 켜기

저장소 루트에서 실행합니다.

```bash
uv sync --all-packages        # Python 3.12와 ai·server 의존성 설치
uv run python -m server.main  # 127.0.0.1:8765
```

처음 켜면 앨범에 보여 줄 시연 여행 세 번(오늘·40일 전·89일 전)이 자동으로 들어갑니다. 이미 있으면 다시 넣지 않고, 넣고 싶지 않으면 `DORAN_SEED_DEMO=0`으로 켭니다.

### 2. 앱 실행

Android Studio에서 `app/` 폴더를 열고 에뮬레이터에서 `app` 구성을 실행합니다. 터미널에서는 이렇게 합니다.

```bash
cd app
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

디버그 앱은 `http://10.0.2.2:8765`(에뮬레이터에서 본 PC 주소)로 서버를 찾습니다. 다른 주소를 쓰려면 `app/local.properties`에 `doran.agentBaseUrl=http://...`을 넣습니다. "도란아"를 들으려면 마이크 권한을 허용해야 합니다.

### 3. 둘러보는 순서

1. 처음 설정: 음성 사용에 동의하면 도란이 듣기 시작합니다.
2. 주행: 기어를 D로 두면 앞길 화면으로 바뀝니다. "도란아, 휴게소 어디야"라고 부르면 가는 길의 휴게소를 거리와 함께 알려 줍니다. "응"이나 "여기 가자"라고 하면 경유지에 넣습니다.
3. 정차: 기어를 P로 두면 추천 카드가 열립니다. "근처 맛집 추천해 줘", "노래 추천해 줘"(고른 뒤 "틀어 줘")를 말해 보세요.
4. 녹음: 도착하면 도란이 한마디 남길지 묻습니다. 15초 녹음한 뒤 남긴 사람을 고릅니다.
5. 앨범: 다녀온 길이 3D 지도로 열리고, 들른 곳을 누르면 그 자리의 목소리가 재생됩니다.

### 차에 타지 않고 시험하기

디버그 빌드에서 도란을 길게 누르면 디버그 패널이 열립니다. 기어·도어·배터리 같은 차량 값을 바꾸고 발화를 글로 넣어 볼 수 있습니다. `adb`로도 됩니다.

```bash
adb shell am start -n com.doran.app/.MainActivity --es gear D         # 주행 화면
adb shell am start -n com.doran.app/.MainActivity --es say "휴게소 어디야"
```

화면을 차례로 찍으려면 `bash app/scripts/capture_screens.sh screens`를 씁니다.

## 환경 변수

`.env.example`을 `.env`로 복사해서 채웁니다. `.env`는 커밋하지 않습니다.

| 변수 | 쓰임 |
| --- | --- |
| `TOURAPI_SERVICE_KEY` | 장소 데이터 수집([data.go.kr](https://www.data.go.kr) 한국관광공사 TourAPI 활용 신청) |
| `DATA_GO_KR_SERVICE_KEY` | 휴게소 데이터 수집(전국휴게소정보표준데이터). 같은 계정이면 위 키 하나로 됩니다 |
| `GROQ_API_KEY` | 대답 문장을 다듬는 LLM. 없으면 규칙으로만 대답합니다 |
| `DORAN_REGION` | 데이터 지역. 기본 `gangneung`, API 없이 시험할 때는 `dummy` |
| `DORAN_SEED_DEMO` | 서버를 켤 때 시연 여행 기록을 넣을지. 기본은 넣고, `0`이면 넣지 않음 |

실행만 해 보실 때는 키를 넣지 않아도 됩니다.

## 저장소 구조

| 폴더 | 내용 |
| --- | --- |
| `apk/` | release APK 내려받는 곳 안내 (APK는 GitHub Release v1.0) |
| `app/` | Android 앱 (Kotlin, Jetpack Compose) |
| `server/` | API 서버 (Python 표준 HTTP, SQLite) |
| `ai/` | AI 에이전트와 지식 데이터, 데이터 수집 |
| `docs/` | 기준 문서. [docs/index.md](docs/index.md)부터 읽으시면 됩니다 |

## 개발

```bash
uv run pytest                     # 전체 테스트
uv run ruff check . && uv run ruff format .
uv run doran-collect              # TourAPI로 ai/data/<지역>/places.json 생성
cd app && ./gradlew test          # 앱 단위 테스트
```

Python 패키지는 uv 워크스페이스로 관리합니다. 루트 `pyproject.toml`이 `ai`, `server`를 묶고 `uv.lock`으로 버전을 고정하니 `pip install`로 따로 설치하지 않습니다. 의존성은 `uv add --package doran-ai <패키지>`처럼 패키지를 지정해서 추가하고, 개발 도구는 `uv add --dev <패키지>`로 루트에 둡니다.

서버 DB는 SQLAlchemy·Alembic으로 관리하며 기본값은 `server/var/doran.db`(SQLite)입니다. 스키마를 바꿀 때는 [docs/db](docs/db/README.md)의 절차를 따릅니다. 로컬 서버는 `127.0.0.1:8765`에만 바인딩하고 인증이 없어서 시연용으로만 씁니다.

에이전트가 읽는 데이터는 `ai/data/<지역>/`에 있습니다. `route.json`(시연 경로), `places.json`(관광 정보), `rest_stops.json`(휴게소), `tracks.json`(곡 목록)이 있고, 서버는 요청마다 현재 위치와 발화에 맞춰 후보 순서를 정합니다. `DORAN_CATALOG_FILE`을 주면 그 파일을 대신 읽습니다.

AI 도구는 [AGENTS.md](AGENTS.md)를 따르고, 사람도 같은 규칙을 씁니다.

## 데이터와 음원 출처

| 자료 | 출처 |
| --- | --- |
| 관광지·음식점·카페·행사 | 한국관광공사 TourAPI 4.0 (공공데이터포털) |
| 고속도로 휴게소 | 전국휴게소정보표준데이터, 한국도로공사 (공공데이터포털) |
| 시연 경로 | OpenStreetMap 도로망 기반 경로(OSRM) |
| 3D 지도 앨범 | 지도 OpenFreeMap (© OpenStreetMap 기여자), 지형 고도 AWS Terrain Tiles, 지도 엔진 MapLibre GL JS |
| 배경 음악 10곡 | Kevin MacLeod (incompetech.com), CC BY 4.0. 곡 목록은 [CREDITS](app/app/src/main/assets/music/CREDITS.md) |
| 글꼴 | SUIT, SIL Open Font License 1.1 |

수집 범위와 방법은 [docs/data](docs/data/README.md)에 있습니다. 앨범에 들어 있는 여행 세 번은 화면을 보여 드리려고 넣은 시연 값이고 실제 여행 기록이 아닙니다.
