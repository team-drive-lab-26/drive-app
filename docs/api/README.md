# 앱 ↔ AI 서버 API

서버 실행 방법은 [server/README.md](../../server/README.md)에 있습니다. 로컬 주소는 서버에서 `127.0.0.1:8765`, Android 에뮬레이터에서 `10.0.2.2:8765`입니다. 심사·시연용 서버는 `https://doran-agent.onrender.com`(`render.yaml`)에 있습니다. 인증 없이 세션 ID만 쓰므로 시연용으로만 씁니다.

## `POST /v1/agent/respond`

요청 본문은 UTF-8 JSON입니다. 필수: `session_id`(영숫자·`_`·`-`, 1~64자), `kind`(`user_utterance`/`vehicle_event`), `vehicle.gear`(`P/R/N/D`). 발화면 `utterance`, 이벤트면 `event`가 필요합니다. `now`를 생략하면 서버 현지 시각을 씁니다. `navigation`과 차량의 추가 값은 [AI 계약](../ai/README.md)을 따릅니다. 선택적으로 현재 위치 `position`(`latitude`·`longitude`)을 보낼 수 있습니다.

서버는 요청마다 장소·휴게소 후보의 순서를 정해 AI에 넘깁니다(`server/candidates.py`). AI는 앞쪽 후보부터 고릅니다.

- 현재 위치: `position`을 시연 경로(`ai/data/<지역>/route.json`)에 투영합니다. 없으면 `navigation.minutes_since_departure`와 `eta_minutes`의 비율로 경로 위 위치를 추정합니다. 둘 다 없으면 목적지 주변을 먼저 둡니다.
- 장소: 이미 지나친 곳(현재 위치에서 15km 넘게 떨어진 곳)은 뒤로 보냅니다. 발화에 카페·축제·식사 단어가 있으면 그 분류를 앞에 두고, 이름·메뉴에 발화 단어가 들어 있는 곳, 소개글에 들어 있는 곳, 가까운 곳 순으로 정렬합니다. 축제·행사를 물었는데 앞으로 갈 길에 진행 중인 행사가 없으면 후보를 비워 "찾지 못했어"로 답하게 합니다.
- 휴게소: 앞으로 나올 휴게소를 가까운 순서로 둡니다. 남은 시간을 알면 휴게소까지 걸리는 분을 `eta_minutes`로 붙입니다.

```json
{"session_id":"demo-1","kind":"user_utterance","vehicle":{"gear":"D"},"utterance":"근처 어디가 좋아?"}
```

응답은 AI 계약의 `status`, `speech`, `caption`, `proposal_type`, `selected_place_id`, `selected_track_id`, `cards`, `commands`에 `effects` 배열을 더합니다. `commands`는 서버 내부 상태 갱신 요청이고, `effects`만 앱이 처리합니다. `effects`에는 `ADD_WAYPOINT`(`place`: ID·이름·위도·경도·선택적 `navi_poi_id`·`navi_poi_sub_id`·주소), `PLAY_MUSIC`(`track_id`), `PLAY_CAPSULE`(`capsule_id`)가 나올 수 있습니다. `PLAY_MUSIC`·`PLAY_CAPSULE`은 재생해 달라는 요청이고 재생이 끝났다는 뜻은 아닙니다. 좌표가 없는 장소는 내비 효과를 반환하지 않습니다. 내비 POI ID는 없으면 `null`이고, 앱은 좌표만으로 경유지를 추가합니다. 관광 API의 콘텐츠 ID와 Pleos NaviHelper의 POI ID는 별개로 관리합니다.

정차 중 카드에서 장소를 고를 때는 `selected_place_id`를 보낼 수 있습니다. 서버는 같은 세션에서 최근 2분 안에 제시한 장소만 받습니다. 곡의 `selected_track_id`도 최근 5분 안에 추천한 곡과 일치해야 합니다. 오래된 ID나 다른 세션의 ID를 요청에 넣어도 선택 상태가 되살아나지 않습니다.

## `POST /v1/trip/record`

```json
{"session_id":"demo-1","type":"mention","id":"place-1","name":"별빛 카페","category":"cafe"}
```

`type`은 `mention`, `stop`, `capsule` 중 하나입니다. `stop`·`capsule`에는 `id`만 필요합니다. 선택적으로 `latitude`·`longitude`(숫자)를 보내면 앨범 핀 위치로 저장합니다. 성공하면 `{"status":"ok"}`를 반환합니다. 음성 파일은 올리지 않고 언제 어디서 무엇을 했는지만 서버 DB(`trip_events`)에 저장합니다. 서버를 다시 켜도 같은 `session_id`의 기록이 남습니다. 저장 구조는 [docs/db](../db/README.md)를 따릅니다.

`stop`의 `id`가 후보 목록(장소·휴게소)에 있으면 서버가 이름과 좌표를 채웁니다. 목록에 없는 곳은 `name`만 함께 보내면 이름이 남습니다. `capsule`의 `name`에는 남긴 사람을 넣습니다.

## `POST /v1/route/ahead`

정차 화면 길가 표지판에 세울 휴게소를 돌려줍니다. 앞으로 나올 휴게소를 가까운 순서로 최대 3곳, 경로를 따라 남은 거리(`km`)와 함께 줍니다.

```json
{"navigation":{"eta_minutes":120,"minutes_since_departure":40}}
```

```json
{"status":"ok","stops":[{"id":"rest-1","name":"횡성 휴게소","km":34.2}]}
```

현재 위치는 `position`이 오면 경로에 투영하고, 없으면 `minutes_since_departure`와 `eta_minutes` 비율로 추정합니다. 둘 다 없으면 빈 목록입니다. 앱은 내비 경로가 있을 때 1분마다 부릅니다.

## `GET /v1/trip/record`

`session_id` 없이 부르면 여행 목록을 최근 순으로 돌려줍니다.

```json
{"status":"ok","trips":[{"session_id":"demo-1","region":"gangneung","started_at":"2026-11-11T10:00:00+09:00","ended_at":null,"event_count":5}]}
```

`?session_id=demo-1`을 붙이면 그 여행의 기록과 앨범 지도에 그릴 경로(`[위도, 경도]` 최대 약 120개)를 돌려줍니다.

```json
{"status":"ok","session_id":"demo-1","events":[{"type":"stop","id":"rest-1","name":"횡성 휴게소","category":"rest_stop","latitude":37.5,"longitude":128.1,"recorded_at":"..."}],"route":[[37.566,126.977],[37.795,128.918]]}
```

정차 중 응답의 `cards`에는 `kind`(`place`/`rest_stop`), `category`, `address`, `summary`(한 줄 설명), `image_url`, `distance_km`가 붙습니다. 곡 카드는 `track_id`, `name`, `artist`, `tags`를 줍니다.

`GET /health`로 서버가 살아 있는지 봅니다. 잘못된 요청에는 HTTP 400과 `{"error":"invalid_request"}`, 없는 경로에는 404를 돌려줍니다. 서버가 닿지 않으면 앱은 연결 실패 자막을 보여 줍니다.

## Android 시연 연결

디버그 앱은 `BuildConfig.AGENT_BASE_URL=http://10.0.2.2:8765`를 쓰고 HTTP 통신을 허용합니다. 포트가 다르면 `app/local.properties`에 `doran.agentBaseUrl=http://10.0.2.2:<포트>`를 넣습니다. release 앱은 `app/gradle.properties`의 `doran.releaseAgentBaseUrl`(호스팅 서버 주소)을 씁니다. 앱은 Retrofit으로 호출하고, 요청·응답 형식은 `core/network/AgentDtos.kt`에 있습니다. 디버그 빌드에서는 도란을 길게 눌러 여는 디버그 패널에서 발화를 글로 넣을 수 있습니다. 기어와 문 열림은 Pleos Vehicle SDK에서, 남은 시간·경로 거리·경유지/목적지 도착은 NaviHelper에서 받습니다. 음성은 Pleos TextToSpeech·SpeechToText 온디바이스 모드를 쓰고, Pleos 음성 서비스가 없으면 Android 기본 음성으로 바꿉니다.

앱은 차량 이벤트를 스스로 판단해 보냅니다. `trip_started`는 처음 D로 바꿨을 때 경로가 있으면 바로, 없으면 2분 뒤 한 번 보냅니다. `arrival_30m`은 남은 시간이 30분 이하가 되면 목적지마다 한 번, `continuous_drive`는 15분 이상 쉬지 않고 110분 달렸을 때, `reboarded`는 P에서 문을 열었다가 15분 넘게 정차한 뒤 닫았을 때 보냅니다. 요청의 `vehicle`에는 `doors_closed`, `last_stop_minutes`, `continuous_driving_minutes`와 Vehicle SDK에서 받은 `battery_percent`, `passenger_count`, `wiper_on`, `headlight_on`을, `navigation`에는 `eta_minutes`, `arrival_local_time`, `minutes_since_departure`를 채웁니다. 집 위치를 아직 받지 않아 `distance_from_home_km`에는 출발 때 잡은 경로 거리를 넣습니다. `return_started`는 아직 보내지 않습니다. 내비 도착 뒤 10분 안에 P로 세우면 `/v1/trip/record`에 `stop`으로 기록하며, 앱이 추가한 경유지는 서버 장소 ID를, 그 밖의 장소는 `navi-<이름>`을 ID로 씁니다.

경유지 효과는 [Pleos NaviHelper v3.0.5 경유지 API](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/waypoint-settings)로 넣습니다. 앱은 SDK에 연결한 뒤 요청을 보내고, 오류가 오면 자막으로 알립니다. `navi_poi_id`가 없으면 좌표와 이름만 보냅니다. 에뮬레이터 내비에서 좌표만으로 경유지가 추가되는 것을 확인했습니다.
