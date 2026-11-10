"""OpenAI 호환 원격 모델의 의도 분류와 음성 문장 생성."""

from __future__ import annotations

import json
import os
import re
from typing import Protocol
from urllib import request
from urllib.parse import urlsplit

INTENTS = {"place", "arrival", "rest", "music", "memory", "replay", "unknown"}


class IntentClassifier(Protocol):
    def classify(self, utterance: str) -> str: ...


class ResponseWriter(Protocol):
    def rewrite(self, draft: str, utterance: str, protected_terms: list[str]) -> str: ...


class ChatCompletionsClassifier:
    """OpenAI 호환 chat/completions로 의도를 분류하고 짧은 답변을 만든다.

    원본 차량 위치와 여행 기록은 보내지 않는다. 응답 생성 시 규칙이 만든
    초안에는 후보 이름이나 도착 시간 같은 정보가 포함될 수 있다.
    """

    def __init__(self, endpoint: str, model: str, api_key: str, timeout: float = 12.0):
        parsed = urlsplit(endpoint)
        if parsed.scheme != "https" or not parsed.netloc or parsed.username or parsed.password:
            raise ValueError("LLM 주소는 HTTPS여야 합니다")
        if not model or not api_key:
            raise ValueError("모델 이름과 API 키가 필요합니다")
        if not 0 < timeout <= 60:
            raise ValueError("LLM 제한 시간은 0초 초과 60초 이하여야 합니다")
        self.endpoint = endpoint
        self.model = model
        self.api_key = api_key
        self.timeout = timeout

    @classmethod
    def from_env(cls) -> ChatCompletionsClassifier | None:
        endpoint = os.getenv("DORAN_LLM_ENDPOINT", "")
        model = os.getenv("DORAN_LLM_MODEL", "")
        api_key = (
            os.getenv("GROQ_API_KEY", "") or os.getenv("DORAN_LLM_API_KEY", "")
            if urlsplit(endpoint).hostname == "api.groq.com"
            else os.getenv("DORAN_LLM_API_KEY", "")
        )
        if not (endpoint and model and api_key):
            return None
        timeout = os.getenv("DORAN_LLM_TIMEOUT_SECONDS")
        return cls(endpoint, model, api_key, float(timeout) if timeout else 12.0)

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
        if self._groq_structured:
            body.update(self._groq_options("intent", {"type": "string", "enum": sorted(INTENTS)}))
        intent = json.loads(self._complete(body))["intent"]
        return intent if intent in INTENTS else "unknown"

    def rewrite(self, draft: str, utterance: str, protected_terms: list[str]) -> str:
        """검증된 초안만 다듬는다. 실행 판단은 이 응답을 사용하지 않는다."""
        body = {
            "model": self.model,
            "messages": [
                {
                    "role": "system",
                    "content": (
                        "차량 여행 도우미의 한국어 음성 문장을 작성한다. 제공된 초안의 사실과 질문 의도를 "
                        "그대로 유지하고 자연스러운 짧은 한 문장으로 다듬어라. 새 장소·곡·시간·거리·수치·"
                        "행동 완료 사실을 만들지 마라. 보호 단어는 정확히 그대로 사용하라. "
                        "명령이나 개인정보를 요청하지 마라. JSON 객체 {\"speech\":\"...\"}만 출력하라."
                    ),
                },
                {"role": "user", "content": json.dumps({
                    "draft": draft,
                    "utterance": utterance[:300],
                    "protected_terms": protected_terms,
                }, ensure_ascii=False)},
            ],
            "temperature": 0,
            "response_format": {"type": "json_object"},
            "max_tokens": 512,
        }
        if self._groq_structured:
            body.pop("max_tokens")
            body.update(self._groq_options("speech", {"type": "string"}))
        speech = json.loads(self._complete(body))["speech"]
        if not isinstance(speech, str):
            raise ValueError("LLM 문장은 문자열이어야 합니다")
        speech = speech.strip()
        if not speech or len(speech) > 100 or "\n" in speech or "\r" in speech or "<" in speech or ">" in speech:
            raise ValueError("LLM 문장 형식이 올바르지 않습니다")
        if any(term not in speech for term in protected_terms):
            raise ValueError("LLM 문장에서 검증된 값이 빠졌습니다")
        if set(re.findall(r"\d+(?:\.\d+)?", speech)) - set(re.findall(r"\d+(?:\.\d+)?", draft)):
            raise ValueError("LLM 문장에 없는 수치가 추가됐습니다")
        if draft.endswith("?") != speech.endswith("?"):
            raise ValueError("LLM 문장의 질문 의도가 달라졌습니다")
        if re.search(r"https?://|www\.|[{}\[\]]", speech):
            raise ValueError("LLM 문장에 허용하지 않는 형식이 있습니다")
        return speech

    @property
    def _groq_structured(self) -> bool:
        return (urlsplit(self.endpoint).hostname == "api.groq.com"
                and self.model in {"openai/gpt-oss-20b", "openai/gpt-oss-120b", "qwen/qwen3.8-27b"})

    def _groq_options(self, field: str, field_schema: dict) -> dict:
        return {
            "response_format": {
                "type": "json_schema",
                "json_schema": {
                    "name": f"doran_{field}",
                    "strict": True,
                    "schema": {
                        "type": "object",
                        "properties": {field: field_schema},
                        "required": [field],
                        "additionalProperties": False,
                    },
                },
            },
            "reasoning_effort": "low" if self.model.startswith("openai/") else "none",
            "include_reasoning": False,
            "max_completion_tokens": 512,
        }

    def _complete(self, body: dict) -> str:
        req = request.Request(
            self.endpoint,
            data=json.dumps(body).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
                "User-Agent": "DoranDemo/0.1",
            },
            method="POST",
        )
        with request.urlopen(req, timeout=self.timeout) as response:
            return json.load(response)["choices"][0]["message"]["content"]
