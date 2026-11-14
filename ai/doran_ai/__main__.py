"""표준 입력 JSON 한 건을 처리하는 로컬 시연·서버 연동 진입점."""

import json
import sys

from .agent import DoranAgent
from .llm import ChatCompletionsClassifier


def main() -> None:
    try:
        payload = json.load(sys.stdin)
        classifier = ChatCompletionsClassifier.from_env()
        result = DoranAgent(classifier).handle(payload)
    except (OSError, ValueError, json.JSONDecodeError):
        result = {
            "status": "invalid_input", "speech": "", "caption": "",
            "proposal_type": None, "selected_place_id": None,
            "selected_track_id": None, "cards": [], "commands": [],
        }
    json.dump(result, sys.stdout, ensure_ascii=False)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
