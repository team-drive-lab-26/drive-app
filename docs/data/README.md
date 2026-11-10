# 에이전트 데이터 출처

수집한 데이터는 `ai/data/<지역>/`에 JSON으로 저장하고, 형식은 `ai/doran_ai/knowledge/schema.py`를 따릅니다. 여행 기록·대화 기록은 서버 DB에 저장합니다([docs/db](../db/README.md)).

| 데이터 | 파일 | 출처 | 상태 |
| --- | --- | --- | --- |
| 관광지·문화시설·축제·레포츠·쇼핑·음식점 | `places.json` | 한국관광공사 TourAPI 4.0 `KorService2` (`locationBasedList2`, `detailCommon2`, `detailIntro2`) | 수집기 있음. 키 발급 후 파라미터 확인 필요 |
| 고속도로 휴게소 위치·편의시설·대표음식 | `rest_stops.json` | 공공데이터포털 전국휴게소정보표준데이터(data.go.kr 15025446, 한국도로공사) | 원본 받는 코드만 있음. 응답 필드명 확인 후 변환 코드 작성 |
| 곡 목록 | `tracks.json` | 쓸 수 있는 API 없음 | 더미. 팀이 직접 큐레이션 |
| 시연 경로 | `route.json` | Pleos NaviHelper 경로 또는 수작업 좌표 | 근사 좌표 |
| 내비 경유지 ID(`navi_poi_id`) | 각 장소 항목 | Pleos NaviHelper | 확보 방법 미정. 없으면 경유지 추가 불가 |

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
```

휴게소는 원본(`raw/rest_areas.json`)을 먼저 받고, 실제 필드명을 확인한 뒤 `RestStop` 형식으로 바꾸는 코드를 `ai/doran_ai/collect/restarea.py`에 추가합니다. `raw/` 폴더는 커밋하지 않습니다.

## 더미 데이터

`ai/data/dummy/`는 API 없이 에이전트를 시험하는 가짜 데이터입니다. 모든 항목에 `"dummy": true`가 있고 이름·소개·메뉴·내비 ID는 실제가 아닙니다. 서버에서 쓰려면 `.env`에 `DORAN_REGION=dummy`를 넣습니다. 제출·시연 영상에는 쓰지 않습니다.

## GitHub Actions

키는 저장소 Actions 시크릿(`TOURAPI_SERVICE_KEY`, `DATA_GO_KR_SERVICE_KEY`)에 있습니다. Actions 탭의 `collect-data`를 수동 실행하면 지역·API·개수를 골라 수집하고, 결과 폴더를 실행 결과의 Artifacts에서 내려받습니다. 저장소에 자동 커밋하지 않으므로 확인한 파일만 `ai/data/<지역>/`에 넣어 커밋합니다. `python-test`는 `ai`·`server` 변경을 push하면 pytest를 실행합니다.
