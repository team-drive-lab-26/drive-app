# 에이전트 데이터 출처

수집한 데이터는 `ai/data/<지역>/`에 JSON으로 저장하고, 형식은 `ai/doran_ai/knowledge/schema.py`를 따릅니다. 여행 기록·대화 기록은 서버 DB에 저장합니다([docs/db](../db/README.md)).

| 데이터 | 파일 | 출처 | 상태 |
| --- | --- | --- | --- |
| 관광지·문화시설·레포츠·쇼핑·음식점·카페 | `places.json` | 한국관광공사 TourAPI 4.0 `KorService2` (`locationBasedList2`, `detailCommon2`, `detailIntro2`) | 2026-11-11 Actions로 182곳 수집. 경로 표본 + 목적지 8km 안(음식점 60, 관광지 30 등). 음식점 메뉴 86곳, 운영 시간 161곳 |
| 축제·공연·행사 | `places.json` (`category: "event"`) | TourAPI `searchFestival2` | 수집일 기준 끝나지 않은 행사 중 경로 5km 또는 목적지 20km 안 26건. 서버는 켤 때 오늘 진행 중인 행사만 씀 |
| 고속도로 휴게소 위치·편의시설 | `rest_stops.json` | 공공데이터포털 전국휴게소정보표준데이터(data.go.kr 15025446, 한국도로공사) | 2026-11-11 전국 215곳 중 경로 20km 안 30곳, 경로 위 4곳(하남드림, 횡성·평창·강릉 강릉방향). 광주원주선(민자) 휴게소는 이 데이터에 없음. 대표음식 필드(`rprsntvRstrt`)는 전부 비어 있어 메뉴는 다른 출처 필요 |
| 곡 목록 | `tracks.json` | 쓸 수 있는 API 없음 | 비어 있음. 팀이 직접 큐레이션 |
| 시연 경로 | `route.json` | OSRM(OpenStreetMap 도로망) 경로를 0.3km 간격으로 줄임 | 서울시청 → 강릉역 → 경포, 중부·광주원주·영동고속도로 약 222km. Pleos 내비 경로와 다를 수 있음 |
| 내비 경유지 ID(`navi_poi_id`) | 각 장소 항목 | Pleos NaviHelper | 확보 방법 미정. 없으면 좌표로 경유지를 추가한다 |

## 키

data.go.kr에서 두 API를 활용 신청하고 `.env`에 넣습니다. 같은 계정 키를 쓰면 `TOURAPI_SERVICE_KEY` 하나만 넣어도 휴게소 수집에 함께 쓰입니다.

```
TOURAPI_SERVICE_KEY=
DATA_GO_KR_SERVICE_KEY=
```

## 수집 명령

테스트할 때는 `--limit`으로 몇 건만 받습니다. API 호출은 1초 간격입니다.

```bash
uv run doran-collect --region gangneung --limit 20                 # TourAPI → places.json
uv run doran-collect --region gangneung --skip-details --limit 20  # 소개글 조회 생략
uv run doran-collect --region gangneung --source restarea --limit 20  # 휴게소 원본 → raw/rest_areas.json
uv run doran-collect --region gangneung --source route    # 키 없이 실행. anchors를 이은 도로 경로로 route.json 갱신 후 장소·휴게소 경로 판정 다시
uv run doran-collect --region gangneung --source remark   # API 호출 없이 경로 판정만 다시
```

`route.json`의 `anchors`는 경로를 만들 출발·경유·도착 좌표입니다. 강릉역을 경유점으로 둔 이유는 경포로 바로 찾으면 서울양양고속도로로 길이 잡혀 영동고속도로 휴게소 시나리오가 빠지기 때문입니다. `highways`는 경로 위 휴게소로 볼 노선과 방향입니다. 장소는 경로에서 5km 안이거나 목적지(경로 끝점) 8km 안이면 `along_route: true`입니다. 이름에 화장실·주차장·체육센터·체육관이 들어간 곳은 뺍니다.

TourAPI 실제 수집은 `--limit 100`이 기본량입니다. 2026-11-11 수집은 목록 조회 86회, 상세 조회 364회(182곳 × 소개·운영 정보)였습니다. 상세 조회는 경로·목적지 주변 후보에만 합니다.

휴게소는 전국 원본을 `raw/`에 저장하고 경로 20km 안만 `rest_stops.json`에 남깁니다. 경로 위 판정은 `route.json`의 `highways`(노선명 → 방향)와 거리(1.5km)로 합니다. `raw/` 폴더는 커밋하지 않습니다. TourAPI는 `--limit`을 경로 지점 수 × 분류 수로 나눠 경로 전체에서 고르게 받습니다.

## 더미 데이터

`ai/data/dummy/`는 API 없이 에이전트를 시험하는 가짜 데이터입니다. 모든 항목에 `"dummy": true`가 있고 이름·소개·메뉴·내비 ID는 실제가 아닙니다. 서버에서 쓰려면 `.env`에 `DORAN_REGION=dummy`를 넣습니다. 제출·시연 영상에는 쓰지 않습니다.

## GitHub Actions

키는 저장소 Actions 시크릿(`TOURAPI_SERVICE_KEY`, `DATA_GO_KR_SERVICE_KEY`)에 있습니다. Actions 탭의 `collect-data`를 수동 실행하면 지역·API·개수를 골라 수집하고, 결과 폴더를 실행 결과의 Artifacts에서 내려받습니다. 저장소에 자동 커밋하지 않으므로 확인한 파일만 `ai/data/<지역>/`에 넣어 커밋합니다. `python-test`는 `ai`·`server` 변경을 push하면 pytest를 실행합니다.
