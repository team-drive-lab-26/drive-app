# 앱 ↔ 로컬 AI 서버 API (시연 계약)

서버 실행은 [server/README.md](../../server/README.md)를 참고합니다. 기본 주소는 서버에서 `127.0.0.1:8765`, Android 에뮬레이터에서 `10.0.2.2:8765`입니다. 일반 HTTP와 인증 없는 세션 ID를 쓰므로 로컬 시연에만 사용합니다.

## `POST /v1/agent/respond`

요청 본문은 UTF-8 JSON입니다. 필수: `session_id`(영숫자·`_`·`-`, 1~64자), `kind`(`user_utterance`/`vehicle_event`), `vehicle.gear`(`P/R/N/D`). 발화면 `utterance`, 이벤트면 `event`가 필요합니다. `now`를 생략하면 서버 현지 시각을 씁니다. `navigation`과 차량의 추가 값은 [AI 계약](../ai/README.md)을 따릅니다.

```json
{"session_id":"demo-1","kind":"user_utterance","vehicle":{"gear":"D"},"utterance":"근처 어디가 좋아?"}
```

응답은 AI 계약의 `status`, `speech`, `caption`, `proposal_type`, `selected_place_id`, `selected_track_id`, `cards`, `commands`에 `effects` 배열을 더합니다. `commands`는 서버 내부 상태 갱신 요청이고, `effects`만 앱이 처리합니다. `effects`에는 `ADD_WAYPOINT`(`place`: ID·이름·위도·경도·`navi_poi_id`·선택적 `navi_poi_sub_id`·주소), `PLAY_MUSIC`(`track_id`), `PLAY_CAPSULE`(`capsule_id`)가 나올 수 있습니다. `PLAY_MUSIC`·`PLAY_CAPSULE`은 재생 완료가 아닌 실행 요청입니다. 좌표나 내비 POI ID가 없는 장소는 내비 효과를 반환하지 않습니다. 관광 API의 콘텐츠 ID와 Pleos NaviHelper의 POI ID는 별개로 관리합니다.

정차 중 카드에서 장소를 고를 때는 `selected_place_id`를 보낼 수 있습니다. 서버는 같은 세션에서 최근 2분 안에 제시한 장소만 받습니다. 곡의 `selected_track_id`도 최근 5분 안에 추천한 곡과 일치해야 합니다. 오래된 ID나 다른 세션의 ID를 요청에 넣어도 선택 상태가 되살아나지 않습니다.

## `POST /v1/trip/record`

```json
{"session_id":"demo-1","type":"mention","id":"place-1","name":"별빛 카페","category":"cafe"}
```

`type`은 `mention`, `stop`, `capsule` 중 하나입니다. `stop`·`capsule`에는 `id`만 필요합니다. 성공하면 `{"status":"ok"}`를 반환합니다. 이 API는 음성 파일을 업로드하지 않고 **기록 메타데이터만** 서버 DB(`trip_events`)에 저장합니다. 서버를 다시 켜도 같은 `session_id`의 기록이 남습니다. 저장 구조는 [docs/db](../db/README.md)를 따릅니다.

`GET /health`는 서버 동작 상태를 알려 줍니다. 잘못된 요청은 HTTP 400과 `{"error":"invalid_request"}`, 없는 경로는 404를 반환합니다. 서버가 닿지 않으면 앱은 연결 실패 자막을 보여 줍니다.

## Android 시연 연결

디버그 앱은 `BuildConfig.AGENT_BASE_URL=http://10.0.2.2:8765`를 사용하고 HTTP 통신을 허용합니다. 릴리스 빌드의 주소는 비어 있어 운영 서버를 결정하기 전에는 AI 호출을 시작하지 않습니다. 앱은 정차 중 텍스트 입력 시연 패널, 주행 중 음성 입력 버튼·한 줄 자막을 사용합니다. 음성 인식과 TTS는 현재 Android 기본 서비스를 호출하므로 Pleos 전용 음성 서비스 연동과 실차 품질 검증이 남아 있습니다. 차량 기어 값도 아직 `FakeVehicleStateSource`에서 옵니다.

경유지 효과는 [Pleos NaviHelper v3.0.5 경유지 API](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/waypoint-settings)에 전달합니다. 앱은 SDK 연결 후 요청을 보내고 오류 콜백을 자막으로 표시합니다. 실제 경유지 반영 여부는 Pleos 전용 에뮬레이터에서 확인해야 합니다.
