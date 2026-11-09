# 로컬 AI 연동 서버

Python 표준 HTTP 서버로 만든 **시연용** 서버입니다. 저장소 루트에서 실행합니다.

```powershell
uv run python -m server.main
uv run pytest server/tests
```

서버는 `127.0.0.1:8765`에만 바인딩합니다. Android 에뮬레이터에서는 `http://10.0.2.2:8765`로 접근합니다. 요청에 시각이 없으면 한국 시간(UTC+09:00)을 사용합니다. 여행 기록은 `server/var/doran.db`(SQLite)에 저장되고, 대화 맥락은 메모리에만 있어 서버를 다시 켜면 사라집니다. DB 주소는 `DORAN_DATABASE_URL`로 바꿉니다. 인증이나 운영용 배포 설정은 아직 없습니다.

기본 장소·휴게소·곡 목록은 동작 확인을 위한 시연 데이터입니다. 실제 후보를 넣으려면 `DORAN_CATALOG_FILE`에 UTF-8 JSON 파일 경로를 지정합니다. 최상위 키는 `places`, `rest_stops`, `tracks`이며 각 항목에는 해당 `source`, `id`, `name`이 필요합니다. 장소·휴게소에는 검증된 `latitude`·`longitude`도 있어야 합니다. 실제 내비 경유지 추가에는 별도로 확인한 `navi_poi_id`가 필요합니다. 데이터 수집 담당이 실제 공급자 응답과 경로 적합성을 확인한 뒤 파일을 생성해야 합니다. 이 서버는 외부 API를 직접 호출하지 않습니다.

API는 [docs/api](../docs/api/README.md), 세션 데이터는 [docs/db](../docs/db/README.md), AI 입력·출력은 [docs/ai](../docs/ai/README.md)를 따릅니다.
