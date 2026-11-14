# 도란 AI 연동 계약

AI는 차량 맥락을 보고 먼저 제안하고, 사용자의 말에 답합니다. 3D 지도 앨범은 여행 기록과 음성 캡슐을 다시 들을 때 쓰고, 지도 화면은 앱이 그립니다. 서버가 외부 데이터와 차량 신호를 정리해 AI에 넘기면, AI는 판단 결과만 돌려주고 직접 아무것도 실행하지 않습니다.

## 실행 범위

- 능동 제안: 여행 출발, 식사 시간에 가까운 도착, 휴식 후 재탑승, 110분 이상 연속 운전, 귀가 출발.
- 발화 응답: 장소·휴게소·도착 시간·음악 추천, 앞서 언급한 장소 찾기, 여행 기록 재생, 조용히 모드 켜기·끄기.
- 주행 중에는 카드 목록을 돌려주지 않습니다. 경유지 추가 요청은 고른 장소가 있고 사용자가 따로 확인했을 때만 냅니다.
- 장소는 관광 데이터, 휴게소는 고속도로 데이터, 음악은 앱에 넣어 둔 곡 목록에서만 고릅니다. AI가 이름·좌표·음원을 만들어 내지 않습니다.

## 입력

서버가 `DoranAgent.handle(payload)`에 JSON 객체를 넘깁니다. 모든 요청에 `kind`, 시간대가 붙은 ISO 8601 `now`, `vehicle.gear`(`P/R/N/D`)가 있어야 합니다.

| 필드 | 내용 |
| --- | --- |
| `kind` | `vehicle_event` 또는 `user_utterance` |
| `event` | 차량 이벤트인 경우 `trip_started`, `arrival_30m`, `reboarded`, `continuous_drive`, `return_started` 중 하나 |
| `utterance` | 사용자 발화인 경우 한국어 문자열 |
| `vehicle` | `gear`, 선택적으로 `doors_closed`, `last_stop_minutes`, `continuous_driving_minutes`, `battery_percent`(EV 배터리 %), `passenger_count`(안전벨트를 맨 좌석 수), `wiper_on`, `headlight_on`. 차량이 값을 주지 않으면 빠진다. 에이전트는 아직 뒤의 네 값을 쓰지 않는다 |
| `navigation` | 선택적으로 `distance_from_home_km`, `eta_minutes`, `arrival_local_time`(`HH:MM`), `destination_is_home`, `minutes_since_departure` |
| `places` | `id`, `name`, `source: "tour_api"`, `category`, `along_route`가 있는 후보 목록 |
| `rest_stops` | `id`, `name`, `source: "expressway_api"`, `along_route`, 선택적으로 `eta_minutes`가 있는 후보 목록 |
| `tracks` | `id`, `name`, `source: "catalog"`, 선택적으로 `tags`(`travel`, `bright`, `refresh`, `calm`)가 있는 곡 목록 |
| `trip` | 선택적으로 `mentions`(언급 장소 이름·카테고리), `stops`(방문 기록), `capsules`(음성 캡슐 ID) |
| `preferences` | `quiet: true`이면 능동 제안을 끔 |
| `recent_proposals` | 서버에 저장한 최근 제안 `[{"type": "rest", "at": "..."}]` |
| `selected_place_id`, `selected_track_id` | 앱에서 사용자가 고른 후보의 ID. 서버가 같은 세션에서 최근 제시한 후보인지 확인한 뒤 넘김 |
| `pending_waypoint_id` | 서버에 보관된, 확인을 기다리는 경유지 ID |
| `network_available` | `false`이고 전달된 장소 후보도 없으면 신규 장소 검색 응답을 보류 |

입력 예시는 [recommend_place.json](../../ai/examples/recommend_place.json)에 있습니다. `source` 문자열은 출처를 보증하지 않으므로, 목록은 서버가 수집 데이터에서 ID·좌표·경로 적합성을 확인해 만듭니다. `along_route: true`가 없는 장소는 추천하지 않습니다.

## 출력

모든 응답에 다음 키가 있습니다. `status`는 `ok`, `no_action`, `needs_confirmation`, `unavailable`, `invalid_input` 중 하나입니다.

| 필드 | 사용처 |
| --- | --- |
| `speech` | TTS에 전달할 짧은 한국어 문장. 빈 값이면 말하지 않음 |
| `caption` | 화면에 보여줄 짧은 자막 |
| `proposal_type` | 능동 제안 종류. 서버가 시각과 함께 기록해 중복 제안을 막음 |
| `selected_place_id`, `selected_track_id` | 후보를 다시 찾을 수 있는 ID |
| `cards` | 정차 중에만 보여줄 카드 목록 |
| `commands` | 서버·앱이 검증 후 실행할 요청 목록. AI가 직접 내비나 플레이어를 조작하지 않음 |

`commands.type`은 `SET_QUIET`, `CLEAR_PENDING`, `SET_PENDING_WAYPOINT`, `ADD_WAYPOINT`, `SAVE_PLACE`, `PLAY_MUSIC`, `PLAY_CAPSULE` 중 하나입니다. 추천 후 `SET_PENDING_WAYPOINT`는 확인 대기 상태만 저장합니다. 서버는 새 주제가 나오면 이전 대기를 지우고, 대기는 2분 뒤 저절로 풀립니다. 고른 장소도 2분, 고른 곡은 5분 뒤 풀립니다. 그다음 사용자가 분명히 “응”이라고 해야 `ADD_WAYPOINT`가 나옵니다. 서버는 ID가 현재 세션의 후보 목록에 있는지 다시 확인한 뒤 `effects`로 앱에 넘기고, 실행은 앱이 합니다. 경유지를 넣지 못하면 앱이 음성 한 줄로 알립니다. `PLAY_MUSIC`은 사용자가 “틀어 줘”라고 할 때만 나오고, 추천만 해서는 틀지 않습니다. `PLAY_CAPSULE`은 같은 여행에 저장된 녹음에만 씁니다.

## 능동 제안 기준과 실패 시 동작

기본값은 21:00~07:00 제안 금지, 한 시간에 최대 2회, 같은 종류는 하루 1회입니다. 시연용으로 정한 값이라 차내에서 써 보고 조정합니다. 지금 서버는 응답을 만들 때 `recent_proposals`에 기록하고, 앱이 실제로 말했는지는 확인하지 않습니다. `reboarded`는 문이 닫히고 변속기가 `P`이며 15분 넘게 정차했을 때, `return_started`는 귀가 목적지 설정 10분 뒤 여행 기록이 있을 때만 제안합니다.

외부 데이터가 없으면 `unavailable`과 이유를 돌려줍니다. 통신이 끊겨도 서버가 넘긴 장소 후보가 있으면 그 후보로 답합니다. 잘못된 입력에는 `invalid_input`을 돌려주고 말할 문장은 비웁니다. LLM은 규칙에 없는 발화의 의도를 분류하고, `ok`·`needs_confirmation` 응답의 음성 문장을 다듬습니다. 서버 규칙이 먼저 후보·상태·명령·답변 초안을 정합니다. 모델이 만든 문장에서 후보 이름과 수치, 질문 형식을 검사하고 통과하지 못하거나 호출에 실패하면 초안을 그대로 씁니다. 실행 명령이 들어간 응답과 오프라인 요청은 문장을 다듬지 않습니다. 기록 검색은 지금 `trip.mentions`에서 이름과 분류로 찾습니다. 임베딩·벡터 검색은 넣지 않았습니다.

의도 분류에는 발화만 보냅니다. 문장 생성에는 서버가 만든 초안, 발화, 초안에 등장한 후보 이름을 보냅니다. 초안에는 장소·곡 이름, 도착 시간, 언급 장소 이름 같은 정보가 담길 수 있습니다. 원본 GPS 좌표, 차량 상태 객체, 여행 기록 전체와 음성 캡슐 내용은 모델에 보내지 않습니다. Groq 설정은 서버의 `.env`(호스팅 서버는 Render 환경 변수)에서 읽고, 키는 Git에 올리지 않습니다.

## 현재 연결과 남은 작업

Android 앱이 Python HTTP 서버(`server/main.py`)에 요청하고, 서버가 AI 모듈을 부릅니다. 심사·시연용 서버는 Render 무료 웹 서비스(`render.yaml`, `https://doran-agent.onrender.com`)에 올려 두었고 release APK가 이 주소를 씁니다. 세션별 제안 이력, 후보 선택, 확인 대기, 조용히 설정은 서버 메모리에 있어서 서버를 다시 켜면 지워집니다. 여행 기록은 SQLite에 남지만, Render 무료 서비스는 디스크가 남지 않아 다시 켤 때마다 시연 여행 3건만 새로 들어갑니다.

| 항목 | 상태 |
| --- | --- |
| 차량·내비 신호 | 앱이 Vehicle SDK의 기어·문·배터리·전조등·와이퍼·안전벨트와 NaviHelper의 남은 시간·도착을 채워 보냅니다. 에이전트는 아직 배터리·탑승 인원·와이퍼·전조등을 판단에 쓰지 않습니다 |
| 후보 데이터 | 강릉 경로의 TourAPI 장소 182곳, 휴게소 30곳, 곡 10곡(`ai/data/gangneung/`). 서버가 요청마다 경로 위치와 발화로 순서를 정합니다 |
| `ADD_WAYPOINT` | 앱이 NaviHelper로 좌표 경유지를 넣습니다. 에뮬레이터 내비에서 확인했습니다 |
| `PLAY_MUSIC` | 앱 안의 음원을 Media3로 틉니다 |
| `PLAY_CAPSULE` | 기기에 저장된 녹음을 틉니다 |
| `return_started` | 앱이 아직 보내지 않습니다. 집 위치를 받지 않아서입니다 |

남은 일은 운영 서버의 인증과 영속 저장소, 차내에서 제안 빈도와 발화 문구 조정, Groq 무료 한도와 응답 지연 확인입니다.

## 검증 상태

2026-11-13 기준 AI·서버 테스트 75개와 하위 테스트 3개가 통과합니다(`uv run pytest`). Groq `openai/gpt-oss-20b` 실호출에서 문장 생성과 의도 분류를 확인했고, 서버 흐름의 장소 추천 응답 2건이 약 0.5초, 1.2초 걸렸습니다. 모델 문장이 검증을 통과하지 못하면 규칙 초안을 그대로 쓰므로 같은 질문에도 문장이 조금씩 다를 수 있습니다. 후보의 경로 적합성(`along_route`)은 시연 경로(`route.json`) 기준이고, 실제 Pleos 내비 경로와는 다를 수 있습니다.
