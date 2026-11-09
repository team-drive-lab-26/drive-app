# 도란 AI 연동 계약 (초안)

현재 MVP는 **차량 맥락 기반 제안 + 사용자 발화 응답**입니다. 3D 여행 앨범은 여행 기록과 음성 캡슐을 다시 들을 때 사용하며, AI가 지도 화면 자체를 그리지는 않습니다. 서버가 외부 API와 차량 신호를 정리해 AI에 전달하고, AI는 부작용 없는 판단 결과만 반환합니다.

## 실행 범위

- 능동 제안: 여행 출발, 식사 시간에 가까운 도착, 휴식 후 재탑승, 110분 이상 연속 운전, 귀가 출발.
- 발화 응답: 장소·휴게소·도착 시간·음악 추천, 앞서 언급한 장소 찾기, 여행 기록 재생, 조용히 모드 켜기·끄기.
- 차량 주행 중에는 카드 목록을 반환하지 않습니다. 경유지 추가는 선택된 장소와 별도의 확인 발화가 있어야 실행 요청을 냅니다.
- 장소는 관광 데이터, 휴게소는 고속도로 데이터, 음악은 팀이 제공하는 곡 목록에서만 고릅니다. AI가 이름·좌표·음원을 만들어 내지 않습니다.

## 입력

서버가 `DoranAgent.handle(payload)`에 JSON 객체를 전달합니다. 모든 요청에 `kind`, 시간대가 포함된 ISO 8601 `now`, `vehicle.gear`(`P/R/N/D`)가 필요합니다.

| 필드 | 내용 |
| --- | --- |
| `kind` | `vehicle_event` 또는 `user_utterance` |
| `event` | 차량 이벤트인 경우 `trip_started`, `arrival_30m`, `reboarded`, `continuous_drive`, `return_started` 중 하나 |
| `utterance` | 사용자 발화인 경우 한국어 문자열 |
| `vehicle` | `gear`, 선택적으로 `doors_closed`, `last_stop_minutes`, `continuous_driving_minutes` |
| `navigation` | 선택적으로 `distance_from_home_km`, `eta_minutes`, `arrival_local_time`(`HH:MM`), `destination_is_home`, `minutes_since_departure` |
| `places` | `id`, `name`, `source: "tour_api"`, `category`, `along_route`가 있는 후보 목록 |
| `rest_stops` | `id`, `name`, `source: "expressway_api"`, `along_route`, 선택적으로 `eta_minutes`가 있는 후보 목록 |
| `tracks` | `id`, `name`, `source: "catalog"`, 선택적으로 `tags`(`travel`, `bright`, `refresh`, `calm`)가 있는 곡 목록 |
| `trip` | 선택적으로 `mentions`(언급 장소 이름·카테고리), `stops`(방문 기록), `capsules`(음성 캡슐 ID) |
| `preferences` | `quiet: true`이면 능동 제안을 끔 |
| `recent_proposals` | 서버에 저장한 최근 제안 `[{"type": "rest", "at": "..."}]` |
| `selected_place_id`, `selected_track_id` | 앱에서 사용자가 고른 후보의 ID. 서버가 같은 세션에서 최근 제시한 후보인지 확인한 뒤 AI에 전달 |
| `pending_waypoint_id` | 서버에 보관된, 확인을 기다리는 경유지 ID |
| `network_available` | `false`이고 전달된 장소 후보도 없으면 신규 장소 검색 응답을 보류 |

입력 예시는 [recommend_place.json](../../ai/examples/recommend_place.json)을 참조합니다. `source` 문자열만으로 출처가 증명되는 것은 아닙니다. 서버가 실제 API 응답을 확인하고 ID·좌표·경로 적합성을 검증한 뒤 목록을 구성해야 합니다. `along_route: true`가 명시되지 않은 장소는 추천하지 않습니다.

## 출력

모든 응답은 다음 키를 갖습니다. `status`는 `ok`, `no_action`, `needs_confirmation`, `unavailable`, `invalid_input` 중 하나입니다.

| 필드 | 사용처 |
| --- | --- |
| `speech` | TTS에 전달할 짧은 한국어 문장. 빈 값이면 말하지 않음 |
| `caption` | 화면에 보여줄 짧은 자막 |
| `proposal_type` | 능동 제안 종류. 서버가 시각과 함께 기록해 중복 제안을 막음 |
| `selected_place_id`, `selected_track_id` | 후보를 다시 찾을 수 있는 ID |
| `cards` | 정차 중에만 보여줄 카드 목록 |
| `commands` | 서버·앱이 검증 후 실행할 요청 목록. AI가 직접 내비나 플레이어를 조작하지 않음 |

`commands.type`은 `SET_QUIET`, `CLEAR_PENDING`, `SET_PENDING_WAYPOINT`, `ADD_WAYPOINT`, `SAVE_PLACE`, `PLAY_MUSIC`, `PLAY_CAPSULE` 중 하나입니다. 추천 후 `SET_PENDING_WAYPOINT`는 확인 대기 상태만 저장합니다. 서버는 새 주제가 나오면 이전 대기를 지우고, 2분 뒤 자동 만료합니다. 선택 장소도 2분, 선택 곡은 5분 뒤 만료합니다. 뒤따른 명시적 “응”에만 `ADD_WAYPOINT`가 나옵니다. 서버는 ID가 현재 사용자 세션의 후보 목록에 있는지 다시 확인하고, 내비·음악 서비스의 성공/실패를 앱에 알려야 합니다. `PLAY_MUSIC`은 사용자가 “틀어 줘”라고 말할 때만 나오며, 추천만으로 자동 재생하지 않습니다. `PLAY_CAPSULE`은 접근 권한이 있는 저장 기록에만 실행해야 합니다.

## 능동 제안 기준과 실패 시 동작

현재 구현 기본값은 21:00~07:00 제안 금지, 한 시간에 최대 2회, 같은 종류는 하루 1회입니다. 이는 시연용 초기값으로 팀의 UX 검토가 필요합니다. 서버는 제안을 실제로 전달했을 때만 `recent_proposals`에 저장해야 합니다. 다만 시연 서버는 응답을 생성할 때 기록하며, 앱의 음성 전달 성공 여부는 확인하지 않습니다. 전달 확인 방식은 앱·서버 연동 때 정해야 합니다. `reboarded`는 문이 닫히고 변속기가 `P`이며 15분 넘게 정차했을 때, `return_started`는 귀가 목적지 설정 10분 뒤 여행 기록이 있을 때만 제안합니다.

외부 데이터가 없으면 `unavailable`과 이유를 반환합니다. 통신이 끊겨도 서버가 유효한 장소 후보를 전달하면 그 후보로 답합니다. 잘못된 입력은 `invalid_input`을 반환하고 차내 TTS 문구는 비웁니다. LLM은 선택형 발화 분류기이며 장애 시 로컬 규칙으로 처리합니다. 기록 검색은 현재 구조화된 `trip.mentions` 조회입니다. 임베딩·벡터 검색, 차량 데이터 실연동, 브이월드 3D 지도와 음원 사업자 연동은 이 모듈 밖의 후속 작업입니다.

## 현재 연결과 남은 작업

로컬 Python HTTP 서버가 AI 모듈을 호출하고, Android 디버그 앱이 이 서버에 요청합니다. 서버는 세션별 제안 이력, 후보 선택, 확인 대기, 조용히 설정을 메모리에 보관합니다. 서버를 다시 켜면 상태가 지워집니다.

1. **운영 서버:** 서버 배포 방식, 인증, 영속 저장소를 정하고 현재 로컬 호출 경로를 옮깁니다.
2. **데이터 수집:** 앱/서버가 차량 기어·문·주행 시간과 내비 ETA를 공급하고, 관광지·휴게소·음악 후보를 실제 데이터에서 구성합니다. 후보의 ID, 좌표, 경로 적합성을 서버가 검증합니다.
3. **명령 실행:** `ADD_WAYPOINT`는 Pleos SDK 호출 코드까지 있으며 전용 에뮬레이터에서 검증해야 합니다. `PLAY_MUSIC`, `PLAY_CAPSULE`은 실제 재생 기능에 연결하고 성공/실패를 앱에 돌려줘야 합니다.
4. **차내 UX 검증:** 주행 중에는 음성·자막 한 줄만 사용하고, 정차 시 카드와 3D 앨범을 표시합니다. 제안 빈도와 발화 문구는 차내 시연으로 조정합니다.
5. **공급사 확정:** LLM, 음원, 지도·관광 데이터의 사용 가능 범위와 인증 방식을 확정한 뒤 실제 연동 테스트를 합니다.

## 검증 상태

AI 단위 테스트 13개와 서버 테스트 14개가 로컬에서 통과했습니다. 서버 테스트에는 출발부터 귀가까지의 시나리오, 중복 제안, 후보 선택 만료, 잘못된 입력과 통신 불가 시 응답이 포함됩니다. Android 디버그 APK도 빌드했습니다. Pleos 전용 에뮬레이터, 실제 관광·휴게소·음원 데이터, 내비 POI ID 매핑, 브이월드 3D 지도는 아직 연결하지 않았습니다. 후보 데이터의 경로 적합성(`along_route`)은 외부 조회만으로 결정하지 않고 실제 경로 정보로 검증해야 합니다.
