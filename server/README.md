# 로컬 AI 연동 서버

Python 3.10+ 표준 라이브러리로 만든 **시연용** HTTP 서버입니다. 저장소 루트에서 실행합니다.

```powershell
python -m server.main
python -m unittest discover -s server/tests -v
```

서버는 `127.0.0.1:8765`에만 바인딩합니다. Android 에뮬레이터에서는 `http://10.0.2.2:8765`로 접근합니다. 요청에 시각이 없으면 한국 시간(UTC+09:00)을 사용합니다. 세션은 메모리에만 보관하므로 서버를 다시 켜면 기록이 사라집니다. 인증이나 운영용 배포 설정은 아직 없습니다.

`DORAN_LLM_ENDPOINT`, `DORAN_LLM_MODEL`, `DORAN_LLM_API_KEY`를 설정하면 서버도 원격 LLM을 사용해 규칙에 없는 발화를 분류합니다. OpenRouter 무료 모델 설정과 제한 시간은 [ai/README.md](../ai/README.md)를 참고합니다. 모델에 보내는 값은 발화 텍스트뿐이며, 호출 실패 시 규칙 기반 처리로 돌아갑니다.

기본 장소·휴게소·곡 목록은 동작 확인을 위한 시연 데이터입니다. 실제 후보를 넣으려면 `DORAN_CATALOG_FILE`에 UTF-8 JSON 파일 경로를 지정합니다. 최상위 키는 `places`, `rest_stops`, `tracks`이며 각 항목에는 해당 `source`, `id`, `name`이 필요합니다. 장소·휴게소에는 검증된 `latitude`·`longitude`도 있어야 합니다. 실제 내비 경유지 추가에는 별도로 확인한 `navi_poi_id`가 필요합니다. 데이터 수집 담당이 실제 공급자 응답과 경로 적합성을 확인한 뒤 파일을 생성해야 합니다. 이 서버는 외부 API를 직접 호출하지 않습니다.

API는 [docs/api](../docs/api/README.md), 세션 데이터는 [docs/db](../docs/db/README.md), AI 입력·출력은 [docs/ai](../docs/ai/README.md)를 따릅니다.
