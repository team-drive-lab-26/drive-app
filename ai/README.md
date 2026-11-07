# 도란 AI 에이전트

차량 상태와 여행 기록을 받아 음성 응답, 화면 카드, 서버 실행 요청을 결정하는 Python 3.10+ 모듈입니다. 표준 라이브러리만 사용합니다. 상태 저장, 외부 데이터 조회, 내비·음악 실행은 서버와 앱이 맡습니다.

## 로컬 실행

`ai` 폴더에서 다음 명령을 실행합니다.

```powershell
Get-Content -Raw examples/recommend_place.json | python -m doran_ai
python -m unittest discover -s tests -v
python -m examples.run_scenario
```

서버에서는 `from doran_ai import DoranAgent`로 불러 `DoranAgent().handle(payload)`를 호출할 수 있습니다. 요청마다 새 인스턴스를 만들 필요는 없습니다. 입력·출력 계약은 [AI 연동 문서](../docs/ai/README.md)에 있습니다.

`examples.run_scenario`는 출발 → 음악 추천·재생 요청 → 휴게소 제안·확인 → 기억 조회 → 귀가 중 음성 캡슐 재생 요청을 보여 줍니다. 서버의 제안 이력과 확인 대기 상태를 메모리에서 흉내 내며 실제 내비·음악·녹음 서비스를 호출하지 않습니다.

## 선택형 LLM 연결

장소, 도착, 휴게소, 음악, 기억, 다시 듣기 외의 표현을 분류할 때만 OpenAI 호환 chat completions 엔드포인트를 사용할 수 있습니다. 모델 공급사가 미정이라 기본값은 로컬 규칙입니다. 외부 모델에는 발화 텍스트만 보내고, 위치·차량 상태·여행 기록은 보내지 않습니다. 네트워크 오류가 나면 로컬 규칙으로 돌아갑니다.

```powershell
$env:DORAN_LLM_ENDPOINT = "https://<provider>/v1/chat/completions"
$env:DORAN_LLM_MODEL = "<model>"
$env:DORAN_LLM_API_KEY = "<secret>"
```

실제 서비스에서는 서버 비밀 저장소에서 환경 변수를 주입해야 합니다. `.env`는 Git에서 제외합니다. 이 어댑터는 공급사 선택 후 해당 API로 별도 통합 검증이 필요합니다.
