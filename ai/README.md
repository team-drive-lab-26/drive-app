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

## Groq LLM 연결

Groq `openai/gpt-oss-20b`는 규칙에 없는 발화의 의도를 분류하고, 규칙이 만든 답변 초안을 짧은 한국어 음성 문장으로 다듬습니다. 모델은 장소·곡·명령을 결정하지 않습니다. 이름·수치·질문 형식을 검사한 문장만 사용하며, 검증 실패나 통신 오류가 나면 초안으로 돌아갑니다. 경유지 추가·음악 재생처럼 실행 명령이 있는 응답은 문장 생성 대상에서 제외합니다.

저장소 루트의 `.env.example`을 참고해 Git에서 제외된 `.env`를 만드세요. Groq 콘솔에서 발급한 키를 `GROQ_API_KEY`에 넣고 서버를 실행합니다.

```dotenv
DORAN_LLM_ENDPOINT=https://api.groq.com/openai/v1/chat/completions
DORAN_LLM_MODEL=openai/gpt-oss-20b
GROQ_API_KEY=<Groq에서 발급한 키>
DORAN_LLM_TIMEOUT_SECONDS=8
```

```powershell
uv run --package doran-server python -m server.main
```

서버는 `.env`를 읽습니다. `DORAN_LLM_API_KEY`도 사용할 수 있지만 Groq 연결에는 `GROQ_API_KEY`를 권장합니다. 의도 분류에는 발화만 보내고, 문장 생성에는 답변 초안·발화·초안에 나온 후보 이름을 보냅니다. 원본 GPS 좌표와 차량·여행 기록 전체는 보내지 않습니다. `.env`와 키는 커밋하지 마세요.

앱의 서버 호출 제한 시간은 20초입니다. 규칙에 없는 발화는 분류와 문장 생성으로 모델을 최대 두 번 호출할 수 있어 기본 제한 시간을 호출당 8초로 뒀습니다. [Groq 요금 및 한도](https://console.groq.com/docs/rate-limits)는 계정 설정과 서비스 상태에 따라 달라지므로 시연 전에 확인하세요. 다른 OpenAI 호환 공급자를 쓰려면 HTTPS 엔드포인트, 모델, `DORAN_LLM_API_KEY`를 바꾸면 됩니다.
