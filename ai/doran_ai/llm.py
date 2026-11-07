"""공급사를 확정하기 전까지 사용할 수 있는 선택형 LLM 분류기."""

from __future__ import annotations

import json
import os
from typing import Protocol
from urllib import request


INTENTS = {"place", "arrival", "rest", "music", "memory", "replay", "unknown"}


class IntentClassifier(Protocol):
    def classify(self, utterance: str) -> str: ...


class ChatCompletionsClassifier:
    """OpenAI 호환 chat/completions 응답에서 허용된 의도만 추출한다.

    발화 외의 차량 위치와 여행 기록은 모델로 보내지 않는다.
    네트워크·모델 오류 시 상위 에이전트가 규칙 기반 응답으로 돌아간다.
    """

    def __init__(self, endpoint: str, model: str, api_key: str, timeout: float = 2.5):
        if not endpoint.startswith("https://"):
            raise ValueError("DORAN_LLM_ENDPOINT는 HTTPS 주소여야 합니다")
        if not model or not api_key:
            raise ValueError("모델 이름과 API 키가 필요합니다")
        self.endpoint = endpoint
        self.model = model
        self.api_key = api_key
        self.timeout = timeout

    @classmethod
    def from_env(cls) -> ChatCompletionsClassifier | None:
        endpoint = os.getenv("DORAN_LLM_ENDPOINT", "")
        model = os.getenv("DORAN_LLM_MODEL", "")
        api_key = os.getenv("DORAN_LLM_API_KEY", "")
        if not (endpoint and model and api_key):
            return None
        return cls(endpoint, model, api_key)

    def classify(self, utterance: str) -> str:
        body = {
            "model": self.model,
            "messages": [
                {
                    "role": "system",
                    "content": (
                        "차량 여행 앱의 발화를 분류한다. 장소 추천 place, 도착 시각 arrival, "
                        "휴게소 rest, 노래 music, 앞서 언급한 장소 memory, 여행 다시 듣기 replay, "
                        "그 밖에는 unknown. 설명 없이 JSON 객체 {\"intent\":\"...\"}만 출력한다."
                    ),
                },
                {"role": "user", "content": utterance[:300]},
            ],
            "temperature": 0,
        }
        req = request.Request(
            self.endpoint,
            data=json.dumps(body).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        with request.urlopen(req, timeout=self.timeout) as response:
            content = json.load(response)["choices"][0]["message"]["content"]
        intent = json.loads(content)["intent"]
        return intent if intent in INTENTS else "unknown"
