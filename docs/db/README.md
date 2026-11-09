# 로컬 시연 세션 데이터

현재 서버에는 DB가 없습니다. `server/main.py`가 `session_id`별 `Session` 객체를 메모리에 보관하며 재시작하면 모두 지워집니다.

| 값 | 용도 |
| --- | --- |
| `quiet` | 먼저 말 걸지 않기 설정 |
| `pending_waypoint_id`, `pending_until` | 경유지 추가 확인 대기, 2분 뒤 만료 |
| `selected_place_id`, `selected_track_id` | 다음 발화의 선택 맥락. 장소는 2분, 곡은 5분 뒤 만료 |
| `offered_place_ids` | 같은 세션에 제시한 장소 카드의 ID. 장소 선택 맥락과 함께 2분 뒤 만료 |
| `recent_proposals` | 중복 제안 제한용 종류·시각 |
| `saved_place_ids` | 저장한 장소 ID |
| `trip.mentions`, `trip.stops`, `trip.capsules` | 장소 언급, 방문, 음성 캡슐 메타데이터 |

음성 원본 파일은 저장하지 않습니다. 실제 서비스에서는 사용자 인증, 저장 기간, 세션 만료, 기록 접근 권한, 파일 저장소와 DB 스키마를 정해야 합니다.
