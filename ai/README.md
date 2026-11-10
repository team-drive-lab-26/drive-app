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

기존 규칙에 없는 표현을 분류할 때만 OpenAI 호환 chat completions 엔드포인트를 호출합니다. 분류 결과는 `place`, `arrival`, `rest`, `music`, `memory`, `replay`, `unknown` 중 하나로 제한합니다. 장소·곡·답변은 검증된 후보와 기존 규칙으로 결정합니다. 모델에는 발화 텍스트만 보내고 위치·차량 상태·여행 기록은 보내지 않습니다. 모델 호출에 실패하면 규칙 기반 처리로 돌아갑니다.

원격 시연 모델은 OpenRouter의 [Nemotron 3.5 Lightning 무료 엔드포인트](https://openrouter.ai/nvidia/nemotron-3.5-lightning%3Afree/)로 확인했습니다. [NVIDIA가 공개한 모델](https://developer.nvidia.com/blog/nvidia-nemotron-3-5-lightning-delivers-fast-accurate-specialized-task-execution-for-long-running-agents/)이며, OpenRouter의 무료 요청에는 계정 API 키와 호출 제한이 있습니다. 모델 비용이 $0이어도 서비스 용량에 따라 요청이 거절될 수 있습니다.

```powershell
if (-not $env:OPENROUTER_API_KEY) { throw "OpenRouter API 키가 필요합니다" }
$env:DORAN_LLM_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
$env:DORAN_LLM_MODEL = "nvidia/nemotron-3.5-lightning:free"
$env:DORAN_LLM_API_KEY = $env:OPENROUTER_API_KEY
$env:DORAN_LLM_TIMEOUT_SECONDS = "12"
Get-Content -Raw examples/llm_recommend_place.json | python -m doran_ai
```

`OPENROUTER_API_KEY`는 사용자가 발급받아 환경 변수로 넣어야 합니다. 키 값은 저장소에 적지 않습니다. 이 예시 발화는 기존 규칙에 걸리지 않아 LLM 분류 경로를 탑니다. 기본 제한 시간은 12초이며 `DORAN_LLM_TIMEOUT_SECONDS`로 0초 초과 60초 이하의 값을 지정할 수 있습니다. Android 디버그 앱의 서버 호출 제한 시간은 20초이므로 앱 시연에는 모델 제한 시간을 그보다 짧게 둡니다. 같은 환경 변수를 설정하고 저장소 루트에서 `python -m server.main`을 실행하면 앱의 요청에도 LLM 분류가 적용됩니다.

OpenAI API를 포함한 다른 OpenAI 호환 공급자도 HTTPS 주소, 모델 이름, 해당 공급자의 API 키로 바꿔 연결할 수 있습니다.

```powershell
$env:DORAN_LLM_ENDPOINT = "https://<provider>/v1/chat/completions"
$env:DORAN_LLM_MODEL = "<model>"
$env:DORAN_LLM_API_KEY = "<secret>"
```

실제 서비스에서는 서버 비밀 저장소에서 환경 변수를 주입해야 합니다. `.env`는 Git에서 제외합니다. 무료 모델의 응답 지연과 호출 제한은 차내 운영 기준으로 따로 검증해야 합니다.
